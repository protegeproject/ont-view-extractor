package org.logical.defs.extcauses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLClass;

public class XMatchCache {

	private Map<OWLClass, List<XMatch>> cls2XMatches = new HashMap<OWLClass, List<XMatch>>();
	
	private Map<OWLClass, XMatch> cls2Mechanism = new HashMap<OWLClass, XMatch>();
	
	private Map<OWLClass, Intent> cls2Intent = new HashMap<OWLClass, Intent>();
	
	
	
	public void addMatch(OWLClass cls, OWLClass filler, OWLClass topXCls, int score, 
			String matchedLabel, String xMatchedLabel, String matchType) {
		List<XMatch> list = cls2XMatches.get(cls);
		
		if (list == null) {
			list = new ArrayList<XMatch>();
		}
		
		list.add(new XMatch(filler, topXCls, score, matchedLabel, xMatchedLabel, matchType));
		cls2XMatches.put(cls, list);
	}
	
	public List<XMatch> getMatches(OWLClass cls) {
		List<XMatch> ret = cls2XMatches.get(cls);
		return ret == null ? new ArrayList<XMatch>() : Collections.unmodifiableList(ret);
	}
	
	public Set<OWLClass> getClsesWithMatches() {
		return Collections.unmodifiableSet(cls2XMatches.keySet());
	}
	
	public Set<OWLClass> getClsesWithIntent() {
		return Collections.unmodifiableSet(cls2Intent.keySet());
	}
	
	public Set<OWLClass> getClsesWithMechanism() {
		return Collections.unmodifiableSet(cls2Mechanism.keySet());
	}
	
	public void setMechanism(OWLClass cls, OWLClass xCls, OWLClass xTopCls, int score,
			String matchedLabel, String xMatchLabel, String matchType) {
		cls2Mechanism.put(cls, new XMatch(xCls, xTopCls, score, matchedLabel, xMatchLabel, matchType));
	}
	
	public void setMechanism(OWLClass cls, XMatch mechMatch) {
		cls2Mechanism.put(cls, mechMatch);
	}
	
	public OWLClass getMechanism(OWLClass cls) {
		XMatch mechXMatch = getMechanismXMatch(cls);
		return mechXMatch == null ? null : mechXMatch.getXMatch();
	}
	
	public XMatch getMechanismXMatch(OWLClass cls) {
		return cls2Mechanism.get(cls);
	}
		
	public void setIntent(OWLClass cls, Intent intent) {
		cls2Intent.put(cls, intent);
	}
	
	public Intent getIntent(OWLClass cls) {
		return cls2Intent.get(cls);
	}
	
	public void deleteMatch(OWLClass cls, XMatch xMatch) {
		List<XMatch> matches = getMatches(cls);
		List<XMatch> copyMatches = new ArrayList<XMatch>(matches);
		copyMatches.remove(xMatch);
		cls2XMatches.put(cls, copyMatches);
	}
	
	public void setMatches(OWLClass cls, List<XMatch> matches) {
		cls2XMatches.put(cls, matches);
	}
}
