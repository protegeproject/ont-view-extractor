package org.fma.icd.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationSubject;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.search.Searcher;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.semanticweb.owlapi.vocab.SKOSVocabulary;


public class OWLAPIUtil {
	
	
	/*************** Annotations *****************/
	

	public static String getRDFSLabelValue(OWLOntology ont, OWLClass cls) {
		OWLAnnotationProperty rdfsLabel = ont.getOWLOntologyManager().getOWLDataFactory().
				getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
		return getStringAnnotationValue(ont, cls, rdfsLabel);
	}
	

	public static String getSKOSPrefLabelValue(OWLOntology ont, OWLClass cls) {
		OWLAnnotationProperty skosPrefLabel = ont.getOWLOntologyManager().getOWLDataFactory().
				getOWLAnnotationProperty(SKOSVocabulary.PREFLABEL.getIRI());
		return getStringAnnotationValue(ont, cls, skosPrefLabel);
	}
	

	public static String getStringAnnotationValue(OWLOntology ont, OWLClass cls, OWLAnnotationProperty prop) {
		Optional<OWLAnnotationValue> ann = Searcher
				.values(Searcher.annotationObjects(ont.annotationAssertionAxioms(cls.getIRI()), prop)).findFirst();
		return ann.isPresent() ? ann.get().asLiteral().get().getLiteral() : null;
	}

	public static String[] getStringAnnotationValuesArray(OWLOntology ont, OWLClass cls, OWLAnnotationProperty prop) {
		List<String> annList = getStringAnnotationValues(ont, cls, prop);
		return annList == null || annList.isEmpty() ? null : annList.toArray(new String[annList.size()]);
	}
	
	
	public static List<String> getStringAnnotationValues(OWLOntology ont, OWLClass cls, OWLAnnotationProperty prop) {
		return Searcher
				.values(Searcher.annotationObjects(ont.annotationAssertionAxioms(cls.getIRI()), prop))
				.filter(v -> v.asLiteral().isPresent()).map(c -> c.asLiteral().get().getLiteral())
				.collect(Collectors.toList());
	}

	public static IRI getIRIAnnotationValue(OWLOntology ont, OWLAnnotationSubject subj, OWLAnnotationProperty prop) {
		Optional<OWLAnnotationValue> ann = Searcher
				.values(Searcher.annotationObjects(ont.annotationAssertionAxioms(subj), prop)).findFirst();
		return ann.isPresent() ? ann.get().asIRI().get() : null;
	}
	
	public static List<IRI> getIRIAnnotationValues(OWLOntology ont, OWLAnnotationSubject subj, OWLAnnotationProperty prop) {
		return  Searcher.
				  values(Searcher.annotationObjects(ont.annotationAssertionAxioms(subj), prop)).
				  filter(v -> v.asIRI().isPresent()).
				  map(c -> c.asIRI().get()).
				  collect(Collectors.toList());
	}
	
	
	public static void addAnnotationProperty(OWLOntology ont, OWLClass cls, OWLAnnotationProperty annProp, String value, String lang) {
		if (value == null || value.length() == 0) {
			return;
		}
		OWLDataFactory df = ont.getOWLOntologyManager().getOWLDataFactory();
		
		OWLAnnotation ann = df.getOWLAnnotation(annProp, lang == null ? df.getOWLLiteral(value) : df.getOWLLiteral(value, lang));
		ont.addAxiom(df.getOWLAnnotationAssertionAxiom(cls.getIRI(), ann));
	}
	
	public static void addIRIAnnotationProperty(OWLOntology ont, OWLClass cls, OWLAnnotationProperty annProp, IRI value) {
		if (value == null || value.length() == 0) {
			return;
		}
		OWLDataFactory df = ont.getOWLOntologyManager().getOWLDataFactory();
		
		OWLAnnotation ann = df.getOWLAnnotation(annProp, value);
		ont.addAxiom(df.getOWLAnnotationAssertionAxiom(cls.getIRI(), ann));
	}
	
	public static boolean isDeprecated(OWLOntology ont, OWLClass cls) {
		OWLDataFactory df = ont.getOWLOntologyManager().getOWLDataFactory();
		Optional<OWLAnnotationValue> optVal = Searcher.
		  values(Searcher.annotationObjects(ont.annotationAssertionAxioms(cls.getIRI()), df.getOWLDeprecated())).
		  findFirst();
		
		return optVal.isPresent() && optVal.get().asLiteral().get().parseBoolean() == true;
	}
	
	
	/*************** Sub- and super-classes *****************/
	
	
	public static List<OWLClassExpression> getSuperclses(OWLOntology ont, OWLClass cls) {
		return ont.subClassAxiomsForSubClass(cls).map(s -> ((OWLSubClassOfAxiom) s).getSuperClass())
				.collect(Collectors.toList());
	}
	
	public static List<OWLClassExpression> getSubclasses(OWLOntology ont, OWLClass cls) {
		return ont.subClassAxiomsForSubClass(cls).map(s -> ((OWLSubClassOfAxiom) s).getSubClass())
				.collect(Collectors.toList());
	}
	
	public static Set<OWLClass> getNamedSubclasses(OWLClass owlClass, OWLOntology ontology, 
			OWLReasoner reasoner, boolean direct) {
		Set<OWLClass> subclses = new HashSet<OWLClass>();
		Stream<OWLClass> subclsesStream = reasoner.getSubClasses(owlClass, direct).entities();
		
		subclsesStream.
			filter(c -> c.isBottomEntity() == false).	//filtering out owl:Nothing
			forEach(subclses::add);

		return subclses;
	}
	
	public static Set<OWLClass> getNamedSuperclasses(OWLClass owlClass, OWLOntology ontology, 
			OWLReasoner reasoner, boolean direct) {
		Set<OWLClass> superClses = new HashSet<OWLClass>();
		Stream<OWLClass> superClsesStream = reasoner.getSuperClasses(owlClass, direct).entities();
		
		superClsesStream.
			filter(c -> c.isBottomEntity() == false).	//filtering out owl:Nothing, which is never a desirable superclass to be returned
			forEach(superClses::add);

		return superClses;
	}
	
	public static OWLClass getFirstNamedSuperclass(OWLClass owlClass, OWLOntology ontology, 
			OWLReasoner reasoner) {
		Set<OWLClass> superclses  = getNamedSuperclasses(owlClass, ontology, reasoner, true);
		return superclses.iterator().next();
	}
	
	public static boolean isSubclassOf(OWLClass subCls, OWLClass superCls, OWLReasoner reasoner) {
		//return reasoner.superClasses(subCls).anyMatch(p -> p.equals(superCls));
		NodeSet<OWLClass> superClses = reasoner.getSuperClasses(subCls, false);
		return superClses.containsEntity(superCls);
	}
	
	/*************** Path to root *****************/
	
	
	public static Set<List<OWLClass>> getPathsToSuperclass(OWLClass owlClass, OWLClass superClass, 
			OWLOntology ontology, OWLReasoner reasoner) {
		Set<List<OWLClass>> res = new HashSet<List<OWLClass>>();
		//OWLClass owlThing = ontology.getOWLOntologyManager().getOWLDataFactory().getOWLThing();
		Set<List<OWLClass>> paths = new HashSet<List<OWLClass>>();
		paths.add(Arrays.asList(new OWLClass[] {owlClass}));
		while ( ! paths.isEmpty() ) {
			Set<List<OWLClass>> nextPaths = new HashSet<List<OWLClass>>();
			for (List<OWLClass> path : paths) {
				OWLClass lastClassInPath = path.get(path.size() - 1);
				if (lastClassInPath.equals(superClass)) {
					res.add(path);
				}
				else if (lastClassInPath.isTopEntity()) {
					//do nothing. We can disregard this path,
					//as it did not lead to the superClass
				}
				else {
					Set<OWLClass> superclasses = getNamedSuperclasses(lastClassInPath, ontology, reasoner, true);
					for (OWLClass superclass : superclasses) {
						if (path.contains(superclass))  {
							//do nothing. We can disregard this path,
							//as it contains a loop and exploring all such paths to the superClass
							//will never end.
						}
						else {
							List<OWLClass> newPath = new ArrayList<OWLClass>(path.size() + 1);
							path.stream().forEach((n)->{newPath.add(n);});
							newPath.add(superclass);
							nextPaths.add(newPath);
						}
					}
					
				}
				paths = nextPaths;
			}
		}
		return res;
	}


}
