package org.processmining.plugins.dataawaredeclarereplayer.mapping;

import java.util.Set;

public interface StringDiscretizer {
	public static final String ANY_VALUE = "ANY VALUE";
	public static final String SMALLER_THAN_ANY_VALUE = "\u0000 < ANY VALUE";
	public static final String LARGER_THAN_ANY_VALUE = "\uFFFF > ANY VALUE";
	
	int convertToInt(String val);
	String convertToString(int val);
	String convertToString(int newVal, int oldVal);
	
	int getUpperBound();
	int getLowerBound();
	
	Set<String> getStoredStrings();
}
