package org.processmining.plugins.dataawaredeclarereplayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.deckfour.uitopia.api.event.TaskListener.InteractionResult;
import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeContinuous;
import org.deckfour.xes.model.XAttributeDiscrete;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XAttributeTimestamp;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.framework.util.ui.widgets.helper.UserCancelledException;
import org.processmining.log.utils.XUtils;
import org.processmining.plugins.DataConformance.GUI.ActivityMatchCostPanel;
import org.processmining.plugins.DataConformance.GUI.MappingPanel;
import org.processmining.plugins.DataConformance.GUI.VariableMatchCostPanel;
import org.processmining.plugins.DataConformance.framework.ActivityMatchCosts;
import org.processmining.plugins.DataConformance.framework.VariableMatchCosts;
import org.processmining.plugins.DeclareConformance.ReplayableActivityDefinition;
import org.processmining.plugins.declareminer.visualizing.ActivityDefinition;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

@SuppressWarnings("rawtypes")
public class DataAwareConformanceConfiguration {

	private static final int VARIABLE_BOUNDS_PRECISION = 1000;

	private Map<String, String> variableMapping = null;

	private VariableMatchCosts variableCost = null;

	private Map<String, Object> lowerBounds = Collections.emptyMap();

	private Map<String, Object> upperBounds = Collections.emptyMap();

	private Map<String, Object> defaultValues = Collections.emptyMap();

	private ActivityMatchCosts<XEventClass> activityCost;

	private Map<ReplayableActivityDefinition, XEventClass> activityMapping;

	private Map<String, Set<String>> variablesToWrite;

	private Map<String, Class> variableTypes = new HashMap<>();

	private boolean prune = true;

	private DataAwareConformanceConfiguration() {
		super();
	}

	public DataAwareConformanceConfiguration(Map<String, String> variableMapping,
			Map<ReplayableActivityDefinition, XEventClass> activityMapping,
			ActivityMatchCosts<XEventClass> activityCost, VariableMatchCosts variableCost,
			Map<String, Object> lowerBounds, Map<String, Object> upperBounds, Map<String, Object> defaultValues,
			Map<String, Set<String>> variablesToWrite, Map<String, Class> variableTypes) {
		super();
		this.variableMapping = variableMapping;
		this.variableCost = variableCost;
		this.lowerBounds = lowerBounds;
		this.upperBounds = upperBounds;
		this.defaultValues = defaultValues;
		this.activityCost = activityCost;
		this.activityMapping = activityMapping;
		this.variablesToWrite = variablesToWrite;
		this.variableTypes = variableTypes;
	}

	public static DataAwareConformanceConfiguration initValues(DataAwareDeclare model, XLog log,
			XEventClasses eventClasses, UIPluginContext context) throws UserCancelledException {

		Set<String> variableNames = model.getDataVariables().keySet();
		Set<String> attributeNames = getAttributeNames(log, eventClasses.getClassifier());

		Map<String, String> variableMapping = new HashMap<>();
		if (!variableNames.isEmpty()) {
			variableMapping = createDefaultVariableMapping(variableNames, attributeNames);
		} else {
			variableMapping = ImmutableMap.of();
		}
		DataAwareConformanceConfiguration config = new DataAwareConformanceConfiguration();

		HashSet<ReplayableActivityDefinition> activitySet = new HashSet<ReplayableActivityDefinition>();
		for (ActivityDefinition activity : model.getModel().getActivityDefinitions())
			if (activity.getName() != null && activity.getName().length() > 0)
				activitySet.add(new ReplayableActivityDefinition(activity.getName()));

		Map<ReplayableActivityDefinition, XEventClass> activityMapping = queryActivityMapping(model, activitySet,
				eventClasses, context);

		HashMap<ReplayableActivityDefinition, XEventClass> activities = new HashMap<>();
		for (Entry<ReplayableActivityDefinition, XEventClass> entry : activityMapping.entrySet()) {
			activities.put(entry.getKey(), entry.getValue());
		}

		config.setVariableTypes(model.getDataVariables());
		config.setActivityMapping(activityMapping);
		config.setActivityCost(queryActivityCosts(config, activitySet, activityMapping, context));
		config.setVariableCost(queryVariableCosts(activities, variableMapping, context));
		config.setVariableMapping(variableMapping);
		config.setVariablesToWrite(queryToWrite(activityMapping, variableNames, log));
		autoGuessBounds(config, model, log);
		return config;
	}

	private static Map<ReplayableActivityDefinition, XEventClass> queryActivityMapping(DataAwareDeclare model,
			HashSet<ReplayableActivityDefinition> activitySet, XEventClasses eventClasses, UIPluginContext context) {
		//Activity Mapping
		MappingPanel<ReplayableActivityDefinition, XEventClass> mapActivityPanel = new MappingPanel<ReplayableActivityDefinition, XEventClass>(
				activitySet, eventClasses.getClasses());
		InteractionResult result = context.showConfiguration("Setup Activity Mapping", mapActivityPanel);
		if (result == InteractionResult.CANCEL) {
			context.getFutureResult(0).cancel(true);
			return null;
		}

		Map<ReplayableActivityDefinition, XEventClass> activityMapping = mapActivityPanel.getMapping(false);

		//Add tick activities
		for (XEventClass eventClass : eventClasses.getClasses()) {
			if (!activityMapping.values().contains(eventClass)) {
				activityMapping.put(new ReplayableActivityDefinition("TICK"), eventClass);
			}

		}

		return activityMapping;
	}

	//Activity Costs
	private static ActivityMatchCosts<XEventClass> queryActivityCosts(DataAwareConformanceConfiguration config,
			HashSet<ReplayableActivityDefinition> activitySet,
			Map<ReplayableActivityDefinition, XEventClass> activityMapping, UIPluginContext context) {

		ActivityMatchCostPanel<XEventClass> activityPanel = new ActivityMatchCostPanel<XEventClass>(activitySet,
				activityMapping, "Prune Search Space");
		InteractionResult result = context.showConfiguration("Activity Mapping Cost", activityPanel);
		if (result == InteractionResult.CANCEL) {
			context.getFutureResult(0).cancel(true);
			return null;
		}

		config.prune = activityPanel.specialOptionOn();
		return activityPanel.getCosts();
	}

	private static VariableMatchCosts queryVariableCosts(HashMap<ReplayableActivityDefinition, XEventClass> activitySet,
			Map<String, String> variableMapping, UIPluginContext context) throws UserCancelledException {
		VariableMatchCostPanel<XEventClass> variablePanel = new VariableMatchCostPanel<>(activitySet, variableMapping);

		InteractionResult result = context.showConfiguration("Cost of Deviations in Data Perspective", variablePanel);
		if (result == InteractionResult.CANCEL) {
			throw new UserCancelledException();
		}

		return variablePanel.getCosts();
	}

	private static Set<String> getAttributeNames(XLog log, XEventClassifier classifier) {
		Set<String> attributeNames;
		if (log.getInfo(classifier) != null) {
			attributeNames = ImmutableSet.copyOf(log.getInfo(classifier).getEventAttributeInfo().getAttributeKeys());
		} else {
			attributeNames = XUtils.getEventAttributeKeys(log);
		}
		return attributeNames;
	}

	public static Map<String, String> createDefaultVariableMapping(Set<String> variableNames,
			Set<String> attributeNames) {
		Map<String, String> mapping = new HashMap<>();
		for (String attributeKey : attributeNames) {
			if (variableNames.contains(attributeKey)) {
				mapping.put(attributeKey, attributeKey);
			}
		}
		return mapping;
	}

	public static Map<String, Set<String>> queryToWrite(Map<ReplayableActivityDefinition, XEventClass> activityMapping,
			Set<String> variableNames, XLog log) {
		Map<String, Set<String>> variablesToWrite = new HashMap<>();
		for (ReplayableActivityDefinition activity : activityMapping.keySet()) {
			variablesToWrite.put(activity.getLabel(), new HashSet<>());
		}
		for (XTrace trace : log) {
			for (XEvent event : trace) {
				String activity = event.getAttributes().get("concept:name").toString();
				if (variablesToWrite.keySet().contains(activity)) {
					for (Entry<String, Set<String>> vars : variablesToWrite.entrySet()) {
						if (vars.getKey().equals(activity)) {
							for (String var : variableNames) {
								XAttributeMap attributes = event.getAttributes();
								XAttribute attribute = attributes.get(var);
								if (attribute != null && !vars.getValue().contains(var)) {
									vars.getValue().add(var);
								}
							}
						}

					}
				}

			}
		}
		return variablesToWrite;
	}

	public static void autoGuessBounds(DataAwareConformanceConfiguration config, DataAwareDeclare model, XLog log) {

		Map<String, Object> upperBounds = new HashMap<>();
		Map<String, Object> lowerBounds = new HashMap<>();

		Collection<DataElement> variables = new ArrayList<>();
		for (Entry<String, Class> e : model.getDataVariables().entrySet()) {
			variables.add(new DataElement(e.getKey(), e.getValue(), null, null));
		}

		if (!variables.isEmpty()) {

			Map<DataElement, Number> minValueMap = new HashMap<>();
			Map<DataElement, Number> maxValueMap = new HashMap<>();

			for (XTrace trace : log) {
				for (XEvent event : trace) {

					XAttributeMap attributes = event.getAttributes();
					for (DataElement elem : variables) {
						String attributeName = config.getVariableMapping().get(elem.getVarName());
						XAttribute attribute = attributes.get(attributeName);
						if (attribute != null) {
							updateBounds(minValueMap, maxValueMap, attribute, elem);
						}
					}
				}
			}

			for (DataElement elem : variables) {

				Object row[] = new Object[4];
				row[0] = elem.getVarName();
				row[1] = elem.getType();

				Number minValue = determineMinValue(elem, minValueMap);
				Number maxValue = determineMaxValue(elem, maxValueMap);

				if (minValue != null && minValue.equals(maxValue)) {
					maxValue = minValue.longValue() + VARIABLE_BOUNDS_PRECISION;
				}

				if (minValue != null) {
					if (elem.getType() == Date.class) {
						// default allow all dates
						lowerBounds.put(elem.getVarName(), new Date(0));
					} else {
						lowerBounds.put(elem.getVarName(), minValue);
					}
				}

				if (maxValue != null) {
					if (elem.getType() == Date.class) {
						upperBounds.put(elem.getVarName(), new Date(maxValue.longValue()));
					} else {
						upperBounds.put(elem.getVarName(), maxValue);
					}
				}
			}
		}

		config.setUpperBounds(upperBounds);
		config.setLowerBounds(lowerBounds);
	}

	private static void updateBounds(Map<DataElement, Number> minValueMap, Map<DataElement, Number> maxValueMap,
			XAttribute attribute, DataElement elem) {
		Number minValue = minValueMap.get(elem);
		Number maxValue = maxValueMap.get(elem);

		if (attribute instanceof XAttributeDiscrete) {
			XAttributeDiscrete dAttr = (XAttributeDiscrete) attribute;
			long value = dAttr.getValue();
			minValueMap.put(elem, minValue == null ? value : Math.min(minValue.longValue(), value));
			maxValueMap.put(elem, maxValue == null ? value : Math.max(maxValue.longValue(), value));
		} else if (attribute instanceof XAttributeContinuous) {
			XAttributeContinuous cAttr = (XAttributeContinuous) attribute;
			double value = cAttr.getValue();
			minValueMap.put(elem, minValue == null ? value : Math.min(minValue.doubleValue(), value));
			maxValueMap.put(elem, maxValue == null ? value : Math.max(maxValue.doubleValue(), value));
		} else if (attribute instanceof XAttributeTimestamp) {
			XAttributeTimestamp tAttr = (XAttributeTimestamp) attribute;
			long value = tAttr.getValueMillis();
			minValueMap.put(elem, minValue == null ? value : Math.min(minValue.longValue(), value));
			maxValueMap.put(elem, maxValue == null ? value : Math.max(maxValue.longValue(), value));
		}
	}

	private static Number determineMaxValue(DataElement elem, Map<DataElement, Number> maxValueMap) {
		if (elem.getMaxValue() != null && elem.getMaxValue() instanceof Number) {
			return (Number) elem.getMaxValue();
		} else {
			Number maxValue = maxValueMap.get(elem);
			if (maxValue != null) {
				double roundedValue = Math.ceil((maxValue.doubleValue() / VARIABLE_BOUNDS_PRECISION))
						* VARIABLE_BOUNDS_PRECISION;
				if (elem.getType() == Long.class) {
					return new Long((int) roundedValue);
				} else if (elem.getType() == Integer.class) {
					return new Integer((int) roundedValue);
				} else if (elem.getType() == Double.class) {
					return new Double(roundedValue);
				} else if (elem.getType() == Float.class) {
					return new Float(roundedValue);
				} else if (elem.getType() == Date.class) {
					return new Long((long) roundedValue);
				} else {
					return null;
				}
			} else {
				return null;
			}
		}
	}

	private static Number determineMinValue(DataElement elem, Map<DataElement, Number> minValueMap) {
		if (elem.getMinValue() != null && elem.getMinValue() instanceof Number) {
			return (Number) elem.getMinValue();
		} else {
			Number minValue = minValueMap.get(elem);
			if (minValue != null) {
				double roundedValue = Math.floor((minValue.doubleValue() / VARIABLE_BOUNDS_PRECISION))
						* VARIABLE_BOUNDS_PRECISION;
				if (elem.getType() == Long.class) {
					return new Long((long) roundedValue);
				} else if (elem.getType() == Integer.class) {
					return new Integer((int) roundedValue);
				} else if (elem.getType() == Double.class) {
					return new Double(roundedValue);
				} else if (elem.getType() == Float.class) {
					return new Float(roundedValue);
				} else if (elem.getType() == Date.class) {
					return new Long((long) roundedValue);
				} else {
					return null;
				}
			} else {
				return null;
			}
		}
	}

	public Map<String, String> getVariableMapping() {
		return variableMapping;
	}

	public void setVariableMapping(Map<String, String> variableMapping) {
		this.variableMapping = variableMapping;
	}

	public VariableMatchCosts getVariableCost() {
		return variableCost;
	}

	public void setVariableCost(VariableMatchCosts variableCost) {
		this.variableCost = variableCost;
	}

	public Map<String, Object> getLowerBounds() {
		return lowerBounds;
	}

	public void setLowerBounds(Map<String, Object> lowerBounds) {
		this.lowerBounds = lowerBounds;
	}

	public Map<String, Object> getUpperBounds() {
		return upperBounds;
	}

	public void setUpperBounds(Map<String, Object> upperBounds) {
		this.upperBounds = upperBounds;
	}

	public Map<String, Object> getDefaultValues() {
		return defaultValues;
	}

	public void setDefaultValues(Map<String, Object> defaultValues) {
		this.defaultValues = defaultValues;
	}

	public ActivityMatchCosts<XEventClass> getActivityCost() {
		return activityCost;
	}

	public void setActivityCost(ActivityMatchCosts<XEventClass> activityCost) {
		this.activityCost = activityCost;
	}

	public Map<ReplayableActivityDefinition, XEventClass> getActivityMapping() {
		return activityMapping;
	}

	public void setActivityMapping(Map<ReplayableActivityDefinition, XEventClass> activityMapping) {
		this.activityMapping = activityMapping;
	}

	public boolean isPrune() {
		return prune;
	}

	public Map<String, Set<String>> getVariablesToWrite() {
		return variablesToWrite;
	}

	public void setVariablesToWrite(Map<String, Set<String>> variablesToWrite) {
		this.variablesToWrite = variablesToWrite;
	}

	public Map<String, Class> getVariableTypes() {
		return variableTypes;
	}

	public void setVariableTypes(Map<String, Class> variableTypes) {
		this.variableTypes = variableTypes;
	}

}
