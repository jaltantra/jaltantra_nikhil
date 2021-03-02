package optimizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//represents a node in the network

//nodeid: unique integer node id
//nodename: name of the node
//elevation: elevation of the node in metres
//demand: actual demand of the node in litres per second 
//basedemand: daily demand of the node in litres per second
// for example: 
// base demand = 2 lps
// if supply hours = 12 hours
// demand = 2 * 24/12 = 4 lps

//residualpressure: minimum pressure required at the node in metres
//head: water head of the node in metres
//allowesr: true if this node can have an ESR
//outgoingpipes: list of pipes that have this node as their starting node
//sourcetonodepipes: list of pipes that form the path from source to this node
//upstreamnodes: list of nodes that are upstream of this node
//downstreamnodes: list of nodes that are downstream of this node
//esr: node id of the esr serving this node
//esrheight: height of the ESR at this node in metres
//esrtotaldemand: total daily demand served by the ESR at this node in litres
//esrcost: total capital cost of the ESR at this node
//servednodes: list of nodes that are served by the ESR at this node

public class Node {
	private int nodeID;
	private String nodeName;
	private double elevation;
	private double demand; // flow in BRANCH
	private double basedemand;
	private double residualPressure;
	private double head;
	private boolean allowESR = true;
	private List<Pipe> outgoingPipes = new ArrayList<Pipe>();
	private List<Pipe> sourceToNodePipes = new ArrayList<Pipe>();
	private Set<Node> upstreamNodes = new HashSet<Node>();
	private Set<Node> downstreamNodes = new HashSet<Node>();
	private int ESR;
	private double esrHeight;
	private double esrTotalDemand;
	private double esrCost;
	private Set<Node> servedNodes = new HashSet<Node>();
	
//	public int getNextNodeID(Set<Integer> usedNodeIDs){
//		int id = 0;
//		while(usedNodeIDs.contains(id))
//		{
//			id++;
//		}
//		return id;
//	}
	
	// set a custom node id to this node
	private void setCustomID(int nodeID, Set<Integer> usedNodeIDs) throws Exception{
		this.nodeID = nodeID;
		if(this.nodeID<=0)
		{
			throw new Exception("Node ID "+this.nodeID + " is invalid");
		}
		if(!usedNodeIDs.add(this.nodeID))
		{
			throw new Exception("Node ID "+this.nodeID + " already being used");
		}
	}
		
	public Node(double elevation, double demand, int nodeID, double minPressure, String nodeName, double peakFactor, Set<Integer> usedNodeIDs) throws Exception{
		this.elevation = elevation;
		this.basedemand = demand;
		this.demand = demand * peakFactor;
		this.residualPressure = minPressure;	
		setCustomID(nodeID, usedNodeIDs);
		this.nodeName = nodeName;
		this.ESR = this.nodeID;
	}
		
	public int getNodeID(){
		return nodeID;	
	}
	
	public double getDemand() {
		return demand;
	}
	
	public boolean getAllowESR(){
		return allowESR;
	}
	
	public void setAllowESR(boolean allowESR){
		this.allowESR = allowESR;
	}
	
	public String getNodeName() {
		return nodeName;
	}
		
	public double getResidualPressure() {
		return residualPressure;
	}
	
	public List<Pipe> getOutgoingPipes(){
		return outgoingPipes;
	}
	
	public void addToOutgoingPipes(Pipe pipe){
		outgoingPipes.add(pipe);
	}
	
	public List<Pipe> getSourceToNodePipes(){
		return sourceToNodePipes;
	}
	
	public void addToSourceToNodePipes(Pipe pipe){
		sourceToNodePipes.add(pipe);
	}

	public void addToSourceToNodePipes(List<Pipe> sourceToNodePipes2) {
		for(Pipe pipe : sourceToNodePipes2)
			this.sourceToNodePipes.add(pipe);
	}
	
	public void addToUpstreamNodes(Set<Node> upstreamNodes){
		for(Node node : upstreamNodes)
			this.upstreamNodes.add(node);	
	}
	
	public void addToUpstreamNodes(Node upstreamNode){
		this.upstreamNodes.add(upstreamNode);
	}
	
	public void addToDownstreamNodes(Set<Node> downstreamNodes){
		for(Node node : downstreamNodes)
			this.downstreamNodes.add(node);	
	}
	
	public Set<Node> getUpstreamNodes(){
		return upstreamNodes;
	}
	
	public Set<Node> getDownstreamNodes(){
		return downstreamNodes;
	}
	
	public void addToDownstreamNodes(Node downstreamNode){
		this.downstreamNodes.add(downstreamNode);
	}
	
	public double getElevation() {
		return elevation;
	}
	
	public double getHead() {
		return head;
	}
	
	public void setHead(double head) {
		this.head = head;
	}

	public double getPressure() {
		return this.head-this.elevation;
	}

	public int getESR() {
		return ESR;
	}

	public void setESR(int eSR) {
		ESR = eSR;
	}

	public double getEsrHeight() {
		return esrHeight;
	}

	public void setEsrHeight(double esrHeight) {
		this.esrHeight = esrHeight;
	}

	public double getEsrTotalDemand() {
		return esrTotalDemand;
	}

	public void setEsrTotalDemand(double esrTotalDemand) {
		this.esrTotalDemand = esrTotalDemand;
	}	
	
	public void addToServedNodes(Set<Node> servedNodes){
		for(Node node : servedNodes)
			this.servedNodes.add(node);	
	}
	
	public void addToServedNodes(Node servedNode){
		this.servedNodes.add(servedNode);	
	}
	
	public Set<Node> getServedNodes(){
		return servedNodes;
	}

	public double getEsrCost() {
		return esrCost;
	}

	public void setEsrCost(double esrCost) {
		this.esrCost = esrCost;
	}
	
	public double getRequiredCapacity(double esrCapacityFactor){
		return esrCapacityFactor * basedemand * 3600*24;
	}
}
	