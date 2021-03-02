package optimizer;
//commercial pipe information

//diameter: diameter of commercial pipe in millimetres
//cost: cost of commercial pipe of certain diameter in rupees
//maxpressure: maximum pressure that the commercial pipe can withstand
//roughness: hazen-williams roughness coefficient of commercial pipe

public class PipeCost implements Comparable<PipeCost>{
	private double diameter;
	private double cost;
	private double maxPressure;
	private double roughness;	
	
	public PipeCost(double diameter, double cost, double maxPressure, double roughness){
		this.diameter = diameter;
		this.cost = cost;
		this.maxPressure = maxPressure;
		this.roughness = roughness;
	}
	
	public double getDiameter(){
		return diameter;
	}
	
	public double getCost(){
		return cost;
	}
	
	public double getMaxPressure(){
		return maxPressure;
	}
	
	public double getRoughness(){
		return roughness;
	}

	@Override
	public int compareTo(PipeCost o) {
		int ret = (int) (maxPressure - o.maxPressure);
		if(ret==0)
			ret = (int) (diameter - o.diameter);
		return ret;
	}
}
