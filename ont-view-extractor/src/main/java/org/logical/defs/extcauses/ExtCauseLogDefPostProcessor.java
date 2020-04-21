package org.logical.defs.extcauses;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.fma.icd.map.OWLAPIUtil;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class ExtCauseLogDefPostProcessor {
	private static transient Logger log = Logger.getLogger(ExtCauseLogDefPostProcessor.class);

	private OWLOntologyManager ontologyManager;
	private OWLDataFactory df;

	private OWLOntology sourceOntology;
	private OWLOntology targetOntology;
	private OWLReasoner reasoner;

	private XMatchCache xMatchCache;

	private OWLAnnotationProperty intentProp;
	private OWLAnnotationProperty mechanismProp;
	private OWLAnnotationProperty fillerProp;
	private OWLAnnotationProperty matchScoreProp;
	private OWLAnnotationProperty matchXTopClsProp;
	private OWLAnnotationProperty matchedLabelProp;
	private OWLAnnotationProperty matchedXLabelProp;
	private OWLAnnotationProperty matchTypeProp;
	private OWLAnnotationProperty inheritedProp;

	public ExtCauseLogDefPostProcessor(OWLOntologyManager manager, OWLOntology sourceOntology,
			OWLOntology targetOntology, OWLReasoner reasoner, XMatchCache xMatchCache) {
		this.ontologyManager = manager;
		this.df = ontologyManager.getOWLDataFactory();
		this.sourceOntology = sourceOntology;
		this.targetOntology = targetOntology;
		this.reasoner = reasoner;
		this.xMatchCache = xMatchCache;

		initProps();
	}

	private void initProps() {
		this.intentProp = df.getOWLAnnotationProperty(ExtCausesConstants.INTENT_PROP);
		this.mechanismProp = df.getOWLAnnotationProperty(ExtCausesConstants.MECHANISM_PROP);
		this.fillerProp = df.getOWLAnnotationProperty(ExtCausesConstants.FILLER_PROP);
		this.matchScoreProp = df.getOWLAnnotationProperty(ExtCausesConstants.MATCH_SCORE_PROP);
		this.matchXTopClsProp = df.getOWLAnnotationProperty(ExtCausesConstants.MATCH_X_TOP_CLS_PROP);
		this.matchedLabelProp = df.getOWLAnnotationProperty(ExtCausesConstants.MATCH_LABEL_PROP);
		this.matchedXLabelProp = df.getOWLAnnotationProperty(ExtCausesConstants.MATCH_X_LABEL_PROP);
		this.matchTypeProp = df.getOWLAnnotationProperty(ExtCausesConstants.MATCH_TYPE_PROP);
		this.inheritedProp = df.getOWLAnnotationProperty(ExtCausesConstants.INHERITED_PROP);
	}

	public void doPostProcessing() {
		keepExactMatches();
		pruneDuplicateLabelMatches();
		pruneSubstringMatchesThatAreContained();
		
		//propagateMechanism();
		pruneMatchesCoveredByMechanism();
		
		//propagateFillers();
		//pruneSubstringMatchesThatAreContained(); //yes, again
		
		addIntent();
		addMechanism();
		addFillers();
	}


	private void pruneDuplicateLabelMatches() {
		log.info("Prunning duplicate short label matches ..");
		for (OWLClass cls : xMatchCache.getClsesWithMatches()) {
			pruneDuplicateLabelMatches(cls);
		}
	}

	private void pruneDuplicateLabelMatches(OWLClass cls) {
		Map<String, XMatch> label2match = new HashMap<String, XMatch>();
		
		List<XMatch> xMatches = xMatchCache.getMatches(cls);
		
		for (XMatch xMatch : xMatches) {
			String xLabel = xMatch.getMatchedXLabel();
			
			XMatch existingMatch = label2match.get(xLabel);
			
			if (existingMatch == null) {
				label2match.put(xLabel, xMatch);
			} else {
				if (xMatch.getMatchedLabel().equals(existingMatch.getMatchedLabel()) && 
					xMatch.getMatchedXLabel().equals(existingMatch.getMatchedXLabel()) ) {
					
					if (xMatch.getScore() > existingMatch.getScore() || 
						(xMatch.getScore() == existingMatch.getScore() && xMatch.getMatchType().equals("PREF LABELS"))	) {
						label2match.put(xLabel, xMatch);
					}
				}
			}
		}
		
		List<XMatch> goodMatches = new ArrayList<XMatch>(label2match.values()); 
		xMatchCache.setMatches(cls, goodMatches);
	}
	
	

	private void keepExactMatches() {
		log.info("Keeping exact matches ..");
		for (OWLClass cls : xMatchCache.getClsesWithMatches()) {
			keepExactMatches(cls);
		}
	}
	
	//Prunes matches in the same x tree (same X top class), if there is
	//a match with 100%
	private void keepExactMatches(OWLClass cls) {
		List<XMatch> matchesWith100 = new ArrayList<XMatch>();
		
		for (XMatch xMatch : xMatchCache.getMatches(cls)) {
			if (xMatch.getScore() == 100) {
				matchesWith100.add(xMatch);
			}
		}
		
		List<XMatch> goodMatches = new ArrayList<XMatch>(xMatchCache.getMatches(cls));
		
		for (XMatch xMatch100 : matchesWith100) {
			OWLClass topX100 = xMatch100.getTopXCls();
			for (XMatch xMatch : xMatchCache.getMatches(cls)) {
				if (matchesWith100.contains(xMatch) == false &&
						xMatch.getTopXCls().equals(topX100)) {
					goodMatches.remove(xMatch);
				}
			}
		}
		
		xMatchCache.setMatches(cls, goodMatches);
	}
	
	private void pruneSubstringMatchesThatAreContained() {
		log.info("Prunning substring matches that are already contained in another match in the same top x ..");
		for (OWLClass cls : xMatchCache.getClsesWithMatches()) {
			pruneSubstringMatchesThatAreContained(cls);
		}
	}

	private void pruneSubstringMatchesThatAreContained(OWLClass cls) {
		Set<XMatch> toPrune = new HashSet<XMatch>();
		
		for (XMatch xMatch1 : xMatchCache.getMatches(cls)) {
			OWLClass topXCls1 = xMatch1.getTopXCls();
			String xMatchedLabel1 = xMatch1.getMatchedXLabel();
			
			for (XMatch xMatch2 : xMatchCache.getMatches(cls)) {
				OWLClass topXCls2 = xMatch2.getTopXCls();
				String xMatchedLabel2 = xMatch2.getMatchedXLabel();
				
				if (xMatch1.equals(xMatch2) == false &&
					topXCls1.equals(topXCls2) && 
					xMatchedLabel1.contains(xMatchedLabel2) ) {
						toPrune.add(xMatch2);
				}
			}
		}
		
		List<XMatch> goodMatches = new ArrayList<XMatch>(xMatchCache.getMatches(cls));
		goodMatches.removeAll(toPrune);
		
		xMatchCache.setMatches(cls, goodMatches);
	}
	
	

	private void pruneMatchesCoveredByMechanism() {
		log.info("Prunning matches that are already contained in mechanism ..");
		for (OWLClass cls : xMatchCache.getClsesWithMatches()) {
			pruneMatchesCoveredByMechanism(cls);
		}
	}

	private void pruneMatchesCoveredByMechanism(OWLClass cls) {
		XMatch mechMatch = xMatchCache.getMechanismXMatch(cls);

		if (mechMatch == null || (mechMatch!= null && mechMatch.isInherited() == true)) {
			return;
		}

		String mechMatchedXLabel = mechMatch.getMatchedXLabel();

		Set<XMatch> toPrune = new HashSet<XMatch>();

		for (XMatch xMatch : xMatchCache.getMatches(cls)) {
			String xMatchedLabel = xMatch.getMatchedXLabel();

			if (mechMatchedXLabel.contains(xMatchedLabel) == true) {
				toPrune.add(xMatch);
			}
		}

		List<XMatch> goodMatches = new ArrayList<XMatch>(xMatchCache.getMatches(cls));
		goodMatches.removeAll(toPrune);

		xMatchCache.setMatches(cls, goodMatches);
	}

	
	/*********************** Propagate mechanism *********************/
	
	private void propagateMechanism() {
		Set<OWLClass> traversed = new HashSet<OWLClass>();
		propagateMechanism(df.getOWLClass(ExtCausesConstants.CHAPTER_EXT_CAUSES_ID), df.getOWLThing(), traversed);
	}
	
	private void propagateMechanism(OWLClass cls, OWLClass parent, Set<OWLClass> traversed) {
		if (traversed.contains(cls)) {
			return;
		}
		
		traversed.add(cls);
		
		propagateMechanism(cls,parent);
		
		//do the children
		Set<OWLClass> subclses = OWLAPIUtil.getNamedSubclasses(cls, sourceOntology, reasoner, true);
		for (OWLClass subcls : subclses) {
			propagateMechanism(subcls, cls, traversed);
		}
	}

	private void propagateMechanism(OWLClass cls, OWLClass parent) {
		XMatch mechMatch = xMatchCache.getMechanismXMatch(cls);
		
		if (mechMatch != null) { //TODO: check if in conflict with parent?
			return;
		}
		
		XMatch parentMechMatch = xMatchCache.getMechanismXMatch(parent);
		
		if (parentMechMatch == null) {
			return; //not much to do..
		}
		
		mechMatch = new XMatch(parentMechMatch.getXMatch(), parentMechMatch.getTopXCls(), 
				parentMechMatch.getScore(), parentMechMatch.getMatchedLabel(),
				parentMechMatch.getMatchedXLabel(), parentMechMatch.getMatchType());
		mechMatch.setInherited(true);
		
		xMatchCache.setMechanism(cls, mechMatch);
	}
	
	
	/********************************** Propagate fillers *********************/
	

	private void propagateFillers() {
		log.info("Propagate fillers..");
			
		Set<OWLClass> traversed = new HashSet<OWLClass>();
		propagateFillers(df.getOWLClass(ExtCausesConstants.CHAPTER_EXT_CAUSES_ID), df.getOWLThing(), traversed);
	}
	
	private void propagateFillers(OWLClass cls, OWLClass parent, Set<OWLClass> traversed) {
		if (traversed.contains(cls)) {
			return;
		}
		
		traversed.add(cls);
		
		propagateFillers(cls,parent);
		
		//do the children
		Set<OWLClass> subclses = OWLAPIUtil.getNamedSubclasses(cls, sourceOntology, reasoner, true);
		for (OWLClass subcls : subclses) {
			propagateFillers(subcls, cls, traversed);
		}
	}

	private void propagateFillers(OWLClass cls, OWLClass parent) {
		
		List<XMatch> parentMatches = xMatchCache.getMatches(parent);
		
		List<XMatch> retMatches = new ArrayList<XMatch>(xMatchCache.getMatches(cls));
		
		for (XMatch parentMatch : parentMatches) {
			if (shouldAddMatch(cls, parentMatch) == true) {
				XMatch clone = parentMatch.clone();
				clone.setInherited(true);
				retMatches.add(clone);
			}
		}
		
		xMatchCache.setMatches(cls, retMatches);
	}

	//don't propagate if the child already has a value in the same top x tree
	private boolean shouldAddMatch(OWLClass cls, XMatch parentMatch) {
		OWLClass parentTopXClass = parentMatch.getTopXCls();
		//if (parentTopXClass.equals(df.getOWLClass(XChapterCache.OBJ_CAUSING_INURY_CLS_ID))) {
		//	return false;
		//}
		
		OWLClass parentXCls = parentMatch.getXMatch();
		
		for (XMatch xMatch : xMatchCache.getMatches(cls)) {
			OWLClass xCls = xMatch.getXMatch();
			
			if (xCls.equals(parentXCls)) {
				return false;
			}
			
			if (xMatch.getTopXCls().equals(parentTopXClass)) {
				return false;
			}
			
			if (OWLAPIUtil.isSubclassOf(xCls, parentXCls, reasoner) == true) {
				return false;
			}
			
		}
		
		return true;
	}

	
	
	/********************** Adding to the target ontology ******************************/

	private void addMechanism() {
		log.info("Adding the mechanisms ..");
		for (OWLClass cls : xMatchCache.getClsesWithMechanism()) {
			fillMechanism(cls, xMatchCache.getMechanism(cls), xMatchCache.getMechanismXMatch(cls).isInherited());
		}
	}

	private void addIntent() {
		log.info("Adding the intents ..");
		for (OWLClass cls : xMatchCache.getClsesWithIntent()) {
			fillIntent(cls, xMatchCache.getIntent(cls));
		}
	}

	private void addFillers() {
		log.info("Adding fillers to target ontology ..");

		for (OWLClass cls : xMatchCache.getClsesWithMatches()) {
			addFillers(cls);
		}

		log.info("Ended adding fillers");
	}

	private void addFillers(OWLClass cls) {
		List<XMatch> xMatches = xMatchCache.getMatches(cls);

		for (XMatch xMatch : xMatches) {
			addFiller(cls, xMatch);
		}
	}
	

	private void fillMechanism(OWLClass cls, OWLClass xcls, boolean inherited) {
		OWLAnnotation ann = df.getOWLAnnotation(mechanismProp, xcls.getIRI());
		
		if (inherited == true) {
			Collection<OWLAnnotation> annsOnAnn = new ArrayList<OWLAnnotation>();
			OWLAnnotation inhAnn = df.getOWLAnnotation(inheritedProp, df.getOWLLiteral(inherited));
			annsOnAnn.add(inhAnn);
			targetOntology.addAxiom(df.getOWLAnnotationAssertionAxiom(cls.getIRI(), ann, annsOnAnn));
		} else {
			targetOntology.addAxiom(df.getOWLAnnotationAssertionAxiom(cls.getIRI(), ann));
		}
	}

	
	private void fillIntent(OWLClass cls, Intent intent) {
		OWLAPIUtil.addAnnotationProperty(targetOntology, cls, intentProp, intent.name(), "en");
	}

	private void addFiller(OWLClass cls, XMatch xMatch) {
		
		OWLClass xCls = xMatch.getXMatch();
		int score = xMatch.getScore();
		OWLClass topXCls = xMatch.getTopXCls();
		String matchedLabel= xMatch.getMatchedLabel();
		String xMatchLabel = xMatch.getMatchedXLabel();
		String matchType = xMatch.getMatchType();
		boolean inherited = xMatch.isInherited();
		
		OWLAnnotation ann = df.getOWLAnnotation(fillerProp, xCls.getIRI());

		OWLAnnotation matchScoreAnn = df.getOWLAnnotation(matchScoreProp, df.getOWLLiteral(score));
		OWLAnnotation matchTopXClsAnn = df.getOWLAnnotation(matchXTopClsProp, topXCls.getIRI());
		OWLAnnotation matchedLabelAnn = df.getOWLAnnotation(matchedLabelProp, df.getOWLLiteral(matchedLabel));
		OWLAnnotation matchedXLabelAnn = df.getOWLAnnotation(matchedXLabelProp, df.getOWLLiteral(xMatchLabel));
		OWLAnnotation matchTypeAnn = df.getOWLAnnotation(matchTypeProp, df.getOWLLiteral(matchType));
		
		Collection<OWLAnnotation> annsOnAnn = new ArrayList<OWLAnnotation>();
		annsOnAnn.add(matchScoreAnn);
		annsOnAnn.add(matchTopXClsAnn);
		annsOnAnn.add(matchedLabelAnn);
		annsOnAnn.add(matchedXLabelAnn);
		annsOnAnn.add(matchTypeAnn);
		
		if (inherited == true) {
			OWLAnnotation inhAnn = df.getOWLAnnotation(inheritedProp, df.getOWLLiteral(inherited));
			annsOnAnn.add(inhAnn);
		}

		targetOntology.addAxiom(df.getOWLAnnotationAssertionAxiom(cls.getIRI(), ann, annsOnAnn));
	}

}
