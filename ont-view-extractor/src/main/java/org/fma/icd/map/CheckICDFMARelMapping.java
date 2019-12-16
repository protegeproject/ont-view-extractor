package org.fma.icd.map;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.search.Searcher;

import uk.ac.manchester.cs.owl.owlapi.OWLLiteralImplString;

public class CheckICDFMARelMapping {
	
	public static final String FMA_prj_path = "/Users/ttania/work/projects/icd-fma-mapping/_running/fma_5.0.0-functional.owl";
	public static final String ICD_prj_path = "/Users/ttania/work/projects/icd-fma-mapping/_running/icd-anatomy.owl";
	public static final String ICD_FMA_prj_path = "/Users/ttania/work/projects/icd-fma-mapping/_running/icd-fma-maps.owl";
	
	public static final String FMA_MAPPED_PROP_NAME = "http://who.int/icd_flattened/anatomy#FMAmap";
	public static final String ICD_MAPPED_PROP_NAME = "http://purl.org/sig/ont/fma.owl#ICDMap";
	
	public static final String MAPPED_IS_A_PROP_NAME = "http://who.int/icd_flattened/anatomy#mappedIsAParent";
	public static final String MAPPED_REL_PROP_NAME = "http://who.int/icd_flattened/anatomy#mappedRelParent";
	
	
	private static OWLOntology icdOnt;
	private static OWLOntology fmaOnt;
	private static OWLOntology mapOnt;
	
	private static OWLOntologyManager man;
	private static OWLAnnotationProperty fmaMapProp;
	private static OWLAnnotationProperty icdMapProp;
	
	
	public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException {
		man = OWLManager.createOWLOntologyManager();
		
		icdOnt = man.loadOntologyFromOntologyDocument(new File(ICD_prj_path));
		fmaOnt = man.loadOntologyFromOntologyDocument(new File(FMA_prj_path));
		mapOnt = man.createOntology();
		
		System.out.println("Loaded the ontologies");
		
		fmaMapProp = man.getOWLDataFactory().getOWLAnnotationProperty(FMA_MAPPED_PROP_NAME);
		icdMapProp = man.getOWLDataFactory().getOWLAnnotationProperty(ICD_MAPPED_PROP_NAME);
	
		icdOnt.classesInSignature().forEach(c -> processCls(c));
		
		System.out.println("Saving map ontology to " + ICD_FMA_prj_path );
		man.saveOntology(mapOnt, new FunctionalSyntaxDocumentFormat(), IRI.create(new File(ICD_FMA_prj_path)));
	}


	private static void processCls(OWLClass icdCls) {
		getMappedFmaClses(icdCls).forEach(c -> checkCls(icdCls,c)); 
	}
	
	private static List<OWLClass> getMappedFmaClses(OWLClass icdCls) {
		List<Stream<OWLAnnotationValue>> annotValList = icdOnt.annotationAssertionAxioms(icdCls.getIRI()).
				map(ax -> Searcher.annotationObject(ax, fmaMapProp)).
				map(ann -> Searcher.values(ann)).
				collect(Collectors.toList());
			
		List<OWLClass> fmaClses = new ArrayList<OWLClass>();
		annotValList.forEach(av -> 
						av.forEach(v -> 
							fmaClses.add(man.getOWLDataFactory().getOWLClass
									(IRI.create((String)((OWLLiteralImplString)v).getLiteral())))));
		
		return fmaClses;
	}
	
	
	private static List<OWLClass> getMappedICDClses(OWLClass fmaCls) {
		List<Stream<OWLAnnotationValue>> annotValList = fmaOnt.annotationAssertionAxioms(fmaCls.getIRI()).
				map(ax -> Searcher.annotationObject(ax, icdMapProp)).
				map(ann -> Searcher.values(ann)).
				collect(Collectors.toList());
			
		List<OWLClass> icdClses = new ArrayList<OWLClass>();
		annotValList.forEach(av -> 
						av.forEach(v -> 
							icdClses.add(man.getOWLDataFactory().getOWLClass
									(IRI.create((String)((OWLLiteralImplString)v).getLiteral())))));
		
		return icdClses;
	}

	private static void checkCls(OWLClass icdCls, OWLClass fmaCls) {
		System.out.println(icdCls + " -> " + fmaCls);
		
		checkClsIsaMaps(icdCls, fmaCls);
		checkClsRelsMaps(icdCls, fmaCls);
	}
	

	private static void checkClsIsaMaps(OWLClass icdCls, OWLClass fmaCls) {
		List<OWLClassExpression> icdSuperclses = getSuperclses(icdCls, icdOnt); 
		for (OWLClassExpression icdSupercls : icdSuperclses) {
			if (icdSupercls instanceof OWLClass) { //always the case in ICD
				List<OWLClass> potMappedFMASuperclses = getMappedFmaClses((OWLClass) icdSupercls);
				for (OWLClass potMappedFMASupercls : potMappedFMASuperclses) {
					OWLSubClassOfAxiom ax = man.getOWLDataFactory().getOWLSubClassOfAxiom(fmaCls, potMappedFMASupercls);
					if (fmaOnt.containsAxiom(ax) == true) { //found a is-a match!
						addMappedIsa(icdCls,fmaCls,potMappedFMASupercls);
					} else {
						//TODO - not sure what do if no match is found
						System.out.println("No is-a match: " + icdCls + " " + fmaCls);
					}
				}
			} 
		}
	}
	
	private static void checkClsRelsMaps(OWLClass icdCls, OWLClass fmaCls) {
		List<OWLClassExpression> fmaSuperclses = getSuperclses(fmaCls, fmaOnt); 
		for (OWLClassExpression fmaSupercls : fmaSuperclses) {
			if (fmaSupercls instanceof OWLObjectSomeValuesFrom) { //TODO: treat the other rel types later
				OWLClassExpression filler = ((OWLObjectSomeValuesFrom)fmaSupercls).getFiller();
				if (filler instanceof OWLClass) { //don't treat other cases
					List<OWLClass> potMappedICDSuperclses = getMappedICDClses((OWLClass) filler);
					for (OWLClass potMappedICDSupercls : potMappedICDSuperclses) {
						OWLSubClassOfAxiom ax = man.getOWLDataFactory().getOWLSubClassOfAxiom(icdCls, potMappedICDSupercls);
						if (icdOnt.containsAxiom(ax) == true) { //found a rel match!
							addMappedRel(icdCls,fmaCls,((OWLObjectSomeValuesFrom)fmaSupercls).getProperty(), (OWLClass) filler, potMappedICDSupercls);
						} else {
							//TODO - not sure what do if no match is found
							System.out.println("No rel match: " + icdCls + " " + fmaCls + ((OWLObjectSomeValuesFrom)fmaSupercls).getProperty() + " " + filler );
						}
					}
				}
			}
		}
	}
	

	//TODO: get also the inherited fillers; get also the reified rels
	private static List<OWLClassExpression> getSuperclses(OWLClass cls, OWLOntology ont) {
		return ont.subClassAxiomsForSubClass(cls).
    	map(s -> ((OWLSubClassOfAxiom)s).getSuperClass()).collect(Collectors.toList());
    	//forEach(ce -> checkCls(icdCls, ce));
	}
	
	
	private static void addMappedIsa(OWLClass icdCls, OWLClass fmaCls, OWLClass potMappedFMASupercls) {
		OWLDataFactory df = man.getOWLDataFactory();
		OWLAnnotation ann = df.getOWLAnnotation(df.getOWLAnnotationProperty(MAPPED_IS_A_PROP_NAME), df.getOWLLiteral(potMappedFMASupercls.getIRI().toQuotedString())) ;
		man.addAxiom(mapOnt, df.getOWLAnnotationAssertionAxiom(icdCls.getIRI(), ann));
	}

	private static void addMappedRel(OWLClass icdCls, OWLClass fmaCls, OWLObjectPropertyExpression prop, 
			OWLClass filler, OWLClass potMappedICDSupercls) {
		OWLDataFactory df = man.getOWLDataFactory();
		String annVal = prop.toString() + " " + filler.getIRI() + " = " +potMappedICDSupercls.getIRI();
		OWLAnnotation ann = df.getOWLAnnotation(df.getOWLAnnotationProperty(MAPPED_REL_PROP_NAME), df.getOWLLiteral(annVal)) ;
		man.addAxiom(mapOnt, df.getOWLAnnotationAssertionAxiom(icdCls.getIRI(), ann));
	}
	
	
}
