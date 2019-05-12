package org.processmining.plugins.dataawaredeclarereplayer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.plugins.DataConformance.framework.ActivityMatchCosts;
import org.processmining.plugins.DataConformance.framework.VariableMatchCosts;
import org.processmining.plugins.dataawaredeclarereplayer.GroupedTraces.GroupedTrace;
import org.processmining.plugins.dataawaredeclarereplayer.mapping.StringDiscretizer;

import com.google.common.collect.Multiset.Entry;

@SuppressWarnings("rawtypes")
public class MultiThreadDeclareConformanceChecking {
	@SuppressWarnings("unchecked")
	public static Collection<DataAlignmentState> perform(DataAwareDeclare model, Map activityMapping,
			Map<String, String> variableMapping, XEventClasses eventClasses, XLog log, ActivityMatchCosts activityCost,
			VariableMatchCosts variableCost, Map<String, Float> maxDistance, UIPluginContext context,
			boolean subOptimalHeur, Map<String, Set<String>> variablesToWrite, Map<String, Object> lowerBounds,
			Map<String, Object> upperBounds, Map<String, Class> variableTypes, StringDiscretizer stringDiscretizer) {
		HashSet<DataAlignmentState> list = new HashSet<DataAlignmentState>();
		for (XTrace trace : log) {
			DataAwareReplayer replayer = new DataAwareReplayer(model, activityMapping, variableMapping, eventClasses,
					trace, activityCost, variableCost, maxDistance, list, context, subOptimalHeur, variablesToWrite,
					lowerBounds, upperBounds, variableTypes, stringDiscretizer);

			replayer.run();
		}
		return list;
	}

	@SuppressWarnings("unchecked")
	public static Collection<DataAlignmentState> perform(DataAwareDeclare model, Map activityMapping,
			Map<String, String> variableMapping, XEventClasses eventClasses, GroupedTraces groupedTraces,
			ActivityMatchCosts activityCost, VariableMatchCosts variableCost, Map<String, Float> maxDistance,
			UIPluginContext context, boolean subOptimalHeur, Map<String, Set<String>> variablesToWrite,
			Map<String, Object> lowerBounds, Map<String, Object> upperBounds, Map<String, Class> variableTypes,
			StringDiscretizer stringDiscretizer) {
		HashSet<DataAlignmentState> list = new HashSet<DataAlignmentState>();
		Iterator<Entry<GroupedTrace>> groupIter = groupedTraces.asMultiset().entrySet().iterator();
		while (groupIter.hasNext()) {
			Entry<GroupedTrace> groupedTrace = groupIter.next();
			XTrace trace = groupedTrace.getElement().getRepresentativeTrace();

			DataAwareReplayer replayer = new DataAwareReplayer(model, activityMapping, variableMapping, eventClasses,
					trace, activityCost, variableCost, maxDistance, list, context, subOptimalHeur, variablesToWrite,
					lowerBounds, upperBounds, variableTypes, stringDiscretizer);
			replayer.run();

		}

		return list;
	}
}
