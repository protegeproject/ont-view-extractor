package org.fma.icd.map;

public class ICDFMAMatchRecord {
	
	private String icdId;
	private String fmaId;
	private String icdTitle;
	private String fmaPrefLabel;
	private String matchedICDString;
	private String icdMatchType;
	private String matchedFMAString;
	private String fmaMatchType;
	private int score;

	public ICDFMAMatchRecord(String icdId, String fmaId, String icdTitle, String fmaPrefLabel, String matchedICDString,
			String icdMatchType, String matchedFMAString, String fmaMatchType, int score) {
		super();
		this.icdId = icdId;
		this.fmaId = fmaId;
		this.icdTitle = icdTitle;
		this.fmaPrefLabel = fmaPrefLabel;
		this.matchedICDString = matchedICDString;
		this.icdMatchType = icdMatchType;
		this.matchedFMAString = matchedFMAString;
		this.fmaMatchType = fmaMatchType;
		this.score = score;
	}

	public String getIcdId() {
		return icdId;
	}

	public String getFmaId() {
		return fmaId;
	}

	public String getIcdTitle() {
		return icdTitle;
	}

	public String getFmaPrefLabel() {
		return fmaPrefLabel;
	}

	public String getMatchedICDString() {
		return matchedICDString;
	}

	public String getIcdMatchType() {
		return icdMatchType;
	}

	public String getMatchedFMAString() {
		return matchedFMAString;
	}

	public String getFmaMatchType() {
		return fmaMatchType;
	}

	public int getScore() {
		return score;
	}
	

}
