package org.processmining.plugins.dataawaredeclarereplayer.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.processmining.plugins.declareminer.visualizing.Condition;

public class Utils {

	public static String getConditionString(Condition condition) {
		String result = null;
		String patternString = "(?<=\\[)(.*?)(?=\\])";

        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(condition.getText());
        if(matcher.find()) {
            result = matcher.group(1);
        }
        
		return result;
	}
	
	
}
