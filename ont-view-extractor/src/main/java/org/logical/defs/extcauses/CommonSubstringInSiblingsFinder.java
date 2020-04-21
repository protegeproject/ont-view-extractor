package org.logical.defs.extcauses;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.fma.icd.map.OWLAPIUtil;
import org.fma.icd.map.StringUtils;
import org.ontologies.extract.ExportProperties;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
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
import org.semanticweb.owlapi.vocab.SKOSVocabulary;

/**
 * Looks for common substrings within the siblings of any class and writes them out to an
 * annotation property commonSubstring and it will also write out the shortened name of siblings 
 * by substracting the common substring in an annotation property called shortLabel.
 * 
 * The output is a separate OWL file. The export is configured via the export.properties.
 * 
 * @author ttania
 *
 */
public class CommonSubstringInSiblingsFinder {

	private static transient Logger log = Logger.getLogger(CommonSubstringInSiblingsFinder.class);
	
	public final static String COMMON_SUBSTR_PROP = "http://id.who.int/icd/schema/commonSubstring";
	public final static String SHORT_LABEL_PROP = "http://id.who.int/icd/schema/shortLabel";
	
	public final static boolean ADD_SHORT_ANN_TO_SKOS_ALT_LABEL = true;
	
	private OWLOntologyManager ontologyManager;
	private OWLDataFactory df;
	
	private OWLOntology sourceOntology;
	private OWLOntology targetOntology;
	private OWLReasoner reasoner;
	
	private URI outputOntologyFileURI; // cached for optimization purposes
	private Set<OWLClass> traversed = new HashSet<OWLClass>();
	
	private OWLAnnotationProperty commonSubstringProp; 
	private OWLAnnotationProperty shortLabelProp;
	
	private int importedClassesCount = 0;

	private int logCount; // default 100
	private int saveCount; // default 100

	public CommonSubstringInSiblingsFinder(OWLOntologyManager manager, OWLOntology sourceOntology, OWLOntology targetOntology,
			URI outputFileURI, OWLReasoner reasoner) {
		this.ontologyManager = manager;
		this.df = ontologyManager.getOWLDataFactory();
		this.sourceOntology = sourceOntology;
		this.targetOntology = targetOntology;
		this.outputOntologyFileURI = outputFileURI;
		this.reasoner = reasoner;
		this.commonSubstringProp = df.getOWLAnnotationProperty(COMMON_SUBSTR_PROP);
		this.shortLabelProp = df.getOWLAnnotationProperty(SHORT_LABEL_PROP);
	}

	public static void main(String[] args) {
		try {

			BasicConfigurator.configure();

			// TODO: move all these intializations in methods and constructor
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

			CommonSubstringInSiblingsFinder extractor = new CommonSubstringInSiblingsFinder(manager, sourceOnt, targetOnt,
					outputOntFile.toURI(), initReasoner(sourceOnt) );

			log.info("Started computation on " + new Date());
			extractor.compute(ExportProperties.getTopClasses());

			log.info("Finished computation on " + new Date());
			log.info("Saving ontology");
			manager.saveOntology(targetOnt, IRI.create(outputOntFile));
			log.info("Done on " + new Date());
		} catch (Throwable t) {
			log.log(Level.ERROR, t.getMessage(), t);
		}

	}

	private static OWLReasoner initReasoner(OWLOntology ontology) {
		// reasoner = OpenlletReasonerFactory.getInstance().createReasoner(sourceOntology);
		// ((OpenlletReasoner)reasoner).getKB().realize();

		OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
		OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
		return reasoner;
	}

	private void compute(Collection<String> topClassNames) {
		Set<OWLClass> topClasses = getTopClasses(sourceOntology, topClassNames);
		if (topClasses.isEmpty() == true) {
			log.info("Empty top classes. Nothing to export.");
			return;
		}

		traversed.clear();

		for (OWLClass sourceClass : topClasses) {
			try {
				extractClass(sourceClass);
			} catch (Throwable t) {
				log.error("Error at adding class: " + sourceClass, t);
			}
		}

		cleanUp();
	}

	private void extractClass(OWLClass sourceClass) {
		if (traversed.contains(sourceClass) == true) {
			return;
		}
		traversed.add(sourceClass);

		try {
			attachAnnotations(sourceClass);
			addChildren(sourceClass);
		} catch (Throwable t) {
			log.error("Error at adding class: " + sourceClass, t);
		}

		importedClassesCount++;

		if (logCount > 0 && importedClassesCount % logCount == 0) {
			log.info("Imported " + importedClassesCount + " classes.\t Last imported class: " + sourceClass + " \t on "
					+ new Date());
		}

		if (saveCount > 0 && importedClassesCount % saveCount == 0) {
			long t0 = System.currentTimeMillis();
			log.info("Saving ontology (" + importedClassesCount + " classes imported) ... ");

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

	private void addChildren(OWLClass sourceClass) throws OWLOntologyChangeException {
		Set<OWLClass> subclses = OWLAPIUtil.getNamedSubclasses(sourceClass, sourceOntology, reasoner, true);
		
		Collection<String> commonSubstr = StringUtils.getCommonSubstrings(sourceOntology, subclses);
		
		for (OWLClass subcls : subclses) {
			OWLSubClassOfAxiom subclsAxiom = df.getOWLSubClassOfAxiom(subcls,
					sourceClass);
			ontologyManager.addAxiom(targetOntology, subclsAxiom);
			extractClass(subcls);
			addCommmonStrAnnotationProps(subcls, commonSubstr);
		}
	}


	private void addCommmonStrAnnotationProps(OWLClass subcls, Collection<String> commonSubstr) {
		OWLAnnotationProperty skosAltProp = df.getOWLAnnotationProperty(SKOSVocabulary.ALTLABEL);
		
		for (String commonStr : commonSubstr) {
			addAnnotationProperty(targetOntology, subcls, commonSubstringProp, commonStr, "en");
		}
		
		String prunnedTitle = StringUtils.pruneString(OWLAPIUtil.getSKOSPrefLabelValue(sourceOntology, subcls), commonSubstr);
		addAnnotationProperty(targetOntology, subcls, shortLabelProp, prunnedTitle, "en");
		
		if (addAnnsToSkosAlt() == true) {
			addAnnotationProperty(targetOntology, subcls, skosAltProp, prunnedTitle, "en");
		}
	}
	
	private void addAnnotationProperty(OWLOntology ont, OWLClass cls, OWLAnnotationProperty annProp, String value, String lang) {
		if (value == null || value.length() == 0) {
			return;
		}
		
		OWLAnnotation ann = df.getOWLAnnotation(annProp, lang == null ? df.getOWLLiteral(value) : df.getOWLLiteral(value, lang));
		ont.addAxiom(df.getOWLAnnotationAssertionAxiom(cls.getIRI(), ann));
	}

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

	private boolean addAnnsToSkosAlt() {
		return ADD_SHORT_ANN_TO_SKOS_ALT_LABEL == true;
	}
	
	private void cleanUp() {
		traversed.clear();
	}

	
}
