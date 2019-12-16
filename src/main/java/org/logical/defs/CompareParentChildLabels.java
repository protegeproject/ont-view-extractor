package org.logical.defs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

import org.apache.log4j.Logger;
import org.fma.icd.map.OWLAPIUtil;
import org.fma.icd.map.StringMatcher;
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


public class CompareParentChildLabels {

	private static transient Logger log = Logger.getLogger(CompareParentChildLabels.class);

	public final static String ICD_CATEGORIES = "http://who.int/icd#ICDCategory";
	public static final String ICD_SYN_PROP = "http://who.int/icd_flattened/synonym";

	private OWLOntologyManager ontManager;
	private OWLReasoner reasoner;
	private OWLOntology icdOnt;
	private OWLAnnotationProperty synProp;

	private BufferedWriter resultCSVWriter;


	public CompareParentChildLabels(OWLOntologyManager manager, OWLOntology sourceOnt,
			BufferedWriter bufferedWriter) {
		this.ontManager = manager;
		this.icdOnt = sourceOnt;
		this.resultCSVWriter = bufferedWriter;
		this.reasoner = initReasoner(icdOnt);
		this.synProp = manager.getOWLDataFactory().getOWLAnnotationProperty(ICD_SYN_PROP);
	}

	public void export() throws IOException {
		exportClasses();
		closeResultWriter();
	}


	private void exportClasses() {
		log.info("Starting comparison..");

		OWLClass icdClses = ontManager.getOWLDataFactory().getOWLClass(ICD_CATEGORIES);

		Set<OWLClass> allSubclses = OWLAPIUtil.getNamedSubclasses(icdClses, icdOnt, reasoner, false);
		
		int i = 0;
		
		for (OWLClass subcls : allSubclses) {
			checkSubcls(subcls);
			i++;
			
			if (i % 100 == 0) {
				log.info("Checked " + i + " classes");
			}
		}

		log.info("Ended export of X Chapter");
	}

	
	
	private void checkSubcls(OWLClass subcls) {
		
		String subclsLabel = OWLAPIUtil.getRDFSLabelValue(icdOnt, subcls);
		Set<OWLClass> superClses = OWLAPIUtil.getNamedSuperclasses(subcls, icdOnt, reasoner, true);
		
		for (OWLClass superCls : superClses) {
			String superClsLabel = OWLAPIUtil.getRDFSLabelValue(icdOnt, superCls);
			boolean isMatch = StringMatcher.contains(subclsLabel, superClsLabel);
			
			if (isMatch == true) {
				writeLine(subcls, superCls, subclsLabel, superClsLabel);
			}
		}
	}
	
	
	private void writeLine(OWLClass subcls, OWLClass supercls, String subclsLabel, String superclsLabel) {
		try {
			resultCSVWriter.write(subcls.getIRI().toString() + StringUtils.COL_SEPARATOR +
					supercls.getIRI().toString() + StringUtils.COL_SEPARATOR + 
					subclsLabel + StringUtils.COL_SEPARATOR +
					superclsLabel);
        			
			resultCSVWriter.newLine();
    	} 
    	catch (IOException ioe) {
			log.error("Could not export line for: " + subcls + " super: " + supercls);
		}
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

		CompareParentChildLabels exporter = new CompareParentChildLabels(man, sourceOnt, resultCSVWriter);
		exporter.export();

	}

}
