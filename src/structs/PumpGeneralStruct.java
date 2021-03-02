package structs;
//container for pump general information

//pump_enabled: true if pumps are to be optimized
//minpumpsize: minimum pump power allowed in kW
//efficiency: efficiency of the pump expressed as a percentage
//capitalcost_per_kw: capital cost per kW of a pump in Rupees per kW
//energycost_per_kw: energy cost per kW of a pump in Rupees per kWh
//energycost_factor: factor by which energy cost of the pump is multiplied
//discount_rate: rate by which future energy cost is discounted to compute present value of the cost, expressed as a percentage
//inflation_rate: rate by which future energy cost is increased, expressed as a percentage
//design_lifetime: number of years for which the energy cost is to be considered
//must_not_pump: list of pipes(represented by their pipe ids) which cannot have pumps installed

public class PumpGeneralStruct{
	
	public boolean pump_enabled;
	public double minpumpsize;
	public double efficiency;
	public double capitalcost_per_kw;
	public double energycost_per_kwh;
	public double energycost_factor;
	public double discount_rate;
	public double inflation_rate;
	public int design_lifetime;
	public int[] must_not_pump;
	
	
	public PumpGeneralStruct(boolean pump_enabled, double minpumpsize,
							double efficiency, double capitalcost_per_kw, 
							double energycost_per_kwh, double energycost_factor, 
							double discount_rate, double inflation_rate, 
							int design_lifetime, int[] must_not_pump) {
		this.pump_enabled = pump_enabled;
		this.minpumpsize = minpumpsize;
		this.efficiency = efficiency;
		this.capitalcost_per_kw = capitalcost_per_kw;
		this.energycost_per_kwh = energycost_per_kwh;
		this.energycost_factor = energycost_factor;
		this.discount_rate = discount_rate;
		this.inflation_rate = inflation_rate;
		this.design_lifetime = design_lifetime;
		this.must_not_pump = must_not_pump;
	}

	@Override
	public String toString() {
		return "PumpGeneralStruct [pump_enabled="+ pump_enabled + ", minpumpsize=" + minpumpsize + ", efficiency=" + efficiency 
				+ ", capitalcost_per_kw=" + capitalcost_per_kw + ", energycost_per_kwh=" + energycost_per_kwh
				+ ", energycost_factor=" + energycost_factor + ", discount_rate=" + discount_rate
				+ ", inflation_rate=" + inflation_rate + ", design_lifetime=" + design_lifetime
				+ ", must_not_pump=" + must_not_pump + "]";
	}
}