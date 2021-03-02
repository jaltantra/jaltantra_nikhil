package structs;
//container for esr cost information

// an instance of this class represents one row of the ESR cost table
// the ESR cost table maps the capacity of an ESR to its capital cost

//mincapacity: minimum capacity of the ESR cost row in litres
//maxcapacity: maximum capacity of the ESR cost row in litres
//basecost: basecost of the ESR cost row in rupees
//unitcost: unitcost of the ESR cost row in rupees/litre

//simple example: 
//sample row:(20,30,100,10)
//cost of a 25 litre tank = 100 + 10*(25-20) = Rs 150

public class EsrCostStruct
{
	public EsrCostStruct(double mincapacity, double maxcapacity,
			double basecost, double unitcost) {
		this.mincapacity = mincapacity;
		this.maxcapacity = maxcapacity;
		this.basecost = basecost;
		this.unitcost = unitcost;
	}

	public double mincapacity;
	public double maxcapacity;
	public double basecost;
	public double unitcost;
	
	@Override
	public String toString() {
		return "EsrCostStruct [minCapacity=" + mincapacity + ", maxCapacity="
				+ maxcapacity + ", baseCost=" + basecost + ", unitCost="
				+ unitcost + "]";
	}
}
