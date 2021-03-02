package optimizer;

//helper class to retrieve results after optimization

public class Result {

	Problem problem;
	public Result(Problem problem){
		this.problem = problem;
	}
	public Double getPrimalValue(String varName) throws Exception {
		return problem.getPrimalValue(varName);
	}
	public Double getObjectiveValue(){
		return problem.solver.objective().value();
	}
}
