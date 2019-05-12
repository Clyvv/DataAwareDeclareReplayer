package org.processmining.plugins.dataawaredeclarereplayer;

import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeLiteral;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XTrace;
import org.processmining.models.guards.Expression;
import org.processmining.plugins.DataConformance.Utility;
import org.processmining.plugins.DataConformance.DataAlignment.ControlFlowAlignment;
import org.processmining.plugins.DataConformance.DataAlignment.ControlFlowAlignmentStep;
import org.processmining.plugins.DataConformance.DataAlignment.IControlFlowAlignment;
import org.processmining.plugins.DataConformance.framework.ActivityMatchCosts;
import org.processmining.plugins.DataConformance.framework.ExecutionStep;
import org.processmining.plugins.DataConformance.framework.ExecutionTrace;
import org.processmining.plugins.DataConformance.framework.LogExecutionTrace;
import org.processmining.plugins.DataConformance.framework.ReplayableActivity;
import org.processmining.plugins.DataConformance.framework.VariableMatchCosts;
import org.processmining.plugins.dataawaredeclarereplayer.mapping.StringDiscretizer;

@SuppressWarnings("rawtypes")
public class DataReplayingHelper {
	XTrace trace;
	Map<ReplayableActivity, XEventClass> activityMapping;
	Map<String, String> variableMapping;
	XEventClasses eventClasses;
	DataAwareDeclare model;
	ActivityMatchCosts activityMatchCosts;
	VariableMatchCosts variableMatchCosts;
	Map<String, Float> maxDistance;
	Map<String, Set<String>> variablesToWrite;
	Map<String, Object> lowerBounds;
	Map<String, Object> upperBounds;
	Map<String, Class> variableTypes;
	float minCost;

	public DataReplayingHelper(DataAwareDeclare model, XTrace trace,
			Map<ReplayableActivity, XEventClass> activityMapping, Map<String, String> variableMapping,
			XEventClasses eventClasses, Map<String, Float> maxDistance, ActivityMatchCosts activityCosts,
			VariableMatchCosts variableCosts, Map<String, Set<String>> variablesToWrite,
			Map<String, Object> lowerBounds, Map<String, Object> upperBounds, Map<String, Class> variableTypes,
			StringDiscretizer stringDiscretizer) {
		this.model = model;
		this.trace = trace;
		this.activityMapping = activityMapping;
		this.variableMapping = variableMapping;
		this.eventClasses = eventClasses;
		this.activityMatchCosts = activityCosts;
		this.variableMatchCosts = variableCosts;
		this.maxDistance = maxDistance;
		this.variablesToWrite = variablesToWrite;
		this.lowerBounds = lowerBounds;
		this.upperBounds = upperBounds;
		this.variableTypes = variableTypes;
		SingleTraceDataAlignmentBuilder.setStringDiscretizer(stringDiscretizer);
		this.minCost = Math.min(activityMatchCosts.lowestCost(), variableMatchCosts.lowestCost());
	}

	public ExecutionTrace buildLogSequence() {
		XAttribute attr = trace.getAttributes().get("concept:name");
		String traceName = "";
		if (attr != null)
			traceName = ((XAttributeLiteral) attr).getValue();
		LogExecutionTrace retValue = new LogExecutionTrace(traceName);
		for (XEvent event : trace) {
			ExecutionStep step = getExecutionStep(event);
			if (step != null)
				retValue.add(step);
		}
		return retValue;
	}

	public Collection<DataAlignmentState> createSuccessors(DataAlignmentState mu) {
		List<DataAlignmentState> successors = new LinkedList<DataAlignmentState>();
		//It always includes the prefix extended with a BOTTOM-step.
		Collection<ExecutionTrace> extendedLogPrefixes = mu.logTracePrefix.successors(null);
		for (ExecutionTrace extendedLogPrefix : extendedLogPrefixes) {
			ExecutionStep lastLogStep = extendedLogPrefix.getLast();
			Collection<ExecutionTrace> extendedProcessPrefixes;
			if (lastLogStep.equals(ExecutionStep.bottomStep))
				extendedProcessPrefixes = mu.processTracePrefix.successors(lastLogStep);
			else
				extendedProcessPrefixes = mu.processTracePrefix
						.successors(new ExecutionStep(lastLogStep.getActivity()));

			for (ExecutionTrace extendedProcessPrefix : extendedProcessPrefixes) {
				//Move in the log + Move in the process
				//If lastLogStep==ExecutionStep.tauStep, then it's Not move in the log + Move in the process
				DataAlignmentState state = new DataAlignmentState(extendedLogPrefix, extendedProcessPrefix, 0);
				successors.add(state);
			}

			if (lastLogStep != ExecutionStep.bottomStep) {
				//Move in the log + Not move in the process
				DataAlignmentState state = new DataAlignmentState((ExecutionTrace) extendedLogPrefix.clone(),
						(ExecutionTrace) mu.processTracePrefix.clone(), 0);
				state.processTracePrefix.add(ExecutionStep.bottomStep);
				successors.add(state);
			}
		}
		return successors;
	}

	public float computeHeuristicValue(DataAlignmentState state, ExecutionTrace completeLogTrace) {

		int logEventToReplay = completeLogTrace.size() - state.logTracePrefix.lengthExecutionTraceWithoutBottomSteps();
		return minCost * logEventToReplay;
	}

	public float computeDistance(DataAlignmentState prevState, DataAlignmentState nextState) {
		float lastStepCost = costMatchingLastStep(nextState);
		if (!nextState.logTracePrefix.getLast().equals(ExecutionStep.bottomStep))
			lastStepCost += minCost;
		else if (model.lengthShortestTrace() > 0)
			if (nextState.processTracePrefix.lengthExecutionTraceWithoutBottomSteps() <= model.lengthShortestTrace()) {
				lastStepCost += minCost;
			}
		return lastStepCost;
	}

	private float costMatchingLastStep(DataAlignmentState state) {
		float cost = 0;
		ExecutionStep lastLogStep = state.logTracePrefix.getLast();
		ExecutionStep lastProcessStep = state.processTracePrefix.getLast();

		if (lastLogStep == ExecutionStep.bottomStep)
			return activityMatchCosts.costMoveOnlyInProcess(lastProcessStep.getActivity());
		else if (lastProcessStep == ExecutionStep.bottomStep)
			return activityMatchCosts.costMoveOnlyInLog(lastLogStep.getActivity());

		if (lastLogStep.getActivity().equals(lastProcessStep.getActivity())) {
			String activity = lastLogStep.getActivity();
			//For each variable var written in the log step...
			for (String var : lastLogStep.keySet()) {
				Object logValue = lastLogStep.get(var);
				Object processValue = lastProcessStep.get(var);

				//If var has also been written in the process step, compute the distance between the values 
				if (processValue != null) {
					if (processValue instanceof String) {
						String logValueAsString = String.valueOf(logValue);
						float distance = org.processmining.plugins.DataConformance.Utility
								.stringDistance((String) processValue, logValueAsString);
						if (distance > 0)
							cost += variableMatchCosts.costFaultyValue(activity, var);//maxDistance.get(var)*distance;
					} else if (processValue instanceof Number) {
						float logValueAsFloat;
						if (logValue instanceof String)
							logValueAsFloat = Float.parseFloat((String) logValue);
						else
							logValueAsFloat = ((Number) logValue).floatValue();
						float distance = Math.abs(((Number) processValue).floatValue() - logValueAsFloat);
						if (distance > 0)
							cost += variableMatchCosts.costFaultyValue(activity, var);//maxDistance.get(var)*distance;					
					} else if (!processValue.equals(logValue)) {
						cost += variableMatchCosts.costFaultyValue(activity, var);
					}
				}
				//If var has not been written, then 
				else
					cost += variableMatchCosts.notWritingCost(activity, var);
			}

			for (String var : lastProcessStep.keySet())
				if (lastLogStep.get(var) == null)
					cost += variableMatchCosts.notWritingCost(activity, var);
		}
		return cost;
	}

	protected ExecutionStep getExecutionStep(XEvent event) {
		String stepName = null;

		for (Entry<ReplayableActivity, XEventClass> activityMappingEntry : activityMapping.entrySet()) {
			if (activityMappingEntry.getValue().equals(eventClasses.getClassOf(event))) {
				stepName = activityMappingEntry.getKey().getLabel();
				break;
			}

		}
		if (stepName == null) {
			stepName = "TICK";
		}
		ExecutionStep step = new ExecutionStep(stepName);
		XAttributeMap attributes = event.getAttributes();
		for (Entry<String, XAttribute> attribute : attributes.entrySet()) {
			Object value = Utility.getValue(attribute.getValue());
			if (value != null) {
				for (Entry<String, String> entr : variableMapping.entrySet()) {
					if (attribute.getKey().equals(entr.getValue())) {
						step.put(entr.getKey(), value);
						break;
					}
				}
			}
		}
		return step;

	}

	@SuppressWarnings({ "cast", "deprecation" })
	public DataAlignmentState augment(DataAlignmentState state) throws ParseException {

		IControlFlowAlignment<Object, Object> list = new ControlFlowAlignment("", 0);
		int i = 0;
		ExecutionStep lastLogStep = state.logTracePrefix.getLast();
		if (needsILPSolver(state.logTracePrefix.getLast())) {
			for (ExecutionStep logStep : state.logTracePrefix) {
				Map<String, Object> variableAssignments = new HashMap<>();
				ExecutionStep processStep = (ExecutionStep) state.processTracePrefix.get(i);
				String activityName = "BOTTOM-STEP";
				if (!logStep.equals(ExecutionStep.bottomStep)) {
					activityName = logStep.getActivity();

					for (Entry<String, Object> entr : logStep.entrySet()) {
						variableAssignments.put(entr.getKey(), entr.getValue());
					}
				}
				int moveType = getMoveType(logStep, processStep);
				String guard = null;
				if (!processStep.equals(ExecutionStep.bottomStep)) {
					activityName = processStep.getActivity();

					guard = model.getGuards().get(processStep.getActivity());
				}
				Expression exp = new Expression("false");
				if (guard != null) {
					exp = new Expression(guard);
					if (processStep.isInvisible()) {
						//Negate guard here
						exp = exp.negate();
					}
				}

				Set<String> listVariableToWrite = variablesToWrite.get(activityName);
				if (listVariableToWrite == null) {
					listVariableToWrite = new HashSet<>();
				}
				ControlFlowAlignmentStep alStep = new ControlFlowAlignmentStep(activityName, exp, variableAssignments,
						moveType, listVariableToWrite);
				list.add(alStep);
				i++;
			}
			DataAlignmentState augmented = SingleTraceDataAlignmentBuilder.createAlignment(list, variableMatchCosts,
					variableTypes, upperBounds, lowerBounds);
			i = 0;
			for (ExecutionStep step : state.processTracePrefix) {
				ExecutionStep logStep = (ExecutionStep) state.logTracePrefix.get(i);
				ExecutionStep augProcessStep = (ExecutionStep) augmented.processTracePrefix.get(i);
				int moveType = getMoveType(logStep, augProcessStep);
				if (moveType != ControlFlowAlignmentStep.MOVE_IN_LOG
						&& moveType != ControlFlowAlignmentStep.MOVE_IN_MODEL_VISIBLE) {
					for (Entry<String, Object> v : augProcessStep.entrySet()) {
						if (logStep.get(v.getKey()) instanceof String
								&& !logStep.get(v.getKey()).equals(v.getValue())) {

							step.put(v.getKey(), v.getValue());
						} else {
							step.put(v.getKey(), v.getValue());
						}
					}
				}
				i++;
			}

			state.setF_cost(augmented.getCost());
		} else {
			i = 0;
			for (ExecutionStep step : state.processTracePrefix) {
				ExecutionStep logStep = (ExecutionStep) state.logTracePrefix.get(i);
				if (logStep == lastLogStep) {
					int moveType = getMoveType(logStep, step);
					if (moveType == ControlFlowAlignmentStep.MOVE_IN_BOTH) {
						for (Entry<String, Object> v : logStep.entrySet()) {
							step.put(v.getKey(), v.getValue());
						}
					}
					break;
				}
				i++;
			}
		}
		return state;
	}

	private boolean needsILPSolver(ExecutionStep last) {
		if (!last.equals(ExecutionStep.bottomStep)) {
			if (model.getGuards().get(last.getActivity()) != null) {
				return true;
			}
		}
		return false;
	}

	private int getMoveType(ExecutionStep logStep, ExecutionStep processStep) {
		if (logStep.equals(ExecutionStep.bottomStep))
			return ControlFlowAlignmentStep.MOVE_IN_MODEL_VISIBLE;

		else if (processStep.equals(ExecutionStep.bottomStep))
			return ControlFlowAlignmentStep.MOVE_IN_LOG;

		else
			return ControlFlowAlignmentStep.MOVE_IN_BOTH;
	}

}
