/*
 * Adapted from org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping
 */
package org.processmining.plugins.dataawaredeclarereplayer.mapping;

import java.util.HashMap;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClassifier;
import org.processmining.plugins.DeclareConformance.ReplayableActivityDefinition;

public class TransEvClassMapping extends HashMap<ReplayableActivityDefinition, XEventClass>{
	private static final long serialVersionUID = -4344051692440782096L;
	private XEventClassifier eventClassifier;
	private XEventClass dummyEventClass;
	
	@SuppressWarnings("unused")
	private TransEvClassMapping(){}; // this constructor is not allowed
	
	/**
	 * Allowed constructor
	 * @param eventClassifier
	 * @param dummyEventClass
	 */
	public TransEvClassMapping(XEventClassifier eventClassifier, XEventClass dummyEventClass){
		this.eventClassifier = eventClassifier;
		this.dummyEventClass = dummyEventClass;
	}
	
	/**
	 * get the classifier
	 * @return
	 */
	public XEventClassifier getEventClassifier(){
		return this.eventClassifier;
	}
	
	/**
	 * Get event class that is used to represent transition (not invisible ones) that is not mapped to 
	 * any activity
	 * 
	 * @return
	 */
	public XEventClass getDummyEventClass(){
		return this.dummyEventClass;
	}
}
