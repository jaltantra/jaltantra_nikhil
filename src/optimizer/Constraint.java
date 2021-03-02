package optimizer;
// represents a constraint in the linear program

//name: name of the constraint
//linear: list of variables and their coefficients on the lhs of the constraint (all variables only occur on the lhs, rhs only contains a constant value)
//sign: sign of the constraint. can be "=" , "<=" or ">="
//rhs: double value representing the rhs of the constraint

public class Constraint {
	
	String name;
	Linear linear;
	String sign;
	double rhs;
	public Constraint(String name, Linear linear, String sign, double rhs){
		this.name = name;
		this.linear = linear;
		this.sign = sign;
		this.rhs = rhs;
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		for(int i=0; i<linear.linearVarNames.size();i++){
			sb.append(linear.linearVarCoefficients.get(i));
			sb.append(linear.linearVarNames.get(i));
			sb.append(" ");
			if(i!=linear.linearVarNames.size()-1)
				sb.append("+ ");
		}
		sb.append(sign+" "+rhs+"\n");
		return sb.toString();
	}
}
