package structs;
//container for map pipe information

//encodedpath: string which encodes the path of the pipe as per https://developers.google.com/maps/documentation/utilities/polylinealgorithm
//originid: integer node id of the starting node
//destinationid: integer node of the ending node
//length: length of the pipe in metres

public class MapPipeStruct
{
	public String encodedpath;
	public int originid;
	public int destinationid;
	public double length;
	
	public MapPipeStruct(String encodedpath, int originid, int destinationid,
			double length) {
		this.encodedpath = encodedpath;
		this.originid = originid;
		this.destinationid = destinationid;
		this.length = length;
	}

	@Override
	public String toString() {
		return "MapPipeStruct [encodedpath=" + encodedpath + ", originid=" + originid
				+ ", destinationid=" + destinationid + ", length=" + length + "]";
	}
}
