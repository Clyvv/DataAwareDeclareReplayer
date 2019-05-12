package org.processmining.plugins.dataawaredeclarereplayer.gui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.JPanel;

import org.processmining.plugins.DataConformance.Alignment;
import org.processmining.plugins.DataConformance.Alignment.AlignmentStep;
import org.processmining.plugins.dataawaredeclarereplayer.utils.GUIUtils;


public class AlignmentResultVisualizer extends JPanel implements MouseMotionListener {

	private static final long serialVersionUID = 3287603101866616636L;
	private Alignment alignment;
	private List<AnalysisSingleResult> alignmentResults;
	private HashMap<String, Integer> constraintToPosition;
	
	private int constraintHeight = 20;
	private int eventWidth = 8;
	private int eventHeight = -1;
	private int detailsRectangleHeight = -1;
	private int detailsVerticalMargin = -1;
	
	private Font defaultFont = null;
//	private Font traceFont = null;
	private Font detailsFont = null;
	private FontMetrics defaultFontMetric = null;
//	private FontMetrics traceFontMetric = null;
	private FontMetrics detailsFontMetric = null;
	private SoftReference<BufferedImage> buffer = null;
	
	private int mouseX = -1;
	private int mouseY = -1;
	
	private String traceName;
	private int totMovesInBoth = 0;
	private int totMovesInBothDiffData = 0;
	private int totMovesInLog = 0;
	private int totMovesInModel = 0;
	private float fitness = 0.0f;
	
	/**
	 * 
	 * @param constraints
	 */
	public AlignmentResultVisualizer(Alignment alignment, Set<AnalysisSingleResult> alignmentResult) {
		this.alignment = alignment;
		this.alignmentResults = new ArrayList<AnalysisSingleResult>(alignmentResult.size());
		this.constraintToPosition = new HashMap<String, Integer>();
		
		int i = 0;
		for (AnalysisSingleResult result : alignmentResult) {
			this.alignmentResults.add(i++, result);
		}
		Collections.sort(this.alignmentResults);
		
		// general information
		this.traceName = alignment.getTraceName();
		
		addMouseMotionListener(this);
		
		// general configuration
		int width = alignment.getStepTypes().size() * (eventWidth + 2);
		int height = (alignmentResult.size() + 1) * constraintHeight + 50;
		int maxLetterConstraint = 0;
		
		for (AnalysisSingleResult ar : alignmentResult) {
			
			totMovesInBoth += ar.getMovesInBoth().size();
			totMovesInBothDiffData += ar.getMovesInBothDiffData().size();
			totMovesInLog += ar.getMovesInLog().size();
			totMovesInModel += ar.getMovesInModel().size();
			fitness = ar.getAlignment().getFitness();
			maxLetterConstraint = Math.max(ar.getAlignment().getTraceName().length(), maxLetterConstraint);
		}
		
		// Heuristics on letter width... we don't yet have the font metrics,
		// let's assume an average of 10 pixel per letter
		width += (maxLetterConstraint * 10);
		
		setMinimumSize(new Dimension(width, height));
		setPreferredSize(new Dimension(width, height));
		setOpaque(false);
	}
	
	@Override
	public void paint(Graphics g) {
		super.paint(g);
		
		int width = this.getWidth();
		int height = this.getHeight();
		
		// create new back buffer
		buffer = new SoftReference<BufferedImage>(new BufferedImage(width, height, BufferedImage.TRANSLUCENT));
		Graphics2D g2d = buffer.get().createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		// setting up the "one-time font stuff"
		if (defaultFont == null) {
			defaultFont = g2d.getFont();
			defaultFont = defaultFont.deriveFont(12f);
			defaultFontMetric = g2d.getFontMetrics(defaultFont);
		}
		if (detailsFont == null) {
			detailsFont = defaultFont.deriveFont(10f);
			detailsFontMetric = g2d.getFontMetrics(detailsFont);
		}
		if (eventHeight == -1) {
			eventHeight = defaultFontMetric.getAscent() + defaultFontMetric.getDescent();
		}
		if (detailsRectangleHeight == -1) {
			detailsRectangleHeight = detailsFontMetric.getAscent() + detailsFontMetric.getDescent();
		}
		if (detailsVerticalMargin == -1) {
			detailsVerticalMargin = (defaultFontMetric.getAscent() + defaultFontMetric.getDescent() - detailsRectangleHeight) / 2;
		}
		
		// trace name
		int maxSpace = 0;
		for (AnalysisSingleResult ar : alignmentResults) {
			maxSpace = Math.max(maxSpace, defaultFontMetric.stringWidth(ar.getAlignment().getTraceName()));
		}
		
		// header
		int headerWidth = 100;
		
		g2d.setColor(GUIUtils.panelTextTitleColor);
		g2d.setFont(detailsFont);
		g2d.drawString("Move in log and model: " + getTotMovesInBoth(),
				headerWidth * 0, detailsFontMetric.getAscent() + defaultFontMetric.getDescent());
		g2d.drawString("Move in log and model with different data: " + getTotMovesInBothDiffData(),
				headerWidth * 2, detailsFontMetric.getAscent() + defaultFontMetric.getDescent());
		g2d.drawString("Move in log: " + getTotMovesInLog(),
				headerWidth * 5, detailsFontMetric.getAscent() + defaultFontMetric.getDescent());
		g2d.drawString("Move in model: " + getTotMovesInModel(),
				headerWidth * 6, detailsFontMetric.getAscent() + defaultFontMetric.getDescent());
		g2d.drawString("Fitness: " + GUIUtils.df2.format(getFitness()),
				headerWidth * 0, 2 * (detailsFontMetric.getAscent() + defaultFontMetric.getDescent()));
		
		// trace
		int k = 0;
		int extraHeight = 20;
		for (AnalysisSingleResult ar : alignmentResults) {
			
			int positionY = extraHeight + defaultFontMetric.getAscent() + 6 + (k * constraintHeight);
			
			// trace name
			g2d.setFont(defaultFont);
			g2d.setColor(GUIUtils.panelTextColor);
			g2d.drawString(
					"Alignment",
					maxSpace - defaultFontMetric.stringWidth(ar.getAlignment().getTraceName()) + 5,
					positionY + defaultFontMetric.getAscent());
			constraintToPosition.put(ar.getAlignment().getTraceName(), positionY);
			
			// the actual trace events
			LinkedList<String> eventNames = new LinkedList<String>();
			for (AlignmentStep step : alignment) {
				eventNames.add(step.getLabel());
			}
			paintTrace(g2d, maxSpace + 15, positionY, width, mouseX, mouseY, eventNames,
					ar.getMovesInBoth(), ar.getMovesInBothDiffData(), ar.getMovesInLog(), ar.getMovesInModel(), false);
			
			k++;
		}
		
		// constraint details
		for (AnalysisSingleResult ar : alignmentResults) {
			paintConstraintDetails(g2d, ar,
					0, constraintToPosition.get(ar.getAlignment().getTraceName()),
					maxSpace, defaultFontMetric.getAscent() + defaultFontMetric.getDescent() + 1, mouseX, mouseY, height);
		}
		
		// final paint stuff
		g2d.dispose();
		Rectangle clip = g.getClipBounds();
		g.drawImage(buffer.get(), clip.x, clip.y, clip.x + clip.width, clip.y + clip.height,
				clip.x, clip.y, clip.x + clip.width, clip.y + clip.height, null);
	}
	
	private void paintTrace(
			Graphics2D g2d,
			int x, int y,
			int width,
			int mouseX, int mouseY,
			List<String> trace,
			Set<Integer> movesInBoth,
			Set<Integer> movesInBothDiffData,
			Set<Integer> movesInLog,
			Set<Integer> movesInModel,
			boolean isResolution) {
		
		int textWidth = 0;
		
		for (int j = 0; j < trace.size(); j++) {
			
			int positionX = x + (j * (eventWidth + 2));
			
			
			if (movesInBoth != null && movesInBoth.contains(j)) {
				g2d.setColor(GUIUtils.moveInBoth);
			}
			
			if (movesInBothDiffData != null && movesInBothDiffData.contains(j)) {
				g2d.setColor(GUIUtils.moveInBothDiffData);
			}
			
			if (movesInLog != null && movesInLog.contains(j)) {
				g2d.setColor(GUIUtils.moveInLog);
			}
			
			if (movesInModel != null && movesInModel.contains(j)) {
				g2d.setColor(GUIUtils.moveInModel);
			}
			
			g2d.fillRoundRect(positionX, y, eventWidth, eventHeight, 4, 4);
		}
		
		/* details part */
		if (mouseX >= x && mouseX <= x + (trace.size() * (eventWidth + 2))) {
			if (mouseY >= y && mouseY <= y + defaultFontMetric.getAscent() + defaultFontMetric.getDescent()) {
				
				int j = (mouseX - x) / (eventWidth + 2);
				
				if (j >= 0 && j < trace.size()) {

					String is = "";
					
					g2d.setColor(GUIUtils.event);
					if (movesInBothDiffData!= null && movesInBothDiffData.contains(j)) {
						if (is == "") {
							is += " is ";
						} else {
							is += ", ";
						}
						is += "move in log and model with different data";
					}
					if (movesInBoth != null && movesInBoth.contains(j)) {
						if (is == "") {
							is += " is ";
						} else {
							is += ", ";
						}
						is += "move in log and model";
					}
					if (movesInLog != null && movesInLog.contains(j)) {
						if (is == "") {
							is += " is ";
						} else {
							is += ", ";
						}
						is += "move in log only";
					}
					
					if (movesInModel != null && movesInModel.contains(j)) {
						if (is == "") {
							is += " is ";
						} else {
							is += ", ";
						}
						is += "move in model only";
					}
					
					is += " (ev. no. " + (j+1) + ")";
					
					int positionX = x + (j * (eventWidth + 2));
					
					/* name of the event and constraint status */
					String text = "\"" + trace.get(j) + "\"" + is;
					textWidth = detailsFontMetric.stringWidth(text);
					
					boolean flip = false;
					if (positionX + textWidth + 11 > width) {
						flip = true;
						positionX -= (textWidth + (eventWidth + 2)*2 + 5);
					}
					
					g2d.setColor(GUIUtils.eventDetailsBackground);
					g2d.fillRoundRect(positionX + eventWidth + 5, y + detailsVerticalMargin, textWidth + 6, detailsRectangleHeight, 5, 5);
					if (!flip) {
						g2d.fillPolygon(new int[] {
								positionX + eventWidth + 1,
								positionX + eventWidth + 5,
								positionX + eventWidth + 5},
							new int[]{
								y + (detailsRectangleHeight / 2) + detailsVerticalMargin,
								y + (detailsRectangleHeight / 2) + detailsVerticalMargin - 3,
								y + (detailsRectangleHeight / 2) + detailsVerticalMargin + 3}, 3);
					} else {
						g2d.fillPolygon(new int[] {
								positionX + textWidth + (eventWidth + 2)*2 + 5,
								positionX + textWidth + (eventWidth + 2)*2 - 1,
								positionX + textWidth + (eventWidth + 2)*2 - 1},
							new int[]{
								y + (detailsRectangleHeight / 2) + detailsVerticalMargin,
								y + (detailsRectangleHeight / 2) + detailsVerticalMargin - 3,
								y + (detailsRectangleHeight / 2) + detailsVerticalMargin + 3}, 3);
					}
					g2d.setColor(GUIUtils.eventDetailsColor);
					g2d.setFont(detailsFont);
					g2d.drawString(text, positionX + eventWidth + 8, y + detailsVerticalMargin + detailsFontMetric.getAscent());
				
				}
			}
		}
	}
	
	private void paintConstraintDetails(
			Graphics2D g2d,
			AnalysisSingleResult ar,
			int x, int y,
			int width, int height,
			int mouseX, int mouseY, int maxYPosition) {
		
		if (mouseX >= x && mouseX <= x + width) {
			if (mouseY >= y && mouseY <= y + height) {
				
				int panelHeight = 90;
				int panelWidth = detailsFontMetric.stringWidth(ar.getAlignment().getTraceName()) + 20;
				if (panelWidth < 230) {
					panelWidth = 230;
				}
				
				int positionY = y - 5;
				int positionX = x + width + 10;
				int stringWidth = defaultFontMetric.stringWidth(ar.getAlignment().getTraceName());
				
				if (positionY + panelHeight > maxYPosition) {
					positionY = maxYPosition - panelHeight - 5;
				}
				
				g2d.setColor(GUIUtils.eventDetailsBackground);
				g2d.fillRoundRect(positionX, positionY, panelWidth, panelHeight, 10, 10);
				g2d.setColor(GUIUtils.panelBackground.darker());
				g2d.fillRoundRect(width - stringWidth, y, stringWidth + 7, height, 7, 7);
				
				g2d.setColor(GUIUtils.panelTextColor);
				g2d.drawRoundRect(width - stringWidth, y, stringWidth + 7, height, 7, 7);
				g2d.drawLine(
						positionX - 3, y + 7,
						positionX, y + 7);
				
				g2d.setFont(defaultFont);
				g2d.setColor(GUIUtils.eventDetailsColor);
				g2d.drawString(
						"Alignment",
						width - defaultFontMetric.stringWidth(ar.getAlignment().getTraceName()) + 5,
						y + defaultFontMetric.getAscent());
				
				g2d.setFont(detailsFont);
				g2d.setColor(GUIUtils.eventDetailsColor);
				g2d.drawString(String.format("%s                   Fitness: %f", ar.getAlignment().getTraceName(), getFitness()),
						positionX + 10,
						positionY + detailsFontMetric.getDescent() + detailsFontMetric.getAscent());
				g2d.drawLine(positionX + 10, positionY + detailsFontMetric.getDescent() + detailsFontMetric.getAscent() + 5,
						positionX + panelWidth - 10, positionY + detailsFontMetric.getDescent() + detailsFontMetric.getAscent() + 5);

				int row = 1;
				g2d.drawString("Moves in log and model:", positionX + 10, positionY + 5 + ++row * (detailsFontMetric.getAscent() + detailsFontMetric.getDescent() + 2));
				g2d.drawString("" + ar.getMovesInBoth().size(), positionX + 130, positionY + 5 + row * (detailsFontMetric.getAscent() + detailsFontMetric.getDescent() + 2));
				g2d.drawString("Moves in model and log with diff data:", positionX + 10, positionY + 5 + ++row * (detailsFontMetric.getAscent() + detailsFontMetric.getDescent() + 2));
				g2d.drawString("" + ar.getMovesInBothDiffData().size(), positionX + 200, positionY + 5 + row * (detailsFontMetric.getAscent() + detailsFontMetric.getDescent() + 2));
				g2d.drawString("Moves in Log:", positionX + 10, positionY + 5 + ++row * (detailsFontMetric.getAscent() + detailsFontMetric.getDescent() + 2));
				g2d.drawString("" + ar.getMovesInLog().size(), positionX + 80, positionY + 5 + row * (detailsFontMetric.getAscent() + detailsFontMetric.getDescent() + 2));
				g2d.drawString("Moves In Model:", positionX + 10, positionY + 5 + ++row * (detailsFontMetric.getAscent() + detailsFontMetric.getDescent() + 2));
				g2d.drawString("" + ar.getMovesInModel().size(), positionX + 90, positionY + 5 + row * (detailsFontMetric.getAscent() + detailsFontMetric.getDescent() + 2));
				
				
			}
		}
	}
	
	public Alignment getAlignment() {
		return alignment;
	}
	
	public String getTraceName() {
		return traceName;
	}
	
	public List<AnalysisSingleResult> getAlignmentResults() {
		return alignmentResults;
	}

	public int getTotMovesInBoth() {
		return totMovesInBoth;
	}

	public int getTotMovesInBothDiffData() {
		return totMovesInBothDiffData;
	}

	public int getTotMovesInLog() {
		return totMovesInLog;
	}

	public int getTotMovesInModel() {
		return totMovesInModel;
	}

	public float getFitness() {
		return fitness;
	}

	@Override
	public void mouseDragged(MouseEvent arg0) {
		
	}

	@Override
	public void mouseMoved(MouseEvent arg0) {
		mouseX = arg0.getX();
		mouseY = arg0.getY();
		repaint();
	}
}