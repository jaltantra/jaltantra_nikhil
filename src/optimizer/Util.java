package optimizer;

import java.math.BigDecimal;
import java.math.RoundingMode;

//contains utility functions

public class Util {
		
	//calculates the hazen williams headloss in a pipe
	// length:metres, flow: litres/sec, diameter: millimetres
	// output headloss : metres
	public static double HWheadLoss(double length, double flow, double HWConstant, double diameter) // length : m, diameter : mm, flow: m3/sec
	{
		return 10.68 * length * Math.pow((flow*0.001)/HWConstant, 1.852)/Math.pow((diameter/1000),4.87);
	}
	
	//calculates the unit hazen williams headloss in a pipe
	//flow: litres/sec, diameter: millimetres
	//output unit headloss: metres / metres
	public static double HWheadLoss(double flow, double HWConstant, double diameter) // diameter : mm, flow: m3/sec
	{
		return 10.68 * Math.pow((flow*0.001)/HWConstant, 1.852)/Math.pow((diameter/1000),4.87);
	}
	
	public static double round(double number, int places){
		if (places < 0) throw new IllegalArgumentException();

	    BigDecimal bd = new BigDecimal(number);
	    bd = bd.setScale(places, RoundingMode.HALF_UP);
	    return bd.doubleValue();
	}
	
	//flow in lps, diameter in mm, speed in m/s
	public static double waterSpeed(double flow, double diameter){
		return (flow*4000)/(Math.PI*diameter*diameter);
	}
	
	//calculates the factor by which a constant cost must be multiplied to get the present value of the total cost over a certain number of years
	//discountrate, inflationrate in %, time in years
	public static double presentValueFactor(double discountrate, double inflationrate, int time){
		if(discountrate==inflationrate){
			return time;
		}
		else{
			double rate = (1 + inflationrate/100)/(1 + discountrate/100);
			return (Math.pow(rate, time) - 1)/ (rate - 1);
		}
	}
}

