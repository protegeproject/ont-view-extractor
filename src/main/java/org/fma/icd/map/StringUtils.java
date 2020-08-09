package org.fma.icd.map;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;

import edu.stanford.smi.protegex.owl.model.RDFResource;

public class StringUtils {
	
	public static final String PUBLIC_BROWSER_BASE_URL = "https://icd.who.int/dev11/f/en#/";
	public static final String ICAT_BASE_URL = "https://icat.stanford.edu/";
	public static final String ICAT_ARGS = "?ontology=ICD&tab=ClassesTab&id=";

	public static final String WP_BASE_URL = "https://webprotege.stanford.edu/#projects/01658fd4-4ed1-469d-a4b9-176d110df9fc/";
	public static final String WP_ARGS = "edit/Classes?selection=Class(%3C";
	public static final String WP_POSTFIX = "%3E)";

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
	
	public static String getCollectionStringPublicIdForClses(Collection<OWLClass> clses, OWLOntology ont) {
		StringBuffer s = new StringBuffer();
		for (OWLClass cls : clses) {
			s.append(getPublicBrowserLink(cls.getIRI().toString(), OWLAPIUtil.getSKOSPrefLabelValue(ont, cls) ));
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
	
	public static String getWPLink(String id, String prettyName) {
		if (id == null) {
			return WP_BASE_URL;
		}
		if (prettyName == null) {
			prettyName = id;
		}
		
		return getHyperlink(WP_BASE_URL + WP_ARGS + id + WP_POSTFIX, prettyName);
	}
	
	public static String getHyperlink(String link, String prettyName) {
		return "=HYPERLINK(" + "\"" + link + "\"" + "," + "\"" + prettyName + "\"" + ")";
	}

	/*********************** Common substrings methods ************************/
	
	public static Collection<String> getCommonSubstrings(OWLOntology ont, Set<OWLClass> subclses) {
		//using just the skos:prefLabel for now, not the syns
		List<String> res = new ArrayList<String>();
		
		for (OWLClass cls : subclses) {
			String prefLabel = OWLAPIUtil.getSKOSPrefLabelValue(ont, cls);
			res.add(prefLabel);
		}
		
		return getCommonWords(res);
	}
	
	public static Collection<String> getCommonWords(List<String> labels) {
		if (labels.size() < 2) {
			return new HashSet<String>();
		}
		
		Set<String> commonTokens = new HashSet<String>();
		commonTokens.addAll(longestCommonTokens(labels.get(0), labels.get(1)));
		
		for (int i = 0; i < labels.size(); i++) {
			for (int j = i+1; j < labels.size(); j++) {
				Set<String> comTok = longestCommonTokens(labels.get(i), labels.get(j));
				if (comTok.size() == 0) {
					return new HashSet<String>();
				} 
				Set<String> intersection = intersectionApprox(commonTokens, comTok);
				if (intersection.size() == 0) {
					return new HashSet<String>();
				}
				
				commonTokens = intersection;
			}
		}
		
		return commonTokens;
	}
	
	
	/**
	 * This intersection will also point a match, if only a substring matches, and keeps the substring.
	 * For example: the approx intersection of ["a", "abc def", "efg"] and ["a", "abc d"] is ["a", "abc d"].
	 * 
	 * @param list1
	 * @param list2
	 * @return
	 */
	private static Set<String> intersectionApprox(Collection<String> list1, Collection<String> list2) {
		Set<String> intersect = new HashSet<String>();
		for (String s1 : list1) {
			String sl1 = s1.toLowerCase();
			for (String s2 : list2) {
				String sl2 = s2.toLowerCase();
				if (sl1.contains(sl2)) { 
					intersect.add(s2);
				} 
				if (sl2.contains(sl1)) {
					intersect.add(s1);
				}
			}
		}
		return intersect;
	}
	
	
	public static Set<String> intersection(Collection<String> list1, Collection<String> list2) {
		return list1.stream()
				  .distinct()
				  .filter(list2::contains)
				  .collect(Collectors.toSet());
	}
	
	
	// Copied form: https://www.techiedelight.com/longest-common-substring-problem/
	public static String longestCommonSubstring(String str1, String str2) {
		int m = str1.length();
		int n = str2.length();
		
		int maxlen = 0; 		// stores the max length of LCS
		int endingIndex = m;	// stores the ending index of LCS in X

		// lookup[i][j] stores the length of LCS of substring
		// X[0..i-1], Y[0..j-1]
		int[][] lookup = new int[m + 1][n + 1];

		// fill the lookup table in bottom-up manner
		for (int i = 1; i <= m; i++)
		{
			for (int j = 1; j <= n; j++)
			{
				// if current character of X and Y matches
				if (str1.charAt(i - 1) == str2.charAt(j - 1))
				{
					lookup[i][j] = lookup[i - 1][j - 1] + 1;

					// update the maximum length and ending index
					if (lookup[i][j] > maxlen)
					{
						maxlen = lookup[i][j];
						endingIndex = i;
					}
				}
			}
		}

		// return Longest common substring having length maxlen
		return str1.substring(endingIndex - maxlen, endingIndex);
	}
	
	
	//Adapted from: https://stackoverflow.com/questions/34805488/finding-all-the-common-substrings-of-given-two-strings
	public static Set<String> longestCommonSubstrings(String str1, String str2) {
		Set<String> result = new HashSet<String>();
		
		if (str1 == null || str2 == null) {
			return result;
		}
		
		str1 = str1.toLowerCase();
		str2 = str2.toLowerCase();
		
	    int[][] table = new int[str1.length()][str2.length()];

	    // first pass, fill in the matrix
	    for (int i = 0; i < str1.length(); i++) {
	        for (int j = 0; j < str2.length(); j++) {
	            if (str1.charAt(i) != str2.charAt(j)) {
	                continue;
	            }
	            table[i][j] = (i == 0 || j == 0) ? 
	            		1 :
	                    1 + table[i - 1][j - 1];
	        }
	    }
	    
	    //second pass, find the longest filled diagonals
	    for (int i = str1.length()-1; i >=0 ; i--) {
	        for (int j = str2.length()-1; j >= 0; j--) {
	        	int val = table[i][j];
	        	if (val > 2) {
	        		String s = str1.substring(i-val+1, i+1);
	        		result.add(s);
	        		i = i - val - 1;
	        		if (i < 0) {
	        			return result;
	        		}
	        		j = str2.length()-1;
	        	}
	        }
	    }
	    
	    return result;
	}
	
	private static String[] tokenize(String str, String delimiters, boolean includeDelims) {
		String WITH_DELIMITER = "((?<=%1$s)|(?=%1$s))";
		String[] aEach = str.split(String.format(WITH_DELIMITER, delimiters));
		return aEach;
	}
	
	public static Set<String> longestCommonTokens(String str1, String str2) {
		Set<String> result = new HashSet<String>();
		
		if (str1 == null || str2 == null) {
			return result;
		}
		
		//str1 = str1.toLowerCase();
		// str2 = str2.toLowerCase();
		
		String delimiters = "\\s+|,\\s*|\\.\\s*";
		
		//String[] tokens1 = str1.split(delimiters);
		//String[] tokens2 = str2.split(delimiters);
		
		String[] tokens1 = tokenize(str1, delimiters, true);
		String[] tokens2 = tokenize(str2, delimiters, true);

	    int[][] table = new int[tokens1.length][tokens2.length];

	    // first pass, fill in the matrix
	    for (int i = 0; i < tokens1.length; i++) {
	        for (int j = 0; j < tokens2.length; j++) {
	            if (tokens1[i].equalsIgnoreCase(tokens2[j]) == false) {
	                continue;
	            }
	            
	          //ignore the space at the beginning of the match
	            if (tokens1[i].equals(" ")) { 
	            	if (i == 0 || j == 0) {
	            		continue;
	            	} else {
	            		if (table[i-1][j-1] == 0) {
	            			continue;
	            		}
	            	}
	            }
	            
	            table[i][j] = (i == 0 || j == 0) ? 
	            		1 :
	                    1 + table[i - 1][j - 1];
	        }
	    }
	    
	    //second pass, find the longest filled diagonals
	    for (int i = tokens1.length-1; i >=0 ; i--) {
	        for (int j = tokens2.length-1; j >= 0; j--) {
	        	int val = table[i][j];
	        	if (val > 0) {
	        		if (i < tokens1.length-1 && j < tokens2.length -1 &&
	        				table[i+1][j+1] == val + 1) { //it was already included, take only longest string
	        			continue;
	        		}
	        		
	        		String s = buildString(tokens1, i-val+1, i+1);
	        		if (s.trim().length() > 2) { //arbitrary, can get us in trouble
	        			result.add(s);
	        		}
	        	}
	        }
	    }
		
		return result;
	}
	
	//this could be implented nicer, but it works this way, too..
	public static String longestCommonToken(String str1, String str2) {	
		Set<String> commonSubstrs = longestCommonTokens(str1, str2);
		
		if (commonSubstrs.size() == 0) {
			return new String();
		}
		
		List<String> commonStrList = new ArrayList<String>();
		commonStrList.addAll(commonSubstrs);
		
		Collections.sort(commonStrList, new Comparator<String>() {
	
			@Override
			public int compare(String o1, String o2) {
				return o2.length() - o1.length();
			}
		});
		
		return commonStrList.get(0);
	
	}
	
	
	public static String buildString(String[] tokens, int startIndex, int endIndex) {
		StringBuffer buffer = new StringBuffer();
		for (int i = startIndex; i < endIndex; i++) {
			buffer.append(tokens[i]);
			//buffer.append(" "); //tricky, might not be right
		}
	//	if (buffer.length() > 0) {
	//		buffer.delete(buffer.length()-1,buffer.length());
	//	}
		return buffer.toString();
	}
	
	public static String buildString(String[] tokens) {
		return buildString(tokens, 0, tokens.length);
	}
	
	public static String pruneString(String label, Collection<String> strsToBePruned) {
		if (label == null) {
			return null;
		}
		//label = label.toLowerCase();
		
		//the string to be pruned need to be sorted by their length
		List<String> strsToBePrunedSorted = new ArrayList<String>();
		strsToBePrunedSorted.addAll(strsToBePruned);
		
		Collections.sort(strsToBePrunedSorted, new Comparator<String>() {

			@Override
			public int compare(String o1, String o2) {
				return o2.length() - o1.length();
			}
		});
		
		
		for (String str : strsToBePrunedSorted) {
			String regEx = "(?i:\\b" + str + "\\b)";
			label = label.replaceAll(regEx, "");
		}
		
		label = label.replaceAll("\\s+", " ");
		label = label.trim();
		
		return label;
	}
	
	public static String removeCommonLongestWord(String bigStr, String smallStr) {
		if (bigStr == null || smallStr == null) {
			return bigStr;
		}
		
		String longestCommon = longestCommonToken(bigStr, smallStr);
		
		String ret = bigStr.replaceAll(longestCommon, " ");
		
		ret = ret.replaceAll("\\s+", " ");
		ret = ret.trim();
		
		return ret;
	}
	
	/**************************/
	

	
	
	
	/**************************/
	
	
	public static void main(String[] args) {
		System.out.println(tokenize("abc,def ghi.ikj", "\\s+|,\\s*|\\.\\s*", true));
		
		
		System.out.println("String compare: " + longestCommonSubstrings(
				"Threat to breathing by external compression of airways or chest with undetermined intent", 
				"Threat to breathing by inhalation or ingestion of liquids with undetermined intent"));
	
		System.out.println("Token compare: " + longestCommonTokens(
				"Threat to breathing by external compression of airways or chest with undetermined intent", 
				"Threat to breathing by inhalation or ingestion of liquids with undetermined intent"));
	
		System.out.println("Token compare: " + longestCommonTokens(
				"Injury other than drowning while in body of water with undetermined intent", 
				"Injury other than drowning following fall into body of water with undetermined intent"));
	
		
		List<String> list = new ArrayList<String>();
		list.add("Threat to breathing by external compression of airways or chest with undetermined intent");
		list.add("Threat to breathing by inhalation or ingestion of liquids with undetermined intent");
		list.add("Threat to breathing by suffocation from object covering mouth or nose with undetermined intent");
		String title = "Threat to breathing by inhalation or ingestion of food with undetermined intent";
		list.add(title);
		Collection<String> commonSubstr = getCommonWords(list);
		
		System.out.println("Common substring of list: " + commonSubstr);
		
		System.out.println("Prunned title: " + pruneString(title, commonSubstr));
		
		System.out.println("\n************\n");
		
		list = new ArrayList<String>();
		list.add("Intentional self-harm by being cut or pierced by knife, sword or dagger");
		list.add("Intentional self-harm by being cut or pierced by  sharp glass");
		title = "Intentional self-harm by body piercing byproduct";
		list.add(title);
		commonSubstr = getCommonWords(list);
		
		System.out.println("Common substring of list: " + commonSubstr);
		
		System.out.println("Prunned title: " + pruneString(title, commonSubstr));
		
		System.out.println("\n************\n");
		
		System.out.println("Token compare: " + longestCommonTokens(
				"Intentional self-harm by body piercing", 
				"Intentional self-harm by being cut or pierced by  sharp glass"));
		
		System.out.println("Token compare: " + longestCommonTokens(
				"Intentional self-harm by being cut or pierced by  sharp glass", 
				"Intentional self-harm by body piercing"));
	
		System.out.println("\n************\n");
		
		System.out.println("Remove common longest substring: " + removeCommonLongestWord(
				"Intentional self-harm by being cut or pierced by  sharp glass", 
				"Intentional self-harm by body piercing"));
		
		System.out.println("\n************\n");
		
		list = new ArrayList<String>();
		list.add("Part of building or grounds, bathroom, toilet");
		list.add("Part of building or grounds, elevator");
		title = "part of building or grounds, playroom or family room";
		list.add(title);
		commonSubstr = getCommonWords(list);
		
		System.out.println("Common substring of list: " + commonSubstr);
		System.out.println("Prunned title: " + pruneString(title, commonSubstr));
		
		System.out.println("\n************\n");
		
		list = new ArrayList<String>();
		list.add("Type of legal intervention, potential arrest related investigation of a suspicious person or incident");
		list.add("Type of legal intervention, potential arrest related execution of an arrest");
		title = "Type of legal intervention, potential arrest related traffic pursuit";
		list.add(title);
		commonSubstr = getCommonWords(list);
		
		System.out.println("Common substring of list: " + commonSubstr);
		System.out.println("Prunned title: " + pruneString(title, commonSubstr));
		
		System.out.println("\n************\n");
		
		System.out.println("\n************\n");
		
		list = new ArrayList<String>();
		list.add("Low-powered passenger vehicle as mode of transport of person injured in transport event");
		//list.add("Type of legal intervention, potential arrest related execution of an arrest");
		title = "Railway vehicle as mode of transport of person injured in transport related event";
		list.add(title);
		commonSubstr = getCommonWords(list);
		
		System.out.println("Common substring of list: " + commonSubstr);
		System.out.println("Prunned title: " + pruneString(title, commonSubstr));
		
		System.out.println("\n************\n");

		System.out.println(longestCommonToken("Biopsy of skin and subcutaneous cell tissue of head or neck", "needle biopsy"));
	}

	

	
}
