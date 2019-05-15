package org.processmining.plugins.dataawaredeclarereplayer.mapping;


import org.processmining.plugins.dataawaredeclarereplayer.functions.VirtualVariable;

public final class Variable {
	public Variable(String name, Class<?> type) {
		this.name = name.intern();
		this.type = type;
	}

	private final String name;
	private final Class<?> type;

	private String attributeName;

	private Object defaultValue;
	private Object upperBound;
	private Object lowerBound;

	private Double ilpUpperBound;
	private Double ilpLowerBound;
	private Double ilpDefaultValue;

	private boolean isUsedInGuard = false;
	private boolean isVirtual = false;

	private VirtualVariable virtualVariable;

	public VirtualVariable getVirtualVariable() {
		return virtualVariable;
	}

	public void setVirtualVariable(VirtualVariable virtualVariable) {
		this.virtualVariable = virtualVariable;
	}

	public boolean isUsedInGuard() {
		return isUsedInGuard;
	}

	public void setUsedInGuard(boolean isUsedInGuard) {
		this.isUsedInGuard = isUsedInGuard;
	}

	public Double getIlpDefaultValue() {
		return ilpDefaultValue;
	}

	public void setIlpDefaultValue(Double ilpDefaultValue) {
		this.ilpDefaultValue = ilpDefaultValue;
	}

	public Double getIlpLowerBound() {
		return ilpLowerBound;
	}

	public void setIlpLowerBound(Double ilpLowerBound) {
		this.ilpLowerBound = ilpLowerBound;
	}

	public boolean isVirtual() {
		return isVirtual;
	}

	public void setVirtual(boolean isVirtual) {
		this.isVirtual = isVirtual;
	}

	public Double getIlpUpperBound() {
		return ilpUpperBound;
	}

	public void setIlpUpperBound(Double ilpUpperBound) {
		this.ilpUpperBound = ilpUpperBound;
	}

	public Object getLowerBound() {
		return lowerBound;
	}

	public void setLowerBound(Object lowerBound) {
		this.lowerBound = lowerBound;
	}

	public Object getUpperBound() {
		return upperBound;
	}

	public void setUpperBound(Object upperBound) {
		this.upperBound = upperBound;
	}

	public Object getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(Object defaultValue) {
		this.defaultValue = defaultValue;
	}

	public String getAttributeName() {
		return attributeName;
	}

	public void setAttributeName(String attributeName) {
		this.attributeName = attributeName;
	}

	public Class<?> getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public String toString() {
		return "Variable [name=" + getName() + ", type=" + getType() + ", isArtifical=" + isVirtual() + "]";
	}

}
