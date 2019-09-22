
package org.processmining.plugins.dataawaredeclarereplayer.mapping;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeLiteral;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.datapetrinets.expression.GuardExpression;
import org.processmining.plugins.DeclareConformance.ReplayableActivityDefinition;
import org.processmining.plugins.balancedconformance.mapping.StringDiscretizerException;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;

public class StringDiscretizerImpl implements StringDiscretizer {

	private final ImmutableBiMap<String, Integer> strToInt;
	private final ImmutableBiMap<Integer, String> intToStr;
	private int maxIndex;

	public StringDiscretizerImpl(XLog log, Collection<GuardExpression> guards, Map<String, String> variableMapping,
			@SuppressWarnings("rawtypes") Map<String, Class> varType, Map<String, Object> defaultValues,
			SetMultimap<String, ReplayableActivityDefinition> eventIdentityToTransition,
			Map<ReplayableActivityDefinition, LinkedHashSet<String>> variablesToWrite, XEventClassifier eventClassifier)
			throws StringDiscretizerException {
		this.strToInt = ImmutableBiMap
				.copyOf(fillMap(log, guards, variableMapping, varType, defaultValues, eventIdentityToTransition,
						variablesToWrite, eventClassifier, ImmutableSetMultimap.<String, String>of()));
		this.intToStr = strToInt.inverse();
	}

	private final Map<String, Integer> fillMap(XLog log, Collection<GuardExpression> guards,
			Map<String, String> variableMapping, @SuppressWarnings("rawtypes") Map<String, Class> varType,
			Map<String, Object> defaultValues,
			SetMultimap<String, ReplayableActivityDefinition> eventIdentityToTransition,
			Map<ReplayableActivityDefinition, LinkedHashSet<String>> variablesToWrite, XEventClassifier eventClassifier,
			SetMultimap<String, String> extraAttributes) throws StringDiscretizerException {

		NavigableMap<String, Integer> varMap = new TreeMap<>();
		SortedMap<String, Integer> indexMap = new TreeMap<>();
		Set<String> strVariables = new HashSet<>();

		for (@SuppressWarnings("rawtypes")
		Entry<String, Class> e : varType.entrySet()) {
			if (e.getValue() == String.class) {
				strVariables.add(e.getKey());
				Object defaultValue = defaultValues.get(e.getKey());
				if (defaultValue != null) {
					varMap.put((String) defaultValue, 0);
				}
			}
		}

		for (String attributeValue : extraAttributes.values()) {
			// Add value with a dummy index of 0			
			varMap.put(attributeValue, 0);
			indexMap.put(attributeValue, 0);
		}

		for (XTrace trace : log) {
			for (XEvent event : trace) {
				String classId = eventClassifier.getClassIdentity(event);
				Set<ReplayableActivityDefinition> mappedTransitions = eventIdentityToTransition.get(classId);
				// For each transition that this event is mapped to
				for (ReplayableActivityDefinition transition : mappedTransitions) {
					Set<String> writeOperations = variablesToWrite.get(transition);
					// For each write operation on this transition
					for (String variableName : writeOperations) {
						// Record the values of a String variable that are seen in the log
						if (strVariables.contains(variableName)) {
							String attributeName = variableMapping.get(variableName);
							XAttribute attr = event.getAttributes().get(attributeName);
							if (attr != null) {
								if (!(attr instanceof XAttributeLiteral)) {
									throw new StringDiscretizerException(
											"String variable is mapped to a non-literal attribute " + variableName
													+ " to " + attributeName + ", invalid attribute: " + attr);
								}
								String attributeValue = ((XAttributeLiteral) attr).getValue();
								// Add value with a dummy index of 0			
								varMap.put(attributeValue, 0);
								indexMap.put(attributeValue, 0);
							}
						}
					}
				}
			}
		}

		// Add values that are used in guards of the model 
		for (GuardExpression e : guards) {
			Set<String> literalValues = e.getLiteralValues(String.class);
			for (String literalValue : literalValues) {
				// Add value with a dummy index of 0			
				varMap.put(literalValue, 0);
				indexMap.put(literalValue, 0);
			}
		}

		// Add a placeholder for ANY_VALUE for each attribute
		varMap.put(ANY_VALUE, 0);
		indexMap.put(ANY_VALUE, 0);

		// Now assign the numbers
		int i = 1;
		for (Entry<String, Integer> entry : indexMap.entrySet()) {
			entry.setValue(i++);
		}
		maxIndex = i;

		// Store mapping for efficient retrieval
		for (Entry<String, Integer> e : varMap.entrySet()) {
			e.setValue(indexMap.get(e.getKey()));
		}

		return varMap;
	}

	public int convertToInt(String val) {
		Integer index = strToInt.get(val);
		if (index == null) {
			if (val.equals(SMALLER_THAN_ANY_VALUE)) {
				return 0;
			} else if (val.equals(LARGER_THAN_ANY_VALUE)) {
				return maxIndex;
			}
			throw new IllegalArgumentException("Value " + val + " is not know! Know mappings:" + getStoredStrings());
		}
		return index;
	}

	public String convertToString(int val) {
		if (val == 0) {
			// Special string smaller than anything
			return SMALLER_THAN_ANY_VALUE;
		}
		if (val == maxIndex) {
			// Special string larger than anything
			return LARGER_THAN_ANY_VALUE;
		}
		String str = intToStr.get(val);
		if (str == null) {
			throw new IllegalArgumentException("Value " + val + " is not know! Know mappings:" + getStoredStrings());
		}
		return str;
	}

	public String convertToString(int newVal, int oldVal) {
		if (getStoredStrings().size() > 2) {
			if (newVal != oldVal) {
				while (newVal <= 1 || newVal == oldVal || newVal >= maxIndex) {
					newVal = (int) (Math.random() * (maxIndex - 1)) + 1;
				}
			}
		} else {
			if (newVal == 0) {
				// Special string smaller than anything
				return SMALLER_THAN_ANY_VALUE;
			}
			if (newVal == maxIndex) {
				// Special string larger than anything
				return LARGER_THAN_ANY_VALUE;
			}
		}

		String str = intToStr.get(newVal);
		if (str == null) {
			throw new IllegalArgumentException("Value " + newVal + " is not know! Know mappings:" + getStoredStrings());
		}
		return str;
	}

	public int getUpperBound() {
		return maxIndex;
	}

	public int getLowerBound() {
		return 0;
	}

	public Set<String> getStoredStrings() {
		return Collections.unmodifiableSet(strToInt.keySet());
	}

	public String toString() {
		return strToInt.toString();
	}

}
