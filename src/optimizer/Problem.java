package optimizer;

import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPSolver.ResultStatus;
import com.google.ortools.linearsolver.MPVariable;

//used to solve the ILP using Google OR Tools interface
//solver used is CBC

//add else to null checks

public class Problem {
	
	static { 
		try{
			System.loadLibrary("jniortools"); 
		}
		catch(Exception e){
			System.out.println(e.getMessage());
		}
	}
	MPSolver solver;
	
	public Problem(){
		solver = new MPSolver("JalTantraSolver", MPSolver.OptimizationProblemType.CBC_MIXED_INTEGER_PROGRAMMING);
		//solver.enableOutput();
	}
	
	public void setVarType(String varName, Class<?> classType){
		if(classType == Boolean.class){
			solver.makeBoolVar(varName);
			//solver.makeNumVar(0, 1, varName);
		}
		else if(classType == Double.class){
			solver.makeNumVar(-MPSolver.infinity(), MPSolver.infinity(), varName);
		}
	}
	
	public void setVarLowerBound(String varName, double lb) throws Exception{
		MPVariable var = solver.lookupVariableOrNull(varName);
		if(var != null){
			var.setLb(lb);
		}
		else
			throw new Exception("Variable "+varName+" not found.");
	}
	
	public void setVarUpperBound(String varName, int ub) throws Exception{
		MPVariable var = solver.lookupVariableOrNull(varName);
		if(var != null){
			var.setUb(ub);
		}
		else
			throw new Exception("Variable "+varName+" not found.");
	}
	
	//direction = true => maximization  / false => minimization
	public void setObjective(Linear linear, boolean direction) throws Exception{
		MPObjective objective = solver.objective();
		objective.setOptimizationDirection(direction);
		
		for(int i=0;i<linear.linearVarNames.size();i++){
			MPVariable var = solver.lookupVariableOrNull(linear.linearVarNames.get(i));
			if(var!=null){
				objective.setCoefficient(var, linear.linearVarCoefficients.get(i));
			}
			else
				throw new Exception("Variable "+linear.linearVarNames.get(i)+" not found.");
		}
	}
	
	public void add(Constraint cons) throws Exception{
		MPConstraint ct = solver.makeConstraint(cons.name);
		//System.out.println(cons);
		for(int i=0;i<cons.linear.linearVarNames.size();i++){
			MPVariable var = solver.lookupVariableOrNull(cons.linear.linearVarNames.get(i));
			if(var!=null){
				ct.setCoefficient(var, cons.linear.linearVarCoefficients.get(i));
			}
			else
				throw new Exception("Variable "+cons.linear.linearVarNames.get(i)+" not found.");
		}		
		
		switch(cons.sign){
			case "=":
				ct.setBounds(cons.rhs, cons.rhs);
				break;
			case "<=":
				ct.setBounds(-MPSolver.infinity(), cons.rhs);
				break;	
			case ">=":
				ct.setBounds(cons.rhs, MPSolver.infinity());
				break;
			default:
				throw new Exception("Invalid sign '"+cons.sign+"' provided in constraint defintion");
		}
	}

	public Double getPrimalValue(String varName) throws Exception {
		MPVariable var = solver.lookupVariableOrNull(varName);
		if(var!=null){
			return var.solutionValue();
		}
		else
			return null;
	}

	public int getConstraintsCount() {
		return solver.numConstraints();
	}
	
	public void setTimeLimit(int milliseconds){
		solver.setTimeLimit(milliseconds);
	}

	public double getTimeTaken(){
		return solver.wallTime();
	}
	
	public double getNodes(){
		return solver.nodes();
	}
	
	public ResultStatus solve() throws Exception {
		ResultStatus resultStatus = solver.solve();
		return resultStatus;
	}
}
