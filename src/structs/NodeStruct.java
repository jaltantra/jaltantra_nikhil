package structs;
//container for node information

//nodeid: unique integer node id
//nodename: name of the node
//elevation: elevation of the node in metres
//demand: demand of the node in litres per second
//dailydemand: daily demand of the node in litres per day
//minpressure: minimum pressure required at the node in metres
//head: water head of the node in metres
//pressure: water pressure of the ndoe in metres (pressure = head - elevation)
//esr: node id of the esr serving this node

public class NodeStruct
{
	public int nodeid;
	public String nodename;
	public double elevation;
	public double demand;
	public double dailydemand;
	public double minpressure;
	public double head;
	public double pressure;
	public int esr;
	
	public NodeStruct(int nodeid, String nodename, double elevation, double demand,
			double dailydemand, double minpressure, double head, double pressure, int esr) {
		this.nodeid = nodeid;
		this.nodename = nodename;
		this.elevation = elevation;
		this.demand = demand;
		this.dailydemand = dailydemand;
		this.minpressure = minpressure;
		this.head = head;
		this.pressure = pressure;
		this.esr = esr;
	}

	@Override
	public String toString() {
		return "NodeStruct [nodeid=" + nodeid 
				+ ", nodename=" + nodename + ", elevation=" + elevation
				+ ", demand=" + demand + ", dailydemand=" + dailydemand
				+ ", minpressure=" + minpressure + ", head=" + head 
				+ ", pressure=" + pressure + ", esr=" + esr + "]";
	}
}
