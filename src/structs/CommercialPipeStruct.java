package structs;

//classes in this package are used to transfer information during post requests to the server

// container for commercial pipe information

// diameter: diameter of commercial pipe in millimetres
// cost: cost of commercial pipe of certain diameter in rupees
// length: length of commercial pipe of certain diameter in metres
// cumulativecost: cumulative cost of commercial pipes so far in rupees
// roughness: hazen-williams roughness coefficient of commercial pipe

public class CommercialPipeStruct
{
	public CommercialPipeStruct(double diameter, double cost,
			double length, double cumulativecost, double roughness) {
		this.diameter = diameter;
		this.cost = cost;
		this.length = length;
		this.cumulativecost = cumulativecost;
		this.roughness = roughness;
	}

	public double diameter;
	public double cost;
	public double length;
	public double cumulativecost;
	public double roughness;
	
	@Override
	public String toString() {
		return "CommercialPipeStruct [diameter=" + diameter + ", cost="
				+ cost + ", roughness=" + roughness + ", length=" + length + ", cumulativecost="
				+ cumulativecost + "]";
	}
}
