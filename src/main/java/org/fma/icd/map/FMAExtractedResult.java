package org.fma.icd.map;

import me.xdrop.fuzzywuzzy.model.ExtractedResult;

public class FMAExtractedResult extends ExtractedResult {

	private String fmaId;
	private String fmaPrefLabel;
	
	public FMAExtractedResult(String string, int score, int index, String fmaId, String fmaPrefLabel) {
		super(string, score, index);
		this.fmaId=fmaId;
		this.fmaPrefLabel=fmaPrefLabel;
	}

	public String getFmaId() {
		return fmaId;
	}
	
	public String getFmaPrefLabel() {
		return fmaPrefLabel;
	}
}