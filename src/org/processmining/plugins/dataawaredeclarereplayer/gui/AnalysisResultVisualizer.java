package org.processmining.plugins.dataawaredeclarereplayer.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.contexts.uitopia.annotations.Visualizer;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.util.ui.widgets.ProMComboBox;
import org.processmining.framework.util.ui.widgets.ProMList;
import org.processmining.framework.util.ui.widgets.ProMScrollPane;
import org.processmining.framework.util.ui.widgets.WidgetColors;
import org.processmining.plugins.DataConformance.Alignment;
import org.processmining.plugins.dataawaredeclarereplayer.result.AlignmentAnalysisResult;
import org.processmining.plugins.dataawaredeclarereplayer.utils.GUIUtils;
import org.processmining.plugins.declareanalyzer.gui.widget.MultilineListEntry;
import org.processmining.plugins.declareanalyzer.gui.widget.MultilineListEntryRenderer;

import com.fluxicon.slickerbox.components.RoundedPanel;
import com.fluxicon.slickerbox.components.SlickerSearchField;
import com.fluxicon.slickerbox.components.SlickerTabbedPane;
import com.fluxicon.slickerbox.factory.SlickerFactory;
import com.fluxicon.slickerbox.ui.SlickerScrollBarUI;
import com.lowagie.text.Font;

public class AnalysisResultVisualizer extends SlickerTabbedPane {

	private static final long serialVersionUID = 321944298882508667L;
	private static HashMap<String, Comparator<AlignmentResultVisualizer>> comparators = new HashMap<String, Comparator<AlignmentResultVisualizer>>();
	
	static {
		comparators.put("Trace name", new Comparator<AlignmentResultVisualizer>() {
			public int compare(AlignmentResultVisualizer o1, AlignmentResultVisualizer o2) {
				return o1.getTraceName().compareTo(o2.getTraceName());
			}
		});
	}
	
	private AlignmentAnalysisResult result;
	private String filter;
	
	private DefaultListModel caseIdsListModel = new DefaultListModel();
	private ProMList<String> caseIdsList;
	private JPanel caseDetails;
	private JPanel tracesContainer;
	private List<AlignmentResultVisualizer> alignmentResultVisualizerList;
	private Comparator<AlignmentResultVisualizer> currentSorter = comparators.get("Trace name");
	

	public AnalysisResultVisualizer() {
		super("Data Aware Declare Alignment Result", GUIUtils.panelBackground, Color.lightGray, Color.gray);
		setBackground(Color.black);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setOpaque(true);
	}
	
	@Plugin(
		name = "Alignment Result Visualizer",
		parameterLabels = { "The result of a data aware declare replayer" },
		returnLabels = { "Alignment visualization" },
		returnTypes = { JComponent.class },
		userAccessible = true
	)
	@UITopiaVariant(
		author = "Clive T Mawoko",
		email = "cmawoko@gmail.com",
		affiliation = "UniTartu"
	)
	@Visualizer(name = "Alignment Result Visualizer")
	public JComponent visualize(UIPluginContext context, AlignmentAnalysisResult result) {
		this.result = result;
		this.filter = "";
		
		populateConstraints();
		initComponents();
		
		return this;
	}
	
	private void populateConstraints() {
		Set<Alignment> alignments = result.getAlignments();
		alignmentResultVisualizerList = new LinkedList<AlignmentResultVisualizer>();
		
		for (Alignment alignment : alignments) {
			AlignmentResultVisualizer visualizer = new AlignmentResultVisualizer(alignment, result.getResults(alignment));
			alignmentResultVisualizerList.add(visualizer);
		}
	}
	
	private void initComponents() {
		/* add everything to the gui */
		addTab("Overall Details", prepareGeneralDetails());
		addTab("Trace/Alignment Details", prepareDetailedView());
		addTab("Trace/Alignment Overview", prepareOverallView());
		
	}
	
	@SuppressWarnings("cast")
	private JPanel prepareGeneralDetails() {

		/* overall details */
		/* ================================================================== */
		RoundedPanel overallDetails = new RoundedPanel(15, 5, 3);
		overallDetails.setLayout(new BorderLayout());
		overallDetails.setBackground(GUIUtils.panelBackground);
		overallDetails.add(GUIUtils.prepareTitle("Alignment Statistics"), BorderLayout.NORTH);
		
		JPanel detailsContainer = new JPanel();
		detailsContainer.setOpaque(false);
		detailsContainer.setLayout(new GridBagLayout());
		
		JScrollPane detailsScrollerContainer = new JScrollPane(detailsContainer);
		detailsScrollerContainer.setOpaque(false);
		detailsScrollerContainer.getViewport().setOpaque(false);
		detailsScrollerContainer.getVerticalScrollBar().setUI(new SlickerScrollBarUI(detailsScrollerContainer.getVerticalScrollBar(), GUIUtils.panelBackground, GUIUtils.panelTextColor, GUIUtils.panelTextColor.brighter(), 4, 11));
		detailsScrollerContainer.getHorizontalScrollBar().setUI(new SlickerScrollBarUI(detailsScrollerContainer.getHorizontalScrollBar(), GUIUtils.panelBackground, GUIUtils.panelTextColor, GUIUtils.panelTextColor.brighter(), 4, 11));
		detailsScrollerContainer.setBorder(BorderFactory.createEmptyBorder());
		overallDetails.add(detailsScrollerContainer, BorderLayout.CENTER);
		
		GridBagConstraints c = new GridBagConstraints();
		c = new GridBagConstraints();
		c.anchor = GridBagConstraints.NORTH;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		GUIUtils.addToGridBagLayout(0, 0, detailsContainer, Box.createVerticalStrut(10));
		GUIUtils.addToGridBagLayout(0, 1, detailsContainer, GUIUtils.prepareTitleBordered("Average Fitness"), c);
		GUIUtils.addToGridBagLayout(0, 2, detailsContainer, GUIUtils.prepareLargeLabel(String.format("%2f", result.getAverageFitness())), c);
		GUIUtils.addToGridBagLayout(0, 2, detailsContainer, Box.createVerticalStrut(10));
		
		GUIUtils.addToGridBagLayout(0, 3, detailsContainer, GUIUtils.prepareTitleBordered("Median Fitness"), c);
		GUIUtils.addToGridBagLayout(0, 4, detailsContainer, GUIUtils.prepareLargeLabel(String.format("%2f", result.getMedianFitness())), c);
		GUIUtils.addToGridBagLayout(0, 4, detailsContainer, Box.createVerticalStrut(10));
		
		GUIUtils.addToGridBagLayout(0, 5, detailsContainer, GUIUtils.prepareTitleBordered("Trace Count"), c);
		GUIUtils.addToGridBagLayout(0, 6, detailsContainer, GUIUtils.prepareLargeLabel(""+result.getTraceCount()), c);
		GUIUtils.addToGridBagLayout(0, 6, detailsContainer, Box.createVerticalStrut(10));
		
		GUIUtils.addToGridBagLayout(0, 7, detailsContainer, GUIUtils.prepareTitleBordered("Number of constraints"), c);
		GUIUtils.addToGridBagLayout(0, 8, detailsContainer, GUIUtils.prepareLargeLabel(""+result.getNumberOfConstraints()), c);
		GUIUtils.addToGridBagLayout(0, 8, detailsContainer, Box.createVerticalStrut(10));
		
		GUIUtils.addToGridBagLayout(0, 9, detailsContainer, GUIUtils.prepareTitleBordered("Computation Time (msec)"), c);
		GUIUtils.addToGridBagLayout(0, 10, detailsContainer, GUIUtils.prepareLargeLabel(""+result.getComputationTime()), c);
		GUIUtils.addToGridBagLayout(0, 10, detailsContainer, Box.createVerticalStrut(10));
		
		GUIUtils.addToGridBagLayout(0, 11, detailsContainer, Box.createVerticalGlue(), 0, 1);
		
		detailsContainer.updateUI();

		return overallDetails;
	}
	
	private JPanel prepareOverallView() {
		
		/* specific details per trace */
		/* ================================================================== */
		// sort field
		final ProMComboBox<String> sorter = new ProMComboBox<String>(comparators.keySet());
		sorter.setPreferredSize(new Dimension(200, 25));
		sorter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				currentSorter = comparators.get(sorter.getSelectedItem());
				refreshTraces(tracesContainer, currentSorter);
			}
		});
		
		// search field
		final SlickerSearchField search = new SlickerSearchField(200, 25, WidgetColors.COLOR_LIST_BG, Color.lightGray, Color.lightGray.brighter(), Color.gray);
		search.addSearchListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				filter = search.getSearchText();
				if (filter.length() >= 2 || filter.length() == 0) {
					refreshTraces(tracesContainer, currentSorter);
				}
			}
		});
		JPanel searchContainer = new JPanel(new FlowLayout());
		searchContainer.setOpaque(false);
		searchContainer.add(GUIUtils.prepareLabel("Trace filter", SwingConstants.LEFT, Color.lightGray));
		searchContainer.add(search);
		searchContainer.add(GUIUtils.prepareLabel("Sort trace by:", SwingConstants.LEFT, Color.lightGray));
		searchContainer.add(sorter);
		
		JPanel titleContainer = new JPanel(new BorderLayout());
		titleContainer.setOpaque(false);
		titleContainer.add(GUIUtils.prepareTitle("Trace/alignment details"), BorderLayout.WEST);
		titleContainer.add(searchContainer, BorderLayout.EAST);
		
		RoundedPanel traceDetails = new RoundedPanel(15, 5, 3);
		traceDetails.setLayout(new BorderLayout());
		traceDetails.setBackground(GUIUtils.panelBackground);
		traceDetails.add(titleContainer, BorderLayout.NORTH);
		
		tracesContainer = new JPanel();
		tracesContainer.setOpaque(false);
		tracesContainer.setLayout(new GridBagLayout());
		
		JScrollPane tracesScrollerContainer = new JScrollPane(tracesContainer);
		tracesScrollerContainer.setOpaque(false);
		tracesScrollerContainer.getViewport().setOpaque(false);
		tracesScrollerContainer.getVerticalScrollBar().setUI(new SlickerScrollBarUI(tracesScrollerContainer.getVerticalScrollBar(), GUIUtils.panelBackground, GUIUtils.panelTextColor, GUIUtils.panelTextColor.brighter(), 4, 11));
		tracesScrollerContainer.getHorizontalScrollBar().setUI(new SlickerScrollBarUI(tracesScrollerContainer.getHorizontalScrollBar(), GUIUtils.panelBackground, GUIUtils.panelTextColor, GUIUtils.panelTextColor.brighter(), 4, 11));
		tracesScrollerContainer.setBorder(BorderFactory.createEmptyBorder());
		tracesScrollerContainer.getVerticalScrollBar().setUnitIncrement(30);
		traceDetails.add(tracesScrollerContainer, BorderLayout.CENTER);
		
		refreshTraces(tracesContainer, currentSorter);
		
		return traceDetails;
	}
	
	private JPanel prepareDetailedView() {
		/* specific details per trace */
		/* ================================================================== */
		// sort field
		final ProMComboBox<String> sorter = new ProMComboBox<String>(comparators.keySet());
		sorter.setPreferredSize(new Dimension(200, 25));
		sorter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				currentSorter = comparators.get(sorter.getSelectedItem());
				new Thread(new Runnable() {
					public void run() {
						refreshTraces(currentSorter);
					}
				}).start();
				
			}
		});
		
		// search field
		final SlickerSearchField search = new SlickerSearchField(200, 25, WidgetColors.COLOR_LIST_BG, Color.lightGray, Color.lightGray.brighter(), Color.gray);
		final Timer timer = new Timer(true);
		search.addSearchListener(new ActionListener() {
			private TimerTask task;
			public void actionPerformed(ActionEvent e) {
				if(task != null) {
					task.cancel();
				}
				task = new TimerTask() {
					@Override
					public void run() {
						filter = search.getSearchText();
						if (filter.length() >= 2 || filter.length() == 0) {
							refreshTraces(currentSorter);
						}
					}
				};
				timer.schedule(task, 1000);
			}
		});
		JPanel searchContainer = new JPanel(new FlowLayout());
		searchContainer.setOpaque(false);
		searchContainer.add(GUIUtils.prepareLabel("Trace filter", SwingConstants.LEFT, Color.lightGray));
		searchContainer.add(search);
		searchContainer.add(GUIUtils.prepareLabel("Sort trace by:", SwingConstants.LEFT, Color.lightGray));
		searchContainer.add(sorter);
		
		JPanel titleContainer = new JPanel(new BorderLayout());
		titleContainer.setOpaque(false);
		titleContainer.add(GUIUtils.prepareTitle("Details per Trace"), BorderLayout.WEST);
		titleContainer.add(searchContainer, BorderLayout.EAST);
		
		RoundedPanel traceDetails = new RoundedPanel(5, 10,0);
		traceDetails.setLayout(new BorderLayout());
		traceDetails.setBackground(GUIUtils.panelBackground);
		traceDetails.add(titleContainer, BorderLayout.NORTH);
		
		
		JPanel boxContainer = new JPanel();
		boxContainer.setOpaque(false);
		
		caseIdsList = new ProMList<String>("Log Traces", caseIdsListModel);
		caseIdsList.setPreferredSize(new Dimension(250, 10000));
		caseIdsList.setMinimumSize(new Dimension(250, 100));
		caseIdsList.setMaximumSize(new Dimension(250, 10000));
		caseIdsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		caseIdsList.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				showTraceDetails();
			}
		});
		((JList)((JViewport)((ProMScrollPane) caseIdsList.getComponent(2)).getComponent(0)).getComponent(0)).setCellRenderer(new MultilineListEntryRenderer());

		
		JLabel tmpLabel = GUIUtils.prepareLabel("Please select a trace...");
		tmpLabel.setFont(tmpLabel.getFont().deriveFont(14f).deriveFont(Font.ITALIC));
		tmpLabel.setForeground(GUIUtils.tabForeground);
		tmpLabel.setHorizontalAlignment(JLabel.CENTER);
		
		caseDetails = SlickerFactory.instance().createRoundedPanel(15, WidgetColors.COLOR_ENCLOSURE_BG);
		caseDetails.setLayout(new BorderLayout(0,15));
		caseDetails.add(tmpLabel);
		
		boxContainer.setLayout(new BoxLayout(boxContainer, BoxLayout.X_AXIS));
		boxContainer.add(caseIdsList);
		boxContainer.add(Box.createHorizontalStrut(10));
		boxContainer.add(caseDetails);
		
		// populate case ids
		refreshTraces(currentSorter);
		
		// populate constraints
		
		traceDetails.add(boxContainer);
		
		return traceDetails;
	}
	
	private void refreshTraces(Comparator<AlignmentResultVisualizer> comparator) {
		caseIdsListModel.removeAllElements();
		Collections.sort(alignmentResultVisualizerList, comparator);
		
		for (AlignmentResultVisualizer visualizer : alignmentResultVisualizerList) {
			String traceName = visualizer.getTraceName();
			
			if (traceName.contains(filter)) {
				caseIdsListModel.addElement(new MultilineListEntry<Alignment>(visualizer.getAlignment(), traceName, " Fitness: " +
						visualizer.getFitness()));
			}
		}
		caseIdsList.updateUI();
	}
	
	private void refreshTraces(JPanel tracesContainer, Comparator<AlignmentResultVisualizer> comparator) {
		
		tracesContainer.removeAll();
		
		GridBagConstraints c = new GridBagConstraints();
		GUIUtils.addToGridBagLayout(0, 0, tracesContainer, Box.createVerticalStrut(10));
		int i = 1;
		
		Collections.sort(alignmentResultVisualizerList, comparator);
		
		for (AlignmentResultVisualizer visualizer : alignmentResultVisualizerList) {
			String traceName = visualizer.getTraceName();
			
			if (traceName.contains(filter)) {
				c = new GridBagConstraints();
				c.anchor = GridBagConstraints.WEST;
				c.fill = GridBagConstraints.BOTH;
				c.weightx = 1;
				
				GUIUtils.addToGridBagLayout(0, i, tracesContainer, GUIUtils.prepareTitleBordered(traceName), c);
				GUIUtils.addToGridBagLayout(0, i + 1, tracesContainer, visualizer, c);
				GUIUtils.addToGridBagLayout(0, i + 2, tracesContainer, Box.createVerticalStrut(10));
				
				i += 2;
			}
		}
		
		GUIUtils.addToGridBagLayout(0, i + 2, tracesContainer, Box.createVerticalGlue(), 0, 1);
		
		tracesContainer.updateUI();
	}
	
	/**
	 * This method shows the actual trace for the selected trace and constraint.
	 */
	@SuppressWarnings({ "cast", "unchecked" })
	private void showTraceDetails() {
		int selectedTraceIndex = ((JList)((JViewport)((ProMScrollPane) caseIdsList.getComponent(2)).getComponent(0)).getComponent(0)).getSelectedIndex();
		if (selectedTraceIndex == -1) {
			return;
		}
		
		// extract the trace and constraint objects from the list models
		Alignment selectedTrace = (Alignment) ((MultilineListEntry<Alignment>) caseIdsListModel.get(selectedTraceIndex)).getObject();
		
		// extract the actual single result to show
		Set<AnalysisSingleResult> analysisResults = result.getResults(selectedTrace);
		AnalysisSingleResult analysisResult = null;
		for(AnalysisSingleResult result : analysisResults) {
			analysisResult = result;
			break;
		}
		
		JPanel statistics = SlickerFactory.instance().createRoundedPanel(15, Color.GRAY);
		statistics.setLayout(new GridLayout(0, 2));
		statistics.add(GUIUtils.prepareLabel("Move in log and model: " + analysisResult.getMovesInBoth().size()));
		statistics.add(GUIUtils.prepareLabel("Fitness: " + parseNicely(Double.valueOf(analysisResult.getAlignment().getFitness()))));
		statistics.add(GUIUtils.prepareLabel("Move in log and model with different data: " + analysisResult.getMovesInBothDiffData().size()));
		statistics.add(GUIUtils.prepareLabel(""));
		statistics.add(GUIUtils.prepareLabel("Move in log: " + analysisResult.getMovesInLog().size()));
		statistics.add(GUIUtils.prepareLabel(""));
		statistics.add(GUIUtils.prepareLabel("Move in model: " + analysisResult.getMovesInModel().size()));
		
		
		SingleResultVisualizer visualizer = new SingleResultVisualizer(analysisResult);
		JScrollPane caseDetailsContainer = new ProMScrollPane(visualizer);
		caseDetails.removeAll();
		caseDetails.add(statistics, BorderLayout.NORTH);
		caseDetails.add(caseDetailsContainer, BorderLayout.CENTER);
		caseDetails.revalidate();
	}
	
	private String parseNicely(Double value) {
		if (value.isNaN()) {
			return "/";
		}
		return GUIUtils.df2.format(value);
	}
}
