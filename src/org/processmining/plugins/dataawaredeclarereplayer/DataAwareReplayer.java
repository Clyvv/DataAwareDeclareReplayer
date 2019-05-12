package org.processmining.plugins.dataawaredeclarereplayer;

import java.text.ParseException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.plugins.DataConformance.framework.ActivityMatchCosts;
import org.processmining.plugins.DataConformance.framework.ExecutionStep;
import org.processmining.plugins.DataConformance.framework.ExecutionTrace;
import org.processmining.plugins.DataConformance.framework.LogExecutionTrace;
import org.processmining.plugins.DataConformance.framework.Replayable;
import org.processmining.plugins.DataConformance.framework.ReplayableActivity;
import org.processmining.plugins.DataConformance.framework.VariableMatchCosts;
import org.processmining.plugins.dataawaredeclarereplayer.mapping.StringDiscretizer;

@SuppressWarnings("rawtypes")
public class DataAwareReplayer extends Thread {
	private Replayable model;

	private XTrace trace;
	private HashSet<DataAlignmentState> list;
	private HashMap<SimpleEntry<ExecutionTrace, ExecutionTrace>, Float> visitedNode = new HashMap<SimpleEntry<ExecutionTrace, ExecutionTrace>, Float>();
	private UIPluginContext bar;

	public int visitedNodes = 0;
	public long treeSize = 0;

	private boolean prune;
	protected DataReplayingHelper helper;

	public DataAwareReplayer(DataAwareDeclare model, Map<ReplayableActivity, XEventClass> activityMapping,
			Map<String, String> variableMapping, XEventClasses eventClasses, XTrace trace,
			ActivityMatchCosts activityCost, VariableMatchCosts variableCost, Map<String, Float> maxDistance,
			HashSet<DataAlignmentState> list, UIPluginContext context, boolean prune,
			Map<String, Set<String>> variablesToWrite, Map<String, Object> lowerBounds, Map<String, Object> upperBounds,
			Map<String, Class> variableTypes, StringDiscretizer stringDiscretizer) {
		this.model = model;
		this.trace = trace;
		this.list = list;
		this.bar = context;
		helper = new DataReplayingHelper(model, trace, activityMapping, variableMapping, eventClasses, maxDistance,
				activityCost, variableCost, variablesToWrite, lowerBounds, upperBounds, variableTypes,
				stringDiscretizer);
		this.prune = prune;
	}

	public void run() {
		try {
			DataAlignmentState replayingState = perform();
			if (replayingState == null)
				bar.log("Impossible to replay the model: is the model sound?");
			else
				replayingState.setOriginalTrace(this.trace);
			list.add(replayingState);
		} catch (Exception err) {
			synchronized (bar) {
				bar.log("Error while replaying trace " + trace.getAttributes().get("concept:name") + ": "
						+ err.getMessage());
			}
			err.printStackTrace();
		}
		synchronized (bar) {
			bar.getProgress().inc();
		}
		synchronized (model) {
			model.notify();
		}
	}

	public DataAlignmentState perform() throws ParseException {
		ExecutionTrace sigmaT = helper.buildLogSequence();
		PriorityQueue<DataAlignmentState> openList = new PriorityQueue<DataAlignmentState>();
		DataAlignmentState mu0 = new DataAlignmentState();
		mu0.processTracePrefix = model.buildEmptyPrefix();
		mu0.logTracePrefix = model.createLogExecutionTrace(sigmaT);

		DataAlignmentState mu = mu0;

		while (mu != null && (!mu.processTracePrefix.isComplete() || !mu.logTracePrefix.isComplete())) {
			Collection<DataAlignmentState> successors = helper.createSuccessors(mu);
			for (DataAlignmentState mu1 : successors) {
				if (mu1.logTracePrefix.getLast() != ExecutionStep.bottomStep
						&& mu1.processTracePrefix.getLast() != ExecutionStep.bottomStep) {
					helper.augment(mu1);
				}
				if (mu1 != null) {
					mu1.setH_cost(helper.computeHeuristicValue(mu1, sigmaT));
					float mu_g_cost = mu.getCost() - mu.getH_cost();
					mu1.setF_cost(mu_g_cost + helper.computeDistance(mu, mu1) + mu1.getH_cost());
					if (!prune || toBeVisited(mu1)) {
						openList.add(mu1);
						treeSize++;
					}
				}
			}

			mu = openList.poll();
			visitedNodes++;
		}
		openList.clear();
		if (mu != null) {
			mu.visitedNodes = visitedNodes;
			mu.treeSize = treeSize;
		}
		return mu;
	}

	private boolean toBeVisited(DataAlignmentState mu1) {
		SimpleEntry<ExecutionTrace, ExecutionTrace> entry = new SimpleEntry<ExecutionTrace, ExecutionTrace>(
				((LogExecutionTrace) mu1.logTracePrefix).removeBottomEvent(), mu1.processTracePrefix);
		float gValueForMu1 = mu1.getCost() - mu1.getH_cost();
		Float gValue = visitedNode.get(entry);
		if (gValue == null || gValueForMu1 < gValue) {
			visitedNode.put(entry, gValueForMu1);
			return true;
		} else
			return false;
	}
}