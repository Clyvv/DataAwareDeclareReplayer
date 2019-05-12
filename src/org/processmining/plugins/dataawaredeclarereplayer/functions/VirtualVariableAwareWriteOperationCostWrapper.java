package org.processmining.plugins.dataawaredeclarereplayer.functions;

import java.util.HashMap;
import java.util.Map;

import org.processmining.plugins.DataConformance.framework.WriteOperationCost;
import org.processmining.plugins.dataawaredeclarereplayer.mapping.Variable;
import org.processmining.plugins.dataawaredeclarereplayer.mapping.VariableAwareWriteOperationCost;

/**
 * Adopted from
 * {@link org.processmining.plugins.balancedconformance.functions.VirtualVariableAwareWriteOperationCostWrapper}
 * 
 * @author C.T Mawoko
 * 
 */
public final class VirtualVariableAwareWriteOperationCostWrapper implements VariableAwareWriteOperationCost {

	private final WriteOperationCost variableCost;
	private final Map<String, VirtualVariable> artificalVariables;

	public VirtualVariableAwareWriteOperationCostWrapper(WriteOperationCost writeOperationCost,
			Map<String, Variable> variables) {
		this.variableCost = writeOperationCost;
		this.artificalVariables = new HashMap<>();
		for (Variable var : variables.values()) {
			if (var.isVirtual()) {
				artificalVariables.put(var.getName(), var.getVirtualVariable());
			}
		}
	}

	@Override
	public float lowestCost() {
		return variableCost.lowestCost();
	}

	@Override
	public float highestCost() {
		return variableCost.highestCost();
	}

	@Override
	public float costFaultyValue(String activity, String variableName) {
		VirtualVariable artificalVariable = artificalVariables.get(variableName);
		if (artificalVariable != null) {
			return artificalVariable.getFaultyValueCost();
		}
		return variableCost.costFaultyValue(activity, variableName);
	}

	@Override
	public int costFaultyValue(String activity, Variable variable) {
		if (variable.isVirtual()) {
			return variable.getVirtualVariable().getFaultyValueCost();
		} else {
			return (int) variableCost.costFaultyValue(activity, variable.getName());
		}
	}

	@Override
	public float notWritingCost(String activity, String variableName) {
		VirtualVariable artificalVariable = artificalVariables.get(variableName);
		if (artificalVariable != null) {
			return artificalVariable.getMissingValueCost();
		}
		return variableCost.notWritingCost(activity, variableName);
	}

	@Override
	public int notWritingCost(String activity, Variable variable) {
		if (variable.isVirtual()) {
			return variable.getVirtualVariable().getMissingValueCost();
		} else {
			return (int) variableCost.notWritingCost(activity, variable.getName());
		}
	}

	@Override
	public boolean isFinal(String activity, String variableName) {
		VirtualVariable artificalVariable = artificalVariables.get(variableName);
		if (artificalVariable != null) {
			return artificalVariable.isFinal();
		}
		return variableCost.isFinal(activity, variableName);
	}

	@Override
	public boolean isFinal(String activity, Variable variable) {
		if (variable.isVirtual()) {
			return variable.getVirtualVariable().isFinal();
		}
		return variableCost.isFinal(activity, variable.getName());
	}
}
