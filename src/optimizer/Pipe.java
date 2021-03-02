package optimizer;

import java.util.Set;

//represents a pipe in the network

//pipeid: unique integer id of the pipe
//diamter: diameter of the pipe in millimetres
//length: length of the pipe in metres
//roughness: hazen williams roughness coefficient of the pipe
//flow: flow of water in the pipe in litres per second
//startnode: starting node of the pipe
//endnode: ending node of the pipe

//a link in the network can consist of multiple diameters
//diameter2,length2,roughness2,chosenpipecost2 are values for the 2nd pipe in the link, if it exists
//alternatively they can also represent the values of a pipe laid in parallel to an existing pipe

//parallelallowed: true if a parallel pipe is allowed to be installed (relevant only for those pipes whose diameter is already fixed, in these cases an additional parallel pipe maybe installed to augment capacity)
//existingpipe: true if the pipe already exists i.e. its diameter is already known before optimization
//chosenpipecost: commercial pipe cost chosen for this pipe
//flowchoice: represents if the pipe is part of the primary network (water comes from the source) or the secondary network (water comes from an ESR)
//pumphead: water head provided by the pump installed in this pipe in metres
//allowpump: true if a pump is allowed to be installed in this pipe
//pumpower: power of the pump installed in this pipe in kW
//valvesetting: amount of pressure reduced by the pressure reducing valve installed in this pipe in metres
 
public class Pipe {
	
	public enum FlowType {
		PRIMARY(1), SECONDARY(0);
		private int value;
		
		private FlowType(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}
	}
	
	private int pipeID;
	private double diameter;// any double or fixed from available list?
	private double length;
	private double roughness;
	private double flow;
	private Node startNode;
	private Node endNode;
	private double diameter2;
	private double length2;
	private double roughness2;
	private boolean allowParallel;
	private boolean existingPipe;
	private PipeCost chosenPipeCost;
	private PipeCost chosenPipeCost2;
	private FlowType flowchoice;
	private double pumphead;
	private boolean allowPump = true;
	private double pumppower;
	private double valvesetting;
			
//	public int getNextPipeID(Set<Integer> usedPipeIDs){
//	int id = 0;
//	while(usedPipeIDs.contains(id))
//	{
//		id++;
//	}
//	return id;
//}
	
	private void setCustomID(int pipeID, Set<Integer> usedPipeIDs) throws Exception{
		this.pipeID = pipeID;
		if(this.pipeID<=0)
		{
			throw new Exception("Pipe ID "+this.pipeID + " is invalid");
		}
		if(!usedPipeIDs.add(this.pipeID))
		{
			throw new Exception("Pipe ID "+this.pipeID + " already being used");
		}
	}
		
	public Pipe(double length, Node startNode, Node endNode, double diameter, double roughness, int pipeID, boolean allowParallel, Set<Integer> usedPipeIDs) throws Exception{
		setCustomID(pipeID, usedPipeIDs);
		this.length = length;
		this.startNode = startNode;
		this.endNode = endNode;
		this.diameter = diameter;
		this.roughness = roughness;
		this.allowParallel = allowParallel && diameter !=0;
		this.startNode.addToOutgoingPipes(this);
		this.existingPipe = diameter != 0;
		this.flowchoice = FlowType.PRIMARY;
	}
	
	public boolean isAllowParallel(){
		return allowParallel;
	}
	
	public boolean existingPipe(){
		return existingPipe;
	}
	
	public int getPipeID(){
		return this.pipeID;
	}	
	
	public double getDiameter() {
		return diameter;
	}
	
	public double getLength() {
		return length;
	}
	
	public double getRoughness() {
		return roughness;
	}
	
	public Node getStartNode() {
		return startNode;
	}
	
	public Node getEndNode() {
		return endNode;
	}
	
	public double getFlow() {
		return flow;
	}

	public double getDiameter2() {
		return diameter2;
	}

	public double getLength2() {
		return length2;
	}
	
	public double getRoughness2() {
		return roughness2;
	}

	public void setFlow(double flow) {
		this.flow = flow;
	}

	public void setDiameter2(Double dia) {
		this.diameter2 = dia;
	}

	public void setDiameter(Double dia) {
		this.diameter = dia;
	}

	public void setLength2(double len) {
		this.length2 = len;
	}
	
	public void setRoughness(double roughness) {
		this.roughness = roughness;
	}
	
	public void setRoughness2(double roughness) {
		this.roughness2 = roughness;
	}

	public PipeCost getChosenPipeCost() {
		return chosenPipeCost;
	}

	public void setChosenPipeCost(PipeCost chosenPipeCost) {
		this.chosenPipeCost = chosenPipeCost;
	}

	public PipeCost getChosenPipeCost2() {
		return chosenPipeCost2;
	}

	public void setChosenPipeCost2(PipeCost chosenPipeCost2) {
		this.chosenPipeCost2 = chosenPipeCost2;
	}

	public FlowType getFlowchoice() {
		return flowchoice;
	}

	public void setFlowchoice(FlowType flowchoice) {
		this.flowchoice = flowchoice;
	}

	public double getPumpHead(){
		return pumphead;
	}
	
	public void setPumpHead(double pumphead) {
		this.pumphead = pumphead;		
	}

	public void setAllowPump(boolean allowPump) {
		this.allowPump = allowPump;
	}
	
	public boolean getAllowPump() {
		return allowPump;
	}

	public double getPumpPower(){
		return pumppower;
	}
	
	public void setPumpPower(double pumppower) {
		this.pumppower = pumppower;
	}

	public double getValveSetting(){
		return valvesetting;
	}
	
	public void setValveSetting(double valvesetting) {
		this.valvesetting = valvesetting;		
	}
}

