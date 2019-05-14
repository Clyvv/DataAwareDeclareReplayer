package org.processmining.plugins.dataawaredeclarereplayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.Progress;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.plugins.DataConformance.Alignment;
import org.processmining.plugins.DataConformance.framework.ActivityMatchCosts;
import org.processmining.plugins.DataConformance.framework.LogExecutionTrace;
import org.processmining.plugins.DataConformance.framework.VariableMatchCosts;
import org.processmining.plugins.DataConformance.visualization.DataAwareStepTypes;
import org.processmining.plugins.dataawaredeclarereplayer.mapping.LogMapping;
import org.processmining.plugins.dataawaredeclarereplayer.result.AlignmentResult;
import org.processmining.plugins.dataawaredeclarereplayer.result.AlignmentAnalysisResult;
import org.processmining.plugins.declareminer.visualizing.DeclareMap;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;

/**
 * Class with the Data Aware Declare Replayer plugin
 * 
 * @author Clive Tinashe Mawoko
 */
public class DataAwareDeclareReplayerPlugin {

	@Plugin(name = "Data Aware Declare Replayer", parameterLabels = { "An event log",
			"A Declare model with data" }, returnLabels = { "Data Aware Declare Alignment Result" }, returnTypes = {
					AlignmentAnalysisResult.class }, userAccessible = true, help = "Conformance checking of a declare model with data with regard to an event log.")
	@UITopiaVariant(affiliation = "University of Tartu", author = "Clive T Mawoko", email = "clive.tinashe.mawoko@ut.ee")
	public static AlignmentAnalysisResult run(UIPluginContext context, XLog log, DeclareMap dmap) throws Exception {

		DataAwareDeclare model = new DataAwareDeclare(dmap);
		XLogInfo summary = XLogInfoFactory.createLogInfo(log);
		XEventClasses eventClasses = summary.getEventClasses();
		DataAwareConformanceConfiguration config = DataAwareConformanceConfiguration.initValues(model, log,
				eventClasses, context);

		Progress progBar = context.getProgress();
		progBar.setMinimum(0);
		progBar.setMaximum(log.size());
		progBar.setValue(0);

		ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		final LogMapping logMapping = new LogMapping(config, model, log);

		final GroupedTraces groupedTraces = createGroupedTraces(progBar, log, logMapping, context);

		Collection<DataAlignmentState> list = new ArrayList<>();
		try {
			Callable<Collection<DataAlignmentState>> callable = new Callable<Collection<DataAlignmentState>>() {
				public Collection<DataAlignmentState> call() throws Exception {
					Collection<DataAlignmentState> list = MultiThreadDeclareConformanceChecking.perform(model,
							config.getActivityMapping(), config.getVariableMapping(), eventClasses, groupedTraces,
							config.getActivityCost(), config.getVariableCost(), context, config.isPrune(),
							config.getVariablesToWrite(), config.getLowerBounds(), config.getUpperBounds(),
							config.getVariableTypes(), logMapping.getStringDiscretizer());

					return list;
				}
			};

			Future<Collection<DataAlignmentState>> future = service.submit(callable);

			long beforeTime = System.currentTimeMillis();
			list = future.get();
			long afterTime = System.currentTimeMillis();

			context.log("Computation time:" + (afterTime - beforeTime));
			long visitedNode = 0;
			long sizeTree = 0;
			for (DataAlignmentState s : list) {
				visitedNode += s.visitedNodes;
				sizeTree += s.treeSize;

			}
			context.log("Average number visited nodes: " + (visitedNode / list.size()));
			context.log("Average number nodes of the tree: " + (sizeTree / list.size()));
			AlignmentResult alignmentResult = prepareAlignmentResult(list, config.getActivityCost(),
					config.getVariableCost(), config.getVariableMapping(), model, log, groupedTraces,
					new XEventNameClassifier(), context);
			return prepareAnalysisResult(alignmentResult, model, log, (afterTime - beforeTime));
		} finally {
			service.shutdown();
		}
	}

	private static GroupedTraces createGroupedTraces(final Progress progressListener, final XLog log,
			final LogMapping logMapping, UIPluginContext context) {
		Stopwatch stopwatch = Stopwatch.createStarted();
		context.log("Grouping identical traces ...");
		final GroupedTraces groupedTraces = groupTraces(log, logMapping);
		int numResults = groupedTraces.size();
		context.log(String.format("Grouped %s traces to %s groups for alignment in %s ms...", log.size(), numResults,
				stopwatch.elapsed(TimeUnit.MILLISECONDS)));

		progressListener.setMinimum(0);
		progressListener.setValue(0);
		progressListener.setMaximum(numResults + 3);
		return groupedTraces;
	}

	private static GroupedTraces groupTraces(final XLog log, final LogMapping logMapping) {
		final GroupedTraces groupedTraces = new GroupedTraces(log, logMapping);
		ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		List<List<XTrace>> partitionedLog = Lists.partition(log, 10000);
		if (partitionedLog.size() == 1) {
			for (XTrace trace : partitionedLog.iterator().next()) {
				groupedTraces.add(trace);
			}
		} else {
			List<Callable<Void>> callables = new ArrayList<>();
			for (final List<XTrace> subTraces : partitionedLog) {
				callables.add(new Callable<Void>() {

					public Void call() throws Exception {
						for (XTrace trace : subTraces) {
							groupedTraces.add(trace);
						}
						return null;
					}
				});
			}
			try {
				pool.invokeAll(callables);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		return groupedTraces;
	}

	private static AlignmentResult prepareAlignmentResult(Collection<DataAlignmentState> list,
			ActivityMatchCosts<XEventClass> activityCost, VariableMatchCosts variableCost,
			Map<String, String> variableMapping, DataAwareDeclare model, XLog log, GroupedTraces groupedTraces,
			XEventNameClassifier classifier, UIPluginContext context) {
		context.log("Gathering results");
		Collection<DataAlignmentState> result = new ArrayList<>();

		for (DataAlignmentState state : list) {
			for (XTrace trace : groupedTraces.getTracesInGroup(state.getOriginalTrace())) {
				XAttribute name = trace.getAttributes().get("concept:name");
				LogExecutionTrace logTrace = new LogExecutionTrace(name.toString());
				logTrace.addAll(state.getLogTracePrefix());
				result.add(new DataAlignmentState(logTrace, state.processTracePrefix, state.getCost()));
			}
		}

		return new AlignmentResult(result, activityCost, variableCost, variableMapping, model, log, classifier);
	}

	private static AlignmentAnalysisResult prepareAnalysisResult(AlignmentResult alignmentResult, DataAwareDeclare model,
			XLog log, long computationTime) {

		AlignmentAnalysisResult result = new AlignmentAnalysisResult(log.size(), model.getModel().constraintDefinitionsCount(),
				computationTime);

		for (Alignment alignment : alignmentResult.getAlignments()) {
			Set<Integer> movesInBoth = new HashSet<Integer>();
			Set<Integer> movesInBothDiffData = new HashSet<Integer>();
			Set<Integer> movesInLog = new HashSet<Integer>();
			Set<Integer> movesInModel = new HashSet<Integer>();
			int i = 0;
			for (DataAwareStepTypes stepType : alignment.getStepTypes()) {
				if (stepType == DataAwareStepTypes.LMGOOD) {
					movesInBoth.add(i);
				}

				if (stepType == DataAwareStepTypes.LMNOGOOD) {
					movesInBothDiffData.add(i);
				}

				if (stepType == DataAwareStepTypes.L) {
					movesInLog.add(i);
				}

				if (stepType == DataAwareStepTypes.MREAL) {
					movesInModel.add(i);
				}

				i++;
			}

			result.addResult(alignment, movesInBoth, movesInBothDiffData, movesInLog, movesInModel);
		}

		return result;
	}

}