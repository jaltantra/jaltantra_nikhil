package structs;
//container for ESR result information
//nodeid: unique integer of the node
//nodename: name of the node
//elevation: elevation of the node in metres
//esrheight: height of the ESR in metres
//capacity: capacity of the ESR in litres
//cost: cost of the ESR in Rupees
//cumulativecost: cumulative cost of the ESRs so far
//hasprimarychild: true if node has demand (used only for EPANET output file generation)

public class ResultEsrStruct
{
	public int nodeid;
	public String nodename;
	public double elevation;
	public double esrheight;
	public double capacity;
	public double cost;
	public double cumulativecost;
	public boolean hasprimarychild;
	
	
	public ResultEsrStruct(int nodeid, String nodename, double elevation, double esrheight,
			double capacity, double cost, double cumulativecost, boolean hasprimarychild) {
		this.nodeid = nodeid;
		this.nodename = nodename;
		this.elevation = elevation;
		this.esrheight = esrheight;
		this.capacity = capacity;
		this.cost = cost;
		this.cumulativecost = cumulativecost;
		this.hasprimarychild = hasprimarychild;
	}

	@Override
	public String toString() {
		return "ResultEsrStruct [nodeid=" + nodeid 
				+ ", nodename=" + nodename + ", elevation=" + elevation
				+ ", esrheight=" + esrheight + ", capacity=" + capacity
				+ ", cost=" + cost + ", cumulativecost=" + cumulativecost+ 
				", hasprimarychild=" + hasprimarychild + "]";
	}
}
