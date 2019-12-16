package org.logical.defs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.fma.icd.map.OWLAPIUtil;
import org.fma.icd.map.StringUtils;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;


public class ExportChapterXTopParentsToCSV {

	private static transient Logger log = Logger.getLogger(ExportChapterXTopParentsToCSV.class);

	public final static String X_CHAPTER = "http://who.int/icd#ChapterX";
	public static final String ICD_SYN_PROP = "http://who.int/icd_flattened/synonym";

	private OWLOntologyManager ontManager;
	private OWLReasoner reasoner;
	private OWLOntology icdOnt;
	private OWLAnnotationProperty synProp;

	private BufferedWriter resultCSVWriter;

	private Map<OWLClass, String> topCls2label = new HashMap<OWLClass, String>();

	public ExportChapterXTopParentsToCSV(OWLOntologyManager manager, OWLOntology sourceOnt,
			BufferedWriter bufferedWriter) {
		this.ontManager = manager;
		this.icdOnt = sourceOnt;
		this.resultCSVWriter = bufferedWriter;
		this.reasoner = initReasoner(icdOnt);
		this.synProp = manager.getOWLDataFactory().getOWLAnnotationProperty(ICD_SYN_PROP);
	}

	public void export() throws IOException {
		precacheTopClses();
		exportClasses();
		closeResultWriter();
	}

	private void precacheTopClses() {
		OWLClass chapterXTopClass = ontManager.getOWLDataFactory().getOWLClass(X_CHAPTER);
		Collection<OWLClass> directSubClses = OWLAPIUtil.getNamedSubclasses(chapterXTopClass, icdOnt, reasoner, true);
		for (OWLClass subcls : directSubClses) {
			topCls2label.put(subcls, OWLAPIUtil.getRDFSLabelValue(icdOnt, subcls));
		}
	}

	private void exportClasses() {
		log.info("Started export of X Chapter..");

		OWLClass chapterXTopClass = ontManager.getOWLDataFactory().getOWLClass(X_CHAPTER);

		Set<OWLClass> allSubclses = OWLAPIUtil.getNamedSubclasses(chapterXTopClass, icdOnt, reasoner, false);
		for (OWLClass subcls : allSubclses) {
			String label = OWLAPIUtil.getRDFSLabelValue(icdOnt, subcls);
			Collection<String> syns = OWLAPIUtil.getStringAnnotationValues(icdOnt, subcls, synProp);
			Set<OWLClass> superClses = OWLAPIUtil.getNamedSuperclasses(subcls, icdOnt, reasoner, true);
			Set<OWLClass> topClses = getTopClsesForCls(subcls);
			writeLine(subcls, label, syns, superClses, topClses);
		}

		log.info("Ended export of X Chapter");
	}

	
	private void writeLine(OWLClass subcls, String label, Collection<String> syns, 
			Set<OWLClass> superClses, Set<OWLClass> topClses) {
		try {
			resultCSVWriter.write(subcls.getIRI().toString() + StringUtils.COL_SEPARATOR +
					label + StringUtils.COL_SEPARATOR + 
					StringUtils.getCollectionString(syns) + StringUtils.COL_SEPARATOR +
					StringUtils.getLabelCollectionString(icdOnt, topClses) + StringUtils.COL_SEPARATOR +
					StringUtils.getCollectionStringForClses(topClses) + StringUtils.COL_SEPARATOR + 
					StringUtils.getLabelCollectionString(icdOnt, superClses) + StringUtils.COL_SEPARATOR +
					StringUtils.getCollectionStringForClses(superClses));
        			
			resultCSVWriter.newLine();
    	} 
    	catch (IOException ioe) {
			log.error("Could not export line for: " + subcls);
		}

	}

	private Set<OWLClass> getTopClsesForCls(OWLClass subcls) {
		Set<OWLClass> topClses = new HashSet<OWLClass>();

		Set<OWLClass> superclses = reasoner.superClasses(subcls, false).collect(Collectors.toSet());

		for (OWLClass topCls : topCls2label.keySet()) {
			if (superclses.contains(topCls)) {
				topClses.add(topCls);
			}
		}

		return topClses;
	}

	private OWLReasoner initReasoner(OWLOntology ontology) {
		OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
		OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
		return reasoner;
	}

	private void closeResultWriter() throws IOException {
		resultCSVWriter.flush();
		resultCSVWriter.close();
	}

	public static void main(String[] args) throws OWLOntologyCreationException, IOException {
		if (args.length < 2) {
			log.error("Needs 2 params: (1) ICD OWL file, (2) output CSV file");
			return;
		}

		OWLOntologyManager man = OWLManager.createOWLOntologyManager();

		File icdOntFile = new File(args[0]);
		OWLOntology sourceOnt = man.loadOntologyFromOntologyDocument(icdOntFile);
		if (sourceOnt == null) {
			log.error("Could not load ICD ontology " + args[0]);
			return;
		}

		BufferedWriter resultCSVWriter = new BufferedWriter(new FileWriter(new File(args[1])));

		ExportChapterXTopParentsToCSV exporter = new ExportChapterXTopParentsToCSV(man, sourceOnt, resultCSVWriter);
		exporter.export();

	}

}
