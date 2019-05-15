package org.processmining.plugins.dataawaredeclarereplayer.mapping;

import org.processmining.plugins.DataConformance.framework.WriteOperationCost;

public interface VariableAwareWriteOperationCost extends WriteOperationCost {

	int costFaultyValue(String activity, Variable variable);

	int notWritingCost(String activity, Variable variable);
	
	boolean isFinal(String activity, Variable variable);
	
}
