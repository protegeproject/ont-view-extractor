package org.fma.icd.map;

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
	

}
