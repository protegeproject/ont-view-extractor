package org.logical.defs;

import java.util.Collection;

import org.semanticweb.owlapi.model.OWLClass;

public class OWLClsWithSyns {

	private OWLClass owlCls;
	private String label;
	private Collection<String> syns;
	private Collection<OWLClass> superClses;

	public OWLClsWithSyns(OWLClass owlCls, String label, Collection<String> syns, Collection<OWLClass> superClses) {
		super();
		this.owlCls = owlCls;
		this.label = label;
		this.syns = syns;
		this.superClses = superClses;
	}

	public OWLClass getOwlCls() {
		return owlCls;
	}

	public String getLabel() {
		return label;
	}

	public Collection<String> getSyns() {
		return syns;
	}

	public Collection<OWLClass> getSuperClses() {
		return superClses;
	}

}
