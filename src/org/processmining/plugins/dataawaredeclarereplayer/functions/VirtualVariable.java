package org.processmining.plugins.dataawaredeclarereplayer.functions;

import java.util.Set;

import org.deckfour.xes.model.XEvent;
import org.processmining.plugins.DeclareConformance.ReplayableActivityDefinition;
import org.processmining.plugins.dataawaredeclarereplayer.mapping.LogMapping;

public interface VirtualVariable {
	/**
	 * Special attribute key that indicates relative time is needed from the log
	 * instead of absolute time stamps
	 */
	public static final String ATTRIBUTE_KEY_RELATIVE_TIME = "virtual:relativetime";

	/**
	 * Special value that indicates that the computation did not change the
	 * current variable value
	 */
	public static final Object UNCHANGED = new Object();

	public interface Prefix {

		Iterable<PrefixStep> getSteps();

		Object variableValue(String variableName);

		boolean isAligned();

	}

	public interface PrefixStep {

		XEvent getLogObject();

		ReplayableActivityDefinition getProcessObject();

	}

	String getName();

	Class<?> getType();

	boolean isFinal();

	boolean needsAlignedPrefix();

	int getFaultyValueCost();

	int getMissingValueCost();

	Double getUpperBound();

	Set<ReplayableActivityDefinition> getRelevantTransitions();

	Set<String> getRelevantAttributes();

	Object compute(Prefix alignmentPrefix, ReplayableActivityDefinition currentTransition, XEvent currentEvent, LogMapping mapping);
}
