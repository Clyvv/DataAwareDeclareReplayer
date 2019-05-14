/*
 * Adapted from org.processmining.plugins.balancedconformance.mapping.LogMapping
 */
package org.processmining.plugins.dataawaredeclarereplayer.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.datapetrinets.expression.GuardExpression;
import org.processmining.datapetrinets.expression.syntax.ParseException;
import org.processmining.log.utils.XUtils;
import org.processmining.plugins.DeclareConformance.ReplayableActivityDefinition;
import org.processmining.plugins.balancedconformance.dataflow.exception.DataAlignmentException;
import org.processmining.plugins.balancedconformance.mapping.StringDiscretizerException;
import org.processmining.plugins.dataawaredeclarereplayer.DataAwareConformanceConfiguration;
import org.processmining.plugins.dataawaredeclarereplayer.DataAwareDeclare;
import org.processmining.plugins.dataawaredeclarereplayer.functions.VirtualVariableAwareWriteOperationCostWrapper;
import org.processmining.plugins.declareminer.visualizing.ActivityDefinition;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

public class LogMapping {

	private final Map<String, Variable> variables = new HashMap<>();
	private final SetMultimap<String, Variable> reverseVariableMapping;
	private final VariableAwareWriteOperationCost variableCost;

	private final Map<ReplayableActivityDefinition, String> transitionToName;
	private final SetMultimap<String, ReplayableActivityDefinition> nameToTransition;
	private final SetMultimap<String, ReplayableActivityDefinition> eventIdentityToTransition;

	private final Map<ReplayableActivityDefinition, Boolean> hasWriteOps;
	private final Map<ReplayableActivityDefinition, LinkedHashSet<String>> variablesToWrite;

	private final TransEvClassMapping transEvClassMapping;
	private final XEventClassifier eventClassifier;
	private final XEventClasses eventClasses;
	private final Map<XEventClass, Integer> eventClass2Cost;
	private final Map<ReplayableActivityDefinition, Integer> transition2Cost;

	private final StringDiscretizer stringDiscretizer;
	private boolean hasVirtualVariables = false;

	// dummy event class (for unmapped transitions)
	public final static XEventClass TICK = new XEventClass("TICK", -1) {

		public boolean equals(Object o) {
			return this == o;
		}

		public int hashCode() {
			return System.identityHashCode(this);
		}

	};

	public enum UnassignedMode {
		NULL, DEFAULT, FREE
	}

	private UnassignedMode unassignedMode = UnassignedMode.FREE;

	public static Map<XEventClass, Integer> createDefaultLogMoveCost(XEventClasses eventClasses,
			float defaultMoveOnLogCost) {
		Map<XEventClass, Integer> mapEvClass2Cost = new HashMap<>();
		for (XEventClass eventClass : eventClasses.getClasses()) {
			mapEvClass2Cost.put(eventClass, (int) defaultMoveOnLogCost);
		}
		mapEvClass2Cost.put(TICK, 0);
		return mapEvClass2Cost;
	}

	public static Map<ReplayableActivityDefinition, Integer> createDefaultModelMoveCost(DataAwareDeclare model,
			float defaultMoveOnModelCost) {
		Map<ReplayableActivityDefinition, Integer> mapTrans2Cost = new HashMap<>();
		for (ActivityDefinition a : model.getModel().getActivityDefinitions()) {
			if (a.getName() != null && a.getName().length() > 0)
				mapTrans2Cost.put(new ReplayableActivityDefinition(a.getName()), (int) defaultMoveOnModelCost);
		}
		return mapTrans2Cost;
	}

	public LogMapping(DataAwareConformanceConfiguration conf, DataAwareDeclare model, XLog log)
			throws ParseException, DataAlignmentException {
		XLogInfo summary = XLogInfoFactory.createLogInfo(log);
		XEventClasses eventClasses = summary.getEventClasses();
		this.eventClass2Cost = createDefaultLogMoveCost(eventClasses, conf.getActivityCost().costMoveOnlyInLog(""));
		this.transition2Cost = createDefaultModelMoveCost(model, conf.getActivityCost().costMoveOnlyInProcess(""));

		this.eventClassifier = eventClasses.getClassifier();
		this.transEvClassMapping = createDefaultMappingTransitionsToEventClasses(eventClasses.getClassifier(),
				conf.getActivityMapping());
		this.eventClasses = XUtils.createEventClasses(transEvClassMapping.getEventClassifier(), log);
		// Mapping for Transitions
		this.transitionToName = createTransitionsToNameMap(model);
		this.nameToTransition = Multimaps.invertFrom(Multimaps.forMap(transitionToName),
				HashMultimap.<String, ReplayableActivityDefinition>create(conf.getVariableMapping().size(), 4));
		this.eventIdentityToTransition = createEventClassToTransitionsMap(transEvClassMapping);

		// Mapping for Variables
		this.variablesToWrite = createVariablesToWriteMap(transEvClassMapping, model.getDataVariables().keySet(), log);
		this.hasWriteOps = new HashMap<>();

		@SuppressWarnings("rawtypes")
		Map<String, Class> varType = model.getDataVariables();
		Map<String, String> variableMapping = conf.getVariableMapping();
		try {
			this.stringDiscretizer = createDiscretizer(model, log, variableMapping, varType, conf.getDefaultValues(),
					eventIdentityToTransition, variablesToWrite, eventClassifier);
		} catch (StringDiscretizerException e1) {
			throw new DataAlignmentException(e1);
		}

		for (Entry<ReplayableActivityDefinition, LinkedHashSet<String>> entry : variablesToWrite.entrySet()) {
			hasWriteOps.put(entry.getKey(), entry.getValue().size() > 0);
		}

		for (@SuppressWarnings("rawtypes")
		Entry<String, Class> entry : varType.entrySet()) {

			String variableName = entry.getKey();
			Class<?> type = entry.getValue();
			Map<String, Object> lowerBounds = conf.getLowerBounds();
			Map<String, Object> upperBounds = conf.getUpperBounds();
			Variable variable = new Variable(variableName, type);
			variable.setAttributeName(variableMapping.get(variableName));
			variable.setLowerBound(lowerBounds.get(variableName));
			variable.setUpperBound(upperBounds.get(variableName));
			variables.put(variable.getName().intern(), variable);
		}

		// Multiple variables in our DPN-net may be mapped to the same attribute name in the event log 
		this.reverseVariableMapping = HashMultimap.create();
		for (Entry<String, String> entry : variableMapping.entrySet()) {
			reverseVariableMapping.put(entry.getValue(), variables.get(entry.getKey()));
		}

		this.variableCost = new VirtualVariableAwareWriteOperationCostWrapper(conf.getVariableCost(), variables);

	}

	private TransEvClassMapping createDefaultMappingTransitionsToEventClasses(XEventClassifier classifier,
			Map<ReplayableActivityDefinition, XEventClass> activityMapping) {
		TransEvClassMapping result = new TransEvClassMapping(classifier, TICK);
		for (Entry<ReplayableActivityDefinition, XEventClass> entry : activityMapping.entrySet()) {
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}

	public static SetMultimap<String, ReplayableActivityDefinition> createEventClassToTransitionsMap(
			TransEvClassMapping transEvClassMapping) {
		SetMultimap<String, ReplayableActivityDefinition> eventClassMap = LinkedHashMultimap.create();
		for (Entry<ReplayableActivityDefinition, XEventClass> entry : transEvClassMapping.entrySet()) {
			eventClassMap.put(entry.getValue().getId(), entry.getKey());
		}
		return ImmutableSetMultimap.copyOf(eventClassMap);
	}

	private static StringDiscretizer createDiscretizer(DataAwareDeclare model, XLog log,
			Map<String, String> variableMapping, @SuppressWarnings("rawtypes") Map<String, Class> varType,
			Map<String, Object> defaultValues,
			SetMultimap<String, ReplayableActivityDefinition> eventIdentityToTransition,
			Map<ReplayableActivityDefinition, LinkedHashSet<String>> variablesToWrite, XEventClassifier eventClassifier)
			throws StringDiscretizerException, ParseException {
		return new StringDiscretizerImpl(log, getGuards(model), variableMapping, varType, defaultValues,
				eventIdentityToTransition, variablesToWrite, eventClassifier);
	}

	public static List<GuardExpression> getGuards(DataAwareDeclare model) throws ParseException {
		List<GuardExpression> guards = new ArrayList<>();
		for (String guard : model.getGuards().values()) {
			guards.add(GuardExpression.Factory.newInstance(guard));
		}
		return guards;
	}

	public VariableAwareWriteOperationCost getVariableCost() {
		return variableCost;
	}

	public Map<ReplayableActivityDefinition, LinkedHashSet<String>> getVariablesToWrite() {
		return variablesToWrite;
	}

	public static Map<ReplayableActivityDefinition, LinkedHashSet<String>> createVariablesToWriteMap(
			TransEvClassMapping activityMapping, Set<String> variableNames, XLog log) {
		Map<ReplayableActivityDefinition, LinkedHashSet<String>> transitionMap = Maps
				.newHashMapWithExpectedSize(activityMapping.size());
		for (ReplayableActivityDefinition activity : activityMapping.keySet()) {
			transitionMap.put(activity, new LinkedHashSet<>());
		}
		for (XTrace trace : log) {
			for (XEvent event : trace) {
				String activity = event.getAttributes().get("concept:name").toString();
				for (Entry<ReplayableActivityDefinition, LinkedHashSet<String>> vars : transitionMap.entrySet()) {
					for (String var : variableNames) {
						XAttributeMap attributes = event.getAttributes();
						XAttribute attribute = attributes.get(var);
						if (vars.getKey().getLabel().equals("TICK")) {
							if (attribute != null && !vars.getValue().contains(var)) {
								vars.getValue().add(var);
							}
						} else if (vars.getKey().getLabel().equals(activity)) {

							if (attribute != null && !vars.getValue().contains(var)) {
								vars.getValue().add(var);
							}
						}
					}
				}
			}
		}
		return ImmutableMap.copyOf(transitionMap);
	}

	public static Map<ReplayableActivityDefinition, String> createTransitionsToNameMap(DataAwareDeclare model) {
		List<ActivityDefinition> transitions = (List<ActivityDefinition>) model.getModel().getActivityDefinitions();
		Map<ReplayableActivityDefinition, String> transitionMap = Maps.newHashMapWithExpectedSize(transitions.size());
		for (ActivityDefinition transition : transitions) {
			transitionMap.put(new ReplayableActivityDefinition(transition.getName()), transition.getName().intern());
		}
		return ImmutableMap.copyOf(transitionMap);
	}

	public Map<ReplayableActivityDefinition, String> getTransitionToName() {
		return transitionToName;
	}

	public StringDiscretizer getStringDiscretizer() {
		return stringDiscretizer;
	}

	public Set<ReplayableActivityDefinition> getMappedTransitions(XEvent e) {
		return eventIdentityToTransition.get(transEvClassMapping.getEventClassifier().getClassIdentity(e));
	}

	public boolean hasWriteOps(ReplayableActivityDefinition transition) {
		return hasWriteOps.get(transition);
	}

	public SetMultimap<String, Variable> getReverseVariableMapping() {
		return reverseVariableMapping;
	}

	public XEventClassifier getEventClassifier() {
		return eventClassifier;
	}

	public SetMultimap<String, ReplayableActivityDefinition> getEventIdentityToTransition() {
		return eventIdentityToTransition;
	}

	public UnassignedMode getUnassignedMode() {
		return unassignedMode;
	}

	public Map<String, Variable> getVariables() {
		return variables;
	}

	@SuppressWarnings("rawtypes")
	public static Map<String, Class> convertToVariableTypes(Map<String, Variable> variables) {
		return Maps.transformValues(variables, new Function<Variable, Class>() {

			public Class apply(Variable var) {
				return var.getType();
			}
		});
	}

	public boolean hasVirtualVariables() {
		return hasVirtualVariables;
	}

	public XEventClasses getEventClasses() {
		return eventClasses;
	}

	public Map<XEventClass, Integer> getEventClass2Cost() {
		return eventClass2Cost;
	}

	public Map<ReplayableActivityDefinition, Integer> getTransition2Cost() {
		return transition2Cost;
	}
}
