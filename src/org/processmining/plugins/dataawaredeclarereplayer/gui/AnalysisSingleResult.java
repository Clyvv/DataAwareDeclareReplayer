package org.processmining.plugins.dataawaredeclarereplayer.gui;

import java.util.HashSet;
import java.util.Set;

import org.processmining.plugins.DataConformance.Alignment;

public class AnalysisSingleResult implements Comparable<AnalysisSingleResult> {

	private final Alignment alignment;
	private Set<Integer> movesInBoth = new HashSet<Integer>();
	private Set<Integer> movesInBothDiffData = new HashSet<Integer>();
	private Set<Integer> movesInLog = new HashSet<Integer>();
	private Set<Integer> movesInModel = new HashSet<Integer>();

	/**
	 * 
	 * @param constraint
	 * @param trace
	 * @param activations
	 * @param violations
	 * @param fulfilments
	 */
	public AnalysisSingleResult(Alignment alignment, Set<Integer> movesInBoth, Set<Integer> movesInBothDiffData,
			Set<Integer> movesInLog, Set<Integer> movesInModel) {
		this.alignment = alignment;
		this.movesInBoth = movesInBoth;
		this.movesInBothDiffData = movesInBothDiffData;
		this.movesInLog = movesInLog;
		this.movesInModel = movesInModel;
	}

	public Alignment getAlignment() {
		return alignment;
	}

	public Set<Integer> getMovesInBoth() {
		return movesInBoth;
	}

	public Set<Integer> getMovesInBothDiffData() {
		return movesInBothDiffData;
	}

	public Set<Integer> getMovesInLog() {
		return movesInLog;
	}

	public Set<Integer> getMovesInModel() {
		return movesInModel;
	}

	@Override
	public int compareTo(AnalysisSingleResult o) {
		return alignment.getTraceName().compareTo(o.alignment.getTraceName());
	}
}
