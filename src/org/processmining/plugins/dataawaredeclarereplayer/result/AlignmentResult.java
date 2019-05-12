package org.processmining.plugins.dataawaredeclarereplayer.result;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.model.XLog;
import org.processmining.plugins.DataConformance.Alignment;
import org.processmining.plugins.DataConformance.ResultReplay;
import org.processmining.plugins.DataConformance.framework.ActivityMatchCosts;
import org.processmining.plugins.DataConformance.framework.ExecutionStep;
import org.processmining.plugins.DataConformance.framework.ReplayState;
import org.processmining.plugins.DataConformance.framework.VariableMatchCosts;
import org.processmining.plugins.DataConformance.visualization.DataAwareStepTypes;
import org.processmining.plugins.balancedconformance.result.StatisticResult;
import org.processmining.plugins.dataawaredeclarereplayer.DataAlignmentState;
import org.processmining.plugins.dataawaredeclarereplayer.DataAwareDeclare;

public class AlignmentResult extends ResultReplay {

	private static final long serialVersionUID = 1702620120478358836L;

	private long calculationTime;
	private final Map<String, StatisticResult> statisticsStore;
	private final DataAwareDeclare model;

	public AlignmentResult(Collection<DataAlignmentState> list, ActivityMatchCosts<XEventClass> activityCost,
			VariableMatchCosts variableCost, Map<String, String> variableMapping, DataAwareDeclare model, XLog log,
			XEventNameClassifier classifier) {
		super(list, activityCost, variableCost, variableMapping, log, classifier);
		this.model = model;
		statisticsStore = new LinkedHashMap<>();
	}

	@Override
	protected void calcReplayResult(Collection<? extends ReplayState> list) {

		double sumFitness = 0;
		long visitedNodes = 0;
		long treeSizes = 0;

		for (ReplayState state : list) {
			if (state != null) { // might not have computed
				visitedNodes += state.visitedNodes;
				treeSizes += state.treeSize;
				float fitness = 0;
				fitness = computeFitness(state);
				sumFitness += fitness;

				Alignment align = createSmallAlignment(state, fitness, new StatisticsCallback() {

					public void updateStatistics(ExecutionStep logStep, ExecutionStep processStep) {
						updateStatisticsMap(logStep, processStep);

					}
				});

				labelStepArray.add(align);
				nameToAlignmentMap.put(state.getTraceName(), align);
			}
		}

		meanVisitedNode = visitedNodes / ((float) list.size());
		meanBuiltTreeSize = treeSizes / ((float) list.size());
		meanFitness = Double.valueOf(sumFitness / list.size()).floatValue();

		for (float[] elem : attrArray.values()) {
			elem[3] = (float) Math.sqrt(elem[3] / (elem[0] - elem[1]));
		}

	}

	public static Alignment createSmallAlignment(ReplayState state, float fitness, StatisticsCallback callback) {
		Iterator<ExecutionStep> logTraceIter = state.getLogTracePrefix().iterator();
		Iterator<ExecutionStep> processTraceIter = state.getProcessTracePrefix().iterator();

		List<DataAwareStepTypes> stepArray = new ArrayList<>();
		Alignment align = new Alignment(state, fitness, Collections.<String>emptyList(), stepArray);

		while (logTraceIter.hasNext() && processTraceIter.hasNext()) {
			ExecutionStep processStep = processTraceIter.next();
			ExecutionStep logStep = logTraceIter.next();

			if (fitness > 0 && callback != null) {
				callback.updateStatistics(logStep, processStep);
			}

			if (logStep == ExecutionStep.bottomStep) {
				stepArray.add(DataAwareStepTypes.MREAL);
			} else if (processStep == ExecutionStep.bottomStep) {
				stepArray.add(DataAwareStepTypes.L);
			} else {
				if (processStep.entrySet().equals(logStep.entrySet())) {
					stepArray.add(DataAwareStepTypes.LMGOOD);
				} else {
					stepArray.add(DataAwareStepTypes.LMNOGOOD);
				}
			}
		}
		return align;
	}

	public long getCalcTime() {
		return calculationTime;
	}

	public void setCalcTime(long calcTime) {
		this.calculationTime = calcTime;
	}

	public Map<String, StatisticResult> getStatisticsStore() {
		return statisticsStore;
	}

	public DataAwareDeclare getModel() {
		return model;
	}

}
