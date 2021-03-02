package optimizer;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import com.google.ortools.linearsolver.MPSolver.ResultStatus;

import optimizer.Pipe.FlowType;
import structs.CommercialPipeStruct;
import structs.EsrCostStruct;
import structs.EsrGeneralStruct;
import structs.GeneralStruct;
import structs.NodeStruct;
import structs.PipeStruct;
import structs.PumpGeneralStruct;
import structs.PumpManualStruct;
import structs.ValveStruct;


//import net.sf.javailp.Constraint;
//import net.sf.javailp.Linear;
//import net.sf.javailp.OptType;
//import net.sf.javailp.Problem;
//import net.sf.javailp.Result;
//import net.sf.javailp.Solver;
//import net.sf.javailp.SolverFactory;
//import net.sf.javailp.SolverFactoryGLPK;


//this class is responsible for optimization of the network
public class Optimizer {
	
	private HashMap<Integer,Node> nodes; // nodeID,node
	private HashMap<Integer,Pipe> pipes; //pipeid,pipe
	private List<PipeCost> pipeCost;
	private List<EsrCost> esrCost;
	
	//following three are data structures received from server request
	private GeneralStruct generalProperties;
	private EsrGeneralStruct esrGeneralProperties;
	private PumpGeneralStruct pumpGeneralProperties;
	
	private Node source;	//source node of the network
	private double totalDemand;	//total demand of the network in litres/sec
	
	private Problem problem;	//contains the ILP model
	
	//helper strings for exporting the ILP model 
	private StringBuilder lpmodelstring = new StringBuilder();
	private StringBuilder lpmodelvar = new StringBuilder();
	
//	private static boolean esrCosting = true;
//	private static boolean esrGen = true;
//	private static boolean removeZeroDemandNodes = false;
	
	
	//0 : only pipe optmization, used by default if no esr costing
	//1 : esr costing with only demand nodes allowed
	//2 : general esr with any node allowed
	//3 : gen2 with option to remove certain nodes as esr candidates
	//4 : use l_i_j_k instead of l_i_j
	//5 : better way to compute z_i_j
	//6 : replace how to implement constraint s_i_i = 0 => s_k_i = s_k_p
	//7 : replace above constraint with f_k=1 => sum(s_i_j)=0 and s_i_i=0=>sum(s_i_j)=0
	//8 : added pumps and valves to the optimization
	//9 : pruned some ESR cost rows, depending on the potential downstream demand
	//10: remove all sij variables and instead of node based model, use an edge based model
	private int modelNumber = 0;
	
	
	//default values are provided for following parameters, but are typically overridden by user in the server request
	
	// flow in secondary = factor * flow in primary
	// primary pumping hours = factor * secondary pumping hours
	private double secondaryFlowFactor = 2; 
	
	// ratio of size of ESR to the daily demand of the nodes it serves
	private double esrCapacityFactor = 1; 
	
	// minimum and maximum height allowed for an ESR
	private double minEsrHeight = 0;
	private double maxEsrHeight = 25;
	
	// minimum and maximum power allowed for a pump
	private double minPumpPower = 1;
	private double maxPumpPower = 10000;
	
	// maximum pressure head that can be provided by a pump
	private int maxPumpHead = 10000;
	
	//string used for EPANET output file
	private String coordinatesString;
	
	//container for pump and valve information
	private PumpManualStruct[] pumpManualArray;
	private ValveStruct[] valves;
	
	//create an instance of optimizer  
	public Optimizer(NodeStruct[] nodeStructs, PipeStruct[] pipeStructs, CommercialPipeStruct[] commercialPipeStructs, GeneralStruct generalStruct, EsrGeneralStruct esrGeneralProperties, EsrCostStruct[] esrCostsArray, PumpGeneralStruct pumpGeneralProperties, PumpManualStruct[] pumpManualArray, ValveStruct[] valves) throws Exception{
		nodes = new HashMap<Integer, Node>();
		pipes = new HashMap<Integer, Pipe>();
		pipeCost = new ArrayList<PipeCost>();
		esrCost = new ArrayList<EsrCost>();
		coordinatesString = "";
		generalProperties = generalStruct;
		
		int[] a = {};
		this.pumpGeneralProperties = new PumpGeneralStruct(false, 1, 100, 0, 0, 1, 0, 0, 1, a);
				
		
		Set<Integer> usedNodeIDs = new HashSet<Integer>();
		
		//initialize source node
		source = new Node(generalProperties.source_elevation, 0, generalProperties.source_nodeid, 0, generalProperties.source_nodename, 24/generalProperties.supply_hours, usedNodeIDs);
		source.setAllowESR(false);
		usedNodeIDs.add(source.getNodeID());
		source.setHead(generalProperties.source_head);
		nodes.put(source.getNodeID(), source);
		
		//initialize all the other nodes
		for(NodeStruct node : nodeStructs){
			double minPressure = node.minpressure == 0 ? generalProperties.min_node_pressure : node.minpressure;
			Node n = new Node(node.elevation, node.demand, node.nodeid, minPressure, node.nodename, 24/generalProperties.supply_hours, usedNodeIDs);
			usedNodeIDs.add(n.getNodeID());
			nodes.put(n.getNodeID(), n);
		}
		
		Set<Integer> usedPipeIDs = new HashSet<Integer>();
		
		//initialize the pipes 
		for(PipeStruct pipe : pipeStructs){
			double roughness = pipe.roughness == 0 ? generalProperties.def_pipe_roughness : pipe.roughness;
			
			Node startNode = nodes.get(pipe.startnode);
            if(startNode==null){
            	throw new Exception("Invalid startNode:" + pipe.startnode + " provided for pipe ID:"+pipe.pipeid);
            }
            
            Node endNode = nodes.get(pipe.endnode);
            if(endNode==null){
            	throw new Exception("Invalid endNode:" + pipe.endnode + " provided for pipe ID:"+pipe.pipeid);
            }
			
			Pipe p = new Pipe(pipe.length, startNode, endNode, pipe.diameter, roughness, pipe.pipeid, pipe.parallelallowed, usedPipeIDs);
			usedPipeIDs.add(p.getPipeID());
			pipes.put(p.getPipeID(), p);
		}
		
		//initialize the commercial pipe information
		for(CommercialPipeStruct commercialPipe : commercialPipeStructs){
			double roughness = commercialPipe.roughness == 0 ? generalProperties.def_pipe_roughness : commercialPipe.roughness;
			pipeCost.add(new PipeCost(commercialPipe.diameter, commercialPipe.cost, Double.MAX_VALUE, roughness));
		}
		this.valves = valves;
		
		//default model number is 0 for only pipe optimization
		modelNumber = 0;
		
		//if ESR optimization enabled, initialize ESR properties and set modelnumber
		if(esrGeneralProperties!=null && esrGeneralProperties.esr_enabled){
			this.esrGeneralProperties = esrGeneralProperties;
			
			if(esrGeneralProperties.secondary_supply_hours==0){
				throw new Exception("ESR option is enabled, but secondary supply hours is provided as zero.");
			}
			
			if(esrGeneralProperties.esr_capacity_factor==0){
				throw new Exception("ESR option is enabled, but esr capacity factor is provided as zero.");
			}
			
			secondaryFlowFactor = generalProperties.supply_hours/esrGeneralProperties.secondary_supply_hours;
			esrCapacityFactor = esrGeneralProperties.esr_capacity_factor;
			maxEsrHeight = esrGeneralProperties.max_esr_height;
			
			modelNumber = 9;
			
			for(EsrCostStruct esrcost : esrCostsArray){
				esrCost.add(new EsrCost(esrcost.mincapacity,
										esrcost.maxcapacity,
										esrcost.basecost,
										esrcost.unitcost));
			}
		}
		
		//if pump enabled, initialize pump properties
		if(pumpGeneralProperties!=null && pumpGeneralProperties.pump_enabled){
			this.pumpGeneralProperties = pumpGeneralProperties;
			this.pumpManualArray = pumpManualArray;
			this.minPumpPower = pumpGeneralProperties.minpumpsize;
			
			if(pumpGeneralProperties.efficiency==0)
				throw new Exception("Pump option is enabled, but pump efficiency is provided as zero.");
			
			if(pumpGeneralProperties.design_lifetime==0)
				throw new Exception("Pump option is enabled, but design lifetime is provided as zero.");
		}
		
		//set total demand required for the network
		totalDemand = getTotalCapacity();		
	}
	
	//validate network structure
	//should not contain cycles
	//every node should be connected
	private int validateNetwork(){
		Node root = source;
		Set<Node> seen = new HashSet<Node>();
		Stack<Node> left = new Stack<Node>();
		left.add(root);
		
		while(!left.isEmpty()){
			Node top = left.pop();
			if(seen.contains(top))
			{
				return 2;//cycle
			}
			seen.add(top);
			for(Pipe pipe : top.getOutgoingPipes())
			{
				left.push(pipe.getEndNode());
			}
		}
		
		if(seen.size()!=nodes.size()){
			System.out.println(seen.size());
			System.out.println(nodes.size());
			return 3;//not fully connected
		}
		return 1;
	}
	
	//get the total water supply that flows through a node in litres per second
	//recursively compute for all downstream nodes
	//simultaneously set the flow through outgoing pipes 
	//simultaneously set the downstreamnodes property (only demand nodes considered)
	private double getNodeSupply(Node node){
		double sum=node.getDemand();
		double supply;
		for(Pipe pipe : node.getOutgoingPipes()){
			Node e = pipe.getEndNode();
			supply = getNodeSupply(e);
			pipe.setFlow(supply);
			sum += supply;
			
			node.addToDownstreamNodes(e.getDownstreamNodes());
			if(e.getDemand()!=0)
				node.addToDownstreamNodes(e);
		}
		return sum;
	}
	
	//return the total ESR capacity required in the network in litres 
	private double getTotalCapacity(){
		double sum = 0;
		for(Node n: nodes.values()){
			sum = sum + n.getRequiredCapacity(esrCapacityFactor);
		}
		return sum;
	}
	
	//get the total water supply that flows through a node in litres per second
	//recursively compute for all downstream nodes
	//simultaneously set the flow through outgoing pipes 
	//simultaneously set the downstreamnodes property (includes zero demand nodes as well)	
	private static double getNodeSupply_gen(Node node){
		double sum=node.getDemand();
		double supply;
		for(Pipe pipe : node.getOutgoingPipes()){
			Node e = pipe.getEndNode();
			supply = getNodeSupply_gen(e);
			pipe.setFlow(supply);
			sum += supply;
			
			node.addToDownstreamNodes(e.getDownstreamNodes());
			node.addToDownstreamNodes(e);
		}
		return sum;
	}
	
	//sets the sourcetonodepipes property of a node
	//recursively call this for all downstream nodes
	//simultaneously set the upstreamnodes property of the node (only considering nodes with demand)
	private static void setSourceToNodePipes(Node node){
		Node n;
		for(Pipe pipe : node.getOutgoingPipes()){
			n = pipe.getEndNode();
			n.addToSourceToNodePipes(node.getSourceToNodePipes());
			n.addToSourceToNodePipes(pipe);
			
			n.addToUpstreamNodes(node.getUpstreamNodes());
			if(node.getDemand()!=0)
				n.addToUpstreamNodes(node);
			
			setSourceToNodePipes(n);
		}
	}
	
	//sets the sourcetonodepipes property of a node
	//recursively call this for all downstream nodes
	//simultaneously set the upstreamnodes property of the node (includes node with zero demand)		
	private static void setSourceToNodePipes_gen(Node node){
		Node n;
		for(Pipe pipe : node.getOutgoingPipes()){
			n = pipe.getEndNode();
			n.addToSourceToNodePipes(node.getSourceToNodePipes());
			n.addToSourceToNodePipes(pipe);
			
			n.addToUpstreamNodes(node.getUpstreamNodes());
			n.addToUpstreamNodes(node);
			
			setSourceToNodePipes_gen(n);
		}
	}
	
	//set the objective cost of the ILP 
	//includes capital cost of pipes and capital+energy cost of pumps(if enbaled)
	private void setObjectiveCost() throws Exception{
		Linear linear = new Linear();
		int j=0;
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();		
			j=0;
			for(PipeCost entry : pipeCost){	
				problem.setVarType("l_"+i+"_"+j, Double.class);
				problem.setVarLowerBound("l_"+i+"_"+j, 0);
				if(pipe.getDiameter()==0) // cost contributes only if diameter is to be computed
					linear.add(entry.getCost(), "l_"+i+"_"+j);
				if(pipe.isAllowParallel()){
					problem.setVarType("p_"+i+"_"+j, Boolean.class);
					linear.add(entry.getCost() * pipe.getLength(), "p_"+i+"_"+j);
				}
				j++;
			}
			if(pipe.isAllowParallel())
				problem.setVarType("p_"+i, Boolean.class);	
			
			if(pumpGeneralProperties.pump_enabled){
				problem.setVarType("pumphead_"+i, Double.class);
				problem.setVarLowerBound("pumphead_"+i, 0);
				problem.setVarUpperBound("pumphead_"+i, maxPumpHead);
							
				problem.setVarType("pumppower_"+i, Double.class);
				problem.setVarLowerBound("pumppower_"+i, 0);
								
				problem.setVarType("pumphelper_"+i, Boolean.class);
				
				double presentvaluefactor = Util.presentValueFactor(pumpGeneralProperties.discount_rate, pumpGeneralProperties.inflation_rate, pumpGeneralProperties.design_lifetime);
				double primarycoefficient = presentvaluefactor*365*generalProperties.supply_hours*pumpGeneralProperties.energycost_per_kwh;
								
				//capital + energy cost
				linear.add(pumpGeneralProperties.capitalcost_per_kw + primarycoefficient, "pumppower_"+i);
			}
			
		}
		
		//problem.setObjective(linear, OptType.MIN);
		problem.setObjective(linear, false);
	}
	
	//add variables to the ILP
	private void addVariables() throws Exception{
		// for each link i, for each commercial pipe j:
		// l_i_j : length of commercial pipe j of link i
		// p_i_j : boolean if ith link has jth pipe diameter in parallel
		// p_i : if ith link has no parallel pipe despite being allowed to have one
		// f_i denotes whether flow in pipe is primary = 1 or secondary = 0
		
		// helper variables to linearize product of binary and continous functions:
		// y_i_j = f_i * l_i_j
		// yp_i_j = p_i_j * f_i
		
		// head_i is the incoming head at node i
		// introduce head_i_j : i is the start node, j is the id of the pipe
		// head_i_j is the head provided by i to the outgoing pipe j
		// if there is no ESR at i or if there is an ESR at i but water to pipe j still comes from the primary network, head_i_j = head_i
		// if there is an ESR at i and it is responsible for providing water to the pipe j, head_i_j = elevation_i + esr_i 
		// i.e. head provided is determined by the esr height and the location elevation
		// k_i_j = s_i_i && !f_j
		// head_i_j = k_i_j * (elevation_i + esr_i - head_i) + head_i
		// ehead_i = elevation_i + esr_i - head_i
		// yhead_i_j = k_i_j * ehead_i
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();	
			problem.setVarType("f_"+i, Boolean.class);
			
			problem.setVarType("headloss_"+i, Double.class);
			problem.setVarLowerBound("headloss_"+i, 0);
			
			if(pipe.isAllowParallel()){
				problem.setVarType("p_"+i, Boolean.class);	
				problem.setVarType("yp_"+i, Boolean.class);
			}
			
			Node startNode = pipe.getStartNode();
			if(startNode.getDemand()>0){
				problem.setVarType("head_"+startNode.getNodeID()+"_"+i, Double.class);
				problem.setVarLowerBound("head_"+startNode.getNodeID()+"_"+i, 0);
				
				problem.setVarType("k_"+startNode.getNodeID()+"_"+i, Boolean.class);
				
				problem.setVarType("yhead_"+startNode.getNodeID()+"_"+i, Double.class);
			}
			
			for(int j=0; j < pipeCost.size() ; j++){					
				problem.setVarType("l_"+i+"_"+j, Double.class);
				problem.setVarLowerBound("l_"+i+"_"+j, 0);
				
				problem.setVarType("y_"+i+"_"+j, Double.class);
				problem.setVarLowerBound("y_"+i+"_"+j, 0);
								
				if(pipe.isAllowParallel()){
					problem.setVarType("p_"+i+"_"+j, Boolean.class);
					problem.setVarType("yp_"+i+"_"+j, Boolean.class);
				}
			}
		}
				
		//s_i_j = 1 means ESR at ith node serves demand of jth node
		//d_i is the total demand served by ith esr
		//head_i is the head at ith node
		
		//e_i_j represents whether ith esr's cost is computed by jth esrcost table row
		//z_i_j = d_i * e_i_j
		
		// esr_i : height of esr at node i
		// ehead_i = elevation_i + esr_i - head_i
		for(int i : nodes.keySet()){			
			problem.setVarType("d_"+i, Double.class);
			
			problem.setVarType("esr_"+i, Double.class);
			problem.setVarLowerBound("esr_"+i, 0);
			
			problem.setVarType("besr_"+i, Boolean.class);
			
			problem.setVarType("head_"+i, Double.class);
			problem.setVarLowerBound("head_"+i, 0);
			
			problem.setVarType("ehead_"+i, Double.class);
			
			for(int j : nodes.keySet()){
				problem.setVarType("s_"+i+"_"+j, Boolean.class);
			}
						
			for(int j=0; j<esrCost.size();j++){
				problem.setVarType("e_"+i+"_"+j, Boolean.class);
				
				problem.setVarType("z_"+i+"_"+j, Double.class);
				problem.setVarLowerBound("z_"+i+"_"+j, 0);
			}
		}
	}
	
	//add variables to the ILP (includes zero demand nodes)
	private void addVariables_gen() throws Exception{
		// for each link i, for each commercial pipe j:
		// l_i_j : length of commercial pipe j of link i
		// p_i_j : boolean if ith link has jth pipe diameter in parallel
		// p_i : if ith link has no parallel pipe despite being allowed to have one
		//f_i denotes whether flow in pipe is primary = 1 or secondary = 0
		//y_i_j = f_i * l_i_j
		//yp_i_j = p_i_j * f_i
		
		// head_i is the incoming head at node i
		// introduce head_i_j : i is the start node, j is the id of the pipe
		// head_i_j is the head provided by i to the outgoing pipe j
		// if there is no ESR at i or if there is an ESR at i but water to pipe j still comes from the primary network, head_i_j = head_i
		// if there is an ESR at i and it is responsible for providing water to the pipe j, head_i_j = elevation_i + esr_i 
		// i.e. head provided is determined by the esr height and the location elevation
		// introduce head_i_j : is the start node, j is the id of the pipe
		// k_i_j = s_i_i && !f_j
		// head_i_j = k_i_j * (elevation_i + esr_i - head_i) + head_i
		// ehead_i = elevation_i + esr_i - head_i
		// yhead_i_j = k_i_j * ehead_i
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();	
			problem.setVarType("f_"+i, Boolean.class);
			
			problem.setVarType("headloss_"+i, Double.class);
			problem.setVarLowerBound("headloss_"+i, 0);
			
			if(pipe.isAllowParallel()){
				problem.setVarType("p_"+i, Boolean.class);	
				problem.setVarType("yp_"+i, Boolean.class);
			}
			
			Node startNode = pipe.getStartNode();

			problem.setVarType("head_"+startNode.getNodeID()+"_"+i, Double.class);
			problem.setVarLowerBound("head_"+startNode.getNodeID()+"_"+i, 0);
			
			problem.setVarType("k_"+startNode.getNodeID()+"_"+i, Boolean.class);
			
			problem.setVarType("yhead_"+startNode.getNodeID()+"_"+i, Double.class);

			
			for(int j=0; j < pipeCost.size() ; j++){					
				problem.setVarType("l_"+i+"_"+j, Double.class);
				problem.setVarLowerBound("l_"+i+"_"+j, 0);
				
				problem.setVarType("y_"+i+"_"+j, Double.class);
				problem.setVarLowerBound("y_"+i+"_"+j, 0);
								
				if(pipe.isAllowParallel()){
					problem.setVarType("p_"+i+"_"+j, Boolean.class);
					problem.setVarType("yp_"+i+"_"+j, Boolean.class);
				}
			}
		}
				
		//s_i_j = 1 means ESR at ith node serves demand of jth node
		//d_i is the total demand served by ith esr
		//head_i is the head at ith node
		
		//e_i_j represents whether ith esr's cost is computed by jth esrcost table row
		//z_i_j = d_i * e_i_j
		
		// esr_i : height of esr at node i
		// ehead_i = elevation_i + esr_i - head_i
		for(int i : nodes.keySet()){			
			problem.setVarType("d_"+i, Double.class);
			
			problem.setVarType("esr_"+i, Double.class);
			problem.setVarLowerBound("esr_"+i, 0);
			
			problem.setVarType("besr_"+i, Boolean.class);
			
			problem.setVarType("head_"+i, Double.class);
			problem.setVarLowerBound("head_"+i, 0);
			
			problem.setVarType("ehead_"+i, Double.class);
			
			for(int j : nodes.keySet()){
				problem.setVarType("s_"+i+"_"+j, Boolean.class);
			}
						
			for(int j=0; j<esrCost.size();j++){
				problem.setVarType("e_"+i+"_"+j, Boolean.class);
				
				problem.setVarType("z_"+i+"_"+j, Double.class);
				problem.setVarLowerBound("z_"+i+"_"+j, 0);
			}
		}
	}
	
	//add variables to the ILP
	//considers l_i_j_0 and l_i_j_1 for secondary and primary networks)
	private void addVariables_gen3() throws Exception{
		// for each link i, for each commercial pipe j:
		// l_i_j_k : length of commercial pipe j of link i, k =1 if primary, 0 if secondary
		// p_i_j_k : boolean if ith link has jth pipe diameter in parallel, k =1 if primary, 0 if secondary
		// p_i : if ith link has no parallel pipe despite being allowed to have one
		//f_i denotes whether flow in pipe is primary = 1 or secondary = 0

		// head_i is the incoming head at node i
		// introduce head_i_j : i is the start node, j is the id of the pipe
		// head_i_j is the head provided by i to the outgoing pipe j
		// if there is no ESR at i or if there is an ESR at i but water to pipe j still comes from the primary network, head_i_j = head_i
		// if there is an ESR at i and it is responsible for providing water to the pipe j, head_i_j = elevation_i + esr_i 
		// i.e. head provided is determined by the esr height and the location elevation
		// introduce head_i_j : is the start node, j is the id of the pipe
		// k_i_j = s_i_i && !f_j
		// head_i_j = k_i_j * (elevation_i + esr_i - head_i) + head_i
		// ehead_i = elevation_i + esr_i - head_i
		// yhead_i_j = k_i_j * ehead_i
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();	
			problem.setVarType("f_"+i, Boolean.class);
			
			problem.setVarType("headloss_"+i, Double.class);
			problem.setVarLowerBound("headloss_"+i, 0);
			
			if(pipe.isAllowParallel()){
				problem.setVarType("p_"+i+"_0", Boolean.class);
				problem.setVarType("p_"+i+"_1", Boolean.class);
			}
			
			Node startNode = pipe.getStartNode();

			problem.setVarType("head_"+startNode.getNodeID()+"_"+i, Double.class);
			problem.setVarLowerBound("head_"+startNode.getNodeID()+"_"+i, 0);
			
			problem.setVarType("k_"+startNode.getNodeID()+"_"+i, Boolean.class);
			
			problem.setVarType("yhead_"+startNode.getNodeID()+"_"+i, Double.class);

			
			for(int j=0; j < pipeCost.size() ; j++){					
				problem.setVarType("l_"+i+"_"+j+"_0", Double.class);
				problem.setVarLowerBound("l_"+i+"_"+j+"_0", 0);
				problem.setVarType("l_"+i+"_"+j+"_1", Double.class);
				problem.setVarLowerBound("l_"+i+"_"+j+"_1", 0);
								
				if(pipe.isAllowParallel()){
					problem.setVarType("p_"+i+"_"+j+"_0", Boolean.class);
					problem.setVarType("p_"+i+"_"+j+"_1", Boolean.class);
				}
			}
		}
				
		//s_i_j = 1 means ESR at ith node serves demand of jth node
		//d_i is the total demand served by ith esr
		//head_i is the head at ith node
		
		//e_i_j represents whether ith esr's cost is computed by jth esrcost table row
		//z_i_j = d_i * e_i_j
		
		// esr_i : height of esr at node i
		// ehead_i = elevation_i + esr_i - head_i
		for(int i : nodes.keySet()){			
			problem.setVarType("d_"+i, Double.class);
			
			problem.setVarType("esr_"+i, Double.class);
			problem.setVarLowerBound("esr_"+i, 0);
			
			problem.setVarType("besr_"+i, Boolean.class);
			
			problem.setVarType("head_"+i, Double.class);
			problem.setVarLowerBound("head_"+i, 0);
			
			problem.setVarType("ehead_"+i, Double.class);
			
			for(int j : nodes.keySet()){
				problem.setVarType("s_"+i+"_"+j, Boolean.class);
			}
						
			for(int j=0; j<esrCost.size();j++){
				problem.setVarType("e_"+i+"_"+j, Boolean.class);
				
				problem.setVarType("z_"+i+"_"+j, Double.class);
				problem.setVarLowerBound("z_"+i+"_"+j, 0);
			}
		}
	}
	
	//add variables to the ILP
	//includes pump related variables
	private void addVariables_gen4() throws Exception{
		// for each link i, for each commercial pipe j:
		// l_i_j_k : length of commercial pipe j of link i, k =1 if primary, 0 if secondary
		// p_i_j_k : boolean if ith link has jth pipe diameter in parallel, k =1 if primary, 0 if secondary
		// p_i : if ith link has no parallel pipe despite being allowed to have one
		// f_i denotes whether flow in pipe is primary = 1 or secondary = 0
		// pumphead_i : head provided by pump in pipe i
		// pumppower_i : power of pump installed in pipe i
		// ppumppower_i : primary power
		// spumppower_i : secondary power
		// f_pumphead_i : f_i * pumphead_i
		
		// head_i is the incoming head at node i
		// introduce head_i_j : i is the start node, j is the id of the pipe
		// head_i_j is the head provided by i to the outgoing pipe j
		// if there is no ESR at i or if there is an ESR at i but water to pipe j still comes from the primary network, head_i_j = head_i
		// if there is an ESR at i and it is responsible for providing water to the pipe j, head_i_j = elevation_i + esr_i 
		// i.e. head provided is determined by the esr height and the location elevation
		// introduce head_i_j : is the start node, j is the id of the pipe
		// k_i_j = s_i_i && !f_j
		// head_i_j = k_i_j * (elevation_i + esr_i - head_i) + head_i
		// ehead_i = elevation_i + esr_i - head_i
		// yhead_i_j = k_i_j * ehead_i
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();	
			problem.setVarType("f_"+i, Boolean.class);
			
			problem.setVarType("headloss_"+i, Double.class);
			
			if(pumpGeneralProperties.pump_enabled){
				problem.setVarType("pumphead_"+i, Double.class);
				problem.setVarLowerBound("pumphead_"+i, 0);
				problem.setVarUpperBound("pumphead_"+i, maxPumpHead);
				
				
				problem.setVarType("pumppower_"+i, Double.class);
				problem.setVarLowerBound("pumppower_"+i, 0);
				
				problem.setVarType("ppumppower_"+i, Double.class);
				problem.setVarLowerBound("ppumppower_"+i, 0);
				
				problem.setVarType("spumppower_"+i, Double.class);
				problem.setVarLowerBound("spumppower_"+i, 0);
				
				problem.setVarType("f_pumphead_"+i, Double.class);
				problem.setVarLowerBound("f_pumphead_"+i, 0);
				
				problem.setVarType("pumphelper_"+i, Boolean.class);
			}
			
			if(pipe.isAllowParallel()){
				problem.setVarType("p_"+i+"_0", Boolean.class);
				problem.setVarType("p_"+i+"_1", Boolean.class);
			}
			
			Node startNode = pipe.getStartNode();

			problem.setVarType("head_"+startNode.getNodeID()+"_"+i, Double.class);
			problem.setVarLowerBound("head_"+startNode.getNodeID()+"_"+i, 0);
			
			problem.setVarType("k_"+startNode.getNodeID()+"_"+i, Boolean.class);
			
			problem.setVarType("yhead_"+startNode.getNodeID()+"_"+i, Double.class);

			
			for(int j=0; j < pipeCost.size() ; j++){					
				problem.setVarType("l_"+i+"_"+j+"_0", Double.class);
				problem.setVarLowerBound("l_"+i+"_"+j+"_0", 0);
				problem.setVarType("l_"+i+"_"+j+"_1", Double.class);
				problem.setVarLowerBound("l_"+i+"_"+j+"_1", 0);
								
				if(pipe.isAllowParallel()){
					problem.setVarType("p_"+i+"_"+j+"_0", Boolean.class);
					problem.setVarType("p_"+i+"_"+j+"_1", Boolean.class);
				}
			}
		}
		
		//s_i_j = 1 means ESR at ith node serves demand of jth node
		//d_i is the total demand served by ith esr
		//head_i is the head at ith node
		
		//e_i_j represents whether ith esr's cost is computed by jth esrcost table row
		//z_i_j = d_i * e_i_j
		
		// esr_i : height of esr at node i
		// ehead_i = elevation_i + esr_i - head_i
		for(int i : nodes.keySet()){			
			problem.setVarType("d_"+i, Double.class);
			
			problem.setVarType("esr_"+i, Double.class);
			problem.setVarLowerBound("esr_"+i, 0);
			
			problem.setVarType("besr_"+i, Boolean.class);
			
			problem.setVarType("head_"+i, Double.class);
			problem.setVarLowerBound("head_"+i, 0);
			
			problem.setVarType("ehead_"+i, Double.class);
			
			for(int j : nodes.keySet()){
				problem.setVarType("s_"+i+"_"+j, Boolean.class);
			}
						
			for(int j=0; j<esrCost.size();j++){
				problem.setVarType("e_"+i+"_"+j, Boolean.class);
				
				problem.setVarType("z_"+i+"_"+j, Double.class);
				problem.setVarLowerBound("z_"+i+"_"+j, 0);
			}
		}
	}
	
	//add variables to the ILP
	//does not include node variables s_i_j
	private void addVariables_gen5() throws Exception{
		// for each link i, for each commercial pipe j:
		// l_i_j_k : length of commercial pipe j of link i, k =1 if primary, 0 if secondary
		// p_i_j_k : boolean if ith link has jth pipe diameter in parallel, k =1 if primary, 0 if secondary
		// p_i : if ith link has no parallel pipe despite being allowed to have one
		// f_i denotes whether flow in pipe is primary = 1 or secondary = 0
		// pumphead_i : head provided by pump in pipe i
		// pumppower_i : power of pump installed in pipe i
		// ppumppower_i : primary power
		// spumppower_i : secondary power
		// f_pumphead_i : f_i * pumphead_i
		
		// head_i is the incoming head at node i
		// introduce head_i_j : i is the start node, j is the id of the pipe
		// head_i_j is the head provided by i to the outgoing pipe j
		// if there is no ESR at i or if there is an ESR at i but water to pipe j still comes from the primary network, head_i_j = head_i
		// if there is an ESR at i and it is responsible for providing water to the pipe j, head_i_j = elevation_i + esr_i 
		// i.e. head provided is determined by the esr height and the location elevation
		// introduce head_i_j : is the start node, j is the id of the pipe
		// k_i_j = s_i_i && !f_j
		// head_i_j = k_i_j * (elevation_i + esr_i - head_i) + head_i
		// ehead_i = elevation_i + esr_i - head_i
		// yhead_i_j = k_i_j * ehead_i
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();	
			problem.setVarType("f_"+i, Boolean.class);
			
			problem.setVarType("headloss_"+i, Double.class);
			
			if(pumpGeneralProperties.pump_enabled){
				problem.setVarType("pumphead_"+i, Double.class);
				problem.setVarLowerBound("pumphead_"+i, 0);
				problem.setVarUpperBound("pumphead_"+i, maxPumpHead);
				
				
				problem.setVarType("pumppower_"+i, Double.class);
				problem.setVarLowerBound("pumppower_"+i, 0);
				
				problem.setVarType("ppumppower_"+i, Double.class);
				problem.setVarLowerBound("ppumppower_"+i, 0);
				
				problem.setVarType("spumppower_"+i, Double.class);
				problem.setVarLowerBound("spumppower_"+i, 0);
				
				problem.setVarType("f_pumphead_"+i, Double.class);
				problem.setVarLowerBound("f_pumphead_"+i, 0);
				
				problem.setVarType("pumphelper_"+i, Boolean.class);
			}
			
			if(pipe.isAllowParallel()){
				problem.setVarType("p_"+i+"_0", Boolean.class);
				problem.setVarType("p_"+i+"_1", Boolean.class);
			}
			
			Node startNode = pipe.getStartNode();

			problem.setVarType("head_"+startNode.getNodeID()+"_"+i, Double.class);
			problem.setVarLowerBound("head_"+startNode.getNodeID()+"_"+i, 0);
			
			problem.setVarType("k_"+startNode.getNodeID()+"_"+i, Boolean.class);
			
			problem.setVarType("yhead_"+startNode.getNodeID()+"_"+i, Double.class);

			
			for(int j=0; j < pipeCost.size() ; j++){					
				problem.setVarType("l_"+i+"_"+j+"_0", Double.class);
				problem.setVarLowerBound("l_"+i+"_"+j+"_0", 0);
				problem.setVarType("l_"+i+"_"+j+"_1", Double.class);
				problem.setVarLowerBound("l_"+i+"_"+j+"_1", 0);
								
				if(pipe.isAllowParallel()){
					problem.setVarType("p_"+i+"_"+j+"_0", Boolean.class);
					problem.setVarType("p_"+i+"_"+j+"_1", Boolean.class);
				}
			}
		}
		
		//d_i is the total demand served by ith esr
		//head_i is the head at ith node
		
		//e_i_j represents whether ith esr's cost is computed by jth esrcost table row
		//z_i_j = d_i * e_i_j
		
		// esr_i : height of esr at node i
		// ehead_i = elevation_i + esr_i - head_i
		for(int i : nodes.keySet()){			
			problem.setVarType("d_"+i, Double.class);
			
			problem.setVarType("esr_"+i, Double.class);
			problem.setVarLowerBound("esr_"+i, 0);
			
			problem.setVarType("besr_"+i, Boolean.class);
			
			problem.setVarType("head_"+i, Double.class);
			problem.setVarLowerBound("head_"+i, 0);
			
			problem.setVarType("ehead_"+i, Double.class);
					
			for(int j=0; j<esrCost.size();j++){
				problem.setVarType("e_"+i+"_"+j, Boolean.class);
				
				problem.setVarType("z_"+i+"_"+j, Double.class);
				problem.setVarLowerBound("z_"+i+"_"+j, 0);
			}
		}
	}
	
	//set the objective function of the ILP
	//includes ESR capital cost
	private void setObjectiveCost_esr() throws Exception{
		Linear linear = new Linear();
		int j=0;
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();		
			j=0;
			for(PipeCost entry : pipeCost){				
				if(pipe.getDiameter()==0) // cost contributes only if diameter is to be computed
					linear.add(entry.getCost(), "l_"+i+"_"+j);
				if(pipe.isAllowParallel()){
					linear.add(entry.getCost() * pipe.getLength(), "p_"+i+"_"+j);
				}
				j++;
			}	
		}
		
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			if(node.getDemand()>0){
				j=0;
				for(EsrCost esr : esrCost){
					linear.add(esr.getBaseCost() - esr.getMinCapacity()*esr.getUnitCost(),"e_"+i+"_"+j);
					linear.add(esr.getUnitCost(),"z_"+i+"_"+j);
					j++;
				}
			}
		}
		
		//problem.setObjective(linear, OptType.MIN);
		problem.setObjective(linear, false);
	}
	
	//set the objective function of the ILP
	//allows nodes with zero demands to have ESRs 
	private void setObjectiveCost_esr_gen() throws Exception{
		Linear linear = new Linear();
		int j=0;
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();		
			j=0;
			for(PipeCost entry : pipeCost){				
				if(pipe.getDiameter()==0) // cost contributes only if diameter is to be computed
					linear.add(entry.getCost(), "l_"+i+"_"+j);
				if(pipe.isAllowParallel()){
					linear.add(entry.getCost() * pipe.getLength(), "p_"+i+"_"+j);
				}
				j++;
			}	
		}
		
		for(Node node : nodes.values()){
			int i = node.getNodeID();
				j=0;
				for(EsrCost esr : esrCost){
					linear.add(esr.getBaseCost() - esr.getMinCapacity()*esr.getUnitCost(),"e_"+i+"_"+j);
					linear.add(esr.getUnitCost(),"z_"+i+"_"+j);
					j++;
				}
		}
		
		//esr height factor
		double minesrcost = getCost(totalDemand, esrCost);
		double nodenumber = nodes.size();
		double avgesrcost = 0.01*minesrcost/nodenumber;
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			linear.add(avgesrcost,"esr_"+i);
		}
		
		//problem.setObjective(linear, OptType.MIN);
		problem.setObjective(linear, false);
	}
	
	//set the objective function of the ILP
	//updated to consider l_i_j_k variables
	private void setObjectiveCost_esr_gen3() throws Exception{
		Linear linear = new Linear();
		int j=0;
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();		
			j=0;
			for(PipeCost entry : pipeCost){				
				if(pipe.getDiameter()==0){ // cost contributes only if diameter is to be computed
					linear.add(entry.getCost(), "l_"+i+"_"+j+"_0");
					linear.add(entry.getCost(), "l_"+i+"_"+j+"_1");
				}
				if(pipe.isAllowParallel()){
					linear.add(entry.getCost() * pipe.getLength(), "p_"+i+"_"+j+"_0");
					linear.add(entry.getCost() * pipe.getLength(), "p_"+i+"_"+j+"_1");
				}
				j++;
			}	
		}
		
		for(Node node : nodes.values()){
			int i = node.getNodeID();
				j=0;
				for(EsrCost esr : esrCost){
					linear.add(esr.getBaseCost() - esr.getMinCapacity()*esr.getUnitCost(),"e_"+i+"_"+j);
					linear.add(esr.getUnitCost(),"z_"+i+"_"+j);
					j++;
				}
		}
		
		
		//esr height factor
		double minesrcost = getCost(totalDemand, esrCost);
		double nodenumber = nodes.size();
		double avgesrcost = 0.01*minesrcost/nodenumber;
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			linear.add(avgesrcost,"esr_"+i);
		}
		
		//problem.setObjective(linear, OptType.MIN);
		problem.setObjective(linear, false);
	}
	
	//set the objective function of the ILP
	//includes pump cost
	private void setObjectiveCost_esr_gen4() throws Exception{
		Linear linear = new Linear();
		int j=0;
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();		
			j=0;
			for(PipeCost entry : pipeCost){				
				if(pipe.getDiameter()==0){ // cost contributes only if diameter is to be computed
					linear.add(entry.getCost(), "l_"+i+"_"+j+"_0");
					linear.add(entry.getCost(), "l_"+i+"_"+j+"_1");
				}
				if(pipe.isAllowParallel()){
					linear.add(entry.getCost() * pipe.getLength(), "p_"+i+"_"+j+"_0");
					linear.add(entry.getCost() * pipe.getLength(), "p_"+i+"_"+j+"_1");
				}
				j++;
			}
			
			// energy cost = power * hours
			// power contains flow term
			// flow = base_flow *base_hours/hours
			// hours term cancel
			// therefore for both primary and secondary pipes
			// energy cost = power as per base flow * base_hours
			
			// flow_m3h = flow*3.6;
			//power = density of water (1000) * g (9.80665) * flow (in m3h = flow*3.6) / (3.6*10^6) * efficiency
			// power = 9.81 * flow (in lps) / (1000 * efficiency)
			// energy cost = power * no of hours * cost per kw
			if(pumpGeneralProperties.pump_enabled){
				double presentvaluefactor = Util.presentValueFactor(pumpGeneralProperties.discount_rate, pumpGeneralProperties.inflation_rate, pumpGeneralProperties.design_lifetime);
				//double pumpcoeffecient = 9.80665*365*presentvaluefactor*pipe.getFlow()*generalProperties.supply_hours*pumpGeneralProperties.energycost_per_kw*pumpGeneralProperties.energycost_factor/(1000*pumpGeneralProperties.efficiency);
				double primarycoefficient = presentvaluefactor*365*generalProperties.supply_hours*pumpGeneralProperties.energycost_per_kwh;
				double secondarycoefficient = presentvaluefactor*365*esrGeneralProperties.secondary_supply_hours*pumpGeneralProperties.energycost_per_kwh;
				
				// energy cost
				linear.add(primarycoefficient, "ppumppower_"+i);
				linear.add(secondarycoefficient, "spumppower_"+i);
				
				//capital cost
				linear.add(pumpGeneralProperties.capitalcost_per_kw, "pumppower_"+i);
			}
		}
		
		for(Node node : nodes.values()){
			int i = node.getNodeID();
				j=0;
				for(EsrCost esr : esrCost){
					linear.add(esr.getBaseCost() - esr.getMinCapacity()*esr.getUnitCost(),"e_"+i+"_"+j);
					linear.add(esr.getUnitCost(),"z_"+i+"_"+j);
					j++;
				}
		}
		
		
		//esr height factor
		double minesrcost = getCost(totalDemand, esrCost);
		double nodenumber = nodes.size();
		double avgesrcost = 0.01*minesrcost/nodenumber;
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			linear.add(avgesrcost,"esr_"+i);
		}
		
		//problem.setObjective(linear, OptType.MIN);
		problem.setObjective(linear, false);
	}
	
	//set the objective function of the ILP
	//removal of s_i_j variables
	private void setObjectiveCost_esr_gen5() throws Exception{
		Linear linear = new Linear();
		int j=0;
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();		
			j=0;
			for(PipeCost entry : pipeCost){				
				if(pipe.getDiameter()==0){ // cost contributes only if diameter is to be computed
					linear.add(entry.getCost(), "l_"+i+"_"+j+"_0");
					linear.add(entry.getCost(), "l_"+i+"_"+j+"_1");
				}
				if(pipe.isAllowParallel()){
					linear.add(entry.getCost() * pipe.getLength(), "p_"+i+"_"+j+"_0");
					linear.add(entry.getCost() * pipe.getLength(), "p_"+i+"_"+j+"_1");
				}
				j++;
			}
			
			// energy cost = power * hours
			// power contains flow term
			// flow = base_flow *base_hours/hours
			// hours term cancel
			// therefore for both primary and secondary pipes
			// energy cost = power as per base flow * base_hours
			
			// flow_m3h = flow*3.6;
			//power = density of water (1000) * g (9.80665) * flow (in m3h = flow*3.6) / (3.6*10^6) * efficiency
			// power = 9.81 * flow (in lps) / (1000 * efficiency)
			// energy cost = power * no of hours * cost per kw
			if(pumpGeneralProperties.pump_enabled){
				double presentvaluefactor = Util.presentValueFactor(pumpGeneralProperties.discount_rate, pumpGeneralProperties.inflation_rate, pumpGeneralProperties.design_lifetime);
				//double pumpcoeffecient = 9.80665*365*presentvaluefactor*pipe.getFlow()*generalProperties.supply_hours*pumpGeneralProperties.energycost_per_kw*pumpGeneralProperties.energycost_factor/(1000*pumpGeneralProperties.efficiency);
				double primarycoefficient = presentvaluefactor*365*generalProperties.supply_hours*pumpGeneralProperties.energycost_per_kwh;
				double secondarycoefficient = presentvaluefactor*365*esrGeneralProperties.secondary_supply_hours*pumpGeneralProperties.energycost_per_kwh;
				
				// energy cost
				linear.add(primarycoefficient, "ppumppower_"+i);
				linear.add(secondarycoefficient, "spumppower_"+i);
				
				//capital cost
				linear.add(pumpGeneralProperties.capitalcost_per_kw, "pumppower_"+i);
			}
		}
		
		for(Node node : nodes.values()){
			int i = node.getNodeID();
				j=0;
				for(EsrCost esr : esrCost){
					linear.add(esr.getBaseCost() - esr.getMinCapacity()*esr.getUnitCost(),"e_"+i+"_"+j);
					linear.add(esr.getUnitCost(),"z_"+i+"_"+j);
					j++;
				}
		}
		
		
		//esr height factor
		double minesrcost = getCost(totalDemand, esrCost);
		double nodenumber = nodes.size();
		double avgesrcost = 0.01*minesrcost/nodenumber;
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			linear.add(avgesrcost,"esr_"+i);
		}
		
		//problem.setObjective(linear, OptType.MIN);
		problem.setObjective(linear, false);
	}
	
	//add constraints for the pipes in the network
	//includes constraints related to headloss/ water speed limits and assignment of commercial pipe diameters
	private void setPipeConstraints() throws Exception{
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();
			Linear linear;
			boolean atleastOneHeadlossCorrect = false;
			boolean atleastOneSpeedCorrect = false;
			
			//true when pipe has already been assigned a diameter by the user
			if(pipe.getDiameter()!=0){
				int j=0;
				boolean diameterFromPipeCost = false;
				for(PipeCost entry : pipeCost){
					linear = new Linear();
					linear.add(1, "l_"+i+"_"+j);
					if(pipe.getDiameter() == entry.getDiameter()){
						problem.add(new Constraint("", linear, "=", pipe.getLength()));
						diameterFromPipeCost = true;
					}
					else
						problem.add(new Constraint("", linear, "=", 0));				
					j++;
				}
				
				//if diameter provided by user is not defined in the pipecost table
				if(!diameterFromPipeCost)
					throw new Exception("The custom diameter: " + pipe.getDiameter() + " with roughness: "+ pipe.getRoughness() +" does not belong to commercial pipes"); 
				
				//if pipe is allowed to have a parallel pipe, add corresponding constraints
				if(pipe.isAllowParallel()){
					linear = new Linear();			
					for(int j1=0;j1<pipeCost.size();j1++){
						linear.add(1, "p_"+i+"_"+j1);
					}
					linear.add(1, "p_"+i);
					problem.add(new Constraint("", linear, "=", 1));	
					
					j=0;
					for(PipeCost entry : pipeCost){
						double flow = pipe.getFlow();
									
						//when parallel pipe of dia j, what is flow in primary
						flow = flow / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852));						
						double headloss = Util.HWheadLoss(flow, pipe.getRoughness(), pipe.getDiameter());
						
						if(headloss < generalProperties.min_hl_perkm/1000 || headloss > generalProperties.max_hl_perkm/1000){
							linear = new Linear();
							linear.add(1, "p_"+i+"_"+j);
							problem.add(new Constraint("", linear, "=", 0));
							//System.out.println(i+" "+entry.getDiameter()+" "+headloss);
						}
						else
							atleastOneHeadlossCorrect = true;
						
						double parallel_flow = pipe.getFlow() - flow;
						double main_speed = Util.waterSpeed(flow, pipe.getDiameter()); 
						double parallel_speed = Util.waterSpeed(parallel_flow, entry.getDiameter()); 
						if( generalProperties.max_water_speed > 0 && (parallel_speed > generalProperties.max_water_speed || main_speed > generalProperties.max_water_speed)){
							linear = new Linear();
							linear.add(1, "p_"+i+"_"+j);
							problem.add(new Constraint("", linear, "=", 0));
						}
						else
							atleastOneSpeedCorrect = true;
						j++;
					}
					
					double flow = pipe.getFlow();
					double headloss = Util.HWheadLoss(flow, pipe.getRoughness(), pipe.getDiameter());
					
					if(headloss < generalProperties.min_hl_perkm/1000 || headloss > generalProperties.max_hl_perkm/1000){
						linear = new Linear();
						linear.add(1, "p_"+i);
						problem.add(new Constraint("", linear, "=", 0));
						//System.out.println(i+" "+pipe.getDiameter()+" "+headloss);
					}
					else
						atleastOneHeadlossCorrect = true;
					
					double speed = Util.waterSpeed(flow, pipe.getDiameter());
					if(generalProperties.max_water_speed > 0 && speed > generalProperties.max_water_speed){
						linear = new Linear();
						linear.add(1, "p_"+i);
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneSpeedCorrect = true;
				}
				else{
					double flow = pipe.getFlow();
					double headloss = Util.HWheadLoss(flow, pipe.getRoughness(), pipe.getDiameter());
					
					if(headloss < generalProperties.min_hl_perkm/1000 || headloss > generalProperties.max_hl_perkm/1000){
						
					}
					else
						atleastOneHeadlossCorrect = true;
										
					double primary_speed = Util.waterSpeed(flow, pipe.getDiameter());
					
					if(generalProperties.max_water_speed > 0 && primary_speed > generalProperties.max_water_speed){
						
					}
					else
						atleastOneSpeedCorrect = true;					
				}
			}
			else{
				linear = new Linear();
				for(int j=0;j<pipeCost.size();j++){
					linear.add(1, "l_"+i+"_"+j);
				}
				problem.add(new Constraint("", linear, "=", pipe.getLength()));	
				
				int j=0;
				
				for(PipeCost entry : pipeCost){
					double flow = pipe.getFlow();
					double headloss = Util.HWheadLoss(flow, entry.getRoughness(), entry.getDiameter());
					double speed = Util.waterSpeed(flow, entry.getDiameter()); 
					
					if(headloss < generalProperties.min_hl_perkm/1000 || headloss > generalProperties.max_hl_perkm/1000){
						linear = new Linear();
						linear.add(1, "l_"+i+"_"+j);
						problem.add(new Constraint("", linear, "=", 0));
						//System.out.println(i+" "+entry.getDiameter()+" "+headloss);
					}
					else
						atleastOneHeadlossCorrect = true;
					
					if(generalProperties.max_water_speed > 0 && speed > generalProperties.max_water_speed){
						linear = new Linear();
						linear.add(1, "l_"+i+"_"+j);
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneSpeedCorrect = true;
					j++;
				}
			}
			
			//if none of the commercial pipe diameters can satisfy headloss/speed constraints for the pipe, terminate optimization and inform user
			if(!atleastOneHeadlossCorrect)
				throw new Exception("Headloss for pipe: " + pipe.getPipeID() + " cannot be set within the min/max headloss values.");
			
			if(!atleastOneSpeedCorrect)
				throw new Exception("Speed of water in pipe: " + pipe.getPipeID() + " cannot be set below the max value.");
		}
		//System.out.println(problem);
	}
	
	//add constraints for the pipes in the network
	//includes constraints related to headloss/ water speed limits and assignment of commercial pipe diameters
	//includes consideration for secondary network, if ESR optimization is enabled	
	private void setPipeConstraints_model3() throws Exception{
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();
			Linear linear;
			boolean atleastOneHeadlossCorrect = false;
			boolean atleastOneSpeedCorrect = false;
			
			//true when pipe has already been assigned a diameter by the user
			if(pipe.getDiameter()!=0){
				int j=0;
				int fixedj = 0;
				boolean diameterFromPipeCost = false;
				for(PipeCost entry : pipeCost){
					linear = new Linear();
					linear.add(1, "l_"+i+"_"+j);
					if(pipe.getDiameter() == entry.getDiameter()){
						problem.add(new Constraint("", linear, "=", pipe.getLength()));
						diameterFromPipeCost = true;
						fixedj = j;
					}
					else
						problem.add(new Constraint("", linear, "=", 0));				
					j++;
				}
				
				//if diameter provided by user is not defined in the pipecost table
				if(!diameterFromPipeCost)
					throw new Exception("The custom diameter: " + pipe.getDiameter() + " with roughness: "+ pipe.getRoughness() +" does not belong to commercial pipes"); 
				
				//if pipe is allowed to have a parallel pipe, add corresponding constraints
				if(pipe.isAllowParallel()){
					linear = new Linear();			
					for(int j1=0;j1<pipeCost.size();j1++){
						linear.add(1, "p_"+i+"_"+j1);
					}
					linear.add(1, "p_"+i);
					problem.add(new Constraint("", linear, "=", 1));	
					
					j=0;
					for(PipeCost entry : pipeCost){
						double flow = pipe.getFlow();
						
						//when parallel pipe of dia j, what is flow in primary
						flow = flow / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852));						
						double headloss = Util.HWheadLoss(flow, pipe.getRoughness(), pipe.getDiameter());
						double secondaryHeadloss = Util.HWheadLoss(flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
												
						if(headloss < generalProperties.min_hl_perkm/1000 || headloss > generalProperties.max_hl_perkm/1000){
							linear = new Linear();
							linear.add(1, "yp_"+i+"_"+j);
							problem.add(new Constraint("", linear, "=", 0));
							//System.out.println(i+" "+entry.getDiameter()+" "+headloss);
						}
						else
							atleastOneHeadlossCorrect = true;
						
						if(secondaryHeadloss < generalProperties.min_hl_perkm/1000 || secondaryHeadloss > generalProperties.max_hl_perkm/1000){
							linear = new Linear();
							linear.add(1, "yp_"+i+"_"+j);
							linear.add(-1, "p_"+i+"_"+j);
							problem.add(new Constraint("", linear, "=", 0));
							//System.out.println(i+" "+entry.getDiameter()+" "+headloss);
						}
						else
							atleastOneHeadlossCorrect = true;
						
						double parallel_flow = pipe.getFlow() - flow;
						double main_speed = Util.waterSpeed(flow, pipe.getDiameter()); 
						double parallel_speed = Util.waterSpeed(parallel_flow, entry.getDiameter()); 
						
						double main_secondary_speed = Util.waterSpeed(flow*secondaryFlowFactor, pipe.getDiameter()); 
						double parallel_secondary_speed = Util.waterSpeed(parallel_flow*secondaryFlowFactor, entry.getDiameter());
						
						
						if(generalProperties.max_water_speed > 0 && (parallel_speed > generalProperties.max_water_speed || main_speed > generalProperties.max_water_speed)){
							linear = new Linear();
							linear.add(1, "yp_"+i+"_"+j);
							problem.add(new Constraint("", linear, "=", 0));
						}
						else
							atleastOneSpeedCorrect = true;
						
						if(generalProperties.max_water_speed > 0 && (parallel_secondary_speed > generalProperties.max_water_speed || main_secondary_speed > generalProperties.max_water_speed)){
							linear = new Linear();
							linear.add(1, "yp_"+i+"_"+j);
							linear.add(-1, "p_"+i+"_"+j);
							problem.add(new Constraint("", linear, "=", 0));
						}
						else
							atleastOneSpeedCorrect = true;
						
						j++;
					}
					
					double flow = pipe.getFlow();
					double headloss = Util.HWheadLoss(flow, pipe.getRoughness(), pipe.getDiameter());
					double secondaryHeadloss = Util.HWheadLoss(flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
					
					if(headloss < generalProperties.min_hl_perkm/1000 || headloss > generalProperties.max_hl_perkm/1000){
						linear = new Linear();
						linear.add(1, "yp_"+i);
						problem.add(new Constraint("", linear, "=", 0));
						//System.out.println(i+" "+pipe.getDiameter()+" "+headloss);
					}
					else
						atleastOneHeadlossCorrect = true;
					
					if(secondaryHeadloss < generalProperties.min_hl_perkm/1000 || secondaryHeadloss > generalProperties.max_hl_perkm/1000){
						linear = new Linear();
						linear.add(1, "yp_"+i);
						linear.add(-1, "p_"+i);
						problem.add(new Constraint("", linear, "=", 0));
						//System.out.println(i+" "+pipe.getDiameter()+" "+headloss);
					}
					else
						atleastOneHeadlossCorrect = true;
					
					double speed = Util.waterSpeed(flow, pipe.getDiameter());
					double secondary_speed = Util.waterSpeed(flow*secondaryFlowFactor, pipe.getDiameter());
					
					if(generalProperties.max_water_speed > 0 && speed > generalProperties.max_water_speed){
						linear = new Linear();
						linear.add(1, "yp_"+i);
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneSpeedCorrect = true;
					
					if(generalProperties.max_water_speed > 0 && secondary_speed > generalProperties.max_water_speed){
						linear = new Linear();
						linear.add(1, "yp_"+i);
						linear.add(-1, "p_"+i);
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneSpeedCorrect = true;
				}
				else{
					double flow = pipe.getFlow();
					double headloss = Util.HWheadLoss(flow, pipe.getRoughness(), pipe.getDiameter());
					double secondaryHeadloss = Util.HWheadLoss(flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
					
					if(headloss < generalProperties.min_hl_perkm/1000 || headloss > generalProperties.max_hl_perkm/1000){
						linear = new Linear();
						linear.add(1, "y_"+i+"_"+fixedj);
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneHeadlossCorrect = true;
					
					if(secondaryHeadloss < generalProperties.min_hl_perkm/1000 || secondaryHeadloss > generalProperties.max_hl_perkm/1000){
						linear = new Linear();
						linear.add(1, "y_"+i+"_"+fixedj);
						linear.add(-1, "l_"+i+"_"+fixedj);
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneHeadlossCorrect = true;
										
					double primary_speed = Util.waterSpeed(flow, pipe.getDiameter());
					double secondary_speed = Util.waterSpeed(flow * secondaryFlowFactor, pipe.getDiameter());
					
					
					if(generalProperties.max_water_speed > 0 && primary_speed > generalProperties.max_water_speed){
						linear = new Linear();
						linear.add(1, "y_"+i+"_"+fixedj);
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneSpeedCorrect = true;	
					
					if(generalProperties.max_water_speed > 0 && secondary_speed > generalProperties.max_water_speed){
						linear = new Linear();
						linear.add(1, "y_"+i+"_"+fixedj);
						linear.add(-1, "l_"+i+"_"+fixedj);
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneSpeedCorrect = true;
				}
			}
			else{
				linear = new Linear();
				for(int j=0;j<pipeCost.size();j++){
					linear.add(1, "l_"+i+"_"+j);
				}
				problem.add(new Constraint("", linear, "=", pipe.getLength()));	
				
				int j=0;
				
				for(PipeCost entry : pipeCost){
					double flow = pipe.getFlow();
					double headloss = Util.HWheadLoss(flow, entry.getRoughness(), entry.getDiameter());
					double secondaryHeadloss = Util.HWheadLoss(flow * secondaryFlowFactor, entry.getRoughness(), entry.getDiameter());
					double speed = Util.waterSpeed(flow, entry.getDiameter()); 
					double secondary_speed = Util.waterSpeed(flow * secondaryFlowFactor, entry.getDiameter()); 
							
					if(headloss < generalProperties.min_hl_perkm/1000 || headloss > generalProperties.max_hl_perkm/1000){
						linear = new Linear();
						linear.add(1, "y_"+i+"_"+j);
						problem.add(new Constraint("", linear, "=", 0));
						//System.out.println(i+" "+entry.getDiameter()+" "+headloss);
					}
					else
						atleastOneHeadlossCorrect = true;
					
					if(secondaryHeadloss < generalProperties.min_hl_perkm/1000 || secondaryHeadloss > generalProperties.max_hl_perkm/1000){
						linear = new Linear();
						linear.add(1, "y_"+i+"_"+j);
						linear.add(-1, "l_"+i+"_"+j);
						problem.add(new Constraint("", linear, "=", 0));
						//System.out.println(i+" "+entry.getDiameter()+" "+headloss);
					}
					else
						atleastOneHeadlossCorrect = true;
					
					if(generalProperties.max_water_speed > 0 && speed > generalProperties.max_water_speed){
						linear = new Linear();
						linear.add(1, "y_"+i+"_"+j);
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneSpeedCorrect = true;
					
					if(generalProperties.max_water_speed > 0 && secondary_speed > generalProperties.max_water_speed){
						linear = new Linear();
						linear.add(1, "y_"+i+"_"+j);
						linear.add(-1, "l_"+i+"_"+j);
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneSpeedCorrect = true;
					
					j++;
				}
			}
			
			//if none of the commercial pipe diameters can satisfy headloss/speed constraints for the pipe, terminate optimization and inform user
			if(!atleastOneHeadlossCorrect)
				throw new Exception("Headloss for pipe: " + pipe.getPipeID() + " cannot be set within the min/max headloss values.");
			
			if(!atleastOneSpeedCorrect)
				throw new Exception("Speed of water in pipe: " + pipe.getPipeID() + " cannot be set below the max value.");
		}
		//System.out.println(problem);
	}
	
	//add constraints for the pipes in the network
	//includes constraints related to headloss/ water speed limits and assignment of commercial pipe diameters
	//updated with l_i_j_k instead of l_i_j
	private void setPipeConstraints_gen3() throws Exception{		
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();
			Linear linear;
			boolean atleastOneHeadlossCorrect = false;
			boolean atleastOneSpeedCorrect = false;
			
			linear = new Linear();
			Linear linear2 = new Linear();
			for(int j=0;j<pipeCost.size();j++){
				linear.add(1, "l_"+i+"_"+j+"_0");
				linear2.add(1, "l_"+i+"_"+j+"_1");
			}
			linear.add(pipe.getLength(), "f_"+i);
			linear2.add(-1*pipe.getLength(), "f_"+i);
			problem.add(new Constraint("", linear, "=", pipe.getLength()));	
			problem.add(new Constraint("", linear2, "=", 0));
			
			if(pipe.getDiameter()!=0){
				int j=0;
				int fixedj = 0;
				boolean diameterFromPipeCost = false;
				for(PipeCost entry : pipeCost){
					linear = new Linear();
					linear.add(1, "l_"+i+"_"+j+"_0");
					linear.add(1, "l_"+i+"_"+j+"_1");
					if(pipe.getDiameter() == entry.getDiameter()){
						problem.add(new Constraint("", linear, "=", pipe.getLength()));
						diameterFromPipeCost = true;
						fixedj = j;
					}
					else
						problem.add(new Constraint("", linear, "=", 0));				
					j++;
				}
				if(!diameterFromPipeCost)
					throw new Exception("The custom diameter: " + pipe.getDiameter() + " with roughness: "+ pipe.getRoughness() +" does not belong to commercial pipes"); 
				
				if(pipe.isAllowParallel()){
//					linear = new Linear();			
//					for(int j1=0;j1<pipeCost.size();j1++){
//						linear.add(1, "p_"+i+"_"+j1+"_0");
//						linear.add(1, "p_"+i+"_"+j1+"_1");
//					}
//					linear.add(1, "p_"+i+"_0");
//					linear.add(1, "p_"+i+"_1");
//					problem.add(new Constraint("", linear, "=", 1));	
					
					linear = new Linear();
					linear2 = new Linear();
					for(int j1=0;j1<pipeCost.size();j1++){
						linear.add(1, "p_"+i+"_"+j1+"_0");
						linear2.add(1, "p_"+i+"_"+j1+"_1");
					}
					linear.add(1, "p_"+i+"_0");
					linear2.add(1, "p_"+i+"_1");
					linear.add(1, "f_"+i);
					linear2.add(-1, "f_"+i);
					problem.add(new Constraint("", linear, "=", 1));
					problem.add(new Constraint("", linear2, "=", 0));
					
					j=0;
					for(PipeCost entry : pipeCost){
						double flow = pipe.getFlow();
									
						//when parallel pipe of dia j, what is flow in primary
						flow = flow / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852));						
						double headloss = Util.HWheadLoss(flow, pipe.getRoughness(), pipe.getDiameter());
						double secondaryHeadloss = Util.HWheadLoss(flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
						
						if(headloss < generalProperties.min_hl_perkm/1000 || headloss > generalProperties.max_hl_perkm/1000){
							linear = new Linear();
							linear.add(1, "p_"+i+"_"+j+"_1");
							problem.add(new Constraint("", linear, "=", 0));
						}
						else
							atleastOneHeadlossCorrect = true;
						
						if(secondaryHeadloss < generalProperties.min_hl_perkm/1000 || secondaryHeadloss > generalProperties.max_hl_perkm/1000){
							linear = new Linear();
							linear.add(1, "p_"+i+"_"+j+"_0");
							problem.add(new Constraint("", linear, "=", 0));
						}
						else
							atleastOneHeadlossCorrect = true;
						
						double parallel_flow = pipe.getFlow() - flow;
						double main_primary_speed = Util.waterSpeed(flow, pipe.getDiameter()); 
						double parallel_primary_speed = Util.waterSpeed(parallel_flow, entry.getDiameter());
						
						double main_secondary_speed = Util.waterSpeed(flow*secondaryFlowFactor, pipe.getDiameter()); 
						double parallel_secondary_speed = Util.waterSpeed(parallel_flow*secondaryFlowFactor, entry.getDiameter());
						
						
						if( generalProperties.max_water_speed > 0 && (main_primary_speed > generalProperties.max_water_speed || parallel_primary_speed > generalProperties.max_water_speed)){
							linear = new Linear();
							linear.add(1, "p_"+i+"_"+j+"_1");
							problem.add(new Constraint("", linear, "=", 0));
						}
						else
							atleastOneSpeedCorrect = true;
						
						if( generalProperties.max_water_speed > 0 && (main_secondary_speed > generalProperties.max_water_speed || parallel_secondary_speed > generalProperties.max_water_speed)){
							linear = new Linear();
							linear.add(1, "p_"+i+"_"+j+"_0");
							problem.add(new Constraint("", linear, "=", 0));
						}
						else
							atleastOneSpeedCorrect = true;
						
						j++;
					}
					
					double flow = pipe.getFlow();
					double headloss = Util.HWheadLoss(flow, pipe.getRoughness(), pipe.getDiameter());
					double secondaryHeadloss = Util.HWheadLoss(flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
					
					if(headloss < generalProperties.min_hl_perkm/1000 || headloss > generalProperties.max_hl_perkm/1000){
						linear = new Linear();
						linear.add(1, "p_"+i+"_1");
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneHeadlossCorrect = true;
					
					if(secondaryHeadloss < generalProperties.min_hl_perkm/1000 || secondaryHeadloss > generalProperties.max_hl_perkm/1000){
						linear = new Linear();
						linear.add(1, "p_"+i+"_0");
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneHeadlossCorrect = true;
					
					double primary_speed = Util.waterSpeed(flow, pipe.getDiameter());
					double secondary_speed = Util.waterSpeed(flow*secondaryFlowFactor, pipe.getDiameter());
					
					if(generalProperties.max_water_speed > 0 && primary_speed > generalProperties.max_water_speed){
						linear = new Linear();
						linear.add(1, "p_"+i+"_1");
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneSpeedCorrect = true;
					
					if(generalProperties.max_water_speed > 0 && secondary_speed > generalProperties.max_water_speed){
						linear = new Linear();
						linear.add(1, "p_"+i+"_0");
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneSpeedCorrect = true;
					
				}
				else{
					double flow = pipe.getFlow();
					double headloss = Util.HWheadLoss(flow, pipe.getRoughness(), pipe.getDiameter());
					double secondaryHeadloss = Util.HWheadLoss(flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
					
					if(headloss < generalProperties.min_hl_perkm/1000 || headloss > generalProperties.max_hl_perkm/1000){
						linear = new Linear();
						linear.add(1, "l_"+i+"_"+fixedj+"_1");
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneHeadlossCorrect = true;
					
					if(secondaryHeadloss < generalProperties.min_hl_perkm/1000 || secondaryHeadloss > generalProperties.max_hl_perkm/1000){
						linear = new Linear();
						linear.add(1, "l_"+i+"_"+fixedj+"_0");
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneHeadlossCorrect = true;
					
					double primary_speed = Util.waterSpeed(flow, pipe.getDiameter());
					double secondary_speed = Util.waterSpeed(flow*secondaryFlowFactor, pipe.getDiameter());
					
					if(generalProperties.max_water_speed > 0 && primary_speed > generalProperties.max_water_speed){
						linear = new Linear();
						linear.add(1, "l_"+i+"_"+fixedj+"_1");
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneSpeedCorrect = true;
					
					if(generalProperties.max_water_speed > 0 && secondary_speed > generalProperties.max_water_speed){
						linear = new Linear();
						linear.add(1, "l_"+i+"_"+fixedj+"_0");
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneSpeedCorrect = true;
				}
			}
			else{
								
				int j=0;
				
				for(PipeCost entry : pipeCost){
					double flow = pipe.getFlow();
					double headloss = Util.HWheadLoss(flow, entry.getRoughness(), entry.getDiameter());
					double secondaryHeadloss = Util.HWheadLoss(flow * secondaryFlowFactor, entry.getRoughness(), entry.getDiameter());
					
					if(headloss < generalProperties.min_hl_perkm/1000 || headloss > generalProperties.max_hl_perkm/1000){
						linear = new Linear();
						linear.add(1, "l_"+i+"_"+j+"_1");
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneHeadlossCorrect = true;
					
					if(secondaryHeadloss < generalProperties.min_hl_perkm/1000 || secondaryHeadloss > generalProperties.max_hl_perkm/1000){
						linear = new Linear();
						linear.add(1, "l_"+i+"_"+j+"_0");
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneHeadlossCorrect = true;
					
					double primary_speed = Util.waterSpeed(flow, entry.getDiameter());
					double secondary_speed = Util.waterSpeed(flow*secondaryFlowFactor, entry.getDiameter());
					
					if(generalProperties.max_water_speed > 0 && primary_speed > generalProperties.max_water_speed){
						linear = new Linear();
						linear.add(1, "l_"+i+"_"+j+"_1");
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneSpeedCorrect = true;
					
					if(generalProperties.max_water_speed > 0 && secondary_speed > generalProperties.max_water_speed){
						linear = new Linear();
						linear.add(1, "l_"+i+"_"+j+"_0");
						problem.add(new Constraint("", linear, "=", 0));
					}
					else
						atleastOneSpeedCorrect = true;
					
					j++;
				}
			}	
			if(!atleastOneHeadlossCorrect)
				throw new Exception("Headloss for pipe: " + pipe.getPipeID() + " cannot be set within the min/max headloss values.");
			if(!atleastOneSpeedCorrect)
				throw new Exception("Speed of water in pipe: " + pipe.getPipeID() + " cannot be set below the max value.");
		}
	}
	
	@SuppressWarnings("unused")
	private void setPipeConstraints_new() throws Exception
	{
		for(Pipe pipe : pipes.values())
		{
			int i = pipe.getPipeID();
			Linear linear;
			
			if(pipe.getDiameter()!=0)
			{
				int j=0;
				boolean diameterFromPipeCost = false;
				for(PipeCost pipeCost : pipeCost)
				{
					linear = new Linear();
					linear.add(1, "l_"+i+"_"+j);
					if(pipe.getDiameter()==pipeCost.getDiameter())
					{
						//problem.add(linear, "=", 1);
						problem.add(new Constraint("", linear, "=", pipe.getLength()));
						diameterFromPipeCost = true;
					}
					else
					{
						//problem.add(linear, "=", 0);
						problem.add(new Constraint("", linear, "=", 0));
					}
					j++;
				}
				if(!diameterFromPipeCost)
					throw new Exception("The custom diameter: " + pipe.getDiameter() + " provided does not belong to commercial pipes"); 
				continue;
			}
			lpmodelstring.append("PC"+i+": ");
			linear = new Linear();
			for(int j=0;j<pipeCost.size();j++)
			{
				linear.add(1, "l_"+i+"_"+j);
				lpmodelstring.append("vl_"+i+"_"+j+" ");
			}
			//System.out.println(linear);
			lpmodelstring.append("= "+pipe.getLength()+";\n");
			problem.add(new Constraint("", linear, "=", pipe.getLength()));	
			
			int j=0;
			/*for(double dia : pipeCost.keySet())
			{
				double headloss = Util.HWheadLoss(pipe.getFlow(), pipe.getRoughness(), dia);
				
				if(headloss < Pipe.getminHeadLossPerKM()/1000 || headloss > Pipe.getmaxHeadLossPerKM()/1000)
				{
					linear = new Linear();
					linear.add(1, "l_"+i+"_"+j);
					problem.add(new Constraint("", linear, "=", 0));
					lpmodelstring.append("PRC"+i+"_"+j+": vl_"+i+"_"+j+" = 0;\n");
					System.out.println(i+" "+dia+" "+headloss);
				}
				j++;
			}*/
			
			int M = 100000; //larger than any head in network
			// pressure constraints
			int startNodeID = pipe.getStartNode().getNodeID();
			int endNodeID = pipe.getEndNode().getNodeID();
			lpmodelvar.append("bin ");
			j=0;
			for(PipeCost pc : pipeCost)
			{
				problem.setVarType("ys_"+i+"_"+j, Boolean.class);
				problem.setVarType("ye_"+i+"_"+j, Boolean.class);
				lpmodelvar.append("vys_"+i+"_"+j+", vye_"+i+"_"+j+", ");
				//following 2 inequalities represent: if l_i_j > 0 then h_start <= max_pressure_j
				
				linear = new Linear();
				linear.add(1,"h_"+startNodeID);
				linear.add(-1*M,"ys_"+i+"_"+j);
				problem.add(new Constraint("", linear, "<=", pc.getMaxPressure()));
				lpmodelstring.append("PRS1C"+i+"_"+j+": vh_"+startNodeID+" -"+M+" vys_"+i+"_"+j+" <= "+pc.getMaxPressure()+";\n");
				
				linear = new Linear();
				linear.add(1,"l_"+i+"_"+j);
				linear.add(M,"ys_"+i+"_"+j);
				problem.add(new Constraint("", linear, "<=", M));
				lpmodelstring.append("PRS2C"+i+"_"+j+": vl_"+i+"_"+j+" "+M+" vys_"+i+"_"+j+" <= "+M+";\n");
				
				linear = new Linear();
				linear.add(1,"h_"+endNodeID);
				linear.add(-1*M,"ye_"+i+"_"+j);
				problem.add(new Constraint("", linear, "<=", pc.getMaxPressure()));
				lpmodelstring.append("PRE1C"+i+"_"+j+": vh_"+endNodeID+" -"+M+" vye_"+i+"_"+j+" <= "+pc.getMaxPressure()+";\n");
				
				linear = new Linear();
				linear.add(1,"l_"+i+"_"+j);
				linear.add(M,"ye_"+i+"_"+j);
				problem.add(new Constraint("", linear, "<=", M));
				lpmodelstring.append("PRE2C"+i+"_"+j+": vl_"+i+"_"+j+" "+M+" vye_"+i+"_"+j+" <= "+M+";\n");
				
				j++;
			}
			lpmodelvar.deleteCharAt(lpmodelvar.length()-1);
			lpmodelvar.deleteCharAt(lpmodelvar.length()-1);
			lpmodelvar.append(";\n");
			//pressure reducing valve constraints
			
			problem.setVarType("v_"+i, Double.class);
			problem.setVarLowerBound("v_"+i, 0);
			
		}
		//System.out.println(problem);
	}
		
	//set the headloss constraints in pipes
	private void setHeadLossConstraints() throws Exception{
		int j=0;
		for(Node node : nodes.values()){
			if(source!=node){
				Linear linear = new Linear();
				double valveloss = 0;
				for(Pipe pipe : node.getSourceToNodePipes()){
					int i = pipe.getPipeID();
					
					if(pipe.isAllowParallel()){
						j=0;
						for(PipeCost entry : pipeCost){
							// primary flow in case of parallel pipe
							double flow = pipe.getFlow() / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852));
							double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
							linear.add(headloss, "p_"+i+"_"+j);
							j++;
						}	
						// primary flow in case of no parallel pipe
						double flow = pipe.getFlow(); 
						double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
						linear.add(headloss, "p_"+i);
					
					}
					else{
						if(pipe.getDiameter()!=0){
							j=0;
							for(PipeCost entry : pipeCost){
								double headloss = Util.HWheadLoss(pipe.getFlow(), pipe.getRoughness(), entry.getDiameter());
								linear.add(headloss, "l_"+i+"_"+j);
								j++;
							}
						}
						else{	
							j=0;
							for(PipeCost entry : pipeCost){
								double headloss = Util.HWheadLoss(pipe.getFlow(), entry.getRoughness(), entry.getDiameter());
								linear.add(headloss, "l_"+i+"_"+j);
								j++;
							}	
						}
					}
					
					//additional water head provided by pump 
					if(pumpGeneralProperties.pump_enabled && pipe.getAllowPump()){
						linear.add(-1, "pumphead_"+i);
					}
					
					//loss of water head due to pressure reducing valve
					valveloss += pipe.getValveSetting();
				}
				
				//System.out.println(linear + " " + (Node.getSourceHGL() - node.getElevation() - node.getResidualPressure()));
				//problem.add(linear, "<=", Node.getSourceHGL() - node.getElevation() - node.getResidualPressure());
				
//				//ESR height h_i for each node
//				if(node.getDemand()>0){
//					problem.setVarType("h_"+node.getNodeID(), Double.class);
//					problem.setVarLowerBound("h_"+node.getNodeID(), 0);
//					linear.add(1,"h_"+node.getNodeID());
//				}
				problem.add(new Constraint("", linear, "<=", source.getHead() - node.getElevation() - node.getResidualPressure() - valveloss));
			}
		}
	}
	
	@SuppressWarnings("unused")
	private void setHeadLossConstraints_esr2() throws Exception{
		int j=0;
		for(Node node : nodes.values()){
			if(source!=node){
				Linear linear = new Linear();
				for(Pipe pipe : node.getSourceToNodePipes()){
					int i = pipe.getPipeID();
					if(pipe.isAllowParallel()){
						j=0;
						for(PipeCost entry : pipeCost){
							// primary flow in case of parallel pipe
							double flow = pipe.getFlow() / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852));
							double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
							double headlossSecondary = Util.HWheadLoss(pipe.getLength(), flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
							linear.add(headlossSecondary, "p_"+i+"_"+j);
							linear.add(headloss-headlossSecondary, "yp_"+i+"_"+j);
							j++;
						}	
						// primary flow in case of no parallel pipe
						double flow = pipe.getFlow(); 
						double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
						double headlossSecondary = Util.HWheadLoss(pipe.getLength(), flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
						linear.add(headlossSecondary, "p_"+i);
						linear.add(headloss-headlossSecondary, "yp_"+i);					
					}
					else{
						if(pipe.getDiameter()!=0){
							j=0;
							for(PipeCost entry : pipeCost){
								double headloss = Util.HWheadLoss(pipe.getFlow(), pipe.getRoughness(), entry.getDiameter());
								double headlossSecondary = Util.HWheadLoss(pipe.getFlow() * secondaryFlowFactor, pipe.getRoughness(), entry.getDiameter());
								linear.add(headlossSecondary, "l_"+i+"_"+j);
								linear.add(headloss-headlossSecondary, "y_"+i+"_"+j);
								j++;
							}
						}
						else{	
							j=0;
							for(PipeCost entry : pipeCost){
								double headloss = Util.HWheadLoss(pipe.getFlow(), entry.getRoughness(), entry.getDiameter());
								double headlossSecondary = Util.HWheadLoss(pipe.getFlow() * secondaryFlowFactor, entry.getRoughness(), entry.getDiameter());
								linear.add(headlossSecondary, "l_"+i+"_"+j);
								linear.add(headloss-headlossSecondary, "y_"+i+"_"+j);
								j++;
							}	
						}
					}
				}
				//System.out.println(linear + " " + (Node.getSourceHGL() - node.getElevation() - node.getResidualPressure()));
				//problem.add(linear, "<=", Node.getSourceHGL() - node.getElevation() - node.getResidualPressure());
				
//				//ESR height h_i for each node
//				if(node.getDemand()>0){
//					problem.setVarType("h_"+node.getNodeID(), Double.class);
//					problem.setVarLowerBound("h_"+node.getNodeID(), 0);
//					linear.add(1,"h_"+node.getNodeID());
//				}
				problem.add(new Constraint("", linear, "<=", source.getHead() - node.getElevation() - node.getResidualPressure()));
			}
		}
	}
	
	//set headloss constraints in pipes
	//includes ESRs and thus primary and secondary network considerations
	private void setHeadLossConstraints_esr() throws Exception{
		Linear linear = new Linear();

		for(Node node : nodes.values()){
			int i = node.getNodeID();
			if(source==node){
				linear = new Linear();
				linear.add(1,"head_"+i);
				problem.add(new Constraint("", linear, "=", source.getHead()));
			}
			else{			
				// min_esr_height <= esr_i <= max_esr_height
				linear = new Linear();
				linear.add(minEsrHeight,"besr_"+i);
				linear.add(-1,"esr_"+i);
				problem.add(new Constraint("", linear, "<=",0));
								
				linear = new Linear();
				linear.add(maxEsrHeight,"besr_"+i);
				linear.add(-1,"esr_"+i);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(1,"besr_"+i);
				linear.add(-1,"s_"+i+"_"+i);
				problem.add(new Constraint("", linear, "=",0));
				
				linear = new Linear();
				linear.add(1,"head_"+i);
				linear.add(-1,"esr_"+i);
				problem.add(new Constraint("", linear, ">=", node.getElevation() + node.getResidualPressure()));
			}
		}		
		
		for(Pipe pipe : pipes.values()){
			linear = new Linear();
			int i = pipe.getPipeID();
			int j = 0;
			if(pipe.isAllowParallel()){
				for(PipeCost entry : pipeCost){
					// primary flow in case of parallel pipe
					double flow = pipe.getFlow() / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852));
					double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
					double headlossSecondary = Util.HWheadLoss(pipe.getLength(), flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
					linear.add(headlossSecondary, "p_"+i+"_"+j);
					linear.add(headloss-headlossSecondary, "yp_"+i+"_"+j);
					j++;
				}	
				// primary flow in case of no parallel pipe
				double flow = pipe.getFlow(); 
				double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
				double headlossSecondary = Util.HWheadLoss(pipe.getLength(), flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
				linear.add(headlossSecondary, "p_"+i);
				linear.add(headloss-headlossSecondary, "yp_"+i);					
			}
			else{
				if(pipe.getDiameter()!=0){
					j=0;
					for(PipeCost entry : pipeCost){
						double headloss = Util.HWheadLoss(pipe.getFlow(), pipe.getRoughness(), entry.getDiameter());
						double headlossSecondary = Util.HWheadLoss(pipe.getFlow() * secondaryFlowFactor, pipe.getRoughness(), entry.getDiameter());
						linear.add(headlossSecondary, "l_"+i+"_"+j);
						linear.add(headloss-headlossSecondary, "y_"+i+"_"+j);
						j++;
					}
				}
				else{	
					j=0;
					for(PipeCost entry : pipeCost){
						double headloss = Util.HWheadLoss(pipe.getFlow(), entry.getRoughness(), entry.getDiameter());
						double headlossSecondary = Util.HWheadLoss(pipe.getFlow() * secondaryFlowFactor, entry.getRoughness(), entry.getDiameter());
						linear.add(headlossSecondary, "l_"+i+"_"+j);
						linear.add(headloss-headlossSecondary, "y_"+i+"_"+j);
						j++;
					}	
				}
			}
			Node startNode = pipe.getStartNode();
			int startid = startNode.getNodeID();
			int destinationid = pipe.getEndNode().getNodeID();
			
			linear.add(-1,"headloss_"+i);
			problem.add(new Constraint("", linear, "=",0));
			
			
			// head_source = head_source if demand_source = 0
			// head_source = head_elevation + esr_height if s_source_source = 1 and f_pipe = 0
			// else head_source = head_source
			// introduce head_i_j : is the start node, j is the id of the pipe
			// k_i_j = s_i_i && !f_j
			// head_i_j = k_i_j * (elevation_i + esr_i - head_i) + head_i
			// ehead_i = elevation_i + esr_i - head_i
			// yhead_i_j = k_i_j * ehead_i
			
			if(startNode.getDemand()>0){				
				// head_i_j = yhead_i_j + head_i
				linear = new Linear();
				linear.add(1,"yhead_"+startid+"_"+i);
				linear.add(1,"head_"+startid);
				linear.add(-1,"head_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "=",0));
				
				// ehead_i = elevation_i + esr_i - head_i
				linear = new Linear();
				linear.add(1,"ehead_"+startid);
				linear.add(1,"head_"+startid);
				linear.add(-1,"esr_"+startid);
				problem.add(new Constraint("", linear, "=",startNode.getElevation()));
				
				//to capture yhead_startid_i = k_startid_i * ehead_startid
				// ehead range assumed to be -10000,10000
				int minEHead = -10000;
				int maxEHead = 10000;
						
				linear = new Linear();
				linear.add(1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",maxEHead));
				
				linear = new Linear();
				linear.add(1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",minEHead));
				
				linear = new Linear();
				linear.add(-1,"yhead_"+startid+"_"+i);
				linear.add(maxEHead,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(-1,"yhead_"+startid+"_"+i);
				linear.add(minEHead,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",0));
				
				linear = new Linear();
				linear.add(1,"ehead_"+startid);
				linear.add(maxEHead,"k_"+startid+"_"+i);
				linear.add(-1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",maxEHead));
				
				linear = new Linear();
				linear.add(1,"ehead_"+startid);
				linear.add(minEHead,"k_"+startid+"_"+i);
				linear.add(-1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",minEHead));
				
				linear = new Linear();
				linear.add(-1,"ehead_"+startid);
				linear.add(maxEHead,"k_"+startid+"_"+i);
				linear.add(1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",maxEHead));
				
				
				//to capture k_startid_i = s_startid_startid ^ !f_i
				
				linear = new Linear();
				linear.add(1,"s_"+startid+"_"+startid);
				linear.add(-1,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(1,"f_"+i);
				linear.add(1,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",1));
				
				linear = new Linear();
				linear.add(1,"f_"+i);
				linear.add(1,"k_"+startid+"_"+i);
				linear.add(-1,"s_"+startid+"_"+startid);
				problem.add(new Constraint("", linear, ">=",0));
				
				//head_destination = head_i_j - headloss
				linear = new Linear();
				linear.add(1,"headloss_"+i);
				linear.add(1,"head_"+destinationid);
				linear.add(-1,"head_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "=",0));
			}
			else{
				// head_destination = head_source - headloss
				linear = new Linear();
				linear.add(1,"headloss_"+i);
				linear.add(1,"head_"+destinationid);
				linear.add(-1,"head_"+startid);
				problem.add(new Constraint("", linear, "=",0));
			}
		}
	}
	
	//set headloss constraints in pipes
	//includes ESRs and thus primary and secondary network considerations
	//also allows nodes with zero demands to have ESRs
	//allows removal of certain nodes from ESR consideration
	private void setHeadLossConstraints_esr_gen2() throws Exception{
		Linear linear = new Linear();

		for(Node node : nodes.values()){
			int i = node.getNodeID();
			if(source==node){
				linear = new Linear();
				linear.add(1,"head_"+i);
				problem.add(new Constraint("", linear, "=", source.getHead()));
			}
			else{
				linear = new Linear();
				linear.add(1,"head_"+i);
				linear.add(-1,"esr_"+i);
				problem.add(new Constraint("", linear, ">=", node.getElevation() + node.getResidualPressure()));
			
			}
				
			if(!node.getAllowESR()){
				linear = new Linear();
				linear.add(1,"d_"+i);
				problem.add(new Constraint("", linear, "=", 0));
			
			}
			
			// min_esr_height <= esr_i <= max_esr_height
			linear = new Linear();
			linear.add(minEsrHeight,"besr_"+i);
			linear.add(-1,"esr_"+i);
			problem.add(new Constraint("", linear, "<=",0));
							
			linear = new Linear();
			linear.add(maxEsrHeight,"besr_"+i);
			linear.add(-1,"esr_"+i);
			problem.add(new Constraint("", linear, ">=",0));
			
			linear = new Linear();
			linear.add(1,"besr_"+i);
			linear.add(-1,"s_"+i+"_"+i);
			problem.add(new Constraint("", linear, "=",0));
			
		}		
		
		for(Pipe pipe : pipes.values()){
			linear = new Linear();
			int i = pipe.getPipeID();
			int j = 0;
			if(pipe.isAllowParallel()){
				for(PipeCost entry : pipeCost){
					// primary flow in case of parallel pipe
					double flow = pipe.getFlow() / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852));
					double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
					double headlossSecondary = Util.HWheadLoss(pipe.getLength(), flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
					linear.add(headlossSecondary, "p_"+i+"_"+j);
					linear.add(headloss-headlossSecondary, "yp_"+i+"_"+j);
					j++;
				}	
				// primary flow in case of no parallel pipe
				double flow = pipe.getFlow(); 
				double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
				double headlossSecondary = Util.HWheadLoss(pipe.getLength(), flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
				linear.add(headlossSecondary, "p_"+i);
				linear.add(headloss-headlossSecondary, "yp_"+i);					
			}
			else{
				if(pipe.getDiameter()!=0){
					j=0;
					for(PipeCost entry : pipeCost){
						double headloss = Util.HWheadLoss(pipe.getFlow(), pipe.getRoughness(), entry.getDiameter());
						double headlossSecondary = Util.HWheadLoss(pipe.getFlow() * secondaryFlowFactor, pipe.getRoughness(), entry.getDiameter());
						linear.add(headlossSecondary, "l_"+i+"_"+j);
						linear.add(headloss-headlossSecondary, "y_"+i+"_"+j);
						j++;
					}
				}
				else{	
					j=0;
					for(PipeCost entry : pipeCost){
						double headloss = Util.HWheadLoss(pipe.getFlow(), entry.getRoughness(), entry.getDiameter());
						double headlossSecondary = Util.HWheadLoss(pipe.getFlow() * secondaryFlowFactor, entry.getRoughness(), entry.getDiameter());
						linear.add(headlossSecondary, "l_"+i+"_"+j);
						linear.add(headloss-headlossSecondary, "y_"+i+"_"+j);
						j++;
					}	
				}
			}
			Node startNode = pipe.getStartNode();
			int startid = startNode.getNodeID();
			int destinationid = pipe.getEndNode().getNodeID();
			
			linear.add(-1,"headloss_"+i);
			problem.add(new Constraint("", linear, "=",0));
			
			
			// head_source = head_source if demand_source = 0
			// head_source = head_elevation + esr_height if s_source_source = 1 and f_pipe = 0
			// else head_source = head_source
			// introduce head_i_j : is the start node, j is the id of the pipe
			// k_i_j = s_i_i && !f_j
			// head_i_j = k_i_j * (elevation_i + esr_i - head_i) + head_i
			// ehead_i = elevation_i + esr_i - head_i
			// yhead_i_j = k_i_j * ehead_i
					
			if(startNode.getAllowESR()){
				// head_i_j = yhead_i_j + head_i
				linear = new Linear();
				linear.add(1,"yhead_"+startid+"_"+i);
				linear.add(1,"head_"+startid);
				linear.add(-1,"head_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "=",0));
				
				// ehead_i = elevation_i + esr_i - head_i
				linear = new Linear();
				linear.add(1,"ehead_"+startid);
				linear.add(1,"head_"+startid);
				linear.add(-1,"esr_"+startid);
				problem.add(new Constraint("", linear, "=",startNode.getElevation()));
				
				//to capture yhead_startid_i = k_startid_i * ehead_startid
				// ehead range assumed to be -10000,10000
				int minEHead = -10000;
				int maxEHead = 10000;
						
				linear = new Linear();
				linear.add(1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",maxEHead));
				
				linear = new Linear();
				linear.add(1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",minEHead));
				
				linear = new Linear();
				linear.add(-1,"yhead_"+startid+"_"+i);
				linear.add(maxEHead,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(-1,"yhead_"+startid+"_"+i);
				linear.add(minEHead,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",0));
				
				linear = new Linear();
				linear.add(1,"ehead_"+startid);
				linear.add(maxEHead,"k_"+startid+"_"+i);
				linear.add(-1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",maxEHead));
				
				linear = new Linear();
				linear.add(1,"ehead_"+startid);
				linear.add(minEHead,"k_"+startid+"_"+i);
				linear.add(-1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",minEHead));
				
				linear = new Linear();
				linear.add(-1,"ehead_"+startid);
				linear.add(maxEHead,"k_"+startid+"_"+i);
				linear.add(1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",maxEHead));
				
				
				//to capture k_startid_i = s_startid_startid ^ !f_i
				
				linear = new Linear();
				linear.add(1,"s_"+startid+"_"+startid);
				linear.add(-1,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(1,"f_"+i);
				linear.add(1,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",1));
				
				linear = new Linear();
				linear.add(1,"f_"+i);
				linear.add(1,"k_"+startid+"_"+i);
				linear.add(-1,"s_"+startid+"_"+startid);
				problem.add(new Constraint("", linear, ">=",0));
				
				//head_destination = head_i_j - headloss
				linear = new Linear();
				linear.add(1,"headloss_"+i);
				linear.add(1,"head_"+destinationid);
				linear.add(-1,"head_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "=",0));
			}
			else{
				// head_destination = head_source - headloss
				linear = new Linear();
				linear.add(1,"headloss_"+i);
				linear.add(1,"head_"+destinationid);
				linear.add(-1,"head_"+startid);
				problem.add(new Constraint("", linear, "=",0));
			}
		}
	}
	
	//set headloss constraints in pipes
	//includes ESRs and thus primary and secondary network considerations
	//also allows nodes with zero demands to have ESRs
	//updated to use l_i_j_k instead of l_i_j
	private void setHeadLossConstraints_esr_gen3() throws Exception{
		Linear linear = new Linear();

		for(Node node : nodes.values()){
			int i = node.getNodeID();
			if(source==node){
				linear = new Linear();
				linear.add(1,"head_"+i);
				problem.add(new Constraint("", linear, "=", source.getHead()));
			}
			else{
				linear = new Linear();
				linear.add(1,"head_"+i);
				linear.add(-1,"esr_"+i);
				problem.add(new Constraint("", linear, ">=", node.getElevation() + node.getResidualPressure()));
			
			}
				
			if(!node.getAllowESR()){
				linear = new Linear();
				linear.add(1,"d_"+i);
				problem.add(new Constraint("", linear, "=", 0));
			
			}
			
			// min_esr_height <= esr_i <= max_esr_height
			linear = new Linear();
			linear.add(minEsrHeight,"besr_"+i);
			linear.add(-1,"esr_"+i);
			problem.add(new Constraint("", linear, "<=",0));
							
			linear = new Linear();
			linear.add(maxEsrHeight,"besr_"+i);
			linear.add(-1,"esr_"+i);
			problem.add(new Constraint("", linear, ">=",0));
			
			linear = new Linear();
			linear.add(1,"besr_"+i);
			linear.add(-1,"s_"+i+"_"+i);
			problem.add(new Constraint("", linear, "=",0));
			
		}		
		
		for(Pipe pipe : pipes.values()){
			linear = new Linear();
			int i = pipe.getPipeID();
			int j = 0;
			if(pipe.isAllowParallel()){
				for(PipeCost entry : pipeCost){
					// primary flow in case of parallel pipe
					double flow = pipe.getFlow() / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852));
					double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
					double headlossSecondary = Util.HWheadLoss(pipe.getLength(), flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
					linear.add(headlossSecondary, "p_"+i+"_"+j+"_0");
					linear.add(headloss, "p_"+i+"_"+j+"_1");
					j++;
				}	
				// primary flow in case of no parallel pipe
				double flow = pipe.getFlow(); 
				double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
				double headlossSecondary = Util.HWheadLoss(pipe.getLength(), flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
				linear.add(headlossSecondary, "p_"+i+"_0");
				linear.add(headloss, "p_"+i+"_1");					
			}
			else{
				if(pipe.getDiameter()!=0){
					j=0;
					for(PipeCost entry : pipeCost){
						double headloss = Util.HWheadLoss(pipe.getFlow(), pipe.getRoughness(), entry.getDiameter());
						double headlossSecondary = Util.HWheadLoss(pipe.getFlow() * secondaryFlowFactor, pipe.getRoughness(), entry.getDiameter());
						linear.add(headlossSecondary, "l_"+i+"_"+j+"_0");
						linear.add(headloss, "l_"+i+"_"+j+"_1");
						j++;
					}
				}
				else{	
					j=0;
					for(PipeCost entry : pipeCost){
						double headloss = Util.HWheadLoss(pipe.getFlow(), entry.getRoughness(), entry.getDiameter());
						double headlossSecondary = Util.HWheadLoss(pipe.getFlow() * secondaryFlowFactor, entry.getRoughness(), entry.getDiameter());
						linear.add(headlossSecondary, "l_"+i+"_"+j+"_0");
						linear.add(headloss, "l_"+i+"_"+j+"_1");
						j++;
					}	
				}
			}
			linear.add(-1,"headloss_"+i);
			problem.add(new Constraint("", linear, "=",0));
			
			
			Node startNode = pipe.getStartNode();
			int startid = startNode.getNodeID();
			int destinationid = pipe.getEndNode().getNodeID();
						
			// head_source = head_source if demand_source = 0
			// head_source = head_elevation + esr_height if s_source_source = 1 and f_pipe = 0
			// else head_source = head_source
			// introduce head_i_j : is the start node, j is the id of the pipe
			// k_i_j = s_i_i && !f_j
			// head_i_j = k_i_j * (elevation_i + esr_i - head_i) + head_i
			// ehead_i = elevation_i + esr_i - head_i
			// yhead_i_j = k_i_j * ehead_i
					
			if(startNode.getAllowESR()){
				// head_i_j = yhead_i_j + head_i
				linear = new Linear();
				linear.add(1,"yhead_"+startid+"_"+i);
				linear.add(1,"head_"+startid);
				linear.add(-1,"head_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "=",0));
				
				// ehead_i = elevation_i + esr_i - head_i
				linear = new Linear();
				linear.add(1,"ehead_"+startid);
				linear.add(1,"head_"+startid);
				linear.add(-1,"esr_"+startid);
				problem.add(new Constraint("", linear, "=",startNode.getElevation()));
				
				//to capture yhead_startid_i = k_startid_i * ehead_startid
				// ehead range assumed to be -10000,10000
				int minEHead = -10000;
				int maxEHead = 10000;
						
				linear = new Linear();
				linear.add(1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",maxEHead));
				
				linear = new Linear();
				linear.add(1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",minEHead));
				
				linear = new Linear();
				linear.add(-1,"yhead_"+startid+"_"+i);
				linear.add(maxEHead,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(-1,"yhead_"+startid+"_"+i);
				linear.add(minEHead,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",0));
				
				linear = new Linear();
				linear.add(1,"ehead_"+startid);
				linear.add(maxEHead,"k_"+startid+"_"+i);
				linear.add(-1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",maxEHead));
				
				linear = new Linear();
				linear.add(1,"ehead_"+startid);
				linear.add(minEHead,"k_"+startid+"_"+i);
				linear.add(-1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",minEHead));
				
				linear = new Linear();
				linear.add(-1,"ehead_"+startid);
				linear.add(maxEHead,"k_"+startid+"_"+i);
				linear.add(1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",maxEHead));
				
				
				//to capture k_startid_i = s_startid_startid ^ !f_i
				
				linear = new Linear();
				linear.add(1,"s_"+startid+"_"+startid);
				linear.add(-1,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(1,"f_"+i);
				linear.add(1,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",1));
				
				linear = new Linear();
				linear.add(1,"f_"+i);
				linear.add(1,"k_"+startid+"_"+i);
				linear.add(-1,"s_"+startid+"_"+startid);
				problem.add(new Constraint("", linear, ">=",0));
				
				//head_destination = head_i_j - headloss
				linear = new Linear();
				linear.add(1,"headloss_"+i);
				linear.add(1,"head_"+destinationid);
				linear.add(-1,"head_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "=",0));
			}
			else{
				// head_destination = head_source - headloss
				linear = new Linear();
				linear.add(1,"headloss_"+i);
				linear.add(1,"head_"+destinationid);
				linear.add(-1,"head_"+startid);
				problem.add(new Constraint("", linear, "=",0));
			}
		}
	}
	
	//set headloss constraints in pipes
	//includes ESRs and thus primary and secondary network considerations
	//also allows nodes with zero demands to have ESRs
	//updated to use l_i_j_k instead of l_i_j
	//includes pumps
	private void setHeadLossConstraints_esr_gen4() throws Exception{
		Linear linear = new Linear();

		for(Node node : nodes.values()){
			int i = node.getNodeID();
			if(source==node){
				linear = new Linear();
				linear.add(1,"head_"+i);
				problem.add(new Constraint("", linear, "=", source.getHead()));
			}
			else{
				linear = new Linear();
				linear.add(1,"head_"+i);
				linear.add(-1,"esr_"+i);
				problem.add(new Constraint("", linear, ">=", node.getElevation() + node.getResidualPressure()));
			
			}
				
			if(!node.getAllowESR()){
				linear = new Linear();
				linear.add(1,"d_"+i);
				problem.add(new Constraint("", linear, "=", 0));
			
			}
			
			// min_esr_height <= esr_i <= max_esr_height
			linear = new Linear();
			linear.add(minEsrHeight,"besr_"+i);
			linear.add(-1,"esr_"+i);
			problem.add(new Constraint("", linear, "<=",0));
							
			linear = new Linear();
			linear.add(maxEsrHeight,"besr_"+i);
			linear.add(-1,"esr_"+i);
			problem.add(new Constraint("", linear, ">=",0));
			
			linear = new Linear();
			linear.add(1,"besr_"+i);
			linear.add(-1,"s_"+i+"_"+i);
			problem.add(new Constraint("", linear, "=",0));
			
		}		
		
		for(Pipe pipe : pipes.values()){
			linear = new Linear();
			int i = pipe.getPipeID();
			int j = 0;
			if(pipe.isAllowParallel()){
				for(PipeCost entry : pipeCost){
					// primary flow in case of parallel pipe
					double flow = pipe.getFlow() / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852));
					double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
					double headlossSecondary = Util.HWheadLoss(pipe.getLength(), flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
					linear.add(headlossSecondary, "p_"+i+"_"+j+"_0");
					linear.add(headloss, "p_"+i+"_"+j+"_1");
					j++;
				}	
				// primary flow in case of no parallel pipe
				double flow = pipe.getFlow(); 
				double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
				double headlossSecondary = Util.HWheadLoss(pipe.getLength(), flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
				linear.add(headlossSecondary, "p_"+i+"_0");
				linear.add(headloss, "p_"+i+"_1");					
			}
			else{
				if(pipe.getDiameter()!=0){
					j=0;
					for(PipeCost entry : pipeCost){
						double headloss = Util.HWheadLoss(pipe.getFlow(), pipe.getRoughness(), entry.getDiameter());
						double headlossSecondary = Util.HWheadLoss(pipe.getFlow() * secondaryFlowFactor, pipe.getRoughness(), entry.getDiameter());
						linear.add(headlossSecondary, "l_"+i+"_"+j+"_0");
						linear.add(headloss, "l_"+i+"_"+j+"_1");
						j++;
					}
				}
				else{	
					j=0;
					for(PipeCost entry : pipeCost){
						double headloss = Util.HWheadLoss(pipe.getFlow(), entry.getRoughness(), entry.getDiameter());
						double headlossSecondary = Util.HWheadLoss(pipe.getFlow() * secondaryFlowFactor, entry.getRoughness(), entry.getDiameter());
						linear.add(headlossSecondary, "l_"+i+"_"+j+"_0");
						linear.add(headloss, "l_"+i+"_"+j+"_1");
						j++;
					}	
				}
			}
			
			if(pumpGeneralProperties.pump_enabled && pipe.getAllowPump()){
				linear.add(-1, "pumphead_"+i);
			}
			
			linear.add(-1,"headloss_"+i);
			problem.add(new Constraint("", linear, "=",-1*pipe.getValveSetting()));
					
			Node startNode = pipe.getStartNode();
			int startid = startNode.getNodeID();
			int destinationid = pipe.getEndNode().getNodeID();
						
			// head_source = head_source if demand_source = 0
			// head_source = head_elevation + esr_height if s_source_source = 1 and f_pipe = 0
			// else head_source = head_source
			// introduce head_i_j : is the start node, j is the id of the pipe
			// k_i_j = s_i_i && !f_j
			// head_i_j = k_i_j * (elevation_i + esr_i - head_i) + head_i
			// ehead_i = elevation_i + esr_i - head_i
			// yhead_i_j = k_i_j * ehead_i
					
			if(startNode.getAllowESR()){
				// head_i_j = yhead_i_j + head_i
				linear = new Linear();
				linear.add(1,"yhead_"+startid+"_"+i);
				linear.add(1,"head_"+startid);
				linear.add(-1,"head_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "=",0));
				
				// ehead_i = elevation_i + esr_i - head_i
				linear = new Linear();
				linear.add(1,"ehead_"+startid);
				linear.add(1,"head_"+startid);
				linear.add(-1,"esr_"+startid);
				problem.add(new Constraint("", linear, "=",startNode.getElevation()));
				
				//to capture yhead_startid_i = k_startid_i * ehead_startid
				// ehead range assumed to be -10000,10000
				int minEHead = -10000;
				int maxEHead = 10000;
						
				linear = new Linear();
				linear.add(1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",maxEHead));
				
				linear = new Linear();
				linear.add(1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",minEHead));
				
				linear = new Linear();
				linear.add(-1,"yhead_"+startid+"_"+i);
				linear.add(maxEHead,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(-1,"yhead_"+startid+"_"+i);
				linear.add(minEHead,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",0));
				
				linear = new Linear();
				linear.add(1,"ehead_"+startid);
				linear.add(maxEHead,"k_"+startid+"_"+i);
				linear.add(-1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",maxEHead));
				
				linear = new Linear();
				linear.add(1,"ehead_"+startid);
				linear.add(minEHead,"k_"+startid+"_"+i);
				linear.add(-1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",minEHead));
				
				linear = new Linear();
				linear.add(-1,"ehead_"+startid);
				linear.add(maxEHead,"k_"+startid+"_"+i);
				linear.add(1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",maxEHead));
				
				
				//to capture k_startid_i = s_startid_startid ^ !f_i
				
				linear = new Linear();
				linear.add(1,"s_"+startid+"_"+startid);
				linear.add(-1,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(1,"f_"+i);
				linear.add(1,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",1));
				
				linear = new Linear();
				linear.add(1,"f_"+i);
				linear.add(1,"k_"+startid+"_"+i);
				linear.add(-1,"s_"+startid+"_"+startid);
				problem.add(new Constraint("", linear, ">=",0));
				
				//head_destination = head_i_j - headloss
				linear = new Linear();
				linear.add(1,"headloss_"+i);
				linear.add(1,"head_"+destinationid);
				linear.add(-1,"head_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "=",0));
			}
			else{
				// head_destination = head_source - headloss
				linear = new Linear();
				linear.add(1,"headloss_"+i);
				linear.add(1,"head_"+destinationid);
				linear.add(-1,"head_"+startid);
				problem.add(new Constraint("", linear, "=",0));
			}
		}
	}
	
	//set headloss constraints in pipes
	//removed usage of s_i_j variables
	private void setHeadLossConstraints_esr_gen5() throws Exception{
		Linear linear = new Linear();

		for(Node node : nodes.values()){
			int i = node.getNodeID();
			if(source==node){
				linear = new Linear();
				linear.add(1,"head_"+i);
				problem.add(new Constraint("", linear, "=", source.getHead()));
			}
			else{
				linear = new Linear();
				linear.add(1,"head_"+i);
				linear.add(-1,"esr_"+i);
				problem.add(new Constraint("", linear, ">=", node.getElevation() + node.getResidualPressure()));
			
			}
				
			if(!node.getAllowESR()){
				linear = new Linear();
				linear.add(1,"d_"+i);
				problem.add(new Constraint("", linear, "=", 0));
			
			}
			
			// min_esr_height <= esr_i <= max_esr_height
			linear = new Linear();
			linear.add(minEsrHeight,"besr_"+i);
			linear.add(-1,"esr_"+i);
			problem.add(new Constraint("", linear, "<=",0));
							
			linear = new Linear();
			linear.add(maxEsrHeight,"besr_"+i);
			linear.add(-1,"esr_"+i);
			problem.add(new Constraint("", linear, ">=",0));			
		}		
		
		for(Pipe pipe : pipes.values()){
			linear = new Linear();
			int i = pipe.getPipeID();
			int j = 0;
			if(pipe.isAllowParallel()){
				for(PipeCost entry : pipeCost){
					// primary flow in case of parallel pipe
					double flow = pipe.getFlow() / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852));
					double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
					double headlossSecondary = Util.HWheadLoss(pipe.getLength(), flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
					linear.add(headlossSecondary, "p_"+i+"_"+j+"_0");
					linear.add(headloss, "p_"+i+"_"+j+"_1");
					j++;
				}	
				// primary flow in case of no parallel pipe
				double flow = pipe.getFlow(); 
				double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
				double headlossSecondary = Util.HWheadLoss(pipe.getLength(), flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
				linear.add(headlossSecondary, "p_"+i+"_0");
				linear.add(headloss, "p_"+i+"_1");					
			}
			else{
				if(pipe.getDiameter()!=0){
					j=0;
					for(PipeCost entry : pipeCost){
						double headloss = Util.HWheadLoss(pipe.getFlow(), pipe.getRoughness(), entry.getDiameter());
						double headlossSecondary = Util.HWheadLoss(pipe.getFlow() * secondaryFlowFactor, pipe.getRoughness(), entry.getDiameter());
						linear.add(headlossSecondary, "l_"+i+"_"+j+"_0");
						linear.add(headloss, "l_"+i+"_"+j+"_1");
						j++;
					}
				}
				else{	
					j=0;
					for(PipeCost entry : pipeCost){
						double headloss = Util.HWheadLoss(pipe.getFlow(), entry.getRoughness(), entry.getDiameter());
						double headlossSecondary = Util.HWheadLoss(pipe.getFlow() * secondaryFlowFactor, entry.getRoughness(), entry.getDiameter());
						linear.add(headlossSecondary, "l_"+i+"_"+j+"_0");
						linear.add(headloss, "l_"+i+"_"+j+"_1");
						j++;
					}	
				}
			}
			
			if(pumpGeneralProperties.pump_enabled && pipe.getAllowPump()){
				linear.add(-1, "pumphead_"+i);
			}
			
			linear.add(-1,"headloss_"+i);
			problem.add(new Constraint("", linear, "=",-1*pipe.getValveSetting()));
					
			Node startNode = pipe.getStartNode();
			int startid = startNode.getNodeID();
			int destinationid = pipe.getEndNode().getNodeID();
						
			// head_source = head_source if demand_source = 0
			// head_source = head_elevation + esr_height if s_source_source = 1 and f_pipe = 0
			// else head_source = head_source
			// introduce head_i_j : i is the start node, j is the id of the pipe
			// k_i_j = besr_i && !f_j
			// head_i_j = k_i_j * (elevation_i + esr_i - head_i) + head_i
			// ehead_i = elevation_i + esr_i - head_i
			// yhead_i_j = k_i_j * ehead_i
					
			if(startNode.getAllowESR()){
				// head_i_j = yhead_i_j + head_i
				linear = new Linear();
				linear.add(1,"yhead_"+startid+"_"+i);
				linear.add(1,"head_"+startid);
				linear.add(-1,"head_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "=",0));
				
				// ehead_i = elevation_i + esr_i - head_i
				linear = new Linear();
				linear.add(1,"ehead_"+startid);
				linear.add(1,"head_"+startid);
				linear.add(-1,"esr_"+startid);
				problem.add(new Constraint("", linear, "=",startNode.getElevation()));
				
				//to capture yhead_startid_i = k_startid_i * ehead_startid
				// ehead range assumed to be -10000,10000
				int minEHead = -10000;
				int maxEHead = 10000;
						
				linear = new Linear();
				linear.add(1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",maxEHead));
				
				linear = new Linear();
				linear.add(1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",minEHead));
				
				linear = new Linear();
				linear.add(-1,"yhead_"+startid+"_"+i);
				linear.add(maxEHead,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(-1,"yhead_"+startid+"_"+i);
				linear.add(minEHead,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",0));
				
				linear = new Linear();
				linear.add(1,"ehead_"+startid);
				linear.add(maxEHead,"k_"+startid+"_"+i);
				linear.add(-1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",maxEHead));
				
				linear = new Linear();
				linear.add(1,"ehead_"+startid);
				linear.add(minEHead,"k_"+startid+"_"+i);
				linear.add(-1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",minEHead));
				
				linear = new Linear();
				linear.add(-1,"ehead_"+startid);
				linear.add(maxEHead,"k_"+startid+"_"+i);
				linear.add(1,"yhead_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",maxEHead));
				
				
				//to capture k_startid_i = besr_startid ^ !f_i
				
				linear = new Linear();
				linear.add(1,"besr_"+startid);
				linear.add(-1,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(1,"f_"+i);
				linear.add(1,"k_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "<=",1));
				
				linear = new Linear();
				linear.add(1,"f_"+i);
				linear.add(1,"k_"+startid+"_"+i);
				linear.add(-1,"besr_"+startid);
				problem.add(new Constraint("", linear, ">=",0));
				
				//head_destination = head_i_j - headloss
				linear = new Linear();
				linear.add(1,"headloss_"+i);
				linear.add(1,"head_"+destinationid);
				linear.add(-1,"head_"+startid+"_"+i);
				problem.add(new Constraint("", linear, "=",0));
			}
			else{
				// head_destination = head_source - headloss
				linear = new Linear();
				linear.add(1,"headloss_"+i);
				linear.add(1,"head_"+destinationid);
				linear.add(-1,"head_"+startid);
				problem.add(new Constraint("", linear, "=",0));
			}
		}
	}
	
	//set headloss constraints in pipes
	//includes ESRs and thus primary and secondary network considerations
	//also allows nodes with zero demands to have ESRs
	private void setHeadLossConstraints_esr_gen() throws Exception{
		Linear linear = new Linear();

		for(Node node : nodes.values()){
			int i = node.getNodeID();
			if(source==node){
				linear = new Linear();
				linear.add(1,"head_"+i);
				problem.add(new Constraint("", linear, "=", source.getHead()));
			}
			else{
				linear = new Linear();
				linear.add(1,"head_"+i);
				linear.add(-1,"esr_"+i);
				problem.add(new Constraint("", linear, ">=", node.getElevation() + node.getResidualPressure()));
			
			}
						
			// min_esr_height <= esr_i <= max_esr_height
			linear = new Linear();
			linear.add(minEsrHeight,"besr_"+i);
			linear.add(-1,"esr_"+i);
			problem.add(new Constraint("", linear, "<=",0));
							
			linear = new Linear();
			linear.add(maxEsrHeight,"besr_"+i);
			linear.add(-1,"esr_"+i);
			problem.add(new Constraint("", linear, ">=",0));
			
			linear = new Linear();
			linear.add(1,"besr_"+i);
			linear.add(-1,"s_"+i+"_"+i);
			problem.add(new Constraint("", linear, "=",0));
			
		}		
		
		for(Pipe pipe : pipes.values()){
			linear = new Linear();
			int i = pipe.getPipeID();
			int j = 0;
			if(pipe.isAllowParallel()){
				for(PipeCost entry : pipeCost){
					// primary flow in case of parallel pipe
					double flow = pipe.getFlow() / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852));
					double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
					double headlossSecondary = Util.HWheadLoss(pipe.getLength(), flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
					linear.add(headlossSecondary, "p_"+i+"_"+j);
					linear.add(headloss-headlossSecondary, "yp_"+i+"_"+j);
					j++;
				}	
				// primary flow in case of no parallel pipe
				double flow = pipe.getFlow(); 
				double headloss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
				double headlossSecondary = Util.HWheadLoss(pipe.getLength(), flow * secondaryFlowFactor, pipe.getRoughness(), pipe.getDiameter());
				linear.add(headlossSecondary, "p_"+i);
				linear.add(headloss-headlossSecondary, "yp_"+i);					
			}
			else{
				if(pipe.getDiameter()!=0){
					j=0;
					for(PipeCost entry : pipeCost){
						double headloss = Util.HWheadLoss(pipe.getFlow(), pipe.getRoughness(), entry.getDiameter());
						double headlossSecondary = Util.HWheadLoss(pipe.getFlow() * secondaryFlowFactor, pipe.getRoughness(), entry.getDiameter());
						linear.add(headlossSecondary, "l_"+i+"_"+j);
						linear.add(headloss-headlossSecondary, "y_"+i+"_"+j);
						j++;
					}
				}
				else{	
					j=0;
					for(PipeCost entry : pipeCost){
						double headloss = Util.HWheadLoss(pipe.getFlow(), entry.getRoughness(), entry.getDiameter());
						double headlossSecondary = Util.HWheadLoss(pipe.getFlow() * secondaryFlowFactor, entry.getRoughness(), entry.getDiameter());
						linear.add(headlossSecondary, "l_"+i+"_"+j);
						linear.add(headloss-headlossSecondary, "y_"+i+"_"+j);
						j++;
					}	
				}
			}
			Node startNode = pipe.getStartNode();
			int startid = startNode.getNodeID();
			int destinationid = pipe.getEndNode().getNodeID();
			
			linear.add(-1,"headloss_"+i);
			problem.add(new Constraint("", linear, "=",0));
			
			
			// head_source = head_source if demand_source = 0
			// head_source = head_elevation + esr_height if s_source_source = 1 and f_pipe = 0
			// else head_source = head_source
			// introduce head_i_j : is the start node, j is the id of the pipe
			// k_i_j = s_i_i && !f_j
			// head_i_j = k_i_j * (elevation_i + esr_i - head_i) + head_i
			// ehead_i = elevation_i + esr_i - head_i
			// yhead_i_j = k_i_j * ehead_i
					
			// head_i_j = yhead_i_j + head_i
			linear = new Linear();
			linear.add(1,"yhead_"+startid+"_"+i);
			linear.add(1,"head_"+startid);
			linear.add(-1,"head_"+startid+"_"+i);
			problem.add(new Constraint("", linear, "=",0));
			
			// ehead_i = elevation_i + esr_i - head_i
			linear = new Linear();
			linear.add(1,"ehead_"+startid);
			linear.add(1,"head_"+startid);
			linear.add(-1,"esr_"+startid);
			problem.add(new Constraint("", linear, "=",startNode.getElevation()));
			
			//to capture yhead_startid_i = k_startid_i * ehead_startid
			// ehead range assumed to be -10000,10000
			int minEHead = -10000;
			int maxEHead = 10000;
					
			linear = new Linear();
			linear.add(1,"yhead_"+startid+"_"+i);
			problem.add(new Constraint("", linear, "<=",maxEHead));
			
			linear = new Linear();
			linear.add(1,"yhead_"+startid+"_"+i);
			problem.add(new Constraint("", linear, ">=",minEHead));
			
			linear = new Linear();
			linear.add(-1,"yhead_"+startid+"_"+i);
			linear.add(maxEHead,"k_"+startid+"_"+i);
			problem.add(new Constraint("", linear, ">=",0));
			
			linear = new Linear();
			linear.add(-1,"yhead_"+startid+"_"+i);
			linear.add(minEHead,"k_"+startid+"_"+i);
			problem.add(new Constraint("", linear, "<=",0));
			
			linear = new Linear();
			linear.add(1,"ehead_"+startid);
			linear.add(maxEHead,"k_"+startid+"_"+i);
			linear.add(-1,"yhead_"+startid+"_"+i);
			problem.add(new Constraint("", linear, "<=",maxEHead));
			
			linear = new Linear();
			linear.add(1,"ehead_"+startid);
			linear.add(minEHead,"k_"+startid+"_"+i);
			linear.add(-1,"yhead_"+startid+"_"+i);
			problem.add(new Constraint("", linear, ">=",minEHead));
			
			linear = new Linear();
			linear.add(-1,"ehead_"+startid);
			linear.add(maxEHead,"k_"+startid+"_"+i);
			linear.add(1,"yhead_"+startid+"_"+i);
			problem.add(new Constraint("", linear, "<=",maxEHead));
			
			
			//to capture k_startid_i = s_startid_startid ^ !f_i
			
			linear = new Linear();
			linear.add(1,"s_"+startid+"_"+startid);
			linear.add(-1,"k_"+startid+"_"+i);
			problem.add(new Constraint("", linear, ">=",0));
			
			linear = new Linear();
			linear.add(1,"f_"+i);
			linear.add(1,"k_"+startid+"_"+i);
			problem.add(new Constraint("", linear, "<=",1));
			
			linear = new Linear();
			linear.add(1,"f_"+i);
			linear.add(1,"k_"+startid+"_"+i);
			linear.add(-1,"s_"+startid+"_"+startid);
			problem.add(new Constraint("", linear, ">=",0));
			
			//head_destination = head_i_j - headloss
			linear = new Linear();
			linear.add(1,"headloss_"+i);
			linear.add(1,"head_"+destinationid);
			linear.add(-1,"head_"+startid+"_"+i);
			problem.add(new Constraint("", linear, "=",0));
		}
	}
	
	//add constraints related to the ESR structure in the network
	//ensures ESR configuration is valid
	//includes constraints related to selection of cost row table for each ESR
	private void setEsrConstraints() throws Exception{		
		Linear linear,linear2;
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			if(node.getDemand()!=0){
				
				for(Node downNode : node.getDownstreamNodes()){
					int j = downNode.getNodeID();
					
					//following constraint represents s_i_i = 0 => s_j_j = 0 where j are downstream of i 
					linear = new Linear();
					linear.add(1, "s_"+i+"_"+i); 
					linear.add(-1, "s_"+j+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
					
					//following constraint represents s_i_i = 0 => s_i_j = 0 where j are downstream of i 
					linear = new Linear();
					linear.add(1, "s_"+i+"_"+i); 
					linear.add(-1, "s_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
				}
				
				// following constraint represents sum over parent i's s_j_i = 1 
				// i.e. exactly one esr serves location i 
				linear = new Linear();
				linear.add(1, "s_"+i+"_"+i);
				for(Node upstreamNode : node.getUpstreamNodes()){
					int j = upstreamNode.getNodeID();
					linear.add(1, "s_"+j+"_"+i);
				}
				problem.add(new Constraint("", linear, "=",1));
		
				// d_i = sum all demand served by i / esrcapacity factor
				linear = new Linear();
				linear.add(node.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+i);
				for(Node downstreamNode : node.getDownstreamNodes()){
					int j = downstreamNode.getNodeID();
					linear.add(downstreamNode.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+j);
				}
				linear.add(-1,"d_"+i);
				problem.add(new Constraint("", linear, "=",0));
				
				int j=0;
				linear2 = new Linear();
				for(EsrCost esr : esrCost){
					
					//to determine e_i_j
					linear = new Linear();
					linear.add(esr.getMinCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"d_"+i);
					problem.add(new Constraint("", linear, "<=",0));
					
					linear = new Linear();
					linear.add(totalDemand,"e_"+i+"_"+j); //totaldemand just stands for a large amount of demand M
					linear.add(1,"d_"+i);
					problem.add(new Constraint("", linear, "<=",esr.getMaxCapacity()+totalDemand));
					
					linear2.add(1,"e_"+i+"_"+j);
					
					//to capture z_i_j = d_i * e_i_j
					
					linear = new Linear();
					linear.add(totalDemand,"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
					
					linear = new Linear();
					linear.add(1,"d_"+i);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
					
					linear = new Linear();
					linear.add(totalDemand,"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					linear.add(1,"d_"+i);
					problem.add(new Constraint("", linear, "<=",totalDemand));
					
					j++;
				}
				
				problem.add(new Constraint("", linear2, "=",1));
			}
		}
	}
	
	//add constraints related to the ESR structure in the network
	//ensures ESR configuration is valid
	//includes constraints related to selection of cost row table for each ESR
	//allows zero demand nodes
	private void setEsrConstraints_gen() throws Exception{		
		Linear linear,linear2;
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			
			for(Pipe pipe : node.getOutgoingPipes()){
				
				int j = pipe.getEndNode().getNodeID();
				
				//following constraint represents s_i_i = 0 => s_j_j = 0 where j are children of i 
				linear = new Linear();
				linear.add(1, "s_"+i+"_"+i); 
				linear.add(-1, "s_"+j+"_"+j);
				problem.add(new Constraint("", linear, ">=",0));
			}
			
			// following constraint represents sum over parent i's s_j_i = 1 
			// i.e. exactly one esr serves location i 
			linear = new Linear();
			linear.add(1, "s_"+i+"_"+i);
			for(Node upstreamNode : node.getUpstreamNodes()){
				int j = upstreamNode.getNodeID();
				linear.add(1, "s_"+j+"_"+i);
			}
			problem.add(new Constraint("", linear, "=",1));
	
			// d_i = sum all demand served by i / esrcapacity factor
			linear = new Linear();
			linear.add(node.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+i);
			for(Node downstreamNode : node.getDownstreamNodes()){
				int j = downstreamNode.getNodeID();
				linear.add(downstreamNode.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+j);
			}
			linear.add(-1,"d_"+i);
			problem.add(new Constraint("", linear, "=",0));
			
			int j=0;
			linear2 = new Linear();
			for(EsrCost esr : esrCost){
				
				//to determine e_i_j
				linear = new Linear();
				linear.add(esr.getMinCapacity(),"e_"+i+"_"+j);
				linear.add(-1,"d_"+i);
				problem.add(new Constraint("", linear, "<=",0));
				
				linear = new Linear();
				linear.add(totalDemand,"e_"+i+"_"+j); //totaldemand just stands for a large amount of demand M
				linear.add(1,"d_"+i);
				problem.add(new Constraint("", linear, "<=",esr.getMaxCapacity()+totalDemand));
				
				linear2.add(1,"e_"+i+"_"+j);
				
				//to capture z_i_j = d_i * e_i_j
				
				linear = new Linear();
				linear.add(totalDemand,"e_"+i+"_"+j);
				linear.add(-1,"z_"+i+"_"+j);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(1,"d_"+i);
				linear.add(-1,"z_"+i+"_"+j);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(totalDemand,"e_"+i+"_"+j);
				linear.add(-1,"z_"+i+"_"+j);
				linear.add(1,"d_"+i);
				problem.add(new Constraint("", linear, "<=",totalDemand));
				
				j++;
			}
			
			problem.add(new Constraint("", linear2, "=",1));
		}
		
		for(Pipe pipe : pipes.values()){
			
			Node parent = pipe.getStartNode();
			Node node = pipe.getEndNode();
			
			int i = node.getNodeID();
			int p = parent.getNodeID();
			
			// represents s_i_i = 0 => (s_k_p = s_k_i) where p is the parent of i
			linear = new Linear();
			linear2 = new Linear();
			int M = 0;
			int counter = 1;
			
			Set<Node> candidates = new HashSet<Node>();
			candidates.addAll(parent.getUpstreamNodes());
			candidates.add(parent);
			
			for(Node n : candidates){
				int k = n.getNodeID();
									
				linear.add(counter, "s_"+k+"_"+i);
				linear.add(-1 * counter, "s_"+k+"_"+p);
				
				linear2.add(counter, "s_"+k+"_"+i);
				linear2.add(-1 * counter, "s_"+k+"_"+p);
				
				M = M + counter;
				counter = counter * 2;
			}
			
			linear.add(-1*M,"s_"+i+"_"+i);
			linear2.add(M,"s_"+i+"_"+i);
			
			problem.add(new Constraint("", linear, "<=",0));			
			problem.add(new Constraint("", linear2, ">=",0));
		}
		
	}
	
	//add constraints related to the ESR structure in the network
	//ensures ESR configuration is valid
	//includes constraints related to selection of cost row table for each ESR
	//allows removal of certain nodes from ESR consideration
	private void setEsrConstraints_gen2() throws Exception{		
		Linear linear,linear2;
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			
			for(Pipe pipe : node.getOutgoingPipes()){
				
				int j = pipe.getEndNode().getNodeID();
				
				//following constraint represents s_i_i = 0 => s_j_j = 0 where j are children of i 
				linear = new Linear();
				linear.add(1, "s_"+i+"_"+i); 
				linear.add(-1, "s_"+j+"_"+j);
				problem.add(new Constraint("", linear, ">=",0));
			}
			
			// following constraint represents sum over parent i's s_j_i = 1 
			// i.e. exactly one esr serves location i 
			linear = new Linear();
			linear.add(1, "s_"+i+"_"+i);
			for(Node upstreamNode : node.getUpstreamNodes()){
				int j = upstreamNode.getNodeID();
				linear.add(1, "s_"+j+"_"+i);
			}
			problem.add(new Constraint("", linear, "=",1));
	
			// d_i = sum all demand served by i / esrcapacity factor
			linear = new Linear();
			linear.add(node.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+i);
			for(Node downstreamNode : node.getDownstreamNodes()){
				int j = downstreamNode.getNodeID();
				linear.add(downstreamNode.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+j);
			}
			linear.add(-1,"d_"+i);
			problem.add(new Constraint("", linear, "=",0));
			
			
			if(node.getAllowESR()){
				int j=0;
				linear2 = new Linear();
				for(EsrCost esr : esrCost){
					
					//to determine e_i_j
					linear = new Linear();
					linear.add(esr.getMinCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"d_"+i);
					problem.add(new Constraint("", linear, "<=",0));
					
					linear = new Linear();
					linear.add(totalDemand,"e_"+i+"_"+j); //totaldemand just stands for a large amount of demand M
					linear.add(1,"d_"+i);
					problem.add(new Constraint("", linear, "<=",esr.getMaxCapacity()+totalDemand));
					
					linear2.add(1,"e_"+i+"_"+j);
					
					//to capture z_i_j = d_i * e_i_j
					
					linear = new Linear();
					linear.add(totalDemand,"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
					
					linear = new Linear();
					linear.add(1,"d_"+i);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
					
					linear = new Linear();
					linear.add(totalDemand,"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					linear.add(1,"d_"+i);
					problem.add(new Constraint("", linear, "<=",totalDemand));
					
					j++;
				}
				
				problem.add(new Constraint("", linear2, "=",1));
			}
		}
		
		for(Pipe pipe : pipes.values()){
			
			Node parent = pipe.getStartNode();
			Node node = pipe.getEndNode();
			
			int i = node.getNodeID();
			int p = parent.getNodeID();
			
			// represents s_i_i = 0 => (s_k_p = s_k_i) where p is the parent of i
			linear = new Linear();
			linear2 = new Linear();
			int M = 0;
			int counter = 1;
			
			Set<Node> candidates = new HashSet<Node>();
			
			for(Node n : node.getUpstreamNodes()){
				if(n.getAllowESR())
					candidates.add(n);
			}
			
			for(Node n : candidates){
				int k = n.getNodeID();
									
				linear.add(counter, "s_"+k+"_"+i);
				linear.add(-1 * counter, "s_"+k+"_"+p);
				
				linear2.add(counter, "s_"+k+"_"+i);
				linear2.add(-1 * counter, "s_"+k+"_"+p);
				
				M = M + counter;
				counter = counter * 2;
			}
			
			linear.add(-1*M,"s_"+i+"_"+i);
			linear2.add(M,"s_"+i+"_"+i);
			
			problem.add(new Constraint("", linear, "<=",0));			
			problem.add(new Constraint("", linear2, ">=",0));
		}
		
	}
		
	//add constraints related to the ESR structure in the network
	//ensures ESR configuration is valid
	//includes constraints related to selection of cost row table for each ESR
	//alternative z_i_j constraints
	private void setEsrConstraints_gen4() throws Exception{		
		Linear linear,linear2;
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			
			for(Pipe pipe : node.getOutgoingPipes()){
				
				int j = pipe.getEndNode().getNodeID();
				
				//following constraint represents s_i_i = 0 => s_j_j = 0 where j are children of i 
				linear = new Linear();
				linear.add(1, "s_"+i+"_"+i); 
				linear.add(-1, "s_"+j+"_"+j);
				problem.add(new Constraint("", linear, ">=",0));
			}
			
			// following constraint represents sum over parent i's s_j_i = 1 
			// i.e. exactly one esr serves location i 
			linear = new Linear();
			linear.add(1, "s_"+i+"_"+i);
			for(Node upstreamNode : node.getUpstreamNodes()){
				int j = upstreamNode.getNodeID();
				linear.add(1, "s_"+j+"_"+i);
			}
			problem.add(new Constraint("", linear, "=",1));
	
			// d_i = sum all demand served by i / esrcapacity factor
			linear = new Linear();
			linear.add(node.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+i);
			for(Node downstreamNode : node.getDownstreamNodes()){
				int j = downstreamNode.getNodeID();
				linear.add(downstreamNode.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+j);
			}
			linear.add(-1,"d_"+i);
			problem.add(new Constraint("", linear, "=",0));
			
			
			if(node.getAllowESR()){
				int j=0;
				linear2 = new Linear();
				Linear linear3 = new Linear();
				for(EsrCost esr : esrCost){
					
					//to determine e_i_j and z_i_j
					// e_i_j*min_j <= z_i_j <= e_i_j*max_j
					// sum e_i_j = 1
					// sum z_i_j = d_i
					
					linear = new Linear();
					linear.add(esr.getMinCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, "<=",0));
					
					linear = new Linear();
					linear.add(esr.getMaxCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
										
					linear2.add(1,"e_"+i+"_"+j);
					linear3.add(1,"z_"+i+"_"+j);
					
					j++;
				}
				
				problem.add(new Constraint("", linear2, "=",1));
				
				linear3.add(-1,"d_"+i);
				problem.add(new Constraint("", linear3, "=",0));
			}
		}
		
		for(Pipe pipe : pipes.values()){
			
			Node parent = pipe.getStartNode();
			Node node = pipe.getEndNode();
			
			int i = node.getNodeID();
			int p = parent.getNodeID();
			
			// represents s_i_i = 0 => (s_k_p = s_k_i) where p is the parent of i
			linear = new Linear();
			linear2 = new Linear();
			int M = 0;
			int counter = 1;
			
			Set<Node> candidates = new HashSet<Node>();
			
			for(Node n : node.getUpstreamNodes()){
				if(n.getAllowESR())
					candidates.add(n);
			}
			
			for(Node n : candidates){
				int k = n.getNodeID();
									
				linear.add(counter, "s_"+k+"_"+i);
				linear.add(-1 * counter, "s_"+k+"_"+p);
				
				linear2.add(counter, "s_"+k+"_"+i);
				linear2.add(-1 * counter, "s_"+k+"_"+p);
				
				M = M + counter;
				counter = counter * 2;
			}
			
			linear.add(-1*M,"s_"+i+"_"+i);
			linear2.add(M,"s_"+i+"_"+i);
			
			problem.add(new Constraint("", linear, "<=",0));			
			problem.add(new Constraint("", linear2, ">=",0));
		}
		
	}
	
	//add constraints related to the ESR structure in the network
	//ensures ESR configuration is valid
	//includes constraints related to selection of cost row table for each ESR
	//replace how to implement constraint s_i_i = 0 => s_k_i = s_k_p
	private void setEsrConstraints_gen5() throws Exception{		
		Linear linear,linear2;
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			
			for(Pipe pipe : node.getOutgoingPipes()){
				
				int j = pipe.getEndNode().getNodeID();
				
				//following constraint represents s_i_i = 0 => s_j_j = 0 where j are children of i 
				linear = new Linear();
				linear.add(1, "s_"+i+"_"+i); 
				linear.add(-1, "s_"+j+"_"+j);
				problem.add(new Constraint("", linear, ">=",0));
			}
			
			// following constraint represents sum over parent i's s_j_i = 1 
			// i.e. exactly one esr serves location i 
			linear = new Linear();
			linear.add(1, "s_"+i+"_"+i);
			for(Node upstreamNode : node.getUpstreamNodes()){
				int j = upstreamNode.getNodeID();
				linear.add(1, "s_"+j+"_"+i);
			}
			problem.add(new Constraint("", linear, "=",1));
	
			// d_i = sum all demand served by i / esrcapacity factor
			linear = new Linear();
			linear.add(node.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+i);
			for(Node downstreamNode : node.getDownstreamNodes()){
				int j = downstreamNode.getNodeID();
				linear.add(downstreamNode.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+j);
			}
			linear.add(-1,"d_"+i);
			problem.add(new Constraint("", linear, "=",0));
			
			
			if(node.getAllowESR()){
				int j=0;
				linear2 = new Linear();
				Linear linear3 = new Linear();
				for(EsrCost esr : esrCost){
					
					//to determine e_i_j and z_i_j
					// e_i_j*min_j <= z_i_j <= e_i_j*max_j
					// sum e_i_j = 1
					// sum z_i_j = d_i
					
					linear = new Linear();
					linear.add(esr.getMinCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, "<=",0));
					
					linear = new Linear();
					linear.add(esr.getMaxCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
										
					linear2.add(1,"e_"+i+"_"+j);
					linear3.add(1,"z_"+i+"_"+j);
					
					j++;
				}
				
				problem.add(new Constraint("", linear2, "=",1));
				
				linear3.add(-1,"d_"+i);
				problem.add(new Constraint("", linear3, "=",0));
			}
		}
		
		for(Pipe pipe : pipes.values()){
			
			Node parent = pipe.getStartNode();
			Node node = pipe.getEndNode();
			
			int i = node.getNodeID();
			int p = parent.getNodeID();
			
			// represents s_i_i = 0 => (s_k_p = s_k_i) where p is the parent of i			
			Set<Node> candidates = new HashSet<Node>();
			
			for(Node n : node.getUpstreamNodes()){
				if(n.getAllowESR())
					candidates.add(n);
			}
			
			for(Node n : candidates){
				int k = n.getNodeID();
				
				linear = new Linear();
				linear.add(1, "s_"+k+"_"+i);
				linear.add(-1, "s_"+k+"_"+p);
				linear.add(-1, "s_"+i+"_"+i);
				problem.add(new Constraint("", linear, "<=",0));
				
				linear = new Linear();
				linear.add(-1, "s_"+k+"_"+i);
				linear.add(1, "s_"+k+"_"+p);
				linear.add(-1, "s_"+i+"_"+i);
				problem.add(new Constraint("", linear, "<=",0));
			}
		}
	}
	
	//add constraints related to user defined list of nodes that must have ESR / cannot have ESR
	private void setEsrOptionConstraints() throws Exception{
		if(!esrGeneralProperties.allow_dummy){
			for(Node n : nodes.values()){
				if(n.getDemand()==0)
					n.setAllowESR(false);
			}
		}
		
		if(esrGeneralProperties.must_esr!=null){
			for(int nodeid : esrGeneralProperties.must_esr){
				Node node = nodes.get(nodeid);
				if(node==null){
	            	throw new Exception("Invalid node:" + nodeid + " provided for must have ESR list in ESR options");
	            }
				Linear linear = new Linear();
				linear.add(1, "s_"+nodeid+"_"+nodeid); 
				problem.add(new Constraint("", linear, "=",1));
				
				linear = new Linear();
				linear.add(1, "d_"+nodeid); 
				problem.add(new Constraint("", linear, ">=",0.00001));
			}
		}
		
		if(esrGeneralProperties.must_not_esr!=null){
			for(int nodeid : esrGeneralProperties.must_not_esr){
				Node node = nodes.get(nodeid);
				if(node==null){
	            	throw new Exception("Invalid node:" + nodeid + " provided for must not have ESR list in ESR options");
	            }
				node.setAllowESR(false);
			}
		}
	}

	//add constraints related to user defined list of nodes that must have ESR / cannot have ESR
	private void setEsrOptionConstraints_gen2() throws Exception{
		if(!esrGeneralProperties.allow_dummy){
			for(Node n : nodes.values()){
				if(n.getDemand()==0)
					n.setAllowESR(false);
			}
		}
		
		if(esrGeneralProperties.must_esr!=null){
			for(int nodeid : esrGeneralProperties.must_esr){
				Node node = nodes.get(nodeid);
				if(node==null){
	            	throw new Exception("Invalid node:" + nodeid + " provided for must have ESR list in ESR options");
	            }
				Linear linear = new Linear();
				
				linear = new Linear();
				linear.add(1, "d_"+nodeid); 
				problem.add(new Constraint("", linear, ">=",0.00001));
			}
		}
		
		if(esrGeneralProperties.must_not_esr!=null){
			for(int nodeid : esrGeneralProperties.must_not_esr){
				Node node = nodes.get(nodeid);
				if(node==null){
	            	throw new Exception("Invalid node:" + nodeid + " provided for must not have ESR list in ESR options");
	            }
				node.setAllowESR(false);
			}
		}
	}
	
	//set valve settings in pipes
	private void setValveOptionConstraints() throws Exception{
		if(valves!=null){
			for(ValveStruct valve:valves){
				Pipe pipe = pipes.get(valve.pipeid);
				if(pipe==null){
	            	throw new Exception("Invalid pipe:" + valve.pipeid + " provided in pressure reducing valve list");
	            }
				pipe.setValveSetting(valve.valvesetting);
			}
		}
	}
	
	//add pump manual option related constraints
	private void setPumpOptionConstraints() throws Exception{
		if(pumpGeneralProperties.pump_enabled){
			if(pumpGeneralProperties.must_not_pump!=null){
				for(int pipeid : pumpGeneralProperties.must_not_pump){
					Pipe pipe = pipes.get(pipeid);
					if(pipe==null){
		            	throw new Exception("Invalid pipe:" + pipeid + " provided for must not have pump list in pump options");
		            }
					pipe.setAllowPump(false);
				}
			}
			
			for(PumpManualStruct s: pumpManualArray){
				Pipe pipe = pipes.get(s.pipeid);
				if(pipe==null){
	            	throw new Exception("Invalid pipe:" + s.pipeid + " provided for manual pump list in pump options");
	            }
				Linear linear = new Linear();
				linear.add(1, "pumppower_"+s.pipeid);
				problem.add(new Constraint("", linear, "=", s.pumppower));
			}
		}
	}
	
	
	@SuppressWarnings("unused")
	private void check() throws Exception{
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			
			for(Pipe pipe : node.getOutgoingPipes()){
				int j = pipe.getEndNode().getNodeID();		
				// along a certain pipe "pipeid", sum(s_i_j) >=1 => f_pipeid = 0
				int n = 1;
				double sum = 0;
				sum += problem.getPrimalValue("s_"+i+"_"+j);
				for(Node downNode : pipe.getEndNode().getDownstreamNodes()){
					int k = downNode.getNodeID();
					sum += problem.getPrimalValue("s_"+i+"_"+k);
					n++;
				}
				sum += n*problem.getPrimalValue("f_"+pipe.getPipeID());
				if(sum > n)
					System.out.println("error 1:"+i);
			}
			
			// d_i = sum all demand served by i / esrcapacity factor
			double totaldemand = 0;
			String errstring = "node i data:";
			totaldemand = totaldemand + node.getRequiredCapacity(esrCapacityFactor);
			errstring += "\nnode "+i+" demand:"+totaldemand;
			
			double sum = 0;
			sum += node.getRequiredCapacity(esrCapacityFactor)*problem.getPrimalValue("s_"+i+"_"+i);
			errstring += "\ns_"+i+"_"+i+" "+problem.getPrimalValue("s_"+i+"_"+i);
			
			for(Node downstreamNode : node.getDownstreamNodes()){
				int j = downstreamNode.getNodeID();
				totaldemand = totaldemand + downstreamNode.getRequiredCapacity(esrCapacityFactor);
				sum += downstreamNode.getRequiredCapacity(esrCapacityFactor)*problem.getPrimalValue("s_"+i+"_"+j);
				errstring += "\nnode "+j+" demand:"+downstreamNode.getRequiredCapacity(esrCapacityFactor);
				errstring += "\ns_"+i+"_"+j+" "+problem.getPrimalValue("s_"+i+"_"+j);
			}
			sum += -1*problem.getPrimalValue("d_"+i);
			errstring += "\nd_"+i+" "+problem.getPrimalValue("d_"+i);
			if(Math.abs(sum) > 0.001){
				System.out.println("error 2:"+i);
				System.out.println(errstring);
			}
			
			// s_i_i = 0 => d_i=0 i.e. all s_i_j=0
			sum = 0;
			sum += -1*problem.getPrimalValue("d_"+i);
			sum += totaldemand*problem.getPrimalValue("s_"+i+"_"+i);
			
			errstring = "\nd_"+i+" "+problem.getPrimalValue("d_"+i);
			errstring += "\ntotdemand*s_i_i: "+totaldemand*problem.getPrimalValue("s_"+i+"_"+i);
			if(sum < -0.001){
				System.out.println("error 3:"+i);
				System.out.println(errstring);
			}
		}
	}
	
	//add constraints related to the ESR structure in the network
	//ensures ESR configuration is valid
	//includes constraints related to selection of cost row table for each ESR
	//replace how to implement constraint s_i_i = 0 => s_k_i = s_k_p
	private void setEsrConstraints_gen6() throws Exception{		
		Linear linear,linear2;
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			
			for(Pipe pipe : node.getOutgoingPipes()){
				
				int j = pipe.getEndNode().getNodeID();
				
				//following constraint represents s_i_i = 0 => s_j_j = 0 where j are children of i 
				linear = new Linear();
				linear.add(1, "s_"+i+"_"+i); 
				linear.add(-1, "s_"+j+"_"+j);
				problem.add(new Constraint("", linear, ">=",0));
				
				// along a certain pipe "pipeid", sum(s_i_j) >=1 => f_pipeid = 0
				int n = 1;
				linear = new Linear();
				linear.add(1,"s_"+i+"_"+j);
				for(Node downNode : pipe.getEndNode().getDownstreamNodes()){
					int k = downNode.getNodeID();
					linear.add(1,"s_"+i+"_"+k);
					n++;
				}
				linear.add(n,"f_"+pipe.getPipeID());
				problem.add(new Constraint("", linear, "<=",n));
				
			}
			
			// following constraint represents sum over parent i's s_j_i = 1 
			// i.e. exactly one esr serves location i 
			linear = new Linear();
			linear.add(1, "s_"+i+"_"+i);
			for(Node upstreamNode : node.getUpstreamNodes()){
				int j = upstreamNode.getNodeID();
				linear.add(1, "s_"+j+"_"+i);
			}
			problem.add(new Constraint("", linear, "=",1));
	
			// d_i = sum all demand served by i / esrcapacity factor
			double totaldemand = 0;
			totaldemand = totaldemand + node.getRequiredCapacity(esrCapacityFactor);
			linear = new Linear();
			linear.add(node.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+i);
			for(Node downstreamNode : node.getDownstreamNodes()){
				int j = downstreamNode.getNodeID();
				totaldemand = totaldemand + downstreamNode.getRequiredCapacity(esrCapacityFactor);
				linear.add(downstreamNode.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+j);
			}
			linear.add(-1,"d_"+i);
			//problem.add(new Constraint("", linear, "=",0));
			problem.add(new Constraint("", linear, "<=",0.0001));
			problem.add(new Constraint("", linear, ">=",-0.0001));
			
			// s_i_i = 0 => d_i=0 i.e. all s_i_j=0
			linear = new Linear();
			linear.add(-1,"d_"+i);
			linear.add(totaldemand,"s_"+i+"_"+i);
			//problem.add(new Constraint("", linear, ">=",0));
			problem.add(new Constraint("", linear, ">=",-0.0001));
			
			if(node.getAllowESR()){
				int j=0;
				linear2 = new Linear();
				Linear linear3 = new Linear();
				for(EsrCost esr : esrCost){
					
					//to determine e_i_j and z_i_j
					// e_i_j*min_j <= z_i_j <= e_i_j*max_j
					// sum e_i_j = 1
					// sum z_i_j = d_i
					
					linear = new Linear();
					linear.add(esr.getMinCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, "<=",0));
					
					linear = new Linear();
					linear.add(esr.getMaxCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
										
					linear2.add(1,"e_"+i+"_"+j);
					linear3.add(1,"z_"+i+"_"+j);
					
					j++;
				}
				
				problem.add(new Constraint("", linear2, "=",1));
				
				linear3.add(-1,"d_"+i);
				problem.add(new Constraint("", linear3, "=",0));
			}
		}		
	}

	//add constraints related to the ESR structure in the network
	//ensures ESR configuration is valid
	//includes constraints related to selection of cost row table for each ESR
	//replace how to implement constraint s_i_i = 0 => s_k_i = s_k_p
	private void setEsrConstraints_gen7() throws Exception{		
		Linear linear,linear2;
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			
			for(Pipe pipe : node.getOutgoingPipes()){
				
				int j = pipe.getEndNode().getNodeID();
				
				//following constraint represents s_i_i = 0 => s_j_j = 0 where j are children of i 
				linear = new Linear();
				linear.add(1, "s_"+i+"_"+i); 
				linear.add(-1, "s_"+j+"_"+j);
				problem.add(new Constraint("", linear, ">=",0));
				
				
				//following constraint represents s_i_i = 0 => s_i_j = 0 where j are children of i 
				//linear = new Linear();
				//linear.add(1, "s_"+i+"_"+i); 
				//linear.add(-1, "s_"+i+"_"+j);
				//problem.add(new Constraint("", linear, ">=",0));
				
				
				// following constraint represents s_i_j = s_i_k where j is child of i and k are downstream of j
				for(Node downNode : pipe.getEndNode().getDownstreamNodes()){
					linear = new Linear();
					linear.add(1,"s_"+i+"_"+j);
					
					int k = downNode.getNodeID();
					linear.add(-1,"s_"+i+"_"+k);
					problem.add(new Constraint("", linear, "=",0));
				}
				
				
			}
			
			// following constraint represents sum over parent i's s_j_i = 1 
			// i.e. exactly one esr serves location i 
			linear = new Linear();
			linear.add(1, "s_"+i+"_"+i);
			for(Node upstreamNode : node.getUpstreamNodes()){
				int j = upstreamNode.getNodeID();
				linear.add(1, "s_"+j+"_"+i);
			}
			problem.add(new Constraint("", linear, "=",1));
	
			// d_i = sum all demand served by i / esrcapacity factor
			double totaldemand = 0;
			totaldemand = totaldemand + node.getRequiredCapacity(esrCapacityFactor);
			linear = new Linear();
			linear.add(node.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+i);
			for(Node downstreamNode : node.getDownstreamNodes()){
				int j = downstreamNode.getNodeID();
				totaldemand = totaldemand + downstreamNode.getRequiredCapacity(esrCapacityFactor);
				linear.add(downstreamNode.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+j);
			}
			linear.add(-1,"d_"+i);
			problem.add(new Constraint("", linear, "=",0));
			//problem.add(new Constraint("", linear, "<=",0.0001));
			//problem.add(new Constraint("", linear, ">=",-0.0001));
			
			
			if(node.getAllowESR()){
				int j=0;
				linear2 = new Linear();
				Linear linear3 = new Linear();
				for(EsrCost esr : esrCost){
					
					//to determine e_i_j and z_i_j
					// e_i_j*min_j <= z_i_j <= e_i_j*max_j
					// sum e_i_j = 1
					// sum z_i_j = d_i
					
					linear = new Linear();
					linear.add(esr.getMinCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, "<=",0));
					
					linear = new Linear();
					linear.add(esr.getMaxCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
										
					linear2.add(1,"e_"+i+"_"+j);
					linear3.add(1,"z_"+i+"_"+j);
					
					j++;
				}
				
				problem.add(new Constraint("", linear2, "=",1));
				
				linear3.add(-1,"d_"+i);
				problem.add(new Constraint("", linear3, "=",0));
			}
		}		
	}
	
	//add constraints related to the ESR structure in the network
	//ensures ESR configuration is valid
	//includes constraints related to selection of cost row table for each ESR
	//replace how to implement constraint s_i_i = 0 => s_k_i = s_k_p
	private void setEsrConstraints_gen8() throws Exception{		
		Linear linear,linear2;
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			
			linear2 = new Linear();
			linear2.add(node.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+i);
			
			for(Pipe pipe : node.getOutgoingPipes()){
				
				int j = pipe.getEndNode().getNodeID();
				
				//d_i sum
				double D_j = 0;
				D_j = pipe.getEndNode().getRequiredCapacity(esrCapacityFactor);
				for(Node n: pipe.getEndNode().getDownstreamNodes()){
					D_j += n.getRequiredCapacity(esrCapacityFactor);
				}
				linear2.add(D_j, "s_"+i+"_"+j);
				
				
				//following constraint represents s_i_i = 0 => s_j_j = 0 where j are children of i 
				//linear = new Linear();
				//linear.add(1, "s_"+i+"_"+i); 
				//linear.add(-1, "s_"+j+"_"+j);
				//problem.add(new Constraint("", linear, ">=",0));
				
				
				//following constraint represents s_i_i = 0 => s_i_j = 0 where j are children of i 
				//linear = new Linear();
				//linear.add(1, "s_"+i+"_"+i); 
				//linear.add(-1, "s_"+i+"_"+j);
				//problem.add(new Constraint("", linear, ">=",0));
				
				
				// following constraint represents s_i_j = s_i_k where j is child of i and k are downstream of j
				for(Node downNode : pipe.getEndNode().getDownstreamNodes()){
					linear = new Linear();
					linear.add(1,"s_"+i+"_"+j);
					
					int k = downNode.getNodeID();
					linear.add(-1,"s_"+i+"_"+k);
					problem.add(new Constraint("", linear, "=",0));
				}
			}
			linear2.add(-1,"d_"+i);
			problem.add(new Constraint("", linear2, "=",0));
			//problem.add(new Constraint("", linear2, "<=",0.0001));
			//problem.add(new Constraint("", linear2, ">=",-0.0001));
			
			
			// following constraint represents sum over parent i's s_j_i = 1 
			// i.e. exactly one esr serves location i 
			linear = new Linear();
			linear.add(1, "s_"+i+"_"+i);
			for(Node upstreamNode : node.getUpstreamNodes()){
				int j = upstreamNode.getNodeID();
				linear.add(1, "s_"+j+"_"+i);
			}
			problem.add(new Constraint("", linear, "=",1));
			
			if(node.getAllowESR()){
				int j=0;
				linear2 = new Linear();
				Linear linear3 = new Linear();
				for(EsrCost esr : esrCost){
					
					//to determine e_i_j and z_i_j
					// e_i_j*min_j <= z_i_j <= e_i_j*max_j
					// sum e_i_j = 1
					// sum z_i_j = d_i
					
					linear = new Linear();
					linear.add(esr.getMinCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, "<=",0));
					
					linear = new Linear();
					linear.add(esr.getMaxCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
										
					linear2.add(1,"e_"+i+"_"+j);
					linear3.add(1,"z_"+i+"_"+j);
					
					j++;
				}
				
				problem.add(new Constraint("", linear2, "=",1));
				
				linear3.add(-1,"d_"+i);
				problem.add(new Constraint("", linear3, "=",0));
			}
		}	
	}
	
	//add constraints related to the ESR structure in the network
	//ensures ESR configuration is valid
	//includes constraints related to selection of cost row table for each ESR
	//add constraints to prune ESR cost table rows
	private void setEsrConstraints_gen9() throws Exception{		
		Linear linear,linear2;
		
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			int npipes = node.getOutgoingPipes().size();
			
			
			if(node.getAllowESR()){
				double demand = node.getRequiredCapacity(esrCapacityFactor);
				for(Node n : node.getDownstreamNodes()){
					demand += n.getRequiredCapacity(esrCapacityFactor); 
				}
				int l = 0;
				for(EsrCost e: esrCost){
					if(e.getMinCapacity() > demand){
						linear = new Linear();
						linear.add(1, "e_"+i+"_"+l);
						problem.add(new Constraint("", linear, "=",0));
					}
					l++;
				}
			}
			
			if(node.getAllowESR() && npipes <=0){
				
				for(int k=0;k<Math.pow(2, npipes);k++){
					int mask = 1;
					linear = new Linear();
					linear.add(1, "s_"+i+"_"+i);
					double demand = node.getRequiredCapacity(esrCapacityFactor);
					int rhs = npipes;
					for(Pipe pipe : node.getOutgoingPipes()){
						int j = pipe.getEndNode().getNodeID();
						
						if((mask & k) != 0){	// is current pipe 'chosen'
							linear.add(1, "s_"+i+"_"+j);
							demand += pipe.getEndNode().getRequiredCapacity(esrCapacityFactor);
							for(Node n: pipe.getEndNode().getDownstreamNodes()){
								demand += n.getRequiredCapacity(esrCapacityFactor);
							}
						}
						else{
							linear.add(-1, "s_"+i+"_"+j);
							rhs--;
						}
						mask = mask << 1;
					}
					int l = 0;
					for(EsrCost e: esrCost){
						if(e.getMinCapacity() <= demand && e.getMaxCapacity() > demand){
							linear.add(-1, "e_"+i+"_"+l);
							problem.add(new Constraint("", linear, "<=",rhs));
							//System.out.println(new Constraint("", linear, "<=",rhs));
						}
						l++;
					}
				}
			}
			
			
			if(node.getAllowESR() && npipes <=0){
				
				for(int k=0;k<Math.pow(2, npipes);k++){
					int mask = 1;
					linear = new Linear();
					double demand = node.getRequiredCapacity(esrCapacityFactor);
					int rhs = npipes;
					for(Pipe pipe : node.getOutgoingPipes()){
						if((mask & k) != 0){	// is current pipe 'chosen'
							demand += pipe.getEndNode().getRequiredCapacity(esrCapacityFactor);
							for(Node n: pipe.getEndNode().getDownstreamNodes()){
								demand += n.getRequiredCapacity(esrCapacityFactor);
							}
						}
						mask = mask << 1;
					}
					
					mask = 1;
					linear.add(demand, "s_"+i+"_"+i);
					for(Pipe pipe : node.getOutgoingPipes()){
						int j = pipe.getEndNode().getNodeID();	
						if((mask & k) != 0){	// is current pipe 'chosen'
							linear.add(demand, "s_"+i+"_"+j);
						}
						else{
							linear.add(-1 * demand, "s_"+i+"_"+j);
							rhs--;
						}
						mask = mask << 1;
					}
										
					int l = 0;
					for(EsrCost e: esrCost){
						if(e.getMinCapacity() <= demand && e.getMaxCapacity() > demand){
							linear.add(-1, "z_"+i+"_"+l);
							problem.add(new Constraint("", linear, "<=",rhs*demand));
							//System.out.println(new Constraint("", linear, "<=",rhs));
							break;
						}
						l++;
					}
				}
			}
			
						
			linear2 = new Linear();
			linear2.add(node.getRequiredCapacity(esrCapacityFactor), "s_"+i+"_"+i);
						
			for(Pipe pipe : node.getOutgoingPipes()){
				
				int j = pipe.getEndNode().getNodeID();
								
				//d_i sum
				double D_j = 0;
				D_j = pipe.getEndNode().getRequiredCapacity(esrCapacityFactor);
				for(Node n: pipe.getEndNode().getDownstreamNodes()){
					D_j += n.getRequiredCapacity(esrCapacityFactor);
				}
				linear2.add(D_j, "s_"+i+"_"+j);
				
				//following constraint represents s_i_i = 0 => s_j_j = 0 where j are children of i 
				linear = new Linear();
				linear.add(1, "s_"+i+"_"+i); 
				linear.add(-1, "s_"+j+"_"+j);
				problem.add(new Constraint("", linear, ">=",0));
				
				
				//following constraint represents s_i_i = 0 => s_i_j = 0 where j are children of i 
				linear = new Linear();
				linear.add(1, "s_"+i+"_"+i); 
				linear.add(-1, "s_"+i+"_"+j);
				problem.add(new Constraint("", linear, ">=",0));
				
				
				
				// following constraint represents s_i_j = s_i_k where j is child of i and k are downstream of j
				for(Node downNode : pipe.getEndNode().getDownstreamNodes()){
					linear = new Linear();
					linear.add(1,"s_"+i+"_"+j);
					
					int k = downNode.getNodeID();
					linear.add(-1,"s_"+i+"_"+k);
					problem.add(new Constraint("", linear, "=",0));
				}
			}
			linear2.add(-1,"d_"+i);
			problem.add(new Constraint("", linear2, "=",0));
						
			// following constraint represents sum over parent i's s_j_i = 1 
			// i.e. exactly one esr serves location i 
			linear = new Linear();
			linear.add(1, "s_"+i+"_"+i);
			for(Node upstreamNode : node.getUpstreamNodes()){
				int j = upstreamNode.getNodeID();
				linear.add(1, "s_"+j+"_"+i);
			}
			problem.add(new Constraint("", linear, "=",1));
			
			if(node.getAllowESR()){
				int j=0;
				linear2 = new Linear();
				Linear linear3 = new Linear();
				for(EsrCost esr : esrCost){
					
					//to determine e_i_j and z_i_j
					// e_i_j*min_j <= z_i_j <= e_i_j*max_j
					// sum e_i_j = 1
					// sum z_i_j = d_i
					
					linear = new Linear();
					linear.add(esr.getMinCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, "<=",0));
					
					linear = new Linear();
					linear.add(esr.getMaxCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
										
					linear2.add(1,"e_"+i+"_"+j);
					linear3.add(1,"z_"+i+"_"+j);
					
					j++;
				}
				
				problem.add(new Constraint("", linear2, "=",1));
				
				linear3.add(-1,"d_"+i);
				problem.add(new Constraint("", linear3, "=",0));
			}
		}	
	}
	
	//add constraints related to the ESR structure in the network
	//ensures ESR configuration is valid
	//includes constraints related to selection of cost row table for each ESR
	//remove s_i_j variables
	private void setEsrConstraints_gen10() throws Exception{		
		Linear linear,linear2;
						
		for(Node node : nodes.values()){
			int i = node.getNodeID();
			
			if(node.getAllowESR()){
				double demand = node.getRequiredCapacity(esrCapacityFactor);
				for(Node n : node.getDownstreamNodes()){
					demand += n.getRequiredCapacity(esrCapacityFactor); 
				}
				int l = 0;
				for(EsrCost e: esrCost){
					if(e.getMinCapacity() > demand){
						linear = new Linear();
						linear.add(1, "e_"+i+"_"+l);
						problem.add(new Constraint("", linear, "=",0));
					}
					l++;
				}
			}
									
			linear2 = new Linear();
			linear2.add(node.getRequiredCapacity(esrCapacityFactor), "besr_"+i);
						
			for(Pipe pipe : node.getOutgoingPipes()){
				
				int j = pipe.getPipeID();
								
				//d_i sum
				double D_j = 0;
				D_j = pipe.getEndNode().getRequiredCapacity(esrCapacityFactor);
				for(Node n: pipe.getEndNode().getDownstreamNodes()){
					D_j += n.getRequiredCapacity(esrCapacityFactor);
				}
				linear2.add(D_j, "k_"+i+"_"+j);
				
				//following constraint represents besr_i = 0 => f_j = 0 where j is outgoing pipe of i 
				linear = new Linear();
				linear.add(1, "besr_"+i); 
				linear.add(-1, "f_"+j);
				problem.add(new Constraint("", linear, ">=",0));
			}
			linear2.add(-1,"d_"+i);
			problem.add(new Constraint("", linear2, "=",0));
						
			if(node.getAllowESR()){
				int j=0;
				linear2 = new Linear();
				Linear linear3 = new Linear();
				for(EsrCost esr : esrCost){
					
					//to determine e_i_j and z_i_j
					// e_i_j*min_j <= z_i_j <= e_i_j*max_j
					// sum e_i_j = 1
					// sum z_i_j = d_i
					
					linear = new Linear();
					linear.add(esr.getMinCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, "<=",0));
					
					linear = new Linear();
					linear.add(esr.getMaxCapacity(),"e_"+i+"_"+j);
					linear.add(-1,"z_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
										
					linear2.add(1,"e_"+i+"_"+j);
					linear3.add(1,"z_"+i+"_"+j);
					
					j++;
				}
				
				problem.add(new Constraint("", linear2, "=",1));
				
				linear3.add(-1,"d_"+i);
				problem.add(new Constraint("", linear3, "=",0));
			}
		}	
	}
	
	//add constraints related to pump
	private void setPumpConstraints() throws Exception{
		if(pumpGeneralProperties.pump_enabled){
			Linear linear;
			for(Pipe pipe : pipes.values()){
				int i = pipe.getPipeID();
				//f_pumphead_i = f_i * pumphead_i
				linear = new Linear();
				linear.add(1, "f_pumphead_"+i);
				linear.add(-1*maxPumpHead, "f_"+i);
				problem.add(new Constraint("", linear, "<=", 0));
				
				linear = new Linear();
				linear.add(1, "f_pumphead_"+i);
				linear.add(-1, "pumphead_"+i);
				problem.add(new Constraint("", linear, "<=", 0));
				
				linear = new Linear();
				linear.add(1, "f_pumphead_"+i);
				linear.add(-1, "pumphead_"+i);
				linear.add(-1*maxPumpHead, "f_"+i);
				problem.add(new Constraint("", linear, ">=", -1*maxPumpHead));
				
				// flow_m3h = flow*3.6;
				//power = density of water (1000) * g (9.80665) * flow (in m3h = flow*3.6) / (3.6*10^6) * efficiency
				// power = 9.81 * flow (in lps) / (1000 * efficiency)
				
				double primaryPowerCoefficient = 9.80665*pipe.getFlow()/(10*pumpGeneralProperties.efficiency);
				double secondaryPowerCoefficient = primaryPowerCoefficient*secondaryFlowFactor;
				
				// power = primarypower + secondarypower
				// power = ppc*f_i*pumphead_i + spc*(1-f_i)*pumphead_i
				// primarypower = ppc*f_pumphead_i
				// secondarypower = spc*pumphead_i - spc*f_pumphead_i
				
				linear = new Linear();
				linear.add(primaryPowerCoefficient,"f_pumphead_"+i);
				linear.add(-1,"ppumppower_"+i);
				problem.add(new Constraint("", linear, "=", 0));
				
				linear = new Linear();
				linear.add(secondaryPowerCoefficient,"pumphead_"+i);
				linear.add(-1*secondaryPowerCoefficient,"f_pumphead_"+i);
				linear.add(-1,"spumppower_"+i);
				problem.add(new Constraint("", linear, "=", 0));
				
				linear = new Linear();
				linear.add(1,"ppumppower_"+i);
				linear.add(1,"spumppower_"+i);
				linear.add(-1,"pumppower_"+i);
				problem.add(new Constraint("", linear, "=", 0));
				
				linear = new Linear();
				linear.add(1,"pumppower_"+i);
				linear.add(-1*minPumpPower,"pumphelper_"+i);
				problem.add(new Constraint("", linear, ">=", 0));
				
				linear = new Linear();
				linear.add(1,"pumppower_"+i);
				linear.add(-1*maxPumpPower,"pumphelper_"+i);
				problem.add(new Constraint("", linear, "<=", 0));
			}
		}
	}
	
	//add constraints related to pump for pipe only optimization
	private void setPumpConstraints_gen0() throws Exception{
		if(pumpGeneralProperties.pump_enabled){
			Linear linear;
			for(Pipe pipe : pipes.values()){
				int i = pipe.getPipeID();
				
				double primaryPowerCoefficient = 9.80665*pipe.getFlow()/(10*pumpGeneralProperties.efficiency);
								
				linear = new Linear();
				linear.add(primaryPowerCoefficient,"pumphead_"+i);
				linear.add(-1,"pumppower_"+i);
				problem.add(new Constraint("", linear, "=", 0));
								
				linear = new Linear();
				linear.add(1,"pumppower_"+i);
				linear.add(-1*minPumpPower,"pumphelper_"+i);
				problem.add(new Constraint("", linear, ">=", 0));
				
				linear = new Linear();
				linear.add(1,"pumppower_"+i);
				linear.add(-1*maxPumpPower,"pumphelper_"+i);
				problem.add(new Constraint("", linear, "<=", 0));
			}
		}
	}
	
	//set constraints relating ESR to pipe network
	private void setEsrPipeConstraints() throws Exception{
				
		Linear linear;
		
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();
			Node end = pipe.getEndNode();
			
			//following constraint captures :
			// if node j has demand f_i = s_j_j 
			// else f_i = f_k where k is downstreampipe 
			linear = new Linear();
			linear.add(1,"f_"+i);
			
			if(end.getDemand()>0){
				int j = end.getNodeID();
				linear.add(-1,"s_"+j+"_"+j);
			}
			else{
				List<Pipe> childPipes = end.getOutgoingPipes();
				if(childPipes.size()>0){
					int k = childPipes.get(0).getPipeID();
					linear.add(-1,"f_"+k);
				}
				else
					throw new Exception("ERROR: Terminal node has no demand.");
			}
			problem.add(new Constraint("", linear, "=",0));			
		}
		
		
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();
			
			for(int j=0; j<pipeCost.size();j++){
				
				//to capture y_i_j = f_i * l_i_j
				linear = new Linear();
				linear.add(pipe.getLength(),"f_"+i);
				linear.add(-1,"y_"+i+"_"+j);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(1,"l_"+i+"_"+j);
				linear.add(-1,"y_"+i+"_"+j);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(pipe.getLength(),"f_"+i);
				linear.add(-1,"y_"+i+"_"+j);
				linear.add(1,"l_"+i+"_"+j);
				problem.add(new Constraint("", linear, "<=",pipe.getLength()));
				
				if(pipe.isAllowParallel()){
					//to capture yp_i_j = f_i * p_i_j ; all boolean
					linear = new Linear();
					linear.add(1,"f_"+i);
					linear.add(-1,"yp_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
					
					linear = new Linear();
					linear.add(1,"p_"+i+"_"+j);
					linear.add(-1,"yp_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
					
					linear = new Linear();
					linear.add(1,"p_"+i+"_"+j);
					linear.add(1,"f_"+i);
					linear.add(-1,"yp_"+i+"_"+j);
					problem.add(new Constraint("", linear, "<=",1));
				}
			}
			
			if(pipe.isAllowParallel()){
				//to capture yp_i = f_i * p_i ; all boolean
				linear = new Linear();
				linear.add(1,"f_"+i);
				linear.add(-1,"yp_"+i);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(1,"p_"+i);
				linear.add(-1,"yp_"+i);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(1,"p_"+i);
				linear.add(1,"f_"+i);
				linear.add(-1,"yp_"+i);
				problem.add(new Constraint("", linear, "<=",1));
			}
		}
		
		//for all 0 demand nodes all child pipes should have same flow type f_i
		for(Node node : nodes.values()){
			if(node.getDemand()==0){
				List<Pipe> childPipes = node.getOutgoingPipes();
				if(childPipes.size()>0){
					int i = childPipes.get(0).getPipeID();
					for(Pipe pipe : childPipes){
						int j = pipe.getPipeID();
						if(i!=j){
							linear = new Linear();
							linear.add(1,"f_"+i);
							linear.add(-1,"f_"+j);
							problem.add(new Constraint("", linear, "=",0));
							i=j;
						}
					}
				}
				else
					throw new Exception("ERROR: Terminal node has no demand");
			}
		}
		
		//if s_i_j=1 then all pipes in path from i to j should all be 0
		for(Node node : nodes.values()){
			if(node.getDemand()>0){
				List<Pipe> sourceToNodePipes = node.getSourceToNodePipes();
				int j = node.getNodeID();
				for(int i=0;i<sourceToNodePipes.size();i++){
					Node startNode = sourceToNodePipes.get(i).getStartNode();
					if(startNode.getDemand()>0){
						int sid = startNode.getNodeID();
						for(int k=i;k<sourceToNodePipes.size();k++){
							int id = sourceToNodePipes.get(k).getPipeID();
							linear = new Linear();
							linear.add(1,"s_"+sid+"_"+j);
							linear.add(1,"f_"+id);
							problem.add(new Constraint("", linear, "<=",1));
						}
					}
				}
			}
		}
		
//		//if s_i_j=1 then first pipe in path from i to j should be 0
//		for(Node node : nodes.values()){
//			if(node.getDemand()>0){
//				List<Pipe> sourceToNodePipes = node.getSourceToNodePipes();
//				int j = node.getNodeID();
//				for(int i=0;i<sourceToNodePipes.size();i++){
//					Pipe pipe = sourceToNodePipes.get(i);
//					Node startNode = pipe.getStartNode();
//					if(startNode.getDemand()>0){
//						int sid = startNode.getNodeID();
//						
//						int id = pipe.getPipeID();
//						linear = new Linear();
//						linear.add(1,"s_"+sid+"_"+j);
//						linear.add(1,"f_"+id);
//						problem.add(new Constraint("", linear, "<=",1));
//
//					}
//				}
//			}
//		}
		
	}
	
	//set constraints relating ESR to pipe network
	//allow zero demand nodes to have ESRs
	private void setEsrPipeConstraints_gen() throws Exception{
		
		Linear linear;
		
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();
			Node end = pipe.getEndNode();
			
			//following constraint captures :
			// f_i = s_j_j 
			linear = new Linear();
			linear.add(1,"f_"+i);
			
			int j = end.getNodeID();
			linear.add(-1,"s_"+j+"_"+j);
			
			problem.add(new Constraint("", linear, "=",0));			
		}
		
		
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();
			
			for(int j=0; j<pipeCost.size();j++){
				
				//to capture y_i_j = f_i * l_i_j
				linear = new Linear();
				linear.add(pipe.getLength(),"f_"+i);
				linear.add(-1,"y_"+i+"_"+j);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(1,"l_"+i+"_"+j);
				linear.add(-1,"y_"+i+"_"+j);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(pipe.getLength(),"f_"+i);
				linear.add(-1,"y_"+i+"_"+j);
				linear.add(1,"l_"+i+"_"+j);
				problem.add(new Constraint("", linear, "<=",pipe.getLength()));
				
				if(pipe.isAllowParallel()){
					//to capture yp_i_j = f_i * p_i_j ; all boolean
					linear = new Linear();
					linear.add(1,"f_"+i);
					linear.add(-1,"yp_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
					
					linear = new Linear();
					linear.add(1,"p_"+i+"_"+j);
					linear.add(-1,"yp_"+i+"_"+j);
					problem.add(new Constraint("", linear, ">=",0));
					
					linear = new Linear();
					linear.add(1,"p_"+i+"_"+j);
					linear.add(1,"f_"+i);
					linear.add(-1,"yp_"+i+"_"+j);
					problem.add(new Constraint("", linear, "<=",1));
				}
			}
			
			if(pipe.isAllowParallel()){
				//to capture yp_i = f_i * p_i ; all boolean
				linear = new Linear();
				linear.add(1,"f_"+i);
				linear.add(-1,"yp_"+i);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(1,"p_"+i);
				linear.add(-1,"yp_"+i);
				problem.add(new Constraint("", linear, ">=",0));
				
				linear = new Linear();
				linear.add(1,"p_"+i);
				linear.add(1,"f_"+i);
				linear.add(-1,"yp_"+i);
				problem.add(new Constraint("", linear, "<=",1));
			}
		}
	}
	
	//set constraints relating ESR to pipe network
	//allow zero demand nodes to have ESRs
	//some constraints removed due to introduction of l_i_j_k
	private void setEsrPipeConstraints_gen3() throws Exception{
		
		Linear linear;
		
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();
			Node end = pipe.getEndNode();
			
			//following constraint captures :
			// f_i = s_j_j 
			linear = new Linear();
			linear.add(1,"f_"+i);
			
			int j = end.getNodeID();
			linear.add(-1,"s_"+j+"_"+j);
			
			problem.add(new Constraint("", linear, "=",0));			
		}		
	}
	
	//set constraints relating ESR to pipe network
	//allow zero demand nodes to have ESRs
	//some constraints removed due to introduction of l_i_j_k
	//remove s_i_j variables
	private void setEsrPipeConstraints_gen4() throws Exception{
		
		Linear linear;
		
		for(Pipe pipe : pipes.values()){
			int i = pipe.getPipeID();
			Node end = pipe.getEndNode();
			
			//following constraint captures :
			// f_i = s_j_j 
			linear = new Linear();
			linear.add(1,"f_"+i);
			
			int j = end.getNodeID();
			linear.add(-1,"besr_"+j);
			
			problem.add(new Constraint("", linear, "=",0));			
		}		
		
		for(Pipe pipe: source.getOutgoingPipes()){
			int i = pipe.getPipeID();
			linear = new Linear();
			linear.add(1,"f_"+i);
			
			problem.add(new Constraint("", linear, "=",1));
		}
	}
	
	@SuppressWarnings("unused")
	private void setHeadLossConstraints_new() throws Exception{
		int j=0;
		for(Node node : nodes.values())
		{
			if(source!=node)
			{
				Linear linear = new Linear();
				lpmodelstring.append("H1C"+node.getNodeID()+": ");
				for(Pipe pipe : node.getSourceToNodePipes())
				{
					int i = pipe.getPipeID();
					for(PipeCost pc : pipeCost)
					{
						linear.add(Util.HWheadLoss(pipe.getFlow(), pipe.getRoughness(), pc.getDiameter()), "l_"+i+"_"+j);
						lpmodelstring.append(Util.HWheadLoss(pipe.getFlow(), pipe.getRoughness(), pc.getDiameter())+ " vl_"+i+"_"+j+" ");
						j++;					
					}
					linear.add(1,"v_"+i);
					lpmodelstring.append("vv_"+i+" ");
					j=0;
				}
				//System.out.println(linear + " " + (Node.getSourceHGL() - node.getElevation() - node.getResidualPressure()));
				//problem.add(linear, "<=", Node.getSourceHGL() - node.getElevation() - node.getResidualPressure());
				
				problem.setVarType("h_"+node.getNodeID(), Double.class);
				linear.add(1,"h_"+node.getNodeID()); // h_i represents the head at node i
				lpmodelstring.append("vh_"+node.getNodeID());
				problem.add(new Constraint("", linear, "=", source.getHead() - node.getElevation()));
				lpmodelstring.append(" = "+(source.getHead() - node.getElevation())+";\n");
				
				linear = new Linear();
				linear.add(1,"h_"+node.getNodeID());
				problem.add(new Constraint("", linear, ">=", node.getResidualPressure()));
				lpmodelstring.append("H2C"+node.getNodeID()+": vh_"+node.getNodeID()+" >= "+node.getResidualPressure()+";\n");				
			}
		}
	}
	
	//post optimization, set the water head at different nodes
	private void setHeadsFromResult(Result result) throws Exception{
		int j=0;
		for(Node node : nodes.values()){
			double headloss = 0;
			for(Pipe pipe : node.getSourceToNodePipes()){
				int i = pipe.getPipeID();
				if(pipe.isAllowParallel()){
					j=0;
					//double temp = 0;
					for(PipeCost entry : pipeCost){				
						// primary flow in case of parallel pipe
						double flow = pipe.getFlow() / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852));
						double loss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
						//double temp2 = result.getPrimalValue(("p_"+i+"_"+j)).doubleValue();
						//temp += temp2;
						headloss += loss * result.getPrimalValue(("p_"+i+"_"+j)).intValue();
						j++;
					}	
					//double temp2 = result.getPrimalValue(("p_"+i)).doubleValue();
					//temp += temp2;
					//System.out.println(pipe.getPipeID() + " " + temp);
					double flow = pipe.getFlow();
					double loss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
					headloss += loss * result.getPrimalValue(("p_"+i)).intValue();
				}
				else{
					if(pipe.getDiameter()!=0){
						j=0;
						for(PipeCost entry : pipeCost){
							double loss = Util.HWheadLoss(pipe.getFlow(), pipe.getRoughness(), entry.getDiameter()); 
							double length = result.getPrimalValue(("l_"+i+"_"+j)).doubleValue();
							length = Util.round(length, 5);
							headloss += loss * length;
							j++;
						}
					}
					else{
						j=0;
						for(PipeCost entry : pipeCost){
							double loss = Util.HWheadLoss(pipe.getFlow(), entry.getRoughness(), entry.getDiameter()); 
							double length = result.getPrimalValue(("l_"+i+"_"+j)).doubleValue();
							length = Util.round(length, 5);
							headloss += loss * length;
							j++;
						}	
					}
				}
				
				if(pumpGeneralProperties.pump_enabled){
					double pumphead = result.getPrimalValue("pumphead_"+i).doubleValue();
					headloss = headloss - pumphead;
				}
				
				headloss = headloss + pipe.getValveSetting();
			}
			//ESR height
//			if(node.getDemand()>0){
//				double esr_height = result.getPrimalValue(("h_"+node.getNodeID())).doubleValue();
//				System.out.println(("h_"+node.getNodeID())+" "+esr_height);
//			}
			node.setHead(source.getHead() - headloss);
		}
	}
			
	@SuppressWarnings("unused")
	private void setHeadsFromResult_esr2(Result result) throws Exception{
		int j=0;
		for(Node node : nodes.values()){
			double headloss = 0;
			for(Pipe pipe : node.getSourceToNodePipes()){
				int i = pipe.getPipeID();
				boolean primaryNetwork = result.getPrimalValue(("f_"+i)).intValue()==1;
				
				if(pipe.isAllowParallel()){
					j=0;

					for(PipeCost entry : pipeCost){				
						// primary flow in case of parallel pipe
						double flow = pipe.getFlow() / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852));
						if(!primaryNetwork)
							flow = flow * secondaryFlowFactor;
						
						double loss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());

						headloss += loss * result.getPrimalValue(("p_"+i+"_"+j)).intValue();
						j++;
					}	
					
					double flow = pipe.getFlow();
					if(!primaryNetwork)
						flow = flow * secondaryFlowFactor;
					double loss = Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter());
					headloss += loss * result.getPrimalValue(("p_"+i)).intValue();
				}
				else{
					if(pipe.getDiameter()!=0){
						j=0;
						for(PipeCost entry : pipeCost){
							double flow = pipe.getFlow();
							if(!primaryNetwork)
								flow = flow * secondaryFlowFactor;
							double loss = Util.HWheadLoss(flow, pipe.getRoughness(), entry.getDiameter()); 
							double length = result.getPrimalValue(("l_"+i+"_"+j)).doubleValue();
							length = Util.round(length, 5);
							headloss += loss * length;
							j++;
						}
					}
					else{
						j=0;
						for(PipeCost entry : pipeCost){
							double flow = pipe.getFlow();
							if(!primaryNetwork)
								flow = flow * secondaryFlowFactor;
							double loss = Util.HWheadLoss(flow, entry.getRoughness(), entry.getDiameter()); 
							double length = result.getPrimalValue(("l_"+i+"_"+j)).doubleValue();
							length = Util.round(length, 5);
							headloss += loss * length;
							j++;
						}	
					}
				}
			}
			//ESR height
//			if(node.getDemand()>0){
//				double esr_height = result.getPrimalValue(("h_"+node.getNodeID())).doubleValue();
//				System.out.println(("h_"+node.getNodeID())+" "+esr_height);
//			}
			node.setHead(source.getHead() - headloss);
		}
	}
	
	//get the ESR cost, given the capacity in litres
	public double getCost(double capacity, List<EsrCost> esrCosts){
		for(EsrCost e: esrCosts){
			if(e.getMinCapacity() <= capacity && e.getMaxCapacity() > capacity){
				return e.getBaseCost() + (capacity-e.getMinCapacity())*e.getUnitCost();
			}
		}
		return Integer.MAX_VALUE;
	}
	
	//after optimization set water head for nodes 
	private void setHeadsFromResult_esr(Result result) throws Exception{

		for(Node node : nodes.values()){
			double head = result.getPrimalValue("head_"+node.getNodeID()).doubleValue();
			node.setHead(head);
			int i = node.getNodeID();
			int esrChoice = result.getPrimalValue("s_"+i+"_"+i).intValue();
			double demandServed = result.getPrimalValue("d_"+i).doubleValue();
			demandServed = Util.round(demandServed, 5);
			if(esrChoice==1 && demandServed > 0){
				node.setESR(i);
				double esrHeight = result.getPrimalValue("esr_"+i).doubleValue();
				node.setEsrHeight(esrHeight);
				node.setEsrTotalDemand(demandServed);
				node.setEsrCost(getCost(demandServed, esrCost));
				node.addToServedNodes(node);
				
				for(Node downstreamNode : node.getDownstreamNodes()){
					int j = downstreamNode.getNodeID();
					esrChoice = result.getPrimalValue("s_"+i+"_"+j).intValue();
					if(esrChoice==1){
						downstreamNode.setESR(i);
						node.addToServedNodes(downstreamNode);
					}
				}
			}
		}
	}
	
	//after optimization set water head for nodes
	//allow zero demand nodes to have ESRs 
	private void setHeadsFromResult_esr_gen2(Result result) throws Exception{

		for(Node node : nodes.values()){
			double head = result.getPrimalValue("head_"+node.getNodeID()).doubleValue();
			node.setHead(head);
			int i = node.getNodeID();
			int esrChoice = result.getPrimalValue("besr_"+i).intValue();
			double demandServed = result.getPrimalValue("d_"+i).doubleValue();
			demandServed = Util.round(demandServed, 5);
			if(esrChoice==1 && demandServed > 0){
				node.setESR(i);
				double esrHeight = result.getPrimalValue("esr_"+i).doubleValue();
				node.setEsrHeight(esrHeight);
				node.setEsrTotalDemand(demandServed);
				node.setEsrCost(getCost(demandServed, esrCost));
				node.addToServedNodes(node);
				
				for(Pipe pipe : node.getOutgoingPipes()){	
					int j = pipe.getPipeID();
					esrChoice = 1 - result.getPrimalValue("f_"+j).intValue();
					if(esrChoice==1){
						Node endnode = pipe.getEndNode();
						endnode.setESR(i);
						node.addToServedNodes(endnode);
						
						for(Node n : endnode.getDownstreamNodes()){
							n.setESR(i);
							node.addToServedNodes(n);
						}
					}
				}
			}
		}
	}
	
	@SuppressWarnings("unused")
	private void setHeadsFromResult_new(Result result) throws Exception
	{
		int j=0;
		for(Node node : nodes.values())
		{
			double headloss = 0;
			for(Pipe pipe : node.getSourceToNodePipes())
			{
				int i = pipe.getPipeID();
				for(PipeCost pc : pipeCost)
				{
					double loss = Util.HWheadLoss(pipe.getFlow(), pipe.getRoughness(), pc.getDiameter()); 
					double length = result.getPrimalValue(("l_"+i+"_"+j)).doubleValue();
					length = Util.round(length, 5);
					headloss += loss * length;
					j++;					
				}
				double valveSetting = result.getPrimalValue("v_"+i).doubleValue();
				valveSetting = Util.round(valveSetting, 5);
				headloss += valveSetting;
				j=0;
			}
			node.setHead(source.getHead() - headloss);
		}
	}
		
	//after optimization set diameters for pipes
	private void setDiametersFromResult(Result result) throws Exception {
		int j;
		for(Pipe pipe : pipes.values()){
			j=0;
			int i = pipe.getPipeID();
			int noOfSubPipes = 0;
			
			if(pumpGeneralProperties.pump_enabled){
				double pumphead = result.getPrimalValue("pumphead_"+i).doubleValue();
				double power = result.getPrimalValue("pumppower_"+i).doubleValue();
				
				pumphead = Util.round(pumphead, 5);
				power = Util.round(power, 5);
				
				pipe.setPumpHead(pumphead);
				pipe.setPumpPower(power);
				
				double presentvaluefactor = Util.presentValueFactor(pumpGeneralProperties.discount_rate, pumpGeneralProperties.inflation_rate, pumpGeneralProperties.design_lifetime);		
				double primarycoeffecient = 365*presentvaluefactor*generalProperties.supply_hours*pumpGeneralProperties.energycost_per_kwh*pumpGeneralProperties.energycost_factor;
				double energycost = power*primarycoeffecient;
				double capitalcost = power*pumpGeneralProperties.capitalcost_per_kw;
				
				System.out.println("pipe:"+i+" pumphead:"+pumphead);
				System.out.println("Power: "+power+" Energy Cost: "+energycost+" Capital Cost:" + capitalcost);				
			}
			
			
			if(pipe.existingPipe()){
				for(PipeCost entry : pipeCost){
					if(pipe.isAllowParallel()){
						double parallelChoice = result.getPrimalValue(("p_"+i+"_"+j)).intValue();
						if(parallelChoice==1){
							pipe.setDiameter2(entry.getDiameter());
							pipe.setRoughness2(entry.getRoughness());
							pipe.setChosenPipeCost2(entry);
						}
					}		
					double length = result.getPrimalValue(("l_"+i+"_"+j)).doubleValue();
					length = Util.round(length, 5);
					if(entry.getDiameter() == pipe.getDiameter()){	
						if(length != pipe.getLength())
							throw new Exception("Something wrong in parallel link "+i);
					}
					j++;
				}
			}
			else{
				for(PipeCost entry : pipeCost){
					double length = result.getPrimalValue(("l_"+i+"_"+j)).doubleValue();
					length = Util.round(length, 5);
					if(length>0){
						noOfSubPipes++;
						if(noOfSubPipes==1){
							pipe.setDiameter(entry.getDiameter());
							pipe.setRoughness(entry.getRoughness());
							pipe.setChosenPipeCost(entry);
						}
						else if(noOfSubPipes==2){
							pipe.setDiameter2(entry.getDiameter());
							pipe.setLength2(length);
							pipe.setRoughness2(entry.getRoughness());
							pipe.setChosenPipeCost2(entry);
						}
						else
							throw new Exception("more than 2 pipes for link "+i);
					}
					j++;
				}
			}
		}
	}
	
	//after optimization set diameters for pipes
	//updated with change of l_i_j to l_i_j_k
	private void setDiametersFromResult_gen3(Result result) throws Exception{
		int j;
		for(Pipe pipe : pipes.values()){
			j=0;
			int i = pipe.getPipeID();
			int noOfSubPipes = 0;
			
			int flowChoice = result.getPrimalValue("f_"+i).intValue();
			pipe.setFlowchoice(flowChoice==1?FlowType.PRIMARY:FlowType.SECONDARY);			
			
			if(pipe.existingPipe()){
				for(PipeCost entry : pipeCost){
					if(pipe.isAllowParallel()){
						double parallelChoice1 = result.getPrimalValue(("p_"+i+"_"+j+"_0")).intValue();
						double parallelChoice2 = result.getPrimalValue(("p_"+i+"_"+j+"_1")).intValue();
						
						if(parallelChoice1==1 || parallelChoice2==1){
							pipe.setDiameter2(entry.getDiameter());
							pipe.setRoughness2(entry.getRoughness());
							pipe.setChosenPipeCost2(entry);
						}
					}		
					double length1 = result.getPrimalValue(("l_"+i+"_"+j+"_0")).doubleValue();
					double length2 = result.getPrimalValue(("l_"+i+"_"+j+"_1")).doubleValue();
					
					length1 = Util.round(length1, 5);
					length2 = Util.round(length2, 5);
					double length = Math.max(length1, length2);
					if(entry.getDiameter() == pipe.getDiameter()){	
						if(length != pipe.getLength())
							throw new Exception("Something wrong in parallel link "+i);
					}
					j++;
				}
			}
			else{
				for(PipeCost entry : pipeCost){
					double length1 = result.getPrimalValue(("l_"+i+"_"+j+"_0")).doubleValue();
					double length2 = result.getPrimalValue(("l_"+i+"_"+j+"_1")).doubleValue();
					
					length1 = Util.round(length1, 5);
					length2 = Util.round(length2, 5);
					double length = Math.max(length1, length2);
					if(length>0){
						noOfSubPipes++;
						if(noOfSubPipes==1){
							pipe.setDiameter(entry.getDiameter());
							pipe.setRoughness(entry.getRoughness());
							pipe.setChosenPipeCost(entry);
						}
						else if(noOfSubPipes==2){
							pipe.setDiameter2(entry.getDiameter());
							pipe.setLength2(length);
							pipe.setRoughness2(entry.getRoughness());
							pipe.setChosenPipeCost2(entry);
						}
						else
							throw new Exception("more than 2 pipes for link "+i);
					}
					j++;
				}
			}
		}
	}
	
	//after optimization set diameters for pipes
	//included pumps
	private void setDiametersFromResult_gen4(Result result) throws Exception{
		int j;
		for(Pipe pipe : pipes.values()){
			j=0;
			int i = pipe.getPipeID();
			int noOfSubPipes = 0;
			
			int flowChoice = result.getPrimalValue("f_"+i).intValue();
			pipe.setFlowchoice(flowChoice==1?FlowType.PRIMARY:FlowType.SECONDARY);			
			
			if(pumpGeneralProperties.pump_enabled){
				double pumphead = result.getPrimalValue("pumphead_"+i).doubleValue();
				double primarypower = result.getPrimalValue("ppumppower_"+i).doubleValue();
				double secondarypower = result.getPrimalValue("spumppower_"+i).doubleValue();
				double power = result.getPrimalValue("pumppower_"+i).doubleValue();
				
				pumphead = Util.round(pumphead, 5);
				primarypower = Util.round(primarypower, 5);
				secondarypower = Util.round(secondarypower, 5);
				power = Util.round(power, 5);
				
				pipe.setPumpHead(pumphead);
				pipe.setPumpPower(power);
				
				double presentvaluefactor = Util.presentValueFactor(pumpGeneralProperties.discount_rate, pumpGeneralProperties.inflation_rate, pumpGeneralProperties.design_lifetime);		
				double primarycoeffecient = 365*presentvaluefactor*generalProperties.supply_hours*pumpGeneralProperties.energycost_per_kwh*pumpGeneralProperties.energycost_factor;
				double secondarycoeffecient = 365*presentvaluefactor*esrGeneralProperties.secondary_supply_hours*pumpGeneralProperties.energycost_per_kwh*pumpGeneralProperties.energycost_factor;
				double energycost = primarypower*primarycoeffecient + secondarypower*secondarycoeffecient;
				double capitalcost = power*pumpGeneralProperties.capitalcost_per_kw;
				
				System.out.println("pipe:"+i+" pumphead:"+pumphead);
				System.out.println("Power: "+power+" Energy Cost: "+energycost+" Capital Cost:" + capitalcost);				
			}
			
			if(pipe.existingPipe()){
				for(PipeCost entry : pipeCost){
					if(pipe.isAllowParallel()){
						double parallelChoice1 = result.getPrimalValue(("p_"+i+"_"+j+"_0")).intValue();
						double parallelChoice2 = result.getPrimalValue(("p_"+i+"_"+j+"_1")).intValue();
						
						if(parallelChoice1==1 || parallelChoice2==1){
							pipe.setDiameter2(entry.getDiameter());
							pipe.setRoughness2(entry.getRoughness());
							pipe.setChosenPipeCost2(entry);
						}
					}		
					double length1 = result.getPrimalValue(("l_"+i+"_"+j+"_0")).doubleValue();
					double length2 = result.getPrimalValue(("l_"+i+"_"+j+"_1")).doubleValue();
					
					length1 = Util.round(length1, 5);
					length2 = Util.round(length2, 5);
					double length = Math.max(length1, length2);
					if(entry.getDiameter() == pipe.getDiameter()){	
						if(length != pipe.getLength())
							throw new Exception("Something wrong in parallel link "+i);
					}
					j++;
				}
			}
			else{
				for(PipeCost entry : pipeCost){
					double length1 = result.getPrimalValue(("l_"+i+"_"+j+"_0")).doubleValue();
					double length2 = result.getPrimalValue(("l_"+i+"_"+j+"_1")).doubleValue();
					
					length1 = Util.round(length1, 5);
					length2 = Util.round(length2, 5);
					double length = Math.max(length1, length2);
					if(length>0){
						noOfSubPipes++;
						if(noOfSubPipes==1){
							pipe.setDiameter(entry.getDiameter());
							pipe.setRoughness(entry.getRoughness());
							pipe.setChosenPipeCost(entry);
						}
						else if(noOfSubPipes==2){
							pipe.setDiameter2(entry.getDiameter());
							pipe.setLength2(length);
							pipe.setRoughness2(entry.getRoughness());
							pipe.setChosenPipeCost2(entry);
						}
						else
							throw new Exception("more than 2 pipes for link "+i);
					}
					j++;
				}
			}
		}
	}
	
	@SuppressWarnings("unused")
	private void setDiametersFromResult_new(Result result) throws Exception
	{
		int j;
		for(Pipe pipe : pipes.values())
		{
			j=0;
			int i = pipe.getPipeID();
			int noOfSubPipes = 0;
			for(PipeCost pc : pipeCost)
			{
				double length = result.getPrimalValue(("l_"+i+"_"+j)).doubleValue();
				length = Util.round(length, 5);
				if(length>0)
				{
					noOfSubPipes++;
					if(noOfSubPipes==1)
						pipe.setDiameter(pc.getDiameter());
					else if(noOfSubPipes==2)
					{
						pipe.setDiameter2(pc.getDiameter());
						pipe.setLength2(length);
					}
					else
						throw new Exception("more than 2 pipes for link "+i);
				}
				j++;
			}
		}
	}
	
	@SuppressWarnings("unused")
	private void printResult(Result result) throws Exception{
		int j=0;
		for(Node node : nodes.values()){
			if(source!=node){
				System.out.println(node.getNodeID()+" "+node.getElevation()+" "+node.getHead());
			}
			for(int k : nodes.keySet()){
				Number esrChoice = result.getPrimalValue(("s_"+node.getNodeID()+"_"+k));
				if(esrChoice!=null && esrChoice.intValue()==1){
					System.out.println(node.getNodeID()+" "+k);
				}
			}
			Number esrDemand = result.getPrimalValue(("d_"+node.getNodeID()));
			if(esrDemand!=null){
				System.out.println(node.getNodeID()+" d:"+esrDemand);
			}
			
			Number head = result.getPrimalValue(("head_"+node.getNodeID()));
			if(head!=null){
				System.out.println(node.getNodeID()+" head:"+head);
			}
			
			Number esrheight = result.getPrimalValue(("esr_"+node.getNodeID()));
			if(esrheight!=null){
				System.out.println(node.getNodeID()+" esrheight:"+esrheight);
			}
			
			Number besr = result.getPrimalValue(("besr_"+node.getNodeID()));
			if(besr!=null){
				System.out.println(node.getNodeID()+" besr:"+besr);
			}
			
			for(int k=0; k<esrCost.size();k++){
				Number esrChoice = result.getPrimalValue(("e_"+node.getNodeID()+"_"+k));
				if(esrChoice!=null && esrChoice.intValue()==1){
					System.out.println(node.getNodeID()+" e:"+k);
				}
			}
		}
		
		for(Pipe pipe : pipes.values()){
			j=0;
			int i = pipe.getPipeID();
			
			Number flowChoice = result.getPrimalValue("f_"+i);
			if(flowChoice!=null)
				System.out.println("f_"+i+" :"+flowChoice);
			
			if(pipe.isAllowParallel()){
				double flow = pipe.getFlow();
				for(PipeCost entry : pipeCost){
					double parallelChoice = result.getPrimalValue(("p_"+i+"_"+j)).intValue();
					if(parallelChoice == 1){
						flow = flow - (pipe.getFlow() / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852))); 
						System.out.println(pipe.getPipeID()+" "+ entry.getDiameter() + " " + flow +" "+ pipe.getLength()
								+" "+Util.HWheadLoss(pipe.getLength(), flow, entry.getRoughness(), entry.getDiameter()));
					}
					j++;
				}
				flow = pipe.getFlow() - flow;
				System.out.println(pipe.getPipeID()+" "+ pipe.getDiameter() + " " + flow +" "+ pipe.getLength()
						+" "+Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter()));
			}
			else{
				for(PipeCost entry : pipeCost){
					double length = result.getPrimalValue("l_"+i+"_"+j).doubleValue();
					length = Util.round(length, 5);
					if(length > 0){
						System.out.println(pipe.getPipeID()+" "+ entry.getDiameter() + " " + pipe.getFlow()+" "+ length
								+" "+Util.HWheadLoss(length, pipe.getFlow(), pipe.getRoughness(), entry.getDiameter()));
					}
					j++;
				}
			}
		}
	}
	
	@SuppressWarnings("unused")
	private void printVariables(Result result) throws Exception{
		for(Node node : nodes.values()){
			System.out.println("s_"+node.getNodeID()+"_"+node.getNodeID()+": "+result.getPrimalValue("s_"+node.getNodeID()+"_"+node.getNodeID()).toString());
			for(Node n: node.getDownstreamNodes()){
				System.out.println("s_"+node.getNodeID()+"_"+n.getNodeID()+": "+result.getPrimalValue("s_"+node.getNodeID()+"_"+n.getNodeID()).toString());
			}
			
			for(int j=0;j<esrCost.size();j++){
				System.out.println("e_"+node.getNodeID()+"_"+j+": "+result.getPrimalValue("e_"+node.getNodeID()+"_"+j).toString());
				System.out.println("z_"+node.getNodeID()+"_"+j+": "+result.getPrimalValue("z_"+node.getNodeID()+"_"+j).toString());
			}
		}
		
		for(Pipe pipe : pipes.values()){
			System.out.println("f_"+pipe.getPipeID()+": "+result.getPrimalValue("f_"+pipe.getPipeID()).toString());
			System.out.println("k_"+pipe.getStartNode().getNodeID()+"_"+pipe.getPipeID()+": "+result.getPrimalValue("k_"+pipe.getStartNode().getNodeID()+"_"+pipe.getPipeID()).toString());
		}
	}
	
	@SuppressWarnings("unused")
	private void printVariables_gen2(Result result) throws Exception{
		for(Node node : nodes.values()){
			System.out.println("besr_"+node.getNodeID()+": "+result.getPrimalValue("besr_"+node.getNodeID()).toString());
			
			for(int j=0;j<esrCost.size();j++){
				System.out.println("e_"+node.getNodeID()+"_"+j+": "+result.getPrimalValue("e_"+node.getNodeID()+"_"+j).toString());
				System.out.println("z_"+node.getNodeID()+"_"+j+": "+result.getPrimalValue("z_"+node.getNodeID()+"_"+j).toString());
			}
		}
		
		for(Pipe pipe : pipes.values()){
			System.out.println("f_"+pipe.getPipeID()+": "+result.getPrimalValue("f_"+pipe.getPipeID()).toString());
			System.out.println("k_"+pipe.getStartNode().getNodeID()+"_"+pipe.getPipeID()+": "+result.getPrimalValue("k_"+pipe.getStartNode().getNodeID()+"_"+pipe.getPipeID()).toString());
		}
	}
	
	@SuppressWarnings("unused")
	private void printResult_gen3(Result result) throws Exception{
		int j=0;
		for(Node node : nodes.values()){
			if(source!=node){
				System.out.println(node.getNodeID()+" "+node.getElevation()+" "+node.getHead());
			}
			for(int k : nodes.keySet()){
				Number esrChoice = result.getPrimalValue(("s_"+node.getNodeID()+"_"+k));
				if(esrChoice!=null && esrChoice.intValue()==1){
					System.out.println(node.getNodeID()+" "+k);
				}
			}
			Number esrDemand = result.getPrimalValue(("d_"+node.getNodeID()));
			if(esrDemand!=null){
				System.out.println(node.getNodeID()+" d:"+esrDemand);
			}
			
			Number head = result.getPrimalValue(("head_"+node.getNodeID()));
			if(head!=null){
				System.out.println(node.getNodeID()+" head:"+head);
			}
			
			Number esrheight = result.getPrimalValue(("esr_"+node.getNodeID()));
			if(esrheight!=null){
				System.out.println(node.getNodeID()+" esrheight:"+esrheight);
			}
			
			Number besr = result.getPrimalValue(("besr_"+node.getNodeID()));
			if(besr!=null){
				System.out.println(node.getNodeID()+" besr:"+besr);
			}
			
			for(int k=0; k<esrCost.size();k++){
				Number esrChoice = result.getPrimalValue(("e_"+node.getNodeID()+"_"+k));
				if(esrChoice!=null && esrChoice.intValue()==1){
					System.out.println(node.getNodeID()+" e:"+k);
				}
			}
		}
		
		for(Pipe pipe : pipes.values()){
			j=0;
			int i = pipe.getPipeID();
			
			Number flowChoice = result.getPrimalValue("f_"+i);
			if(flowChoice!=null)
				System.out.println("f_"+i+" :"+flowChoice);
			
			if(pipe.isAllowParallel()){
				double flow = pipe.getFlow();
				for(PipeCost entry : pipeCost){
					int parallelChoice1 = result.getPrimalValue(("p_"+i+"_"+j+"_0")).intValue();
					int parallelChoice2 = result.getPrimalValue(("p_"+i+"_"+j+"_1")).intValue();
					if(parallelChoice2 == 1){
						flow = flow - (pipe.getFlow() / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852))); 
						System.out.println(pipe.getPipeID()+" "+ entry.getDiameter() + " " + flow +" "+ pipe.getLength()
								+" "+Util.HWheadLoss(pipe.getLength(), flow, entry.getRoughness(), entry.getDiameter()));
					}
					if(parallelChoice1 == 1){
						flow = flow - (pipe.getFlow() / (1 + (entry.getRoughness()/pipe.getRoughness())*Math.pow(entry.getDiameter()/pipe.getDiameter(), 4.87/1.852))); 
						flow = flow * secondaryFlowFactor;
						System.out.println(pipe.getPipeID()+" "+ entry.getDiameter() + " " + flow +" "+ pipe.getLength()
								+" "+Util.HWheadLoss(pipe.getLength(), flow, entry.getRoughness(), entry.getDiameter()));
					}
					j++;
				}
				if(flowChoice.intValue()==1)
					flow = pipe.getFlow() - flow;
				else
					flow = pipe.getFlow() * secondaryFlowFactor - flow;
				System.out.println(pipe.getPipeID()+" "+ pipe.getDiameter() + " " + flow +" "+ pipe.getLength()
						+" "+Util.HWheadLoss(pipe.getLength(), flow, pipe.getRoughness(), pipe.getDiameter()));
			}
			else{
				for(PipeCost entry : pipeCost){
					double length1 = result.getPrimalValue(("l_"+i+"_"+j+"_0")).doubleValue();
					double length2 = result.getPrimalValue(("l_"+i+"_"+j+"_1")).doubleValue();
					
					length1 = Util.round(length1, 5);
					length2 = Util.round(length2, 5);
					double length = Math.max(length1, length2);
					if(length > 0){
						double flow;
						if(flowChoice.intValue()==1)
							flow = pipe.getFlow();
						else
							flow = pipe.getFlow() * secondaryFlowFactor;
						System.out.println(pipe.getPipeID()+" "+ entry.getDiameter() + " " + flow+" "+ length
								+" "+Util.HWheadLoss(length, flow, pipe.getRoughness(), entry.getDiameter()));
					}
					j++;
				}
			}
		}
	}
	
	@SuppressWarnings("unused")
	private void printResult_new(Result result) throws Exception
	{
		int j=0;
		for(Node node : nodes.values())
		{
			if(source!=node)
			{
				double h = result.getPrimalValue("h_"+node.getNodeID()).doubleValue();
				h = Util.round(h, 5);
				System.out.println(node.getNodeID()+" "+node.getElevation()+" "+node.getHead()+" "+(node.getHead()-node.getElevation()) + " " + h);
			}
		}
		
		for(Pipe pipe : pipes.values())
		{
			j=0;
			int i = pipe.getPipeID();
			for(PipeCost pc : pipeCost)
			{
				double length = result.getPrimalValue("l_"+i+"_"+j).doubleValue();
				length = Util.round(length, 5);
				double valve = result.getPrimalValue("v_"+i).doubleValue();
				valve = Util.round(valve, 5);
				if(length > 0)
				{
					System.out.println(pipe.getPipeID()+" "+ pc.getDiameter() + " " + pipe.getFlow()+" "+ length
							+" "+Util.HWheadLoss(length, pipe.getFlow(), pipe.getRoughness(), pc.getDiameter()) + " " + pc.getMaxPressure() + " " + valve);
				}
				j++;
			}
		}
	}
	
	//optimize the network
	public boolean Optimize() throws Exception{		
		
		long startTime = System.currentTimeMillis();
		
		//validate the network layout
		switch (validateNetwork()){
			case 1:
				break;
			case 2:
				throw new Exception("Input is not valid. Cycle in the network");
				//return false;
			case 3:
				throw new Exception("Input is not valid. Nodes unconnected in the network");
				//return false;
		}
		
		//switch between different models and setup downstream/upstream node information
		switch(modelNumber){
			case 0:
			case 1:
				getNodeSupply(source);
				setSourceToNodePipes(source);
				break;
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
				getNodeSupply_gen(source);
				setSourceToNodePipes_gen(source);
				break;
		}
		
		//legacy code, earlier ILP library used was GPLK, can be removed
//		SolverFactory factory = new SolverFactoryGLPK(); // use GPLK
//		
//		factory.setParameter(Solver.VERBOSE, 0); 
//		factory.setParameter(Solver.TIMEOUT, 50); // set timeout in seconds
		
		problem = new Problem();
		//problem.setTimeLimit(50000); // set timeout in milliseconds for web
		problem.setTimeLimit(60000*60*10); // set timeout in milliseconds for local
		
		//problem.setTimeLimit(4*60*60*1000);
		
		//switch between different models and set up the objective cost and constraints of the ILP model
		switch(modelNumber){
			case 0:
				setObjectiveCost();
				System.out.println("After setobjective: "+problem.getConstraintsCount());
				setPumpOptionConstraints();
				System.out.println("After setpumpoption: "+problem.getConstraintsCount());
				setValveOptionConstraints();
				System.out.println("After setvalveoption: "+problem.getConstraintsCount());
				setPipeConstraints();
				System.out.println("After pipeconstraints: "+problem.getConstraintsCount());
				setHeadLossConstraints();
				System.out.println("After headlossconstraints: "+problem.getConstraintsCount());
				setPumpConstraints_gen0();
				System.out.println("After pumpconstraints: "+problem.getConstraintsCount());
				break;
			case 1:
				addVariables();
				System.out.println("After addvariables: "+problem.getConstraintsCount());
				setObjectiveCost_esr();
				System.out.println("After setobjective: "+problem.getConstraintsCount());
				setPipeConstraints();
				System.out.println("After pipeconstraints: "+problem.getConstraintsCount());
				setHeadLossConstraints_esr();
				System.out.println("After headlossconstraints: "+problem.getConstraintsCount());
				setEsrConstraints();
				System.out.println("After esrconstraints: "+problem.getConstraintsCount());
				setEsrPipeConstraints();
				System.out.println("After esrpipeconstraints: "+problem.getConstraintsCount());
				break;
			case 2:
				addVariables_gen();
				System.out.println("After addvariables: "+problem.getConstraintsCount());
				setObjectiveCost_esr_gen();
				System.out.println("After setobjective: "+problem.getConstraintsCount());
				setPipeConstraints();
				System.out.println("After pipeconstraints: "+problem.getConstraintsCount());
				setHeadLossConstraints_esr_gen();
				System.out.println("After headlossconstraints: "+problem.getConstraintsCount());
				setEsrConstraints_gen();
				System.out.println("After esrconstraints: "+problem.getConstraintsCount());
				setEsrPipeConstraints_gen();
				System.out.println("After esrpipeconstraints: "+problem.getConstraintsCount());
				break;
			case 3:
				addVariables_gen();
				System.out.println("After addvariables:aaaa "+problem.getConstraintsCount());
				setObjectiveCost_esr_gen();
				System.out.println("After setobjective: "+problem.getConstraintsCount());
				setPipeConstraints_model3();
				System.out.println("After pipeconstraints: "+problem.getConstraintsCount());
				setHeadLossConstraints_esr_gen2();
				System.out.println("After headlossconstraints: "+problem.getConstraintsCount());
				setEsrConstraints_gen2();
				System.out.println("After esrconstraints: "+problem.getConstraintsCount());
				setEsrPipeConstraints_gen();
				System.out.println("After esrpipeconstraints: "+problem.getConstraintsCount());
				break;
			case 4:
				addVariables_gen3();
				System.out.println("After addvariables: "+problem.getConstraintsCount());
				setObjectiveCost_esr_gen3();
				System.out.println("After setobjective: "+problem.getConstraintsCount());
				setPipeConstraints_gen3();
				System.out.println("After pipeconstraints: "+problem.getConstraintsCount());
				setHeadLossConstraints_esr_gen3();
				System.out.println("After headlossconstraints: "+problem.getConstraintsCount());
				setEsrConstraints_gen2();
				System.out.println("After esrconstraints: "+problem.getConstraintsCount());
				setEsrPipeConstraints_gen3();
				System.out.println("After esrpipeconstraints: "+problem.getConstraintsCount());
				break;
			case 5:
				addVariables_gen3();
				System.out.println("After addvariables: "+problem.getConstraintsCount());
				setObjectiveCost_esr_gen3();
				System.out.println("After setobjective: "+problem.getConstraintsCount());
				setPipeConstraints_gen3();
				System.out.println("After pipeconstraints: "+problem.getConstraintsCount());
				setHeadLossConstraints_esr_gen3();
				System.out.println("After headlossconstraints: "+problem.getConstraintsCount());
				setEsrConstraints_gen4();
				System.out.println("After esrconstraints: "+problem.getConstraintsCount());
				setEsrPipeConstraints_gen3();
				System.out.println("After esrpipeconstraints: "+problem.getConstraintsCount());
				break;
			case 6:
				addVariables_gen3();
				System.out.println("After addvariables: "+problem.getConstraintsCount());
				setObjectiveCost_esr_gen3();
				System.out.println("After setobjective: "+problem.getConstraintsCount());
				setPipeConstraints_gen3();
				System.out.println("After pipeconstraints: "+problem.getConstraintsCount());
				setHeadLossConstraints_esr_gen3();
				System.out.println("After headlossconstraints: "+problem.getConstraintsCount());
				setEsrConstraints_gen5();
				System.out.println("After esrconstraints: "+problem.getConstraintsCount());
				setEsrPipeConstraints_gen3();
				System.out.println("After esrpipeconstraints: "+problem.getConstraintsCount());
				break;
			case 7:
				addVariables_gen3();
				System.out.println("After addvariables: "+problem.getConstraintsCount());
				setObjectiveCost_esr_gen3();
				System.out.println("After setobjective: "+problem.getConstraintsCount());
				setEsrOptionConstraints();
				System.out.println("After setesroption: "+problem.getConstraintsCount());
				setPipeConstraints_gen3();
				System.out.println("After pipeconstraints: "+problem.getConstraintsCount());
				setHeadLossConstraints_esr_gen3();
				System.out.println("After headlossconstraints: "+problem.getConstraintsCount());
				setEsrConstraints_gen6();
				System.out.println("After esrconstraints: "+problem.getConstraintsCount());
				setEsrPipeConstraints_gen3();
				System.out.println("After esrpipeconstraints: "+problem.getConstraintsCount());
				break;
			case 8:
				addVariables_gen4();
				System.out.println("After addvariables: "+problem.getConstraintsCount());
				setObjectiveCost_esr_gen4();
				System.out.println("After setobjective: "+problem.getConstraintsCount());
				setEsrOptionConstraints();
				System.out.println("After setesroption: "+problem.getConstraintsCount());
				setPumpOptionConstraints();
				System.out.println("After setpumpoption: "+problem.getConstraintsCount());
				setValveOptionConstraints();
				System.out.println("After setvalveoption: "+problem.getConstraintsCount());
				setPipeConstraints_gen3();
				System.out.println("After pipeconstraints: "+problem.getConstraintsCount());
				setHeadLossConstraints_esr_gen4();
				System.out.println("After headlossconstraints: "+problem.getConstraintsCount());
				setEsrConstraints_gen4();
				System.out.println("After esrconstraints: "+problem.getConstraintsCount());
				setEsrPipeConstraints_gen3();
				System.out.println("After esrpipeconstraints: "+problem.getConstraintsCount());
				setPumpConstraints();
				System.out.println("After pumpconstraints: "+problem.getConstraintsCount());
				
				break;
			case 9:
				addVariables_gen4();
				System.out.println("After addvariables: "+problem.getConstraintsCount());
				setObjectiveCost_esr_gen4();
				System.out.println("After setobjective: "+problem.getConstraintsCount());
				setEsrOptionConstraints();
				System.out.println("After setesroption: "+problem.getConstraintsCount());
				setPumpOptionConstraints();
				System.out.println("After setpumpoption: "+problem.getConstraintsCount());
				setValveOptionConstraints();
				System.out.println("After setvalveoption: "+problem.getConstraintsCount());
				setPipeConstraints_gen3();
				System.out.println("After pipeconstraints: "+problem.getConstraintsCount());
				setHeadLossConstraints_esr_gen4();
				System.out.println("After headlossconstraints: "+problem.getConstraintsCount());
				setEsrConstraints_gen9();
				System.out.println("After esrconstraints: "+problem.getConstraintsCount());
				setEsrPipeConstraints_gen3();
				System.out.println("After esrpipeconstraints: "+problem.getConstraintsCount());
				setPumpConstraints();
				System.out.println("After pumpconstraints: "+problem.getConstraintsCount());
				break;
				
			case 10:
				addVariables_gen5();
				System.out.println("After addvariables: "+problem.getConstraintsCount());
				setObjectiveCost_esr_gen5();
				System.out.println("After setobjective: "+problem.getConstraintsCount());
				setEsrOptionConstraints_gen2();
				System.out.println("After setesroption: "+problem.getConstraintsCount());
				setPumpOptionConstraints();
				System.out.println("After setpumpoption: "+problem.getConstraintsCount());
				setValveOptionConstraints();
				System.out.println("After setvalveoption: "+problem.getConstraintsCount());
				setPipeConstraints_gen3();
				System.out.println("After pipeconstraints: "+problem.getConstraintsCount());
				setHeadLossConstraints_esr_gen5();
				System.out.println("After headlossconstraints: "+problem.getConstraintsCount());
				setEsrConstraints_gen10();
				System.out.println("After esrconstraints: "+problem.getConstraintsCount());
				setEsrPipeConstraints_gen4();
				System.out.println("After esrpipeconstraints: "+problem.getConstraintsCount());
				setPumpConstraints();
				System.out.println("After pumpconstraints: "+problem.getConstraintsCount());
				
				break;
		}
				
		System.out.println("Constraint count:" + problem.getConstraintsCount());
//		for (Constraint c : problem.getConstraints()){
//			System.out.println(c);
//		}
		
		
		
		//writeLpFile("Mokhada_input");
		//System.exit(0);
		
/////////Solver solver = factory.get(); // you should use this solver only once for one problem
		
//		SolverGLPK s = (SolverGLPK) solver;
//		Hook h = new Hook() {
//			
//			@Override
//			public void call(glp_prob arg0, glp_smcp arg1, glp_iocp arg2,
//					Map<Object, Integer> arg3) {
//				arg1.setIt_lim(1);
//			}
//		};
//		s.addHook(h);
		
		ResultStatus resultStatus = problem.solve();
		//Result result = solver.solve(problem);
		Result result = new Result(problem);
		//Result result = s.solve(problem);
		//if(result==null){
		if(resultStatus==ResultStatus.NOT_SOLVED){
			long endTime = System.currentTimeMillis();
			System.out.println("Time taken: " + (endTime - startTime) + " milliseconds");
			System.out.println("Time taken: " + problem.getTimeTaken() + " milliseconds");
			System.out.println("Nodes: "+problem.getNodes());
			throw new Exception("Optimization took too long. Please reduce input network size. If using ESR option consider removing zero demand nodes.<br> To run for extended period, please download the local version from https://www.cse.iitb.ac.in/~nikhilh/jaltantra");
		}
		else if(resultStatus==ResultStatus.OPTIMAL){
			System.out.println(result.getObjectiveValue());
			
			//set water head and pipe diameters from the result
			switch(modelNumber){
				case 0:
					setHeadsFromResult(result);
					setDiametersFromResult(result);
					//System.out.println(result);
					//printResult(result);
					break;
				case 1:
				case 2:
				case 3:
					setHeadsFromResult_esr(result);
					setDiametersFromResult(result);
					//System.out.println(result);
					//printResult(result);
					break;
				case 4:
				case 5:
				case 6:
				case 7:
					setHeadsFromResult_esr(result);
					setDiametersFromResult_gen3(result);
					//printResult_gen3(result);
					break;
				case 8:
				case 9:
					setHeadsFromResult_esr(result);
					setDiametersFromResult_gen4(result);
					//printVariables(result);
					break;
				case 10:
					setHeadsFromResult_esr_gen2(result);
					setDiametersFromResult_gen4(result);
					//printVariables_gen2(result);
					break;
				
			}
			//check();
			long endTime = System.currentTimeMillis();
			System.out.println("Time taken: " + (endTime - startTime) + " milliseconds");
			System.out.println("Time taken: " + problem.getTimeTaken() + " milliseconds");
			System.out.println("Nodes: "+problem.getNodes());
			
			//generateCoordinates();
			return true;
		}
		else{
			System.out.println("Network could not be solved.");
			return false;
		}
	}
	
	public String getCoordinatesString(){
		return coordinatesString;
	}
	
	@SuppressWarnings("unused")
	private void writeLpFile(String filename)
	{
		try 
		{ 
			PrintStream output = new PrintStream(filename+".lp");
			output.print(lpmodelstring);
			output.println();
			output.print(lpmodelvar);
			output.close();
		}
		catch(Exception e)
		{
			System.out.println(e.toString());
		}
	}
	
	//generate coordinate string to use for EPANET output file generation
	private void generateCoordinatesHelper(Node head, double x, double y, double minx, double maxx){
		coordinatesString += head.getNodeID()+" "+x+" "+y+",";
		List<Pipe> outgoingPipes = head.getOutgoingPipes();
		int n = outgoingPipes.size();
		
		if(n>1){
			double min_length = Double.MAX_VALUE;
			for(Pipe p : outgoingPipes){
				min_length = Math.min(min_length, p.getLength());
			}
			double max_width = min_length/(1-(1/(double)n));
			minx = Math.max(minx, x-max_width);
			maxx = Math.min(maxx, x+max_width);
		}
		for(int i=0;i<n;i++){
			Pipe p = outgoingPipes.get(i);
			double newminx = minx + (maxx-minx)*i/n;
			double newmaxx = minx + (maxx-minx)*(i+1)/n;
			double newx = (newminx+newmaxx)/2;
			double newy = y + Math.sqrt(p.getLength()*p.getLength() - (newx-x)*(newx-x));
			generateCoordinatesHelper(p.getEndNode(), newx, newy, newminx, newmaxx);
		}
		
	}
	
	@SuppressWarnings("unused")
	private void generateCoordinates(){
		coordinatesString = "";
		generateCoordinatesHelper(source,0,0,-10000,10000);
	}
	
	public HashMap<Integer,Node> getNodes(){
		return nodes;
	}
	
	public HashMap<Integer,Pipe> getPipes(){
		return pipes;
	}
	
	public List<PipeCost> getPipeCost(){
		return pipeCost;
	}
		
}

