/*
 * Adapted from org.processmining.plugins.DataConformance.DataAlignment.SingleTraceDataAlignmentBuilder
 */
package org.processmining.plugins.dataawaredeclarereplayer;

import java.text.ParseException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.processmining.models.guards.Expression;
import org.processmining.models.guards.SafeEnumeration;
import org.processmining.plugins.DataConformance.DataAlignment.ControlFlowAlignmentStep;
import org.processmining.plugins.DataConformance.DataAlignment.GenericTrace;
import org.processmining.plugins.DataConformance.DataAlignment.IControlFlowAlignment;
import org.processmining.plugins.DataConformance.DataAlignment.IControlFlowAlignmentStep;
import org.processmining.plugins.DataConformance.framework.ExecutionStep;
import org.processmining.plugins.DataConformance.framework.VariableMatchCosts;
import org.processmining.plugins.dataawaredeclarereplayer.mapping.StringDiscretizer;

import lpsolve.LpSolve;
import net.sf.javailp.Linear;
import net.sf.javailp.Problem;
import net.sf.javailp.Result;
import net.sf.javailp.Solver;
import net.sf.javailp.SolverFactory;
import net.sf.javailp.SolverFactoryLpSolve;
import net.sf.javailp.SolverLpSolve;
import net.sf.javailp.SolverLpSolve.Hook;
import net.sf.javailp.Term;
import net.sf.javailp.VarType;

@SuppressWarnings("unchecked")
public class SingleTraceDataAlignmentBuilder {

	private static final char WRITE_CHAR = '\'';
	private static final double EPSILON = 0.5;
	private static final double MAX_VALUE = 10000000.0; // Should not be too big, otherwise introduces errors in lp_solve
	private static final double LP_SOLVE_PRECISION = 0.00000001;
	private static int numBooleanVar = 0;
	private static StringDiscretizer stringDiscretizer;

	static {
		System.loadLibrary("lpsolve55");
		System.loadLibrary("lpsolve55j");
	}

	public static DataAlignmentState createAlignment(IControlFlowAlignment<Object, Object> list,
			VariableMatchCosts variableCost, Map<String, Class> varType, Map<String, Object> upperBounds,
			Map<String, Object> lowerBounds) throws ParseException {
		HashMap<String, Integer> timesWrittenVariable = new HashMap<String, Integer>();
		Problem ilpProblem = new Problem();
		HashMap<IControlFlowAlignmentStep<Object, Object>, Map<String, String>> mappingVariablesToConditionStep = new HashMap<IControlFlowAlignmentStep<Object, Object>, Map<String, String>>();
		Set<String> listVariableToWrite;
		Linear objective = new Linear();
		float additionalCost = 0;
		for (IControlFlowAlignmentStep<Object, Object> step : list) {
			if (step.getMoveType() == ControlFlowAlignmentStep.MOVE_IN_LOG)
				continue;
			listVariableToWrite = step.getVariableToWrite();
			Map<String, String> mappingVariables = new HashMap<String, String>();
			//TODO remove deprecated code
			Expression expr = step.getGuardExpression();
			for (String var : listVariableToWrite) {
				Integer times = timesWrittenVariable.get(var);
				if (times == null)
					times = 1;
				else
					times++;
				timesWrittenVariable.put(var, times);
				ilpProblem.setVarType(var + times, getType(varType.get(var)));
				mappingVariables.put(var + times, var);
				if (upperBounds.get(var) != null)
					ilpProblem.setVarUpperBound(var + times, org.processmining.models.guards.NumericValueConversion
							.toNumericValue(upperBounds.get(var)));
				else if (varType.get(var) == String.class)
					ilpProblem.setVarUpperBound(var + times, stringDiscretizer.getUpperBound());
				if (lowerBounds.get(var) != null)
					ilpProblem.setVarLowerBound(var + times, org.processmining.models.guards.NumericValueConversion
							.toNumericValue(lowerBounds.get(var)));
				else
					ilpProblem.setVarLowerBound(var + times, 0);

			}
			mappingVariablesToConditionStep.put(step, mappingVariables);
			addConditionToProblem(ilpProblem, expr, timesWrittenVariable, listVariableToWrite, null, null);

			boolean invisibleStep = (step.getMoveType() == ControlFlowAlignmentStep.MOVE_IN_MODEL_INVISIBLE);
			additionalCost += addToObjectiveFunction(ilpProblem, step, objective, listVariableToWrite,
					timesWrittenVariable, variableCost, invisibleStep);
		}

		SolverFactory factory = new SolverFactoryLpSolve(); // use lp_solve
		factory.setParameter(Solver.VERBOSE, 0);
		factory.setParameter(Solver.TIMEOUT, 20);

		ilpProblem.setObjective(objective);

		SolverLpSolve solver = (SolverLpSolve) factory.get();
		solver.addHook(new Hook() {

			public void call(LpSolve lpSolve, Map<Object, Integer> varToIndex) {
				lpSolve.setEpsint(LP_SOLVE_PRECISION);
			}
		});
		System.out.println(ilpProblem);
		Result result = solver.solve(ilpProblem);
		//System.out.println(ilpProblem);
		//System.out.println(result);
		float f_cost;

		if (result == null && ilpProblem.getObjective().size() == 0) {
			f_cost = 0.0f;
		} else if (result == null) {
			f_cost = Float.MAX_VALUE;
		} else
			f_cost = (float) (result.getObjective().doubleValue() + additionalCost);

		Entry<GenericTrace, GenericTrace> processAndLogTrace = createTraces(ilpProblem, result, list,
				mappingVariablesToConditionStep, varType);

		return new DataAlignmentState(processAndLogTrace.getValue(), processAndLogTrace.getKey(), f_cost);

	}

	@SuppressWarnings("fallthrough")
	private static Entry<GenericTrace, GenericTrace> createTraces(Problem ilpProblem, Result result,
			IControlFlowAlignment<Object, Object> list,
			Map<IControlFlowAlignmentStep<Object, Object>, Map<String, String>> mappingVariablesToConditionStep,
			Map<String, Class> varType) {
		final GenericTrace logTrace = new GenericTrace(list.size(), list.getTraceName());
		final GenericTrace processTrace = new GenericTrace(list.size(), list.getTraceName());
		Map<String, String> mappingVariables;
		for (IControlFlowAlignmentStep<Object, Object> step : list) {
			ExecutionStep logStep = ExecutionStep.bottomStep;
			ExecutionStep processStep = ExecutionStep.bottomStep;
			switch (step.getMoveType()) {
				case ControlFlowAlignmentStep.MOVE_IN_LOG :
					logStep = new ExecutionStep(step.getActivityName());
					logStep.putAll(step.getVariableAssignments());
					break;
				case ControlFlowAlignmentStep.MOVE_IN_BOTH :
					logStep = new ExecutionStep(step.getActivityName());
					logStep.putAll(step.getVariableAssignments());
				case ControlFlowAlignmentStep.MOVE_IN_MODEL_VISIBLE :
				case ControlFlowAlignmentStep.MOVE_IN_MODEL_INVISIBLE :
					processStep = new ExecutionStep(step.getActivityName());
					if (step.getMoveType() == ControlFlowAlignmentStep.MOVE_IN_MODEL_INVISIBLE)
						processStep.setInvisible(true);
					mappingVariables = mappingVariablesToConditionStep.get(step);
					for (Entry<String, String> entry : mappingVariables.entrySet()) {
						String ilpVariable = entry.getKey();
						String processVariable = entry.getValue();
						Number processValue = null;
						if (result != null)
							processValue = result.get(ilpVariable);
						if (processValue == null)
							processValue = ilpProblem.getVarLowerBound(ilpVariable);
						if (processValue == null)
							processValue = ilpProblem.getVarUpperBound(ilpVariable);
						if (processValue == null)
							processValue = new Random().nextInt(10000);
						double logValue = 0;
						if (logStep.containsKey(processVariable)) {
							

							if (logStep.get(processVariable) instanceof String) {
								logValue = new Double(
										stringDiscretizer.convertToInt(logStep.get(processVariable).toString()))
												.doubleValue();
							} else {
								logValue = org.processmining.models.guards.NumericValueConversion
										.toNumericValue(logStep.get(processVariable));
							}
							if (Math.abs(logValue - processValue.doubleValue()) < EPSILON)
								processValue = logValue;
						}
						if (varType.get(processVariable) == String.class) {
							processStep.put(processVariable,
									stringDiscretizer.convertToString(processValue.intValue(), (int)logValue));
						} else {
							processStep.put(processVariable, org.processmining.models.guards.NumericValueConversion
									.fromNumericValue(processValue.doubleValue(), varType.get(processVariable)));
						}
					}
					break;
				default :
					assert (false);
			}
			logTrace.add(logStep);
			processTrace.add(processStep);
		}
		return new Entry<GenericTrace, GenericTrace>() {
			public GenericTrace setValue(GenericTrace value) {
				return null;
			}

			public GenericTrace getValue() {
				return logTrace;
			}

			public GenericTrace getKey() {
				return processTrace;
			}
		};
	}

	private static float addToObjectiveFunction(Problem problem, IControlFlowAlignmentStep<Object, Object> step,
			Linear objective, Set<String> listVariableToWrite, Map<String, Integer> timesWrittenVariable,
			VariableMatchCosts variableCost, boolean invisibleStep) {
		float additionalCost = 0;
		for (String varName : listVariableToWrite) {
			Object attrValue = step.getVariableAssignments().get(varName);
			String ilpVarName = varName + timesWrittenVariable.get(varName);
			if (attrValue != null) {
				String boolVar = ilpVarName + "_D";
				problem.setVarType(boolVar, Boolean.class);
				double value;
				if (attrValue instanceof String) {
					value = new Double(stringDiscretizer.convertToInt(attrValue.toString()));
				} else {
					value = org.processmining.models.guards.NumericValueConversion.toNumericValue(attrValue);
				}
				double upperbound;
				if (problem.getVarType(ilpVarName) == VarType.BOOL)
					upperbound = 1;
				else if (problem.getVarUpperBound(ilpVarName) != null)
					upperbound = problem.getVarUpperBound(ilpVarName).doubleValue();
				else if (problem.getVarType(ilpVarName) == VarType.INT)
					upperbound = MAX_VALUE;
				else
					upperbound = MAX_VALUE;
				Linear constraint = new Linear();
				constraint.add(1, ilpVarName);
				constraint.add(-1.0 * upperbound, boolVar);
				problem.add(constraint, "<=", value);
				constraint = new Linear();
				constraint.add(-1, ilpVarName);
				constraint.add(-1.0 * upperbound, boolVar);
				problem.add(constraint, "<=", -1.0 * value);
				objective.add(variableCost.costFaultyValue(step.getActivityName(), varName), boolVar);
			} else if (!invisibleStep)
				additionalCost += variableCost.notWritingCost(step.getActivityName(), varName);
		}
		return additionalCost;
	}

	private static Class getType(Class aClass) {
		if (aClass == String.class)
			return Double.class;
		if (aClass == java.util.Date.class || aClass == Float.class)
			return Double.class;
		if (aClass == Long.class)
			return Integer.class;
		return aClass;
	}

	@SuppressWarnings({ "deprecation" })
	private static void addConditionToProblem(Problem ilpProblem, Expression expr,
			HashMap<String, Integer> timesWrittenVariable, Set<String> listVariableToWrite, String variableForOR,
			Set<String> variablesForOR) throws ParseException {
		if (expr == null)
			return;
		if (expr.getOperator() == null)
			return;

		if (expr.getOperand2() == null && expr.getOperand1() instanceof Expression) {
			addConditionToProblem(ilpProblem, (Expression) expr.getOperand1(), timesWrittenVariable,
					listVariableToWrite, variableForOR, variablesForOR);
			return;
		}
		if (expr.getOperand1() == null && expr.getOperand2() instanceof Expression) {

			addConditionToProblem(ilpProblem, (Expression) expr.getOperand2(), timesWrittenVariable,
					listVariableToWrite, variableForOR, variablesForOR);
			return;
		}

		if (expr.getOperator().equals("&&")) {
			addConditionToProblem(ilpProblem, (Expression) expr.getOperand1(), timesWrittenVariable,
					listVariableToWrite, variableForOR, variablesForOR);
			addConditionToProblem(ilpProblem, (Expression) expr.getOperand2(), timesWrittenVariable,
					listVariableToWrite, variableForOR, variablesForOR);
			return;
		}
		if (expr.getOperator().equals("!=") || expr.getOperator().equals("<>")) {
			Expression firstTerm = new Expression(expr.getOperand1(), "<", expr.getOperand2());
			Expression secondTerm = new Expression(expr.getOperand1(), ">", expr.getOperand2());
			expr = new Expression(firstTerm, "||", secondTerm);
		}
		if (expr.getOperator().equals("||")) {
			String variableForOR1, variableForOR2;
			if (variableForOR == null) {
				variablesForOR = new HashSet<String>();
				variableForOR1 = "OR" + (++numBooleanVar);
				variablesForOR.add(variableForOR1);
			} else
				variableForOR1 = variableForOR;
			variableForOR2 = "OR" + (++numBooleanVar);
			variablesForOR.add(variableForOR2);
			addConditionToProblem(ilpProblem, (Expression) expr.getOperand1(), timesWrittenVariable,
					listVariableToWrite, variableForOR1, variablesForOR);
			addConditionToProblem(ilpProblem, (Expression) expr.getOperand2(), timesWrittenVariable,
					listVariableToWrite, variableForOR2, variablesForOR);
			if (variableForOR == null) {
				Linear constraintForOR = new Linear();
				for (String variable : variablesForOR) {
					ilpProblem.setVarType(variable, VarType.BOOL);
					constraintForOR.add(1, variable);
				}
				ilpProblem.add(constraintForOR, "<=", variablesForOR.size() - 1);
			}
			return;
		}
		Hashtable linearForm = generateLinearForm(expr);
		if (linearForm == null)
			throw new ParseException("The expression is neither parseable nor linear", -1);
		Iterator<Entry> variables = linearForm.entrySet().iterator();
		Linear constraint = new Linear();
		String operator = null;
		Double constant = 0D;
		while (variables.hasNext()) {
			Entry nextEntry = variables.next();
			String nextVar = (String) nextEntry.getKey();
			Object nextVarValue = nextEntry.getValue();
			if (nextVar.equals("OPERATOR")) {
				operator = (String) nextEntry.getValue();
				continue;
			}
			if (nextVar.equals("CONSTANT")) {
				constant = ((Number) nextEntry.getValue()).doubleValue();
				continue;
			}
			if (nextVar.contains(WRITE_CHAR + "")) {
				nextVar = nextVar.replaceAll(WRITE_CHAR + "", " ").trim();
				nextVar += timesWrittenVariable.get(nextVar);
			} else {
				//				if (listVariableToWrite.contains(nextVar))
				//					nextVar+=timesWrittenVariable.get(nextVar)-1;	
				//				else
				nextVar += timesWrittenVariable.get(nextVar);
			}
			if (nextVarValue instanceof String) {
				constraint.add(new Double(stringDiscretizer.convertToInt(nextVarValue.toString())), nextVar);
			} else
				constraint.add((Number) nextVarValue, nextVar);

		}

		operator = operator.trim();
		if (operator.equals("=="))
			operator = "=";
		else if (operator.equals("<") || operator.equals(">")) {
			if (operator.equals("<"))
				constant += EPSILON;
			else
				constant -= EPSILON;
			operator += "=";
		}
		if (variableForOR != null) {
			if (operator.equals("=")) {
				Linear secondConstraint = clone(constraint);
				operator = "<=";
				constraint.add(-1 * MAX_VALUE, variableForOR);

				secondConstraint.add(+1 * MAX_VALUE, variableForOR);
				ilpProblem.add(secondConstraint, ">=", constant * (-1.0));
			} else {
				if (operator.equals("<="))
					constraint.add(-1 * MAX_VALUE, variableForOR);
				else if (operator.equals(">="))
					constraint.add(+1 * MAX_VALUE, variableForOR);
			}
		}
		ilpProblem.add(constraint, operator, constant * (-1.0));
	}

	private static Linear clone(Linear constraint) {
		Linear retValue = new Linear();
		for (Term term : constraint) {
			retValue.add(term.getCoefficient().doubleValue(), term.getVariable());
		}
		return retValue;
	}

	public static void setStringDiscretizer(StringDiscretizer discretizer) {
		stringDiscretizer = discretizer;
	}

	private static Hashtable generateLinearForm(Expression expr) {
		Hashtable atable, btable;

		// Checks
		if (expr.isBooleanExpression())
			return null;
		if ((expr.getOperand1() == null) || (expr.getOperand2() == null))
			return null;

		// Op 1
		if (expr.getOperand1() instanceof Expression) {
			atable = generateLinearForm((Expression) expr.getOperand1());
			if (atable == null)
				return null;
		} else {
			Object ao = simplifyOperand(expr.getOperand1());
			if (ao == null)
				return null;
			atable = new Hashtable();
			if (ao instanceof String) {
				atable.put(ao, new Double(1));
			} else if (ao instanceof Double) {
				atable.put("CONSTANT", ao);
			} else
				return null;
		}

		// Op 2
		if (expr.getOperand2() instanceof Expression) {
			btable = generateLinearForm((Expression) expr.getOperand2());
			if (btable == null)
				return null;
		} else {
			Object bo = simplifyOperand(expr.getOperand2());
			if (bo == null)
				return null;
			btable = new Hashtable();
			if (bo instanceof String) {
				btable.put(bo, new Double(1));
			} else if (bo instanceof Double) {
				btable.put("CONSTANT", bo);
			} else
				return null;
		}

		// Apply operator
		if (expr.getOperator().equals("+")) {
			Enumeration e = btable.keys();
			while (e.hasMoreElements()) {
				String key = (String) e.nextElement();
				if (atable.containsKey(key)) {
					Double ad = (Double) atable.get(key);
					Double bd = (Double) btable.get(key);
					Double rd = new Double(ad.doubleValue() + bd.doubleValue());
					atable.put(key, rd);
				} else {
					atable.put(key, btable.get(key));
				}
			}

		} else if (expr.getOperator().equals("-") || expr.isComparisonExpression()) {
			Enumeration e = btable.keys();
			while (e.hasMoreElements()) {
				String key = (String) e.nextElement();
				if (atable.containsKey(key)) {
					Double ad = (Double) atable.get(key);
					Double bd = (Double) btable.get(key);
					Double rd = new Double(ad.doubleValue() - bd.doubleValue());
					atable.put(key, rd);
				} else {
					Double bd = (Double) btable.get(key);
					Double rd = new Double(-1 * bd.doubleValue());
					atable.put(key, rd);
				}
			}

		} else if (expr.getOperator().equals("*") || expr.getOperator().equals("/")) {
			if ((btable.size() == 1) && btable.containsKey("CONSTANT")) {
				Double bd = (Double) btable.get("CONSTANT");
				Enumeration e = new SafeEnumeration(atable.keys());
				while (e.hasMoreElements()) {
					String key = (String) e.nextElement();
					Double ad = (Double) atable.get(key);
					Double rd;
					if (expr.getOperator().equals("*"))
						rd = new Double(ad.doubleValue() * bd.doubleValue());
					else
						rd = new Double(ad.doubleValue() / bd.doubleValue());
					atable.put(key, rd);
				}
			} else if ((atable.size() == 1) && atable.containsKey("CONSTANT")) {
				Double ad = (Double) atable.get("CONSTANT");
				Enumeration e = new SafeEnumeration(btable.keys());
				while (e.hasMoreElements()) {
					String key = (String) e.nextElement();
					Double bd = (Double) btable.get(key);
					Double rd;
					if (expr.getOperator().equals("*"))
						rd = new Double(ad.doubleValue() * bd.doubleValue());
					else
						rd = new Double(ad.doubleValue() / bd.doubleValue());
					btable.put(key, rd);
				}
				atable = btable;
			} else
				return null;

		} else if (expr.getOperator().equals("%")) {
			return null;
		}

		// Finally...
		if (expr.isComparisonExpression()) {
			atable.put("OPERATOR", expr.getOperator());
		}

		return atable;
	}

	private static Object simplifyOperand(Object o) {
		Object r = null;
		boolean var = false;
		if (o instanceof String) {
			String s = (String) o;
			if (s.charAt(0) == '"') {
				r = s.substring(1, s.length() - 1);
			} else if (s.equalsIgnoreCase("null")) {
				r = "null";
			} else {
				r = s;
				var = true;
			}

			if (r == null)
				r = "null";

			if (var == false)
				r = new Double(stringDiscretizer.convertToInt((String) r));

		} else if (o instanceof Number) {
			r = new Double(((Number) o).doubleValue());
		} else if (o instanceof Boolean) {
			r = ((Boolean) o).booleanValue() ? 1.0 : 0.0;
		}

		return r;
	}
}
