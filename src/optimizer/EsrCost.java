package optimizer;

// class representing a row in the ESR cost table
//the ESR cost table maps the capacity of an ESR to its capital cost

//mincapacity: minimum capacity of the ESR cost row in litres
//maxcapacity: maximum capacity of the ESR cost row in litres
//basecost: basecost of the ESR cost row in rupees
//unitcost: unitcost of the ESR cost row in rupees/litre

//simple example: 
//sample row:(20,30,100,10)
//cost of a 25 litre tank = 100 + 10*(25-20) = Rs 150

public class EsrCost {
	private double minCapacity;
	private double maxCapacity;
	private double baseCost;
	private double unitCost;
	
	//private double lpslpdconverter = 3600*24/2;
	
	public EsrCost(double minCapacity, double maxCapacity, double baseCost, double unitCost){
		this.minCapacity = minCapacity;
		this.maxCapacity = maxCapacity;
		this.baseCost = baseCost;
		this.unitCost = unitCost;
	}

	public double getMinCapacity() {
		return minCapacity;
	}

	public double getMaxCapacity() {
		return maxCapacity;
	}

	public double getBaseCost() {
		return baseCost;
	}

	public double getUnitCost() {
		return unitCost;
	}	
}
