package org.processmining.plugins.dataawaredeclarereplayer.utils;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.text.DecimalFormat;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import org.processmining.framework.util.ui.scalableview.VerticalLabelUI;

import com.fluxicon.slickerbox.factory.SlickerFactory;

public class GUIUtils {
	public static Color tabBackground = new Color(28, 28, 28);
	public static Color tabForeground = new Color(119, 119, 119);
	public static Color tabTitle = new Color(119, 119, 119);

	public static Color panelBackground = new Color(130, 130, 130);
	public static Color panelTitleColor = new Color(28, 28, 28);
	public static Color panelTextColor = new Color(28, 28, 28);
	public static Color panelTextTitleColor = new Color(220, 220, 220);
	
	public static Color event = Color.lightGray;
	public static Color moveInBoth = new Color(0,210,0);
	public static Color moveInBothDiffData = Color.WHITE;
	public static Color moveInLog = Color.YELLOW;
	public static Color moveInModel = new Color(224, 176, 255);
	

	
	public static Color eventDetailsBackground = new Color(0, 0, 0, 180);
	public static Color eventDetailsColor = Color.lightGray;
	
	public static DecimalFormat df2 = new DecimalFormat("#.####");
	
	/**
	 * This method creates a new {@link JLabel} correctly formatted in order to
	 * be a frame title
	 * 
	 * @param s the string to be inserted into the label
	 * @return a component with the required widget
	 */
	public static JLabel prepareTitle(String s) {
		JLabel labelTitle = SlickerFactory.instance().createLabel(s);
		labelTitle.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 16));
		labelTitle.setAlignmentX(JLabel.LEFT);
		labelTitle.setBorder(new EmptyBorder(5, 0, 10, 0));
		return labelTitle;
	}
	
	public static JLabel prepareLargeLabel(String s) {
		JLabel labelTitle = SlickerFactory.instance().createLabel(s);
		labelTitle.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 16));
		labelTitle.setAlignmentX(JLabel.LEFT);
		labelTitle.setBorder(new EmptyBorder(5, 0, 10, 0));
		return labelTitle;
	}
	
	public static JComponent prepareTitleBordered(String s) {
		JPanel title = SlickerFactory.instance().createRoundedPanel(10, panelTextTitleColor.darker());
		title.setLayout(new BorderLayout());
		
		JLabel titleLabel = new JLabel(s);
		titleLabel.setForeground(panelTextTitleColor.brighter());
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14));
		
		title.add(titleLabel, BorderLayout.CENTER);
		
		return title;
	}
	
	/**
	 * This method creates a new {@link JLabel} correctly formatted
	 * 
	 * @param s the string to be inserted into the label
	 * @return a component with the required widget
	 */
	public static JLabel prepareLabel(String s) {
		return prepareLabel(s, SwingConstants.LEFT, panelTextColor);
	}

	/**
	 * This method creates a new {@link JLabel} correctly formatted
	 * 
	 * @param s the string to be inserted into the label
	 * @param alignment the alignment
	 * @param foreground the foreground color
	 * @return a component with the required widget
	 */
	public static JLabel prepareLabel(String s, int alignment, Color foreground) {
		JLabel l = SlickerFactory.instance().createLabel(s);
		l.setHorizontalAlignment(alignment);
		l.setForeground(foreground);
		return l;
	}
	
	/**
	 * This method creates a new {@link JLabel} correctly formatted
	 * 
	 * @param i the string to be inserted into the label
	 * @return a component with the required widget
	 */
	public static JLabel prepareLabel(int i) {
		return prepareLabel(i, SwingConstants.RIGHT, panelTextColor);
	}
	
	/**
	 * This method creates a new {@link JLabel} correctly formatted
	 * 
	 * @param i the string to be inserted into the label
	 * @param df the decimal format to use
	 * @return a component with the required widget
	 */
	public static JLabel prepareLabel(double i, DecimalFormat df) {
		if (Double.compare(Double.NaN, i) == 0)
			return prepareLabel("-", SwingConstants.RIGHT, panelTextColor);
		return prepareLabel(df.format(i), SwingConstants.RIGHT, panelTextColor);
	}
	
	/**
	 * This method creates a new {@link JLabel} correctly formatted
	 * 
	 * @param i the string to be inserted into the label
	 * @param alignment the alignment
	 * @param foreground the foreground color
	 * @return a component with the required widget
	 */
	public static JLabel prepareLabel(int i, int alignment, Color foreground) {
		Integer value = i;
		JLabel l = SlickerFactory.instance().createLabel(value.toString());
		l.setHorizontalAlignment(alignment);
		l.setForeground(new Color(40,40,40));
		return l;
	}
	
	/**
	 * This method creates a new {@link JLabel} correctly formatted as a title
	 * 
	 * @param s the string to be inserted into the label
	 * @return a component with the required widget
	 */
	public static JLabel prepareTitleLabel(String s) {
		return prepareLabel(s, SwingConstants.LEFT, panelTextTitleColor);
	}
	
	/**
	 * This method creates a new {@link JLabel} correctly formatted as a title
	 * 
	 * @param s the string to be inserted into the label
	 * @return a component with the required widget
	 */
	public static JLabel prepareVericalTitleLabel(String s) {
		JLabel l = prepareLabel(s, SwingConstants.LEFT, panelTextTitleColor);
		l.setUI(new VerticalLabelUI(false));
		return l;
	}
	
	/**
	 * 
	 * @param x
	 * @param y
	 * @param container
	 * @param component
	 */
	public static void addToGridBagLayout(int x, int y, JComponent container, Component component) {
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = x;
		c.gridy = y;
		container.add(component, c);
	}
	
	/**
	 * 
	 * @param x
	 * @param y
	 * @param container
	 * @param component
	 * @param constraint
	 */
	public static void addToGridBagLayout(int x, int y, JComponent container, Component component, GridBagConstraints constraint) {
		constraint.gridx = x;
		constraint.gridy = y;
		container.add(component, constraint);
	}
	
	/**
	 * 
	 * @param x
	 * @param y
	 * @param container
	 * @param component
	 * @param weightx
	 * @param weighty
	 */
	public static void addToGridBagLayout(int x, int y, JComponent container, Component component, double weightx, double weighty) {
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = x;
		c.gridy = y;
		c.weightx = weightx;
		c.weighty = weighty;
		container.add(component, c);
	}
}
