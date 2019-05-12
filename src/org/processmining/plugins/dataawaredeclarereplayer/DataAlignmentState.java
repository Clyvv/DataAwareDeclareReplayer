package org.processmining.plugins.dataawaredeclarereplayer;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.deckfour.xes.model.XTrace;
import org.processmining.plugins.DataConformance.framework.ExecutionStep;
import org.processmining.plugins.DataConformance.framework.ExecutionTrace;
import org.processmining.plugins.DataConformance.framework.ReplayState;

public class DataAlignmentState extends ReplayState {
	private float controlFlowFitness = 1.0f;
	public ExecutionTrace processTracePrefix;
	public ExecutionTrace logTracePrefix;
	private XTrace originalTrace;
	
	public DataAlignmentState() {}

	public DataAlignmentState(ExecutionTrace logTrace, ExecutionTrace processTrace, float cost) {
		this(logTrace, processTrace, cost, true);
	}

	public DataAlignmentState(ExecutionTrace logTrace, ExecutionTrace processTrace, float cost,
			boolean adjustStringValues) {
		if (adjustStringValues) {
			// This is a workaround for the inprecise String representation in the 'old' data conformance checker.
			Iterator<ExecutionStep> logIter = logTrace.iterator();
			for (ExecutionStep processStep : processTrace) {
				ExecutionStep logStep = logIter.next();
				for (Entry<String, Object> entry : processStep.entrySet()) {
					if (!(entry.getValue() instanceof String))
						continue;
					String processVarValue = (String) entry.getValue();
					Object logVarValue = logStep.get(entry.getKey());
					if (logVarValue != null && logVarValue instanceof String) {
						String stringLogVarValue = (String) logVarValue;
						if (stringLogVarValue.length() > 4) {
							int stringLength = (int) Math
									.ceil((Math.min(stringLogVarValue.length(), processVarValue.length()) * 0.75));
							if (stringLength > 0)
								if (stringLogVarValue.substring(0, stringLength)
										.equals(processVarValue.substring(0, stringLength)))
									entry.setValue(logVarValue);
						}
					}
				}
			}
		}
		this.processTracePrefix = processTrace;
		this.logTracePrefix = logTrace;
		super.processTracePrefix = processTrace;
		super.logTracePrefix = logTrace;
		super.f_cost = cost;
	}

	public float getControlFlowFitness() {
		return controlFlowFitness;
	}

	public void setControlFlowFitness(float controlFlowFitness) {
		this.controlFlowFitness = controlFlowFitness;

	}
	public List<ExecutionStep> getProcessTracePrefix() {
		return Collections.unmodifiableList(processTracePrefix);
	}

	public List<ExecutionStep> getLogTracePrefix() {
		return Collections.unmodifiableList(logTracePrefix);
	}

	public String getTraceName() {
		return logTracePrefix.getTraceName();
	}

	public float getCost() {
		return f_cost;
	}

	public void setF_cost(float f_cost) {
		this.f_cost = f_cost;
	}

	public void setH_cost(float h_cost) {
		this.h_cost = h_cost;
	}
	
	public float getH_cost() {
		return h_cost;
	}
	
	public XTrace getOriginalTrace() {
		return originalTrace;
	}

	public void setOriginalTrace(XTrace originalTrace) {
		this.originalTrace = originalTrace;
	}
	
}
