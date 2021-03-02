package structs;
//container for map node information

//nodeid: unique integer id for a node
//nodename: name of the node
//latitude: latitude of the node
//longitude: longitude of the name
//isesr: true if there is an ESR at the node
public class MapNodeStruct
{
	public int nodeid;
	public String nodename;
	public double latitude;
	public double longitude;
	public boolean isesr;
		
	public MapNodeStruct(int nodeid, String nodename, 
			double latitude, double longitude, boolean isesr) {
		this.nodeid = nodeid;
		this.nodename = nodename;
		this.latitude = latitude;
		this.longitude = longitude;
		this.isesr = isesr;
	}

	@Override
	public String toString() {
		return "MapNodeStruct [nodeid=" + nodeid + ", nodename=" + nodename
				+ ", latitude=" + latitude + ", longitude=" + longitude + ", isesr=" + isesr + "]";
	}
}
