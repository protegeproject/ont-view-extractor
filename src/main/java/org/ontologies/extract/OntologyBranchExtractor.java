package org.ontologies.extract;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
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

/**
 * @author Tania Tudorache
 *
 */
public class OntologyBranchExtractor {

	private static transient Logger log = Logger.getLogger(OntologyBranchExtractor.class);

	private OWLOntologyManager ontologyManager;
	private OWLOntology sourceOntology;
	private OWLOntology targetOntology;
	private OWLReasoner reasoner;
	private URI outputOntologyFileURI; // cached for optimization purposes
	private Set<OWLClass> traversed = new HashSet<OWLClass>();
	private int importedClassesCount = 0;

	private int logCount; // default 100
	private int saveCount; // default 100

	public OntologyBranchExtractor(OWLOntologyManager manager, OWLOntology sourceOntology, OWLOntology targetOntology,
			URI outputFileURI, OWLReasoner reasoner) {
		this.ontologyManager = manager;
		this.sourceOntology = sourceOntology;
		this.targetOntology = targetOntology;
		this.outputOntologyFileURI = outputFileURI;
		this.reasoner = reasoner;
	}

	public static void main(String[] args) {
		try {

			// BasicConfigurator.configure();

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

			OntologyBranchExtractor extractor = new OntologyBranchExtractor(manager, sourceOnt, targetOnt,
					outputOntFile.toURI(), initReasoner(sourceOnt) );

			log.info("Started ontology extraction on " + new Date());
			extractor.extract(ExportProperties.getTopClasses());

			log.info("Finished ontology extraction on " + new Date());
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

	private void extract(Collection<String> topClassNames) {
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
			declare(sourceClass);
			declareAndAttachAnnotations(sourceClass);
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
		Set<OWLClass> subclses = getNamedSubclasses(sourceClass, sourceOntology);
		for (OWLClass subcls : subclses) {
			OWLSubClassOfAxiom subclsAxiom = ontologyManager.getOWLDataFactory().getOWLSubClassOfAxiom(subcls,
					sourceClass);
			ontologyManager.addAxiom(targetOntology, subclsAxiom);
			extractClass(subcls);
		}
	}

	private Set<OWLClass> getNamedSubclasses(OWLClass owlClass, OWLOntology ontology) {
		Set<OWLClass> subclses = new HashSet<OWLClass>();
		Stream<OWLClass> subclsesStream = reasoner.getSubClasses(owlClass, true).entities();
		
		subclsesStream.
			filter(c -> c.isBottomEntity() == false).
			forEach(subclses::add);

		return subclses;
	}

	private void declareAndAttachAnnotations(OWLClass sourceClass)
			throws OWLOntologyChangeException {
		Stream<OWLAnnotationAssertionAxiom> annAssertionsStream = sourceOntology
				.annotationAssertionAxioms(sourceClass.getIRI());
		annAssertionsStream.forEach(s -> ontologyManager.addAxiom(targetOntology, s)); //TODO filter by excluded annotations
	}

	private void declare(OWLClass sourceClass) throws OWLOntologyChangeException {
		OWLDataFactory owlDataFactory = ontologyManager.getOWLDataFactory();
		ontologyManager.addAxiom(targetOntology, owlDataFactory.getOWLDeclarationAxiom(sourceClass));
	}

	private Set<OWLClass> getTopClasses(OWLOntology ontology, Collection<String> topClassNames) {
		HashSet<OWLClass> classes = new HashSet<OWLClass>();
		OWLDataFactory factory = ontologyManager.getOWLDataFactory();

		for (String owlClassName : topClassNames) {
			IRI iri = IRI.create(owlClassName);
			OWLClass owlClass = factory.getOWLClass(iri);
			if (owlClass == null) {
				log.warn("Could not find OWL class " + owlClassName + ". Ignore.");
			} else {
				classes.add(owlClass);
			}
		}

		return classes;
	}

	private void cleanUp() {
		traversed.clear();
	}

}
