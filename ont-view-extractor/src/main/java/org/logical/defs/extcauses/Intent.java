package org.logical.defs.extcauses;


enum Intent {
	
	//Appears as intent word + "of" or "with"
	//the order is important
	UNDET("undetermined intent", "UNDET"), 
	UNKNOWN("unknown intent", "UNKN"), 
	UNINTENTIONAL("unintentional", "UNINT"), 
	INTENTIONAL("intentional", "INT"); 
	

	private final String text;
	private final String shortText;

	Intent(String text, String shortText) {
		this.text = text;
		this.shortText = shortText;
	}

	public String getText() {
		return text;
	}
	
	public String getShortText() {
		return shortText;
	}

}
