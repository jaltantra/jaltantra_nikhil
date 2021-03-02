package structs;
//container for pipe information

//pipeid: unique integer id of the pipe
//startnode: node id of the starting node
//endnode: node id of the ending node
//length: length of the pipe in metres
//diamter: diameter of the pipe in millimetres
//roughness: hazen williams roughness coefficient of the pipe
//flow: flow of water in the pipe in litres per second
//headloss: headloss in the pipe in metres
//headlossperkm: headloss per kilometre in the pipe in metres/km
//speed: speed of the water in the pipe in metres per second
//cost: cost of the pipe in Rupees
//parallelallowed: true if a parallel pipe is allowed to be installed (relevant only for those pipes whose diameter is already fixed, in these cases an additional parallel pipe maybe installed to augment capacity)
//pressureexceeded: true if pressure in pipe exceeds the maximum pressure constraint
//isprimary: true if the pipe belongs to the primary network, false if it belongs to the secondary network
//pumphead: water head provided by the pump installed in this pipe in metres
//pumpower: power of the pump installed in this pipe in kW
//valvesetting: amount of pressure reduced by the pressure reducing valve installed in this pipe in metres

public class PipeStruct
{		
	public PipeStruct(int pipeid, int startnode, int endnode,
			double length, double diameter, double roughness, double flow,
			double headloss, double headlossperkm, double speed, double cost, boolean parallelallowed,
			boolean pressureexceeded, boolean isprimary, double pumphead, double pumppower, double valvesetting) {
		this.pipeid = pipeid;
		this.startnode = startnode;
		this.endnode = endnode;
		this.length = length;
		this.diameter = diameter;
		this.roughness = roughness;
		this.flow = flow;
		this.headloss = headloss;
		this.headlossperkm = headlossperkm;
		this.speed = speed;
		this.cost = cost;
		this.parallelallowed = parallelallowed;
		this.pressureexceeded = pressureexceeded;
		this.isprimary = isprimary;
		this.pumphead = pumphead;
		this.pumppower = pumppower;
		this.valvesetting = valvesetting;
	}

	public int pipeid;
	public int startnode;
	public int endnode;
	public double length;
	public double diameter;
	public double roughness;
	public double flow;
	public double headloss;
	public double headlossperkm;
	public double speed;
	public double cost;
	public boolean parallelallowed;
	public boolean pressureexceeded;
	public boolean isprimary;
	public double pumphead;
	public double pumppower;
	public double valvesetting;
	
	@Override
	public String toString() {
		return "PipeStruct [pipeid=" + pipeid + ", startnode=" + startnode
				+ ", endnode=" + endnode + ", length=" + length
				+ ", diameter=" + diameter + ", roughness=" + roughness
				+ ", flow=" + flow + ", headloss=" + headloss
				+ ", headlossperkm=" + headlossperkm + ", speed=" + speed
				+ ", cost=" + cost + ", parallelallowed=" + parallelallowed
				+ ", pressureexceeded=" + pressureexceeded + ", isprimary=" + isprimary 
				+ ", pumphead=" + pumphead + ", pumppower=" + pumppower
				+ ", valvesetting=" + valvesetting + "]";
	}
}
