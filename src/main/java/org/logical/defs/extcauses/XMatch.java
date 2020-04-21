package org.logical.defs.extcauses;

import org.semanticweb.owlapi.model.OWLClass;

public class XMatch {

	private OWLClass xMatch;

	private OWLClass topXCls;
	
	private int score;
	private String matchedLabel;
	private String xMatchedLabel;
	private String matchType;
	private boolean isInherited = false;
	
	public XMatch(OWLClass xMatch, OWLClass topXCls, int score, 
			String matchedLabel, String xMatchedLabel, String matchType) {
		this.xMatch = xMatch;
		this.topXCls = topXCls;
		this.score = score;
		this.matchedLabel = matchedLabel;
		this.xMatchedLabel = xMatchedLabel;
		this.matchType = matchType;
		this.isInherited = false;
	}
	
	public OWLClass getXMatch() {
		return xMatch;
	}

	public OWLClass getTopXCls() {
		return topXCls;
	}
	
	public int getScore() {
		return score;
	}

	public String getMatchedLabel() {
		return matchedLabel;
	}

	public String getMatchedXLabel() {
		return xMatchedLabel;
	}

	public String getMatchType() {
		return matchType;
	}

	public void setInherited(boolean isInherited) {
		this.isInherited = isInherited;
	}
	
	public boolean isInherited() {
		return isInherited;
	}
	
	public XMatch clone() {
		XMatch clone = new XMatch(xMatch, topXCls, score, matchedLabel, xMatchedLabel, matchType);
		clone.setInherited(isInherited);
		return clone;
	}
	
}
