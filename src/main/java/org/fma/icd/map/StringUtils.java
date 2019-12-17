package org.fma.icd.map;

import java.util.Collection;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;

public class StringUtils {
	
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
	
}
