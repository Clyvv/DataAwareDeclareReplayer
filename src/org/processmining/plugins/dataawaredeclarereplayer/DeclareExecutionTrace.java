package org.processmining.plugins.dataawaredeclarereplayer;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.processmining.ltl2automaton.plugins.automaton.Automaton;
import org.processmining.ltl2automaton.plugins.automaton.Transition;
import org.processmining.ltl2automaton.plugins.formula.DefaultParser;
import org.processmining.ltl2automaton.plugins.formula.Formula;
import org.processmining.ltl2automaton.plugins.formula.conjunction.ConjunctionFactory;
import org.processmining.ltl2automaton.plugins.formula.conjunction.ConjunctionTreeLeaf;
import org.processmining.ltl2automaton.plugins.formula.conjunction.ConjunctionTreeNode;
import org.processmining.ltl2automaton.plugins.formula.conjunction.DefaultTreeFactory;
import org.processmining.ltl2automaton.plugins.formula.conjunction.GroupedTreeConjunction;
import org.processmining.ltl2automaton.plugins.formula.conjunction.TreeFactory;
import org.processmining.plugins.DataConformance.framework.ExecutionStep;
import org.processmining.plugins.DataConformance.framework.ExecutionTrace;
import org.processmining.plugins.dataawaredeclarereplayer.automata.ExecutableAutomaton;
import org.processmining.plugins.dataawaredeclarereplayer.automata.PossibleNodes;
import org.processmining.plugins.dataawaredeclarereplayer.utils.Utils;
import org.processmining.plugins.declareminer.visualizing.ActivityDefinition;
import org.processmining.plugins.declareminer.visualizing.AssignmentModel;
import org.processmining.plugins.declareminer.visualizing.ConstraintDefinition;
import org.processmining.plugins.declareminer.visualizing.DeclareMap;
import org.processmining.plugins.declareminer.visualizing.Parameter;

public class DeclareExecutionTrace extends ExecutionTrace {
	/**
	 * 
	 */
	private static final long serialVersionUID = 633458989512676589L;
	public static ExecutionStep tickStep = new ExecutionStep("TICK");
	private Automaton[] aut;
	private ExecutableAutomaton[] execAut;
	private DeclareMap model;
	
	private boolean added;

	@Override
	public int hashCode() {
		int hash = 9; // arbitrary seed value
		int multiplier = 13; // arbitrary multiplier value
		for (int i = 0; i < execAut.length; i++) {
			hash = hash * multiplier + execAut[i].currentState().get(0).getId();
		}
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj.getClass() != this.getClass())
			return false;
		DeclareExecutionTrace other = (DeclareExecutionTrace) obj;
		int stateId, otherStateId;
		for (int i = 0; i < execAut.length; i++) {
			stateId = execAut[i].currentState().get(0).getId();
			otherStateId = other.execAut[i].currentState().get(0).getId();
			if (stateId != otherStateId)
				return false;
		}
		return (true);
	}

	public DeclareExecutionTrace(DeclareMap model) {
		super(1);
		try {
			this.model = model;
			AssignmentModel amodel = model.getModel();
			String[] constraints = new String[amodel.constraintDefinitionsCount()];
			String[] forms = new String[amodel.constraintDefinitionsCount()];
			int i = 0;
			aut = new Automaton[amodel.getConstraintDefinitions().size()];
			execAut = new ExecutableAutomaton[amodel.getConstraintDefinitions().size()];
			//	String formula = "";
			for (ConstraintDefinition cd : amodel.getConstraintDefinitions()) {
				constraints[i] = cd.getName();

				int count = 1;
				constraints[i] = constraints[i] + "(";
				for (Parameter p : cd.getParameters()) {
					if (count < cd.parameterCount()) {
						int countB = 1;
						constraints[i] = constraints[i] + "[";
						if (cd.getBranches(p).size() == 0) {
							constraints[i] = constraints[i] + "EMPTY_PARAM" + "\"]";
						}
						for (ActivityDefinition b : cd.getBranches(p)) {
							String bname = b.getName();
							if (bname == null || bname == null || bname.equals("")) {
								bname = "EMPTY_PARAM";
							}
							if (countB < cd.branchesCount(p)) {
								constraints[i] = constraints[i] + bname + ",";
							} else {
								constraints[i] = constraints[i] + bname + "],";
							}
							countB++;
						}
					} else {
						int countB = 1;
						constraints[i] = constraints[i] + "[";
						if (cd.getBranches(p).size() == 0) {
							constraints[i] = constraints[i] + "EMPTY_PARAM" + "\"]";
						}
						for (ActivityDefinition b : cd.getBranches(p)) {
							String bname = b.getName();
							if (bname == null || bname.equals("")) {
								bname = "EMPTY_PARAM";
							}
							if (countB < cd.branchesCount(p)) {
								constraints[i] = constraints[i] + bname + ",";
							} else {
								constraints[i] = constraints[i] + bname + "])";
							}
							countB++;
						}
					}
					count++;
				}
				String currentF = cd.getText();

				if (cd.getName().equals("absence")) {
					currentF = "!( <> ( \"A\" ) )";
				}
				if (cd.getName().equals("absence2")) {
					currentF = "! ( <> ( ( \"A\" /\\ X(<>(\"A\")) ) ) )";
				}
				if (cd.getName().equals("absence3")) {
					currentF = "! ( <> ( ( \"A\" /\\  X ( <> ((\"A\" /\\  X ( <> ( \"A\" ) )) ) ) ) ))";
				}

				if (cd.getName().equals("alternate precedence")) {
					currentF = "(((( !(\"B\") U \"A\") \\/ []( !(\"B\"))) /\\ []((\"B\" ->( (!(X(\"A\")) /\\ !(X(!(\"A\"))) ) \\/ X((( !(\"B\") U \"A\") \\/ []( !(\"B\")))))))) /\\ !(\"B\"))";
				}
				if (cd.getName().equals("alternate response")) {
					currentF = "( []( ( \"A\" -> X(( (! ( \"A\" )) U \"B\" ) )) ) )";
				}
				if (cd.getName().equals("alternate succession")) {
					currentF = "( []((\"A\" -> X(( !(\"A\") U \"B\")))) /\\ (((( !(\"B\") U \"A\") \\/ []( !(\"B\"))) /\\ []((\"B\" ->( (!(X(\"A\")) /\\ !(X(!(\"A\"))) ) \\/ X((( !(\"B\") U \"A\") \\/ []( !(\"B\")))))))) /\\ !(\"B\")))";
				}
				if (cd.getName().equals("chain precedence")) {
					currentF = "[]( ( X( \"B\" ) -> \"A\") )/\\ ! (\"B\" )";
				}
				if (cd.getName().equals("chain response")) {
					currentF = "[] ( ( \"A\" -> X( \"B\" ) ) )";
				}
				if (cd.getName().equals("chain succession")) {
					currentF = "([]( ( \"A\" -> X( \"B\" ) ) )) /\\ ([]( ( X( \"B\" ) ->  \"A\") ) /\\ ! (\"B\" ))";
				}
				if (cd.getName().contains("of")) {
					currentF = "(  <> ( \"A\" ) \\/ <>( \"B\" )  )";
				}
				if (cd.getName().equals("co-existence")) {
					currentF = "( ( <>(\"A\") -> <>( \"B\" ) ) /\\ ( <>(\"B\") -> <>( \"A\" ) )  )";
				}
				if (cd.getName().equals("exactly1")) {
					currentF = "(  <> (\"A\") /\\ ! ( <> ( ( \"A\" /\\ X(<>(\"A\")) ) ) ) )";
				}

				if (cd.getName().equals("exactly2")) {
					currentF = "( <> (\"A\" /\\ (\"A\" -> (X(<>(\"A\"))))) /\\  ! ( <>( \"A\" /\\ (\"A\" -> X( <>( \"A\" /\\ (\"A\" -> X ( <> ( \"A\" ) ))) ) ) ) ) )";
				}
				if (cd.getName().equals("exclusive choice")) {
					currentF = "(  ( <>( \"A\" ) \\/ <>( \"B\" )  )  /\\ !( (  <>( \"A\" ) /\\ <>( \"B\" ) ) ) )";
				}
				if (cd.getName().equals("existence")) {
					currentF = "( <> ( \"A\" ) )";
				}
				if (cd.getName().equals("existence2")) {
					currentF = "<> ( ( \"A\" /\\ X(<>(\"A\")) ) )";
				}
				if (cd.getName().equals("existence3")) {
					currentF = "<>( \"A\" /\\ X(  <>( \"A\" /\\ X( <> \"A\" )) ))";
				}
				if (cd.getName().equals("strong init")) {
					currentF = "( \"A\" )";
				}
				if (cd.getName().equals("init")) {
					currentF = "( \"A\" )";
				}
				if (cd.getName().equals("not chain succession")) {
					currentF = "[]( ( \"A\" -> !(X( \"B\" ) ) ))";
				}
				if (cd.getName().equals("not co-existence")) {
					currentF = "(<>(\"A\")) -> (!(<>( \"B\" )))";
				}
				if (cd.getName().equals("not succession")) {
					currentF = "[]( ( \"A\" -> !(<>( \"B\" ) ) ))";
				}
				if (cd.getName().equals("precedence")) {
					currentF = "( ! (\"B\" ) U \"A\" ) \\/ ([](!(\"B\"))) /\\ ! (\"B\" )";
				}
				if (cd.getName().equals("responded existence")) {
					currentF = "(( ( <>( \"A\" ) -> (<>( \"B\" ) )) ))";
				}
				if (cd.getName().equals("response")) {
					currentF = "( []( ( \"A\" -> <>( \"B\" ) ) ))";
				}
				if (cd.getName().equals("succession")) {
					currentF = "(( []( ( \"A\" -> <>( \"B\" ) ) ))) /\\ (( ! (\"B\" ) U \"A\" ) \\/ ([](!(\"B\"))) /\\ ! (\"B\" ))";
				}
				for (Parameter p : cd.getParameters()) {
					int countB = 1;
					String actualParameter = "(\"";

					if (cd.getBranches(p).size() == 0) {
						actualParameter = actualParameter + "EMPTY_PARAM" + "\")";
					}
					for (ActivityDefinition b : cd.getBranches(p)) {
						String bname = b.getName();
						if (bname == null || bname.equals("")) {
							bname = "EMPTY_PARAM";
						}
						if (countB < cd.branchesCount(p)) {
							actualParameter = actualParameter + bname + "\"||\"";
						} else {
							actualParameter = actualParameter + bname + "\")";
						}
						countB++;
					}
					if( p.getName().equals("B")) {
						currentF = currentF.replace("<>( \"" + p.getName() + "\" )", "<>( " + actualParameter + " )");
					}else {
						currentF = currentF.replace("\"" + p.getName() + "\"", actualParameter);
					}
				}
				currentF = currentF.replace("/\\ event==COMPLETE", "");
				currentF = currentF.replace("/\\ event==complete", "");
				currentF = currentF.replace("activity==", "");
				currentF = currentF.replace("_O", "X");
				currentF = currentF.replace("U_", "U");
				forms[i] = currentF;
				//	if (i != (forms.length - 1)) {
				//		formula = formula + currentF + ")&&(";
				//	} else {
				//		formula = formula + currentF + ")";
				//	}
				List<Formula> formulaeParsed = new ArrayList<Formula>();
				formulaeParsed.add(new DefaultParser(forms[i]).parse());
				TreeFactory<ConjunctionTreeNode, ConjunctionTreeLeaf> treeFactory = DefaultTreeFactory.getInstance();
				ConjunctionFactory<? extends GroupedTreeConjunction> conjunctionFactory = GroupedTreeConjunction
						.getFactory(treeFactory);
				GroupedTreeConjunction conjunction = conjunctionFactory.instance(formulaeParsed);
				aut[i] = conjunction.getAutomaton().op.reduce();
				execAut[i] = new ExecutableAutomaton(aut[i]);
				execAut[i].ini();
				i++;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private DeclareExecutionTrace(int initialCapacity) {
		super(initialCapacity);
	}

	public Object clone() {
		DeclareExecutionTrace clone = new DeclareExecutionTrace(size() + 1);
		clone.addAll(this, numberOfBottomSteps);
		clone.aut = aut;
		clone.model = model;
		clone.execAut = execAut;
		return clone;
	}

	public boolean isComplete() {
		try {
			boolean accepting = true;
			for (int i = 0; i < aut.length; i++) {
				if (!execAut[i].currentState().isAccepting()) {
					accepting = false;
					break;
				}
			}
			return accepting;
		} catch (NoSuchElementException e) {
			return false;
		}
	}

	private ExecutableAutomaton[] cloneAutomata(ExecutableAutomaton[] automata) {
		ExecutableAutomaton[] execAut = new ExecutableAutomaton[model.getModel().getConstraintDefinitions().size()];
		for (int j = 0; j < aut.length; j++) {
			execAut[j] = new ExecutableAutomaton(aut[j]);
			execAut[j].ini();
			for (ExecutionStep step : this)
				if (!step.equals(ExecutionStep.bottomStep))
					execAut[j].next(step.getActivity(), step.isInvisible());
		}
		return execAut;
	}

	public List<ExecutionTrace> successors(ExecutionStep logStep) {
		ArrayList<ExecutionTrace> retValue = new ArrayList<ExecutionTrace>();
		DeclareExecutionTrace execTr = null;
		String label = null;
		
		if (!logStep.equals(ExecutionStep.bottomStep)) {
			label = logStep.getActivity();
			boolean violated = true;
			PossibleNodes current = null;
			for (int i = 0; i < aut.length; i++) {
				violated = true;
				current = execAut[i].currentState();
				for (Transition out : current.output()) {
					if (out.parses(label)) {
						violated = false;
						break;
					}
				}
				if (violated) {
					if (current.get(0).getId() == 0 && isActivationActivity(label)) {
						execTr = (DeclareExecutionTrace) clone();
						execTr.execAut = cloneAutomata(execTr.execAut);
						ExecutionStep step = new ExecutionStep(label);
						step.setInvisible(true);
						execTr.add(step);
						for (int j = 0; j < aut.length; j++) {
							execTr.execAut[j].next(label, true);
						}
						added = true;
						retValue.add(execTr);
						break;
					} else {
						break;
					}

				}
			}
			if (!violated) {
				execTr = (DeclareExecutionTrace) clone();
				execTr.execAut = cloneAutomata(execTr.execAut);
				execTr.add(logStep);
				for (int i = 0; i < aut.length; i++) {
					execTr.execAut[i].next(label);
				}
				added = true;
				retValue.add(execTr);
				if (current.get(0).getId() == 0 && isActivationActivity(label)) {
					execTr = (DeclareExecutionTrace) clone();
					execTr.execAut = cloneAutomata(execTr.execAut);
					ExecutionStep step = new ExecutionStep(label);
					step.setInvisible(true);
					execTr.add(step);
					for (int j = 0; j < aut.length; j++) {
						execTr.execAut[j].next(label, true);
					}
					added = true;
					retValue.add(execTr);
				}
			}
		} else {
			boolean violated = true;
			PossibleNodes current = null;
			added = false;
			for (ActivityDefinition ad : model.getModel().getActivityDefinitions()) {
				if (ad.getName() != null && ad.getName().length() > 0) {
					label = ad.getName();
					violated = true;
					current = null;
					for (int i = 0; i < aut.length; i++) {
						violated = true;
						current = execAut[i].currentState();
						for (Transition out : current.output()) {
							if (out.parses(label)) {
								violated = false;
								break;
							}
						}
						if (violated) {
							break;
						}
					}
					if (!violated) {
						execTr = (DeclareExecutionTrace) clone();
						execTr.execAut = cloneAutomata(execTr.execAut);
						execTr.add(new ExecutionStep(label));
						for (int i = 0; i < aut.length; i++) {
							execTr.execAut[i].next(label);
						}
						if(stateChanged(current, label) || !added) {
							retValue.add(execTr);
							if(!added) 
								added = true;
							
						}
					}
				}
			}
		}
		return retValue;
	}
	
	private boolean stateChanged(PossibleNodes current, String label) {
		boolean result = false;
		DeclareExecutionTrace execTr = (DeclareExecutionTrace) clone();
		execTr.execAut = cloneAutomata(execTr.execAut);
		for (int i = 0; i < aut.length; i++) {
			PossibleNodes newState = execTr.execAut[i].next(label);
			if(!current.equals(newState)) {
				result = true;
				break;
			}
		}
		return result;
	}

	private boolean isActivationActivity(String activity) {
		for (ConstraintDefinition cd : model.getModel().getConstraintDefinitions()) {
			String condition = Utils.getConditionString(cd.getCondition());
			if (cd.getFirstBranch(cd.getFirstParameter()).getName().equals(activity) && condition !=null && condition.length()>0) {
				return true;
			}
		}
		return false;

	}

	public String getTraceName() {
		return null;
	}
}
