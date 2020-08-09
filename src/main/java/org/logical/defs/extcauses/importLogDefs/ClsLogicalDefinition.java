package org.logical.defs.extcauses.importLogDefs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.RDFProperty;
import edu.stanford.smi.protegex.owl.model.RDFSNamedClass;

public class ClsLogicalDefinition {
	
	private OWLModel owlModel;
	private RDFSNamedClass cls;
	
	private RDFSNamedClass precoordParent;
	private List<RDFSNamedClass> inheritancePath;
	
	private Map<RDFProperty, RDFSNamedClass> prop2Filler = new HashMap<RDFProperty, RDFSNamedClass>();
	
	public ClsLogicalDefinition(OWLModel owlModel, RDFSNamedClass cls) {
		this.owlModel = owlModel;
		this.cls = cls;
	}
	
	public void setProp2Filler(RDFProperty prop, RDFSNamedClass filler) {
		prop2Filler.put(prop, filler);
	}
	
	public void removePropFiller(RDFProperty prop) {
		prop2Filler.remove(prop);
	}
	
	public RDFSNamedClass getFiller(RDFProperty prop) {
		return prop2Filler.get(prop);
	}
	
	public RDFSNamedClass getPrecoordParent() {
		return precoordParent;
	}
	
	public List<RDFSNamedClass> getInheritancePath() {
		return inheritancePath;
	}
	
	public void setInheritancePath(List<RDFSNamedClass> inheritancePath) {
		this.inheritancePath = inheritancePath;
		//the last in the inheritance path
		this.precoordParent = inheritancePath.get(inheritancePath.size() - 1);
	}
	
	public boolean hasAnyFillers() {
		return prop2Filler.size() > 0;
	}
	
	public RDFSNamedClass getCls() {
		return cls;
	}
	
	public Collection<RDFProperty> getPropertiesWithFillers() {
		List<RDFProperty> filledProps = new ArrayList<RDFProperty>();
		
		for (RDFProperty prop : prop2Filler.keySet()) {
			if (getFiller(prop) != null) {
				filledProps.add(prop);
			}
		}
		return filledProps;
	}
}

