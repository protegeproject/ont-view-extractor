package org.icd.retired;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
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

import edu.stanford.smi.protegex.owl.model.impl.OWLUtil;


public class RetiredClasses {

	private static transient Logger log = Logger.getLogger(RetiredClasses.class);

	public final static String ICD_CATEGORIES = "http://who.int/icd#ICDCategory";
//	public static final String ICD_SYN_PROP = "http://who.int/icd_flattened/synonym";
	public static final String ICAT_ID = "http://id.who.int/icd/schema/icatId";

	private OWLOntologyManager ontManager;
	private OWLReasoner reasoner;
	private OWLOntology icdOnt;
//	private OWLAnnotationProperty synProp;
	private OWLAnnotationProperty icatIdProp;

	private BufferedWriter resultCSVWriter;


	public RetiredClasses(OWLOntologyManager manager, OWLOntology sourceOnt,
			BufferedWriter bufferedWriter) {
		this.ontManager = manager;
		this.icdOnt = sourceOnt;
		this.resultCSVWriter = bufferedWriter;
		this.reasoner = initReasoner(icdOnt);
//		this.synProp = manager.getOWLDataFactory().getOWLAnnotationProperty(ICD_SYN_PROP);
		this.icatIdProp = manager.getOWLDataFactory().getOWLAnnotationProperty(ICAT_ID);
	}

	public void export() throws IOException {
		exportClasses();
		closeResultWriter();
	}


	private void exportClasses() {
		log.info("Starting comparison..");

		OWLClass icdClses = getRootClass();

		Set<OWLClass> allSubclses = OWLAPIUtil.getNamedSubclasses(icdClses, icdOnt, reasoner, false);
		
		int i = 0;
		
		for (OWLClass subcls : allSubclses) {
			checkSubcls(subcls, icdClses);
			i++;
			
			if (i % 100 == 0) {
				log.info("Checked " + i + " classes");
			}
		}

		log.info("Finished generating list of retired to be deleted");
	}

	
	private OWLClass getRootClass() {
		//original
		//return ontManager.getOWLDataFactory().getOWLClass(ICD_CATEGORIES);
		
		//new
		return ontManager.getOWLDataFactory().getOWLThing();
	}

	private String getClassLabel(OWLClass cls) {
		//original
		//return OWLAPIUtil.getRDFSLabelValue(icdOnt, cls);
		
		//new
		return OWLAPIUtil.getSKOSPrefLabelValue(icdOnt, cls);
	}

	private String getClassIRI(OWLClass cls) {
		//original
		//return cls.getIRI().toString();
		
		//new
		return OWLAPIUtil.getStringAnnotationValue(icdOnt, cls, icatIdProp);
	}
	
	
	private void checkSubcls(OWLClass subcls, OWLClass icdCategory) {
		
		String subclsLabel = getClassLabel(subcls);
		Set<OWLClass> superClses = OWLAPIUtil.getNamedSuperclasses(subcls, icdOnt, reasoner, false);
		
		for (OWLClass superCls : superClses) {
			if (labelContainsRetired(superCls)) {
				boolean toBeDeleted = true;
				for (List<OWLClass> path : OWLAPIUtil.getPathsToSuperclass(subcls, icdCategory, icdOnt, reasoner)) {
					if (! path.contains(superCls)) {
						//this is an alternative path to ICD Categories, which does not go through the retired class identified above.
						//We need to check that this path does not contain another retired class either
						boolean nonRetiredPath = true;
						for (OWLClass cls : path) {
							if (labelContainsRetired(cls)) {
								nonRetiredPath = false;
								break;
							}
						}
						if (nonRetiredPath) {
							System.out.println(subcls + " / " + superCls + " / " + subclsLabel + " / " +  getClassLabel(superCls));
							System.out.println(OWLAPIUtil.getPathsToSuperclass(subcls, icdCategory, icdOnt, reasoner));
							toBeDeleted = false;
							break;
						}
					}
				}
				if (toBeDeleted) {
					//TODO: check for ICD-10 code and not being part of the released set
					writeLine(subcls, superCls, subclsLabel, getClassLabel(superCls));
				}
			}
		}
	}
	
	private boolean labelContainsRetired(OWLClass cls) {
		String clsLabel = getClassLabel(cls);
		if (clsLabel == null) { //e.g. for owl:Thing
			return false;
		}

		return StringMatcher.contains(clsLabel.toLowerCase(), "retired");
	}
	
	private void writeLine(OWLClass subcls, OWLClass supercls, String subclsLabel, String superclsLabel) {
		try {
			resultCSVWriter.write(getClassIRI(subcls) + StringUtils.COL_SEPARATOR +
					getClassIRI(supercls) + StringUtils.COL_SEPARATOR + 
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

		RetiredClasses exporter = new RetiredClasses(man, sourceOnt, resultCSVWriter);
		exporter.export();

	}

}
