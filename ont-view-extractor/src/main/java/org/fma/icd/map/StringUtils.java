package org.fma.icd.map;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;

import edu.stanford.smi.protegex.owl.model.RDFResource;

public class StringUtils {
	
	public static final String PUBLIC_BROWSER_BASE_URL = "https://icd.who.int/dev11/f/en#/";
	public static final String ICAT_BASE_URL = "https://icat.stanford.edu/";
	public static final String ICAT_ARGS = "?ontology=ICD&tab=ClassesTab&id=";
	
	public static final String COL_SEPARATOR = "\t";
	public static final String VALUE_SEPARATOR = "*";
	public static final String QUOTE = "\"";

	public static String toCsvField(Object o) {
		String res = (o == null ? "" : o.toString());
		if (res.contains("\n")) {
			res = res.replace("\n", " ");
		}
		if (res.contains(COL_SEPARATOR) || res.contains(VALUE_SEPARATOR) || res.contains(QUOTE)) {
			res = res.replaceAll(QUOTE, QUOTE + QUOTE);
			res = QUOTE + res + QUOTE;
		}
		return res;
	}
	
	public static String getCollectionString(Collection<String> vals) {
		StringBuffer s = new StringBuffer();
		for (String val : vals) {
			s.append(val);
			s.append(VALUE_SEPARATOR);
		}
		//remove last value separator
		if (s.length() > 0) {
			s.delete(s.length()-VALUE_SEPARATOR.length(),s.length());
		}
		return s.toString();
	}
	
	public static String getCollectionStringForClses(Collection<OWLClass> clses) {
		StringBuffer s = new StringBuffer();
		for (OWLClass cls : clses) {
			s.append(cls.getIRI().toQuotedString());
			s.append(VALUE_SEPARATOR);
		}
		//remove last value separator
		if (s.length() > 0) {
			s.delete(s.length()-VALUE_SEPARATOR.length(),s.length());
		}
		return s.toString();
	}
	
	public static String getLabelCollectionString(OWLOntology ont, Collection<OWLClass> clses) {
		StringBuffer s = new StringBuffer();
		for (OWLClass cls : clses) {
			s.append(OWLAPIUtil.getRDFSLabelValue(ont, cls));
			s.append(VALUE_SEPARATOR);
		}
		//remove last value separator
		if (s.length() > 0) {
			s.delete(s.length()-VALUE_SEPARATOR.length(),s.length());
		}
		return s.toString();
	}
	
	
	public static String getLabelCollectionString(Collection<RDFResource> resources) {
		StringBuffer s = new StringBuffer();
		for (RDFResource res : resources) {
			s.append(res.getBrowserText());
			s.append(VALUE_SEPARATOR);
		}
		//remove last value separator
		if (s.length() > 0) {
			s.delete(s.length()-VALUE_SEPARATOR.length(),s.length());
		}
		return s.toString();
	}
	
	public static String getLabelCollection(Collection<RDFResource> resources) {
		StringBuffer s = new StringBuffer();
		for (RDFResource res : resources) {
			s.append(res.getName());
			s.append(VALUE_SEPARATOR);
		}
		//remove last value separator
		if (s.length() > 0) {
			s.delete(s.length()-VALUE_SEPARATOR.length(),s.length());
		}
		return s.toString();
	}
	
	public static String stripSingleQuotes(String str) {
		str = str.replace("'", "");
		return str;
	}
	
	public static String getURLEncodedString(String str) {
		try {
			return URLEncoder.encode(str, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return str; //should never get here
	}
	
	public static String getPublicBrowserLink(String id, String prettyName) {
		if (id == null) {
			return PUBLIC_BROWSER_BASE_URL;
		}
		if (prettyName == null) {
			prettyName = id;
		}
		
		return getHyperlink(PUBLIC_BROWSER_BASE_URL + getURLEncodedString(id), prettyName);
	}
	
	public static String getiCatLink(String id, String prettyName) {
		if (id == null) {
			return ICAT_BASE_URL;
		}
		if (prettyName == null) {
			prettyName = id;
		}
		
		return getHyperlink(ICAT_BASE_URL + ICAT_ARGS + getURLEncodedString(id), prettyName);
	}
	
	public static String getHyperlink(String link, String prettyName) {
		return "=HYPERLINK(" + "\"" + link + "\"" + "," + "\"" + prettyName + "\"" + ")";
	}
	
}
