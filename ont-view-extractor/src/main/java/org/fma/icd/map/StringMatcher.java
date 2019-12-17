package org.fma.icd.map;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import edu.stanford.nlp.process.Stemmer;
import edu.stanford.nlp.simple.Document;
import edu.stanford.nlp.simple.Sentence;
import me.xdrop.fuzzywuzzy.FuzzySearch;

public class StringMatcher {
	
	public final static List<String> excludedAnnotations = Arrays.asList("DT","IN","TO","CC");
	public final static List<String> excludedWords = Arrays.asList
			("not elsewhere classified", " due to ", " to "," the ", "The ", " a "," an "," of ", "(", ")", "NOS", ", nos", "'s"," in ", ",",
					 " other ", " and ", "or ", " unspecified ", " with ", " by ", " on ", " from ", ":",
					 "complicating", " cause");
	public final static String LEFT = "left";
	public final static String RIGHT = "right";
	public final static int LEFT_RIGHT_NO_MATCH_SCORE = 0;
	
	//this is a hack for performance reasons
	private static Map<String,String> str2stemCache = new HashMap<String,String>();
		
	
	
	public static int simpleFuzzyMatch(String s1, String s2) {
		return FuzzySearch.tokenSortRatio(s1, s2);
	}
	
	
	public static int lemmaFuzzyMatch(String s1, String s2) {
		return simpleFuzzyMatch(lemmatize(s1), lemmatize(s2));
	}
	
	public static int stemFuzzyMatch(String s1, String s2) {
		s1 = preprocessString(s1);
		s2 = preprocessString(s2);
		
//		if (leftRightNoMatch(s1,s2) == true) {
//			return LEFT_RIGHT_NO_MATCH_SCORE;
//		}
		return simpleFuzzyMatch(s1, s2);
	}
	
	
	private static boolean leftRightNoMatch(String s1, String s2) {
		if (s1.contains(LEFT) && s2.contains(LEFT) == false) {
			return true;
		}
		if (s1.contains(RIGHT) && s2.contains(RIGHT) == false) {
			return true;
		}
		if (s2.contains(LEFT) && s1.contains(LEFT) == false) {
			return true;
		}
		if (s2.contains(RIGHT) && s1.contains(RIGHT) == false) {
			return true;
		}
		return false;
	}


	private static String preprocessString(String s) {
		//first try to get it from the cache
		String cached = str2stemCache.get(s);
		if (cached != null) {
			return cached;
		}
		
		StringBuffer buff = new StringBuffer();
		
		//this stemmer is not perfect, but at least it is biased the same way
		//e.g. "house" -> "hous"
		Stemmer stemmer = new Stemmer();
		
		s = removeExcludedWords(s);
		s = s.toLowerCase();
		s = replaceWords(s);
		
		StringTokenizer st = new StringTokenizer(s);
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			buff.append(stemmer.stem(token));
			buff.append(" ");
		}
		
		String ret = buff.toString().trim();
		str2stemCache.put(s, ret);
		return ret;
	}
	
	private static String replaceWords(String s) {
		s = s.replace("neural", "nerve");
		s = s.replace("nervous", "nerve");
		s = s.replace("teeth", "tooth");
		s = s.replace("feet", "foot");
		s = s.replace("coeliac", "celiac");
		//s = s.replace("renal", "kidney");
		
		return s;
	}


	public static String removeExcludedWords(String s) {
		for (String ex : excludedWords) {
			s = s.replace(ex, " ");
		}
		return s;
	}
	
	public static String stem(String s) {
		Stemmer stemmer = new Stemmer();
		return stemmer.stem(s);
	}
	
	//very slow
	public static String lemmatize(String s) {
		StringBuffer buff = new StringBuffer();
		Document doc = new Document(s);
		for (Sentence sent : doc.sentences()) {
			for (int i = 0; i < sent.words().size(); i++) {
				String posTag = sent.posTag(i);
				if (posTag != null && excludedAnnotations.contains(posTag) == false) {
					buff.append(sent.lemma(i));
					buff.append(" ");
				}
			}
		}
		return buff.toString().trim();
	}
	
	public static boolean contains(String str, String subStr) {
		if (str == null || subStr == null) {
			return false;
		}
		str = str.toLowerCase();
		subStr = subStr.toLowerCase();
		subStr = Pattern.quote(subStr);
		return str.matches("^" + subStr + "\\W.*|.*\\W" + subStr + "\\W.*|.*\\W" + subStr + "$");
	}
	
	private static void compareStringMatchMethods(String s1, String s2) {
		System.out.println("--- Comparing: " + s1 + " *** " + s2 + " ---");
		System.out.println("Ratio: " + FuzzySearch.ratio(s1,s2));
		System.out.println("Partial ratio: " + FuzzySearch.partialRatio(s1,s2));
		
		System.out.println("Token set ratio: " + FuzzySearch.tokenSetRatio(s1,s2));
		System.out.println("Token set partial ratio: " + FuzzySearch.tokenSetPartialRatio(s1,s2));
		
		System.out.println("Token sort ratio: " + FuzzySearch.tokenSortRatio(s1,s2));
		System.out.println("Token sort partial ratio: " + FuzzySearch.tokenSortPartialRatio(s1,s2));
		System.out.println("---");

	}
	
	public static void main(String[] args) {
		System.out.println(preprocessString("The old foxes jumped over the a house of cards in Hensons's diseases"));
		System.out.println(preprocessString("chest wall, nos"));
		System.out.println(preprocessString("anterior part of right ankle"));
		System.out.println(preprocessString("extremities limbs")); //extrem limb
		System.out.println(leftRightNoMatch("ankle", "right ankle"));
		System.out.println(preprocessString("seminiferous tubules"));
		System.out.println(preprocessString("seminiferous tubule"));
		System.out.println(preprocessString("shaft of the fifth metacarpal bone"));
		System.out.println(preprocessString("shaft of fifth metacarpal bone"));
		System.out.println(preprocessString("short ciliary nerves"));
		System.out.println(preprocessString("short ciliary nerve"));
		System.out.println(preprocessString("palatal mucosa"));
		System.out.println(preprocessString("mucosa of the palate"));
		System.out.println(stemFuzzyMatch("palatal mucosa", "mucosa of palate"));
		System.out.println(stemFuzzyMatch("right ankle", "ankle"));
		System.out.println(stemFuzzyMatch("nail bed of big toe", "nail bed of great toe"));
		System.out.println(stemFuzzyMatch("nail bed of great toe", "nail of big toe"));
		System.out.println(preprocessString("nail bed of great toe"));
		System.out.println(preprocessString("nail bed of big toe"));
		System.out.println(stemFuzzyMatch("permanent teeth", "permanent dentition"));
		System.out.println(preprocessString("permanent teeth"));
		System.out.println(preprocessString("permanent dentition"));
		System.out.println(preprocessString("Chlamydial colitis"));
		System.out.println(preprocessString("Infections due to Chlamydia"));
		System.out.println(lemmatize("Infections due to Chlamydia"));
	
		compareStringMatchMethods("Chlamydial colitis", "Chlamydia");
		compareStringMatchMethods(preprocessString("Chlamydial colitis"), preprocessString("Chlamydia"));
		
		System.out.println(preprocessString("46,XX androgen-induced disorder of sex development due to maternal Krukenberg tumour"));
		
		
		System.out.println(preprocessString("Abscess of tendon sheath : lower leg"));
		
		System.out.println(contains("Tibial muscular (dystrophy), \\[Udd-type]", "\\"));
		System.out.println(contains("Cervicitis", "Acute cervicitis"));
		System.out.println(contains("Acute cervicitis", "Cervicitis"));
		System.out.println(stemFuzzyMatch("Acute kidney failure", "Acute kidney failure, stage 1"));
	}
	
	
	
}
