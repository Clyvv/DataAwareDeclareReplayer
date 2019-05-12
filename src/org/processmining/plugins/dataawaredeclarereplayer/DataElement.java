package org.processmining.plugins.dataawaredeclarereplayer;

import java.io.Serializable;

import org.processmining.models.graphbased.NodeID;

public class DataElement implements Serializable {

	private static final long serialVersionUID = -8923126527923571380L;
	
	private final String varName;
	private final Class<?> type;
	private final Comparable<?> minValue;
	private final Comparable<?> maxValue;
	private final NodeID id = new NodeID();

	@SuppressWarnings("rawtypes")
	public DataElement(String varName, Class type, Comparable minValue, Comparable maxValue) {
		if (minValue != null && !minValue.getClass().equals(type))
			throw new IllegalArgumentException("The minimum value is incompatible with " + type);
		if (maxValue != null && !maxValue.getClass().equals(type))
			throw new IllegalArgumentException("The maximum value is incompatible with " + type);
		if (maxValue != null && minValue != null && maxValue.compareTo(minValue) < 0)
			throw new IllegalArgumentException(maxValue + "is smaller than " + minValue);
		this.varName = varName;
		this.minValue = minValue;
		this.maxValue = maxValue;
		this.type = type;

	}

	public String toString() {
		return getVarName();
	}

	public String getVarName() {
		return varName;
	}

	@SuppressWarnings("rawtypes")
	public Class getType() {
		return type;
	}

	@Override
	public int hashCode() {
		return varName.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DataElement other = (DataElement) obj;
		return varName.equals(other.varName);
	}

	public NodeID getId() {
		return id;
	}
	@SuppressWarnings("rawtypes")
	public Comparable getMinValue() {
		return minValue;
	}

	@SuppressWarnings("rawtypes")
	public Comparable getMaxValue() {
		return maxValue;
	}
}
