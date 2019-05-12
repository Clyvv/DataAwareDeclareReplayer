package org.processmining.plugins.dataawaredeclarereplayer.result;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.processmining.framework.annotations.AuthoredType;
import org.processmining.plugins.DataConformance.Alignment;
import org.processmining.plugins.dataawaredeclarereplayer.gui.AnalysisSingleResult;

/**
 * Result type of the Data Aware Declare Replayer
 * 
 * @author Clive Tinashe Mawoko
 */
@AuthoredType(typeName = "Data Aware Declare Alignment Result", author = "Clive T Mawoko", email = "clive.tinashe.mawoko@ut.ee", affiliation = "University of Tartu")
public class AnalysisResult {

	private final int traceCount;
	private final int numberOfConstraints;
	private final long computationTime;

	/* counters detailed per trace */
	private Map<Alignment, Set<AnalysisSingleResult>> detailedResults = new HashMap<Alignment, Set<AnalysisSingleResult>>();

	public AnalysisResult(int traceCount, int numberOfConstraints, long computationTime) {
		this.traceCount = traceCount;
		this.numberOfConstraints = numberOfConstraints;
		this.computationTime = computationTime;

	}

	public void addResult(Alignment alignment, Set<Integer> movesInBoth, Set<Integer> movesInBothDiffData,
			Set<Integer> movesInLog, Set<Integer> movesInModel) {
		addResult(new AnalysisSingleResult(alignment, movesInBoth, movesInBothDiffData, movesInLog, movesInModel));
	}

	public void addResult(AnalysisSingleResult analysis) {

		Alignment alignment = analysis.getAlignment();

		/* update detailed results */
		Set<AnalysisSingleResult> constraintsDetails = detailedResults.get(alignment);
		if (constraintsDetails == null) {
			constraintsDetails = new HashSet<AnalysisSingleResult>();
		}
		constraintsDetails.add(analysis);
		detailedResults.put(alignment, constraintsDetails);
	}

	public Set<Alignment> getAlignments() {
		return detailedResults.keySet();
	}

	public Set<AnalysisSingleResult> getResults(Alignment alignment) {
		return detailedResults.get(alignment);
	}

	public double getAverageFitness() {
		double sumFitness = 0;

		for (Alignment alignment : getAlignments()) {
			sumFitness += alignment.getFitness();
		}
		return sumFitness / getAlignments().size();
	}

	@SuppressWarnings("cast")
	public Double getMedianFitness() {
		double median;
		double fitnesses[] = new double[getAlignments().size()];
		Object[] alignments = getAlignments().toArray();
		for (int i = 0; i < alignments.length; i++) {
			Alignment alignment = (Alignment) alignments[i];
			fitnesses[i] = alignment.getFitness();
		}
		Arrays.sort(fitnesses);
		if (fitnesses.length % 2 == 0)
			median = ((double) fitnesses[fitnesses.length / 2] + (double) fitnesses[fitnesses.length / 2 - 1]) / 2;
		else
			median = (double) fitnesses[fitnesses.length / 2];
		return median;
	}

	public int getTraceCount() {
		return traceCount;
	}

	public int getNumberOfConstraints() {
		return numberOfConstraints;
	}


	public long getComputationTime() {
		return computationTime;
	}

	public Map<Alignment, Set<AnalysisSingleResult>> getDetailedResults() {
		return detailedResults;
	}

}
