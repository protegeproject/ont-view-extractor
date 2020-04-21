package org.logical.defs.extcauses;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.fma.icd.map.OWLAPIUtil;
import org.fma.icd.map.StringMatcher;
import org.ontologies.extract.ExportProperties;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChangeException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.UnknownOWLOntologyException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

import me.xdrop.fuzzywuzzy.FuzzySearch;

/**
 * This class is trying to find the fillers for logical definition in 
 * external causes by trying to restrict the fillers going down from the parent,
 * and many other strategies.
 * 
 * It will write the results as annotation properties in the output OWL file.
 * 
 * The output is a separate OWL file. The export is configured via the export.properties.
 * 
 * @author ttania
 *
 */
public class ExtCausesFindLogDefFillers {

	private static transient Logger log = Logger.getLogger(ExtCausesFindLogDefFillers.class);
	
	//we already check the string containment, this is only used as a guide
	//depending on the fuzzy match this can be very low, but we want all matches
	//and use this score only for potential optimization at the end.
	public static final int FILLER_MATCH_TRESHOLD = 1;
	
	private OWLOntologyManager ontologyManager;
	private OWLDataFactory df;
	
	private OWLOntology sourceOntology;
	private OWLOntology targetOntology;
	private OWLReasoner reasoner;
	
	private URI outputOntologyFileURI; // cached for optimization purposes
	private Set<OWLClass> traversed = new HashSet<OWLClass>();
	
	private XChapterCache xChapterCache;
	LabelCache labelCache;
	private XMatchCache xMatchCache = new XMatchCache();
	
	private Map<OWLClass, OWLClass> cls2TopCls = new HashMap<OWLClass, OWLClass>();

	private int importedClassesCount = 0;

	private int logCount = 100; 
	private int saveCount = -1; 

	public ExtCausesFindLogDefFillers(OWLOntologyManager manager, OWLOntology sourceOntology, OWLOntology targetOntology,
			URI outputFileURI, OWLReasoner reasoner) {
		this.ontologyManager = manager;
		this.df = ontologyManager.getOWLDataFactory();
		this.sourceOntology = sourceOntology;
		this.targetOntology = targetOntology;
		this.outputOntologyFileURI = outputFileURI;
		this.reasoner = reasoner;
	}

	
	public static void main(String[] args) {
		try {

			BasicConfigurator.configure();

			OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

			File sourceOntFile = new File(ExportProperties.getSourceOntologyFileLocation());
			OWLOntology sourceOnt = manager.loadOntologyFromOntologyDocument(sourceOntFile);
			if (sourceOnt == null) {
				log.error("Could not load source ontology " + ExportProperties.getSourceOntologyFileLocation());
				return;
			}

			File outputOntFile = new File(ExportProperties.getTargetOntologyFileLocation());
			OWLOntology targetOnt = null;

			if (ExportProperties.getAppendOntologyFile() && outputOntFile.exists()) {
				log.info("Loading existing ontology from " + outputOntFile.getAbsolutePath());
				targetOnt = manager.loadOntologyFromOntologyDocument(outputOntFile);
			} else {
				targetOnt = manager.createOntology(IRI.create(ExportProperties.getTargetOntologyName()));
			}

			ExtCausesFindLogDefFillers extractor = new ExtCausesFindLogDefFillers(manager, sourceOnt, targetOnt,
					outputOntFile.toURI(), initReasoner(sourceOnt) );

			log.info("Starting ... " + new Date());
			extractor.start(ExportProperties.getTopClasses());

			log.info("Finished computation on " + new Date());
			log.info("Saving ontology");
			manager.saveOntology(targetOnt, IRI.create(outputOntFile));
			log.info("Done on " + new Date());
		} catch (Throwable t) {
			log.log(Level.ERROR, t.getMessage(), t);
		}

	}

	
	private void start(Collection<String> topClassNames) {
		init();
		
		log.info("Starting logical definition computation..");
		
		//saveCount = 300; //while debugging
		
		Set<OWLClass> topClasses = getTopClasses(sourceOntology, topClassNames);
		if (topClasses.isEmpty() == true) {
			log.info("Empty top classes. Nothing to export.");
			return;
		}

		traversed.clear();

		for (OWLClass sourceClass : topClasses) {
			try {
				extractClass(sourceClass, df.getOWLThing(), true);
			} catch (Throwable t) {
				log.error("Error at adding class: " + sourceClass, t);
			}
		}

		postProcess();
		
		cleanUp();
		
		exportToCSV();
	}

	private void exportToCSV() {
		String exportCSVPath = ExportProperties.getExportCSVFileLocation();
		if (exportCSVPath == null) {
			log.info("No CSV export file path configured. Will not export CSV.");
			return;
		}
		
		File exportCSVFile = new File(exportCSVPath);
		
		log.info("Saving to CSV file: " + exportCSVFile.getAbsolutePath());
		
		ExportExtCauseLogDefToCSV exporter = new ExportExtCauseLogDefToCSV(ontologyManager, sourceOntology, reasoner, xMatchCache);
		exporter.export(exportCSVFile);		
	}


	private void postProcess() {
		ExtCauseLogDefPostProcessor postProcessor = new ExtCauseLogDefPostProcessor(ontologyManager, sourceOntology, 
				targetOntology, reasoner, xMatchCache);
		
		postProcessor.doPostProcessing();
	}


	private void init() {
		log.info("Started building caches ..");
		
		this.xChapterCache = new XChapterCache(ontologyManager, sourceOntology, reasoner);
		xChapterCache.init();
		
		this.labelCache = new LabelCache(ontologyManager, sourceOntology, reasoner, xChapterCache);
		labelCache.init(); //it also initializes the lemmaCache; just an optimization
		
		fillExtCausesTopClsesCache();
		
		log.info("Ended building caches ..");
		
		log.info("Adding Chapter X ..");
		
		addXChapter();
		
		log.info("Ended adding X Chapter");
	}		

	
	private void fillExtCausesTopClsesCache() {
		for (OWLClass topCls : OWLAPIUtil.getNamedSubclasses(df.getOWLClass(ExtCausesConstants.CHAPTER_EXT_CAUSES_ID), sourceOntology, reasoner, true)) {
			Set<OWLClass> subclses = OWLAPIUtil.getNamedSubclasses(topCls, sourceOntology, reasoner, false);
			for (OWLClass subcls : subclses) {
				cls2TopCls.put(subcls, topCls);
			}
		}
	}


	private void addXChapter() {
		OWLClass xTopClass = df.getOWLClass(ExtCausesConstants.CHAPTER_X_ID);
				
		try {
			extractClass(xTopClass, df.getOWLThing(), false);
		} catch (Throwable t) {
			log.error("Error at extracting Extension codes chapter", t);
		}
		
		cleanUp();
	}

	private void extractClass(OWLClass sourceCls, OWLClass parentCls, boolean checkXChapterMatches) {
		if (traversed.contains(sourceCls) == true) {
			return;
		}
		traversed.add(sourceCls);

		try {
			attachAnnotations(sourceCls);
			
			if (checkXChapterMatches == true) {
				matchWithXChapter(sourceCls);
			}
			
			addChildren(sourceCls, checkXChapterMatches);
		} catch (Throwable t) {
			log.error("Error at adding class: " + sourceCls, t);
		}

		importedClassesCount++;

		logCount(importedClassesCount);
	}
	

	private void logCount(int importedClassesCount) {
		if (logCount > 0 && importedClassesCount % logCount == 0) {
			log.info("Processed " + importedClassesCount + " classes");
		}

		if (saveCount > 0 && importedClassesCount % saveCount == 0) {
			long t0 = System.currentTimeMillis();
			log.info("Saving ontology (" + importedClassesCount + " classes processed) ... ");

			try {
				ontologyManager.saveOntology(targetOntology, new RDFXMLDocumentFormat(),
						IRI.create(outputOntologyFileURI));
			} catch (UnknownOWLOntologyException e) {
				log.error(e.getMessage(), e);
			} catch (OWLOntologyStorageException e) {
				log.error(e.getMessage(), e);
			}
			log.info("\tin " + (System.currentTimeMillis() - t0) / 1000 + " seconds");
		}
	}

	
	private void addChildren(OWLClass parentCls, boolean checkXChapterMatches) throws OWLOntologyChangeException {
		Set<OWLClass> subclses = OWLAPIUtil.getNamedSubclasses(parentCls, sourceOntology, reasoner, true);
		
		for (OWLClass subcls : subclses) {
			OWLSubClassOfAxiom subclsAxiom = df.getOWLSubClassOfAxiom(subcls,
					parentCls);
			ontologyManager.addAxiom(targetOntology, subclsAxiom);
			extractClass(subcls, parentCls, checkXChapterMatches);
		}
	}

	
	private void matchWithXChapter(OWLClass cls) {
		addIntent(cls);
		
		if (cls.getIRI().toString().equals("http://id.who.int/icd/entity/1763852271")) {
			System.out.println(labelCache.getLabel(cls));
		}
		
		if (labelCache.isTransportCls(cls) == true) {
			matchTransportCls(cls);
			return;
		}
		
		findMechanism(cls);

		
		if  (findSubstances(cls) == true) {
			return;
		}
	
		//search in each subtree of XChapter, not in mechanism or substances
		for (OWLClass topXCls : xChapterCache.getXTopClasses(cls2TopCls.get(cls))) {
			matchWithTopXCls(cls, topXCls);
		}
	}


	private boolean findSubstances(OWLClass cls) {
		if (shouldMatchWithSubstances(cls) == true) {
			return matchWithTopXCls(cls, xChapterCache.getSubstancesTopXCls());
		}
		return false;
	}

	private boolean findMechanism(OWLClass cls) {
		//if the parent has a mechanism, first look in the subclasses of that mechanism
		//if not, look everywhere
		//Ext Causes chapter does not use multiple inheritance, so only take the first parent
		
		if (cls.getIRI().toString().equals("http://id.who.int/icd/entity/871505556")) {
			System.out.println(labelCache.getLabel(cls)+ " ");
		}
		
		OWLClass parent = OWLAPIUtil.getFirstNamedSuperclass(cls, sourceOntology, reasoner);
		OWLClass parentMech = xMatchCache.getMechanism(parent);
		
		boolean mechFound = false;
		
		if (parentMech != null) {
			mechFound = matchWithXTree(cls, xChapterCache.getAspectsOfMechanismTopXCls(), null, true, parentMech);
		}
		
		if (mechFound == false) {
			mechFound = matchWithTopXCls(cls, xChapterCache.getAspectsOfMechanismTopXCls());
		}
		
		return mechFound;
	}

	private boolean matchWithTopXCls(OWLClass cls, OWLClass topXCls) {
		return matchWithTopXCls(cls, topXCls, null, true);
	}
	
	private boolean matchWithXTree(OWLClass cls, OWLClass topXCls, String label, 
			boolean checkForDuplicates, OWLClass topXParent) {
		Set<OWLClass> subclses = OWLAPIUtil.getNamedSubclasses(topXParent, sourceOntology, reasoner, false);
		
		boolean match = false;
		for (OWLClass xCls : subclses) {
			if (matchWithXCls(cls, topXCls, xCls, label, checkForDuplicates) == true) {
				match = true;
			}
		}
		return match;
	}
	
	
	private boolean matchWithTopXCls(OWLClass cls, OWLClass topXCls, 
									 String label, boolean checkForDuplicates) {
		boolean match = false;
		for (OWLClass xCls : xChapterCache.getXChildren(topXCls)) {
			if (matchWithXCls(cls, topXCls, xCls, label, checkForDuplicates) == true) {
				match = true;
			}
		}
		return match;
	}

	
	private boolean matchWithXCls(OWLClass cls, OWLClass topXCls, OWLClass xCls, 
								  String clsLabel, boolean checkForDuplicates) {
		String xClsLabel = labelCache.getLabel(xCls);
		clsLabel = clsLabel == null ? labelCache.getLabel(cls) : clsLabel;
		
		if (cls.getIRI().toString().equals("http://id.who.int/icd/entity/1018874801") &&
			xCls.getIRI().toString().equals("http://id.who.int/icd/entity/922767790")) {
			System.out.println(labelCache.getLabel(cls) + " ?-> " + labelCache.getLabel(xCls));
		}
		
		boolean match = matchWithLabel(cls, xCls, topXCls, clsLabel, xClsLabel, "PREF LABELS");
		
		match = match || matchWithShortLabel(cls, xCls, topXCls, clsLabel, checkForDuplicates);
		
		match = match || matchWithSplitOrs(cls, xCls, topXCls, clsLabel);
		
		return match;
	}
	
	
	private void matchTransportCls(OWLClass cls) {
		if (cls.getIRI().toString().equals("http://id.who.int/icd/entity/29768622")) {
			System.out.println(labelCache.getLabel(cls));
		}
		
		findTransportMechanism(cls);
		
		findModeOfTransport(cls);
		
		
		if (findTransportCounterpart(cls) == false) {
			findTransportNoCounterpart(cls);
		}
	
		findTransportUserRole(cls);
		
		boolean objMatch = matchWithTopXCls(cls, xChapterCache.getObjectsCausingInjuryTopXCls());
		if (objMatch == true) {
			pruneAlreadyCoveredObjMatches(cls);
		}
	}
	

	private void pruneAlreadyCoveredObjMatches(OWLClass cls) {
		Set<XMatch> toPrune = new HashSet<XMatch>();
		OWLClass objCausingInjTopXCls = xChapterCache.getObjectsCausingInjuryTopXCls();
		
		Set<OWLClass> clsesToCheck = new HashSet<>(OWLAPIUtil.getNamedSuperclasses(cls, sourceOntology, reasoner, false));
		clsesToCheck.add(cls);
		
		for (XMatch xMatch1 : xMatchCache.getMatches(cls)) {
			OWLClass topXCls1 = xMatch1.getTopXCls();
			String xMatchedLabel1 = xMatch1.getMatchedXLabel();
			
			if (topXCls1.equals(objCausingInjTopXCls) == false) {
				continue;
			}
			
			for (OWLClass clsToCheck : clsesToCheck) {
				for (XMatch xMatch2 : xMatchCache.getMatches(clsToCheck)) {
					String xMatchedLabel2 = xMatch2.getMatchedXLabel();
					
					if (xMatch1.equals(xMatch2) == false &&
						xMatchedLabel2.contains(xMatchedLabel1) ) {
							toPrune.add(xMatch1);
					}
				}
			}
			
		}
		
		List<XMatch> goodMatches = new ArrayList<XMatch>(xMatchCache.getMatches(cls));
		goodMatches.removeAll(toPrune);
		
		xMatchCache.setMatches(cls, goodMatches);
		
	}


	private boolean findTransportMechanism(OWLClass cls) {
		OWLClass transportMechanismTopXCls = xChapterCache.getTransportMechanismTopXCls();
		
		if (matchWithTopXCls(cls, transportMechanismTopXCls) == true) {
			return true;
		}
		
		//check if it matches the top class itself
		if  (matchWithXCls(cls, transportMechanismTopXCls, transportMechanismTopXCls, 
				labelCache.getLabel(cls), false) == true) {
			return true;
		}
		
		return false;
	}

	
	private boolean findModeOfTransport(OWLClass cls) {
		String prefLabel = OWLAPIUtil.getSKOSPrefLabelValue(sourceOntology, cls);
		String modeOfTransport = labelCache.getModeOfTransport(prefLabel);
		
		if (cls.getIRI().toString().equals("http://id.who.int/icd/entity/1758338082")) {
			System.out.println(prefLabel);
		}
		
		if (modeOfTransport.length() == 0) {
			return false;
		}
		
		modeOfTransport = modeOfTransport.length() == 0 ? prefLabel : modeOfTransport;
		
		OWLClass modeOfTransportTopXCls = xChapterCache.getModeOfTransportTopXCls();
		
		return matchWithTopXCls(cls, modeOfTransportTopXCls, modeOfTransport, false);
	}
	
	private boolean findTransportCounterpart(OWLClass cls) {
		String prefLabel = OWLAPIUtil.getSKOSPrefLabelValue(sourceOntology, cls);
		String counterpart = labelCache.getInjuryCounterpart(prefLabel);
		
		if (counterpart.length() == 0) {
			return false;
		}
		
		counterpart = counterpart.length() == 0 ? prefLabel : counterpart;

		return matchWithTopXCls(cls, xChapterCache.getCounterpartTopXCls(), counterpart, false);
	}

	private boolean findTransportNoCounterpart(OWLClass cls) {
		if (isSuitableForNoCounterpart(cls) == false) {
			return false;
		}
		
		return matchWithTopXCls(cls, xChapterCache.getNoCounterpartTopXCls(), labelCache.getLabel(cls), false);
	}
	
	
	private boolean isSuitableForNoCounterpart(OWLClass cls) {
		String prefLabel = OWLAPIUtil.getSKOSPrefLabelValue(sourceOntology, cls);
		prefLabel = prefLabel.toLowerCase();
		return prefLabel.contains("collision") == false && 
				prefLabel.contains("other specified") == false &&
				prefLabel.contains("unspecified transport accidents") == false &&
				prefLabel.contains("motor vehicle") == false; //this is because of many false matches
	}
	
	
	private boolean findTransportUserRole(OWLClass cls) {
		return matchWithTopXCls(cls, xChapterCache.getUserRoleTopXCls(),labelCache.getLabel(cls),  false);
	}
	
	
	private boolean matchWithSplitOrs(OWLClass cls, OWLClass xCls, OWLClass topXCls, String clsLabel) {
		//pref -> x pref split - theoretically covered already
		//pref -> x short split
		//short -> x pref split
		//short -> x short split
		
		boolean lookOnlyForLabel = clsLabel != null;
		
		clsLabel = clsLabel == null ? labelCache.getLabel(cls) : clsLabel;
		String shortLabel = labelCache.getShortLabel(cls);
		
		List<String> xOrSplitPref = labelCache.getOrSplitPrefLabel(xCls);
		List<String> xOrSplitShort = labelCache.getOrSplitShortLabel(xCls);
		
//		if (cls.getIRI().toString().equals("http://id.who.int/icd/entity/1054699166")) {
//			System.out.println("Here: " + cls);
//		}
		
		boolean match = matchWithSplitOrs(cls, xCls, topXCls, clsLabel, xOrSplitPref, "OR: Pref XPref");
		
		if (match == true) {
			return true;
		}
		
		match = matchWithSplitOrs(cls, xCls, topXCls, clsLabel, xOrSplitShort, "OR: Pref XShort");
		
		if (match == true) {
			return true;
		}
		
		if (lookOnlyForLabel == true) {
			return match;
		}
		
		match = matchWithSplitOrs(cls, xCls, topXCls, shortLabel, xOrSplitPref, "OR: Short XPref");
		
		if (match == true) {
			return true;
		}

		match = matchWithSplitOrs(cls, xCls, topXCls, shortLabel, xOrSplitShort, "OR: Short XShort");
		
		return match;
	}
	
	
	private boolean matchWithSplitOrs(OWLClass cls, OWLClass xCls, OWLClass topXCls, 
			String label, List<String> xOrSplit, String matchType) {
		
		if (cls.getIRI().toString().equals("http://id.who.int/icd/entity/1451975552") && 
			xCls.getIRI().toString().equals("http://id.who.int/icd/entity/1158662338") ) {
			System.out.println(cls + " " + xCls + " " + label + " " + xOrSplit);
		}
		
		if (xOrSplit.size() < 2) { //means it is the same as the string, and was covered before
			return false;
		}
		
		for (String xLabelOrSplit : xOrSplit) {
			boolean match = matchWithLabel(cls, xCls, topXCls, label, xLabelOrSplit, matchType);
			if (match == true) {
				return true;
			}
		}
		
		return false;
	}

	private boolean matchWithShortLabel(OWLClass cls, OWLClass xCls, OWLClass topXCls, 
										String shortLabel, boolean checkForDuplicates) {
		String xShortLabel = labelCache.getShortLabel(xCls);
		
//		if (xCls.getIRI().toString().equals("http://id.who.int/icd/entity/1485925736")) {
//			System.out.println(cls + " " + xCls);
//		}
		
		if (labelCache.isExcludedLabel(xShortLabel)) {
			return false;
		}
		
		//don't match if this short label is duplicated, e.g. "unknown", or "adult"
		if (checkForDuplicates == true && labelCache.isDuplicatedLabel(xShortLabel)) { 
			return false;
		}
		
		String xClsLabel = labelCache.getLabel(xCls);
		String clsLabel = labelCache.getLabel(cls);
		shortLabel = shortLabel == null ? labelCache.getShortLabel(cls) : shortLabel;
		
		boolean match = false;
		
		if (xShortLabel != null && shortLabel != null && 
				(xShortLabel.equals(xClsLabel) == false || 
				shortLabel.equals(clsLabel) == false)) {
			if (matchWithLabel(cls, xCls, topXCls, shortLabel, xShortLabel, "SHORT LABELS") == true) {
				match = true;
			}
		}
		
		return match;
	}

	
	private boolean matchWithLabel(OWLClass cls, OWLClass xCls, OWLClass topXCls, 
			String label, String xLabel, String matchType) {
		
		if (labelCache.isExcludedLabel(xLabel) == true) {
			return false;
		}
		
		int matchScore = match(label, xLabel);

		if (	matchScore > FILLER_MATCH_TRESHOLD && 
				matchExists(cls, xCls, topXCls) == false
				//&& parentHasSameOrMoreSpecificXMatch(cls, xCls, topXCls) == false 
				) {
					addMatch(cls, xCls, matchScore, topXCls, label, xLabel, matchType);
					return true;
		}
		
		return false;
	}



	private boolean matchExists(OWLClass cls, OWLClass xCls, OWLClass topXCls) {
		List<XMatch> matches = xMatchCache.getMatches(cls);
		
		for (XMatch xMatch : matches) {
			if (xCls.equals(xMatch.getXMatch()) && topXCls.equals(xMatch.getTopXCls())) {
				return true;
			}
		}
		return false;
	}

	//can do at post-processing
	private boolean parentHasSameOrMoreSpecificXMatch(OWLClass cls, OWLClass xCls, OWLClass topXCls) {
		Set<OWLClass> parents = OWLAPIUtil.getNamedSuperclasses(cls, sourceOntology, reasoner, false);
		
		for (OWLClass parent : parents) {
			List<XMatch> parentMatches = xMatchCache.getMatches(parent);
			if (parentMatches == null) {
				continue;
			}
			
			for (XMatch match : parentMatches) {
				OWLClass parentFiller = match.getXMatch();
				OWLClass parentTopXCls = match.getTopXCls();
				if (topXCls.equals(parentTopXCls)) {
					if (parentFiller.equals(xCls)) {
						return true;
					}
					if (OWLAPIUtil.isSubclassOf(parentFiller, xCls, reasoner)) {
						return true;
					}
				}
			}
		}
	
		return false;
	}
	
	private boolean isContainedInMechanism(OWLClass cls, OWLClass xCls, String xLabel) {
		//just testing for the local mechanism
		OWLClass mech = xMatchCache.getMechanism(cls);
		
		if (mech == null) {
			return false;
		}
		
		return labelCache.getLabel(mech).contains(xLabel);
	}

	/**
	 * It will only add the match in a cache, and we will add the fillers in the post-processing
	 */
	private void addMatch(OWLClass cls, OWLClass xcls, int score, OWLClass xTopCls, 
			String matchedLabel, String xMatchLabel, String matchType) {
		
		if (cls.getIRI().toString().equals("http://id.who.int/icd/entity/29768622") && 
				xcls.getIRI().toString().equals("http://id.who.int/icd/entity/1652082013")) {
			System.out.println(labelCache.getLabel(cls) + " -> " + labelCache.getLabel(xcls));
		}
		
		if (isMechanismTopXCls(xTopCls) == true) {
			
			if (shouldAddMechanism(cls, xcls, xTopCls, score, matchType) == true) {
				xMatchCache.setMechanism(cls, xcls, xTopCls, score, matchedLabel, xMatchLabel, matchType);
				//xMatchCache.addMatch(cls, xcls, xTopCls, score, matchedLabel, xMatchLabel, matchType);
			}
		} else {
			xMatchCache.addMatch(cls, xcls, xTopCls, score, matchedLabel, xMatchLabel, matchType);
		}
	}
	
	
	private boolean shouldAddMechanism(OWLClass cls, OWLClass xcls, OWLClass xTopCls, 
			int score, String matchType) {
		if (cls.getIRI().toString().equals("http://id.who.int/icd/entity/1018874801")) {
			System.out.println(labelCache.getLabel(cls));
		}
		
		XMatch mechXMatch = xMatchCache.getMechanismXMatch(cls);
		if (mechXMatch == null) {
			return true;
		}
		
		if (mechXMatch.getScore() < score || 
			(mechXMatch.getScore() == score && matchType.equals("PREF LABELS"))) {
			return true;
		}
		
		return false;
	}

	private boolean isMechanismTopXCls(OWLClass xTopCls) {
		return xChapterCache.getAspectsOfMechanismTopXCls().equals(xTopCls) ||
				xChapterCache.getTransportMechanismTopXCls().equals(xTopCls);
	}
	


	private int match(String bigStr, String smallStr) {
		if (bigStr == null || smallStr == null) {
			return -1;
		}
		
		//return bigStr.contains(smallStr) ? 100 : 0;
		//return StringMatcher.contains(bigStr, smallStr, false) ? 100 : 0;
		//return FuzzySearch.tokenSortPartialRatio(bigStr, smallStr);
		//boolean contains = bigStr.contains(smallStr);
		boolean contains = bigStr.contains(smallStr) && 
						   StringMatcher.contains(bigStr, smallStr, false) ;
		return contains == true ? FuzzySearch.ratio(bigStr, smallStr) : 0;
	}
	

	private void addIntent(OWLClass subcls) {
		String title = OWLAPIUtil.getSKOSPrefLabelValue(sourceOntology, subcls);
		Intent intent = ExtCausesStringMatcher.getIntent(title);
		
		if (intent != null) {
			xMatchCache.setIntent(subcls, intent);
		}
	}
	
	
	private OWLClass getMechanism(OWLClass cls) {
		 OWLClass mech = xMatchCache.getMechanism(cls);
		 
		 if (mech != null) {
			 return mech;
		 }
		 
		 //let's hope the parents are ordered.., if not, go through them
		 for (OWLClass parent : OWLAPIUtil.getNamedSuperclasses(cls, sourceOntology, reasoner, false)) {
			mech = xMatchCache.getMechanism(parent);
			if (mech != null) {
				return mech;
			}
		}
		 
		 return mech;
	 }
	
	 
	 private boolean shouldMatchWithSubstances(OWLClass cls) {
		 String label = labelCache.getLabel(cls);
		 
		 return (label != null && label.contains("harmful")) || hasSubstancesMechanism(cls);
	 }
	 
	 private boolean hasSubstancesMechanism(OWLClass cls) {
		OWLClass mech = getMechanism(cls);
		return mech == null ?  false : xChapterCache.getSubstancesMechanismTopXCls().equals(mech);
	 }
	
	
	/***************** general methods *******************/
	
	private void attachAnnotations(OWLClass sourceClass)
			throws OWLOntologyChangeException {
		Stream<OWLAnnotationAssertionAxiom> annAssertionsStream = sourceOntology
				.annotationAssertionAxioms(sourceClass.getIRI());
		annAssertionsStream.forEach(s -> ontologyManager.addAxiom(targetOntology, s)); //TODO filter by excluded annotations
	}


	private Set<OWLClass> getTopClasses(OWLOntology ontology, Collection<String> topClassNames) {
		HashSet<OWLClass> classes = new HashSet<OWLClass>();

		for (String owlClassName : topClassNames) {
			IRI iri = IRI.create(owlClassName);
			OWLClass owlClass = df.getOWLClass(iri);
			if (owlClass == null) {
				log.warn("Could not find OWL class " + owlClassName + ". Ignore.");
			} else {
				classes.add(owlClass);
			}
		}

		return classes;
	}
	
	private static OWLReasoner initReasoner(OWLOntology ontology) {
		OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
		OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
		return reasoner;
	}
	
	private void cleanUp() {
		traversed.clear();
		importedClassesCount = 0;
	}

	
}
