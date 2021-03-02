package structs;
//container for esr general information

//esr_enabled: true if esr optimization is enabled
//secondary_supply_hours: number of hours in a day for which secondary network supply is active
//esr_capacity_factor: ratio of size of ESR to the daily water demand
//max_esr_height: maximum height of ESR allowed in metres
//allow_dummy: true if nodes with no demand are allowed to have ESRs (will serve downstream nodes with demand)
//must_esr: array of nodeids representing nodes that must have ESRs
//must_not_esr: array of nodeids represting nodes that cannot have ESRs

public class EsrGeneralStruct
{
	public boolean esr_enabled;
	public double secondary_supply_hours;
	public double esr_capacity_factor;
	public double max_esr_height;
	public boolean allow_dummy;
	public int[] must_esr;
	public int[] must_not_esr;
	
	
	public EsrGeneralStruct(boolean esr_enabled, double secondary_supply_hours,	
							double esr_capacity_factor, double max_esr_height, 
			boolean allow_dummy, int[] must_esr, int[] must_not_esr) {
		this.esr_enabled = esr_enabled;
		this.secondary_supply_hours = secondary_supply_hours;
		this.esr_capacity_factor = esr_capacity_factor;
		this.max_esr_height = max_esr_height;
		this.allow_dummy = allow_dummy;
		this.must_esr = must_esr;
		this.must_not_esr = must_not_esr;
	}

	@Override
	public String toString() {
		return "EsrGeneralStruct [esr_enabled="+ esr_enabled + ", secondary_supply_hours=" + secondary_supply_hours 
				+ ", esr_capacity_factor=" + esr_capacity_factor + ", max_esr_height=" + max_esr_height
				+ ", allow_dummy=" + allow_dummy + ", must_esr=" + must_esr
				+ ", must_not_esr=" + must_not_esr + "]";
	}
}