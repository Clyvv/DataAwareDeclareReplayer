package org.processmining.plugins.dataawaredeclarereplayer;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeMapImpl;
import org.deckfour.xes.model.impl.XAttributeTimestampImpl;
import org.deckfour.xes.model.impl.XTraceImpl;
import org.processmining.log.utils.XUtils;
import org.processmining.plugins.DeclareConformance.ReplayableActivityDefinition;
import org.processmining.plugins.balancedconformance.functions.VirtualVariable;
import org.processmining.plugins.balancedconformance.result.BalancedDataAlignmentState;
import org.processmining.plugins.dataawaredeclarereplayer.mapping.LogMapping;
import org.processmining.plugins.dataawaredeclarereplayer.mapping.Variable;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;

final class GroupedTraces {

	private static final class AbstractedEvent {

		private final String classId;
		private final Object[] relevantAttributes;
		private final int hashCode;

		public AbstractedEvent(XEvent event, String classId, SortedSet<String> consideredAttributes) {
			this.classId = classId;
			this.relevantAttributes = new Object[consideredAttributes.size()];
			int i = 0;
			for (String attributeKey : consideredAttributes) {
				XAttribute attr = event.getAttributes().get(attributeKey);
				if (attr != null) {
					relevantAttributes[i++] = XUtils.getAttributeValue(attr);
				} else {
					relevantAttributes[i++] = null;
				}
			}
			hashCode = calcHashCode(classId, relevantAttributes);
		}

		private static int calcHashCode(String classId, Object[] relevantAttribute) {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((classId == null) ? 0 : classId.hashCode());
			result = prime * result + Arrays.hashCode(relevantAttribute); // relies on sorted array of considered attributes
			return result;
		}

		public int hashCode() {
			return hashCode;
		}

		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			AbstractedEvent other = (AbstractedEvent) obj;
			if (classId == null) {
				if (other.classId != null)
					return false;
			} else if (!classId.equals(other.classId))
				return false;
			if (!Arrays.equals(relevantAttributes, other.relevantAttributes))
				return false;
			return true;
		}

	}

	private static Date findStartTimeOfMappedTrace(LogMapping logMapping, XTrace trace) {
		for (XEvent event : trace) {
			if (!logMapping.getMappedTransitions(event).isEmpty()) {
				Date startTime = XUtils.getTimestamp(event);
				if (startTime != null) {
					return startTime;
				}
			}
		}
		// trace without time information
		return null;
	}

	interface GroupedTrace {

		XTrace getRepresentativeTrace();

	}

	private static final class GroupedXTrace implements GroupedTrace {

		private final XTrace traceWithoutUnmapped;
		private final AbstractedEvent[] abstractTrace;
		private final int hashCode;

		public GroupedXTrace(XTrace trace, LogMapping logMapping,
				Map<String, SortedSet<String>> consideredAttributesMap) {
			if (logMapping.hasVirtualVariables()) {
				//TODO only if relative time is needed & only consider relative time for grouping but avoid adding relative time
				addRelativeTime(trace, logMapping);
			}

			this.traceWithoutUnmapped = new XTraceImpl(new XAttributeMapImpl());
			AbstractedEvent[] tempTrace = new AbstractedEvent[trace.size()];
			int i = 0;
			for (XEvent event : trace) {
				if (!logMapping.getMappedTransitions(event).isEmpty()) {
					// Only consider mapped events
					String classId = logMapping.getEventClassifier().getClassIdentity(event);
					SortedSet<String> consideredAttributes = consideredAttributesMap.get(classId);
					if (consideredAttributes != null) {
						tempTrace[i++] = new AbstractedEvent(event, classId, consideredAttributes);
					} else {
						// No attribute is relevant
						tempTrace[i++] = new AbstractedEvent(event, classId, ImmutableSortedSet.<String>of());
					}
					traceWithoutUnmapped.add(event);
				}
			}
			abstractTrace = Arrays.copyOf(tempTrace, i);
			hashCode = Arrays.hashCode(abstractTrace);
		}

		private void addRelativeTime(XTrace trace, LogMapping logMapping) {
			for (XEvent event : trace) {
				if (!logMapping.getMappedTransitions(event).isEmpty()) {
					Date startTime = findStartTimeOfMappedTrace(logMapping, trace);
					Date currentTime = XUtils.getTimestamp(event);
					if (currentTime != null && startTime != null) {
						XUtils.putAttribute(event,
								new XAttributeTimestampImpl(VirtualVariable.ATTRIBUTE_KEY_RELATIVE_TIME,
										currentTime.getTime() - startTime.getTime()));
					}
				}
			}
		}

		public XTrace getRepresentativeTrace() {
			return traceWithoutUnmapped;
		}

		public int hashCode() {
			return hashCode;
		}

		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			GroupedXTrace other = (GroupedXTrace) obj;
			if (!Arrays.equals(abstractTrace, other.abstractTrace))
				return false;
			return true;
		}

	}

	private final LogMapping logMapping;
	private final ListMultimap<GroupedTrace, XTrace> groupedTraces = Multimaps
			.synchronizedListMultimap(ArrayListMultimap.<GroupedTrace, XTrace>create());
	private final Map<String, SortedSet<String>> consideredAttributesMap;
	//private final MaxAlignmentCostHelper maxCostHelper;

	public GroupedTraces(XLog log, final LogMapping logMapping) {
		this.logMapping = logMapping;
		this.consideredAttributesMap = lookupConsideredAttributes(logMapping);
	}

	private static Map<String, SortedSet<String>> lookupConsideredAttributes(LogMapping logMapping) {
		Map<String, SortedSet<String>> consideredAttributesMap = new HashMap<String, SortedSet<String>>();
		for (Entry<String, Collection<ReplayableActivityDefinition>> entry : logMapping.getEventIdentityToTransition()
				.asMap().entrySet()) {
			SortedSet<String> consideredAttributes = new TreeSet<>(); // Order is important!! 
			String classId = entry.getKey();
			Set<ReplayableActivityDefinition> possibleTransitions = logMapping.getEventIdentityToTransition()
					.get(classId);
			// Consider attributes for all transitions this event class might be mapped to
			for (ReplayableActivityDefinition t : possibleTransitions) {
				lookupVariables(logMapping, consideredAttributes, possibleTransitions, t);
			}
			consideredAttributesMap.put(classId, consideredAttributes);
		}
		return consideredAttributesMap;
	}

	private static void lookupVariables(LogMapping logMapping, SortedSet<String> consideredAttributes,
			Set<ReplayableActivityDefinition> possibleTransitions, ReplayableActivityDefinition t) {
		for (String var : logMapping.getVariablesToWrite().get(t)) {
			lookupVariable(logMapping, consideredAttributes, possibleTransitions, var);
		}
	}

	private static void lookupVariable(LogMapping logMapping, SortedSet<String> consideredAttributes,
			Set<ReplayableActivityDefinition> possibleTransitions, String var) {
		Variable variable = logMapping.getVariables().get(var);
		if (variable.isUsedInGuard()) {
			String attributeName = variable.getAttributeName();
			if (attributeName != null) {
				consideredAttributes.add(attributeName);
			} // otherwise unmapped so not relevant either
		}
	}


	public void add(XTrace trace) {
		groupedTraces.put(new GroupedXTrace(trace, logMapping, consideredAttributesMap), trace);
	}

	public int size() {
		return groupedTraces.keySet().size();
	}

	public int groupSize(BalancedDataAlignmentState state) {
		return groupedTraces.keys()
				.count(new GroupedXTrace(state.getOriginalTrace(), logMapping, consideredAttributesMap));
	}

	public List<XTrace> getTracesInGroup(XTrace originalTrace) {
		return groupedTraces.get(new GroupedXTrace(originalTrace, logMapping, consideredAttributesMap));
	}

	public Map<GroupedTrace, Collection<XTrace>> asMap() {
		return groupedTraces.asMap();
	}

	public Multiset<GroupedTrace> asMultiset() {
		return groupedTraces.keys();
	}
}
