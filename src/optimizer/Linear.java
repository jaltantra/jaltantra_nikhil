package optimizer;

import java.util.ArrayList;
import java.util.List;


//represents the lhs of a constraint
//linearvarnames : list of variable names on the lhs
//linearvarcoefficients: list of coefficients for the variables in linearvarnames

public class Linear {
	List<String> linearVarNames;
	List<Double> linearVarCoefficients;
	
	public Linear(){
		linearVarCoefficients = new ArrayList<Double>();
		linearVarNames = new ArrayList<String>();
	}
	
	public void add(double coefficient, String varName){
		linearVarCoefficients.add(coefficient);
		linearVarNames.add(varName);
	}
}
