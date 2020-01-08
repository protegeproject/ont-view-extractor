package org.fma.icd.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.search.Searcher;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;


public class OWLAPIUtil {

	public static String getRDFSLabelValue(OWLOntology ont, OWLClass cls) {
		OWLAnnotationProperty rdfsLabel = ont.getOWLOntologyManager().getOWLDataFactory().
				getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
		return getStringAnnotationValue(ont, cls, rdfsLabel);
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
			filter(c -> c.isBottomEntity() == false).
			forEach(subclses::add);

		return subclses;
	}
	
	public static Set<OWLClass> getNamedSuperclasses(OWLClass owlClass, OWLOntology ontology, 
			OWLReasoner reasoner, boolean direct) {
		Set<OWLClass> superClses = new HashSet<OWLClass>();
		Stream<OWLClass> superClsesStream = reasoner.getSuperClasses(owlClass, direct).entities();
		
		superClsesStream.
			filter(c -> c.isBottomEntity() == false).
			forEach(superClses::add);

		return superClses;
	}
	
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
					//do nothing. We can disreagard this is path,
					//as it did not lead to the superClass
					
					//TODO: delete this condition after we fix getNamedSuperClasses
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
