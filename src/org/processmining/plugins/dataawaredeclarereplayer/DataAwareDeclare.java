package org.processmining.plugins.dataawaredeclarereplayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.processmining.models.guards.Expression;
import org.processmining.plugins.DataConformance.framework.ExecutionTrace;
import org.processmining.plugins.DataConformance.framework.LogExecutionTrace;
import org.processmining.plugins.DeclareConformance.ReplayableDeclare;
import org.processmining.plugins.dataawaredeclarereplayer.utils.Utils;
import org.processmining.plugins.declareminer.visualizing.ActivityDefinition;
import org.processmining.plugins.declareminer.visualizing.AssignmentModel;
import org.processmining.plugins.declareminer.visualizing.ConstraintDefinition;
import org.processmining.plugins.declareminer.visualizing.DeclareMap;

@SuppressWarnings("deprecation")
public class DataAwareDeclare extends ReplayableDeclare {
	@SuppressWarnings("rawtypes")
	Map<String, Class> dataVariables = new HashMap<>();
	Map<String, String> guards = new HashMap<>();
	private final AssignmentModel model;
	private DeclareMap declareModel;

	public DataAwareDeclare(DeclareMap model) throws Exception {
		super(model);
		this.model = model.getModel();
		this.declareModel = model;
		List<Expression> expressions = new ArrayList<>();
		for (ConstraintDefinition cd : model.getModel().getConstraintDefinitions()) {
			String condition = Utils.getConditionString(cd.getCondition());
			boolean conditionValid = condition.contains("A.");
			condition = condition.replace("A.", "");
			if (conditionValid && condition != null && condition.length() > 0) {
				expressions.add(new Expression(condition));
				for (ActivityDefinition a : cd.getBranches(cd.getFirstParameter())) {
					guards.put(a.getName(), condition);
				}

			}

		}

		setVariables(expressions);
	}

	private void getExpressionWithVariables(Expression exp) {
		try {
			Expression op1 = (Expression) exp.getOperand1();
			getExpressionWithVariables(op1);
			getExpressionWithVariables((Expression) exp.getOperand2());
		} catch (ClassCastException e) {
			if (!dataVariables.containsKey(exp.getOperand1())) {
				dataVariables.put(exp.getOperand1().toString(), exp.getOperand2().getClass());
			}
		}
	}

	private void setVariables(List<Expression> expressions) throws Exception {
		for (Expression exp : expressions) {

			try {
				Expression op1 = (Expression) exp.getOperand1();
				getExpressionWithVariables(op1);
				getExpressionWithVariables((Expression) exp.getOperand2());
			} catch (ClassCastException e) {
				if (!dataVariables.containsKey(exp.getOperand1())) {
					dataVariables.put(exp.getOperand1().toString(), exp.getOperand2().getClass());
				}
			}

		}
	}

	public ExecutionTrace buildEmptyPrefix() {
		return new DeclareExecutionTrace(declareModel);
	}

	@SuppressWarnings("rawtypes")
	public Map<String, Class> getDataVariables() {
		return dataVariables;
	}

	public Map<String, String> getGuards() {
		return guards;
	}

	public ExecutionTrace createLogExecutionTrace(ExecutionTrace sigmaT) {
		return new LogExecutionTrace(sigmaT);
	}

	public AssignmentModel getModel() {
		return model;
	}

}
