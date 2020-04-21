package org.logical.defs.extcauses;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.fma.icd.map.StringMatcher;

import edu.stanford.nlp.process.Stemmer;
import edu.stanford.nlp.simple.Document;
import edu.stanford.nlp.simple.Sentence;

public class ExtCausesStringMatcher {

	public static final String EXPOSURE_TO="Exposure to";
	public static final String EXPOSURE_TO_BEING="Exposure to being";
	public static final String CONTACT_WITH="Contact with";
	
	public static final String ASPECT_OF="Aspect of";
	
	public final static List<String> EXCLUDED_SHORT_LABELS = List.of(
			"unknown", "human", "natural", "other specify", "other specified",
			"collision", "specify", "body", "animal", "drug", "mixed", "substance",
			"assault", "part", "self", "self-harm","compound", "water"
			//"other"
	);
	
	private static HashMap<String, String> str2preproc = new HashMap<String, String>();
	
	
	public static Intent getIntent(String str) {
		if (str == null) {
			return null;
		}
		str = str.toLowerCase();
		for (Intent intent : Intent.values()) {
			if (str.contains(intent.getText())) {
				return intent;
			}
		}
		return null;
	}
	
	public static String preprocess(String str, LabelCache labelCache) {
		if (str == null) {
			return null;
		}
		
		str = str.toLowerCase();
		str = replaceWords(str);
		str = StringMatcher.removeExcludedWords(str);
		
		if (labelCache == null) {
			str = lemmatize(str);
		} else {
			str = lemmatize(str, labelCache);
		}
		
		return str;
	}
	
	
	public static String lemmatize(String str, LabelCache labelCache) {
		StringBuffer buffer = new StringBuffer();
		
		String[] tokens = str.split("\\s+|,\\s*|\\.\\s*");
		
		for (String token : tokens) {
			token = labelCache.getLemma(token);
			buffer.append(token);
			buffer.append(" ");
		}
		
		String ret = buffer.toString();
		ret = ret.trim();
		return ret;
	}
	
	public static String preprocess(String str) {
		if (str == null) {
			return null;
		}
		
		String ret = str2preproc.get(str);
		if (ret != null) {
			return ret;
		}
		
		ret = str.toLowerCase();
		ret = ExtCausesStringMatcher.replaceWords(ret);
		ret = StringMatcher.removeExcludedWords(ret);
		ret = lemmatize(ret);
		
		str2preproc.put(str, ret);
		
		return ret;
	}
	
	public static String lemmatize(String str) {
		if (str == null) {
			return null;
		}
		
		StringBuffer buffer = new StringBuffer();
		
		Document doc = new Document(str);
		for (Sentence sentence : doc.sentences()) {
			for (String lemma : sentence.lemmas()) {
				buffer.append(lemma);
				buffer.append(" ");
			}
		}
		
		String ret = buffer.toString();
		ret = ret.trim();
		return ret;
	}
	
	public static String replaceWords(String str) {
		if (str == null) {
			return null;
		}
		
		//some medicaments use [], will make them an OR instead..
		str = str.replaceAll("\\[", " or ");
		str = str.replaceAll("\\]", "");
		
		str = str.replaceAll("(?i:" + EXPOSURE_TO_BEING + ")", "");
		str = str.replaceAll("(?i:" + CONTACT_WITH + ")", "");
		str = str.replaceAll("(?i:" + "Exposure" + ")", "");
		str = str.replaceAll("(?i:" + ASPECT_OF + ")", "");
		str = str.replaceAll("(?i:" + "being" + ")", "");
		str = str.replaceAll("(?i:" + "accidental" + ")", "");
		
		str = str.replaceAll("(?i:" + "elewhere" + ")", "elsewhere");
		
		str = str.replaceAll("(?i:" + "undeterminedintent" + ")", "undetermined intent");
		str = str.replaceAll("(?i:" + "undetremined" + ")", "undetermined");
		str = str.replaceAll("(?i:" + "underermined" + ")", "undetermined");
		
		str = str.replaceAll("(?i:" + "related" + ")", "");
		str = str.replaceAll("(?i:" + "nonpowered" + ")", "unpowered");
		str = str.replaceAll("(?i:" + "low-powered" + ")", "low powered");
		str = str.replaceAll("(?i:" + "animal-powered" + ")", "animal powered");
		str = str.replaceAll("(?i:" + "vehicle driver" + ")", "driver");
		str = str.replaceAll("(?i:" + "vehicle passenger" + ")", "passenger");
		str = str.replaceAll("(?i:" + "motor cyclist" + ")", "motorcycle");
		str = str.replaceAll("(?i:" + "cyclist" + ")", "cycle");
		str = str.replaceAll("(?i:" + "off-road nontraffic" + ")", "off-road");
		str = str.replaceAll("(?i:" + "transport on-road" + ")", "transport road");
		str = str.replaceAll("(?i:" + "injurious transport event" + ")", "transport injury event");
		str = str.replaceAll("(?i:" + "swimming-pool" + ")", "swimming pool");
		str = str.replaceAll("(?i:" + "hand gun" + ")", "handgun");
		str = str.replaceAll("(?i:" + "air craft" + ")", "aircraft");
		str = str.replaceAll("(?i:" + "anaesthetic" + ")", "anesthetic");
		
		str = str.replaceAll("(?i:" + "fluid" + ")", "liquid");
		str = str.replaceAll("(?i:" + "person, animal or plant" + ")", "animal, plant, or person");
		
		str = str.replaceAll("(?i:" + "analgesics, antipyretics or nonsteroidal anti-inflammatory drugs" + ")",
									  "analgesics, antipyretics and anti-inflammatory drugs");
		
		str = str.replaceAll("(?i:" + "cannabinoids & hallucinogens" + ")", "cannabinoids or hallucinogens");
		str = str.replaceAll("(?i:" + "cannabis (natural; phytocannabinoids)" + ")", "cannabis");
		
		str = str.replaceAll("(?i:" + "controlled fire, flame" + ")", "controlled fire");
		
		return str;
	}
	
	public static String preprocessString(String s, boolean doStemming) {
		StringBuffer buff = new StringBuffer();
		
		s = s.toLowerCase();
		s = replaceWords(s);
		String ret = s;
		
		if (doStemming == true) {
			//this stemmer is not perfect, but at least it is biased the same way
			//e.g. "house" -> "hous"
			Stemmer stemmer = new Stemmer();
			StringTokenizer st = new StringTokenizer(s);
			while (st.hasMoreTokens()) {
				String token = st.nextToken();
				buff.append(stemmer.stem(token));
				buff.append(" ");
			}
			ret = buff.toString();
		}
		
		ret = ret.trim();
		return ret;
	}
	
	public static String removeIntent(String str) {
		return removeIntent(str, getIntent(str));
	}
	
	public static String removeIntent(String str, Intent intent) {
		if (intent == null) {
			return str;
		}
		//str = str.toLowerCase();
		//trying to get the maximum match
		String intentText = intent.getText();
		
		String ret = str.replaceAll("(?i:" + "with " + intentText + ")\\s*", "");
		ret = ret.replaceAll("(?i:" + "of " + intentText + ")\\s*", "");
		ret = ret.replaceAll("(?i:" + intentText + "ly)\\s*", "");
		ret = ret.replaceAll("(?i:" + intentText + ")\\s*", "");
		
		ret = ret.trim();
		
		return ret;
	}
	
	
	public static List<String> splitByOrAndComma(String str) {
		return splitByOrAndComma(str, false);
	}
	
	
	public static List<String> splitByOrAndComma(String str, boolean preprocess) {
		if (str == null) {
			return null;
		}
		
		
		str = str.replaceAll("\\]", "");
		
		String[] split = str.split("\\s*,\\s*|\\s*:\\s*|\\s*\\bor\\b\\s*|\\s*\\[\\s*");
		List<String> list = Arrays.asList(split);
		List<String> filteredList = list.stream().filter(e -> e != null && e.length() > 0).collect(Collectors.toList());
	
		return preprocess == true? 
				filteredList.stream().map(s -> StringMatcher.preprocessString(s)).
					filter(p -> p != null && p.length() > 0).
					collect(Collectors.toList()) :
				filteredList;
	}
	
	
	public static String stripWord(String str, String toBeStripped) {
		return str.replaceAll("(?i:\\b" + toBeStripped + "\\b)\\s*", "");
	}
	
	@Deprecated
	public static String getModeOfTransport(String str) {
		int index = str.indexOf("injuring");
		if (index == -1) {
			return "";
		}
		str = str.substring(index + 8);
		
		str = stripWord(str, "occupant");
		str = stripWord(str, "user");
		str = stripWord(str, "the");
		str = stripWord(str, "of");
		str = stripWord(str, "an");
		str = stripWord(str, "a");
		
		str = preprocess(str);
		
		return str;
	}
	
	@Deprecated 
	public static String getInjuryCounterpart(String str) {
		int index = str.indexOf("collision with");
		if (index == -1) {
			return "";
		}
		
		int indexColon = str.indexOf(":", index + 14);
		
		str = indexColon == -1 ? str.substring(index + 14) : str.substring(index + 14, indexColon);
		
		str = stripWord(str, "occupant");
		str = stripWord(str, "user");
		str = stripWord(str, "the");
		str = stripWord(str, "of");
		str = stripWord(str, "an");
		str = stripWord(str, "a");
		
		str = preprocess(str);
		
		return str;
	}
	
	
	
	public static void main(String[] args) {
		System.out.println(removeIntent("Threat to breathing with undetermined intent"));
		System.out.println(removeIntent("Fall or jump of undetermined intent from pedestrian conveyance"));
		System.out.println(removeIntent("Unintentional cut, puncture, or perforation during surgical and medical care"));
		System.out.println(removeIntent("Struck by projectile from firearm of undetermined intent"));
	
		System.out.println("\n****************\n");
		
		//System.out.println(preprocess("Exposure to explosion or rupture of pressurised materials or object with undetermined intent"));
		
		//System.out.println(preprocess("Exposure to contact with hot object or liquid"));
		//System.out.println(preprocess("Contact with hot object or liquid with undetermined intent"));
	
		
		System.out.println(splitByOrAndComma("Unintentionally struck, kicked, or bumped by insect or bird"));
		System.out.println(splitByOrAndComma("Drugs medicaments or biological substances associated with injury or harm in therapeutic use, Agents primarily acting on smooth or skeletal muscles or the respiratory system, Antiasthmatics, not elsewhere classified"));
		System.out.println(splitByOrAndComma(", synthetic membranes"));
	
		System.out.println(splitByOrAndComma("Unintentionally struck, kicked, or bumped by insect or bird", true));
		System.out.println(splitByOrAndComma("Drugs medicaments or biological substances associated with injury or harm in therapeutic use, Agents primarily acting on smooth or skeletal muscles or the respiratory system, Antiasthmatics, not elsewhere classified", true));
		System.out.println(splitByOrAndComma(", synthetic membranes", true));

		
		System.out.println(removeIntent("Unintentionally bitten by rat"));
		
		System.out.println("\n****************\n");
		
		System.out.println(getModeOfTransport("Land transport injury event of undetermined intent, unknown whether road traffic or off-road nontraffic injuring a car occupant"));
		System.out.println(getModeOfTransport("Unintentional land transport event unknown whether traffic or nontraffic injuring the user of a pedestrian conveyance"));
		System.out.println(getModeOfTransport("Land transport nontraffic injury event of undetermined intent injuring a pedestrian"));

		System.out.println("\n****************\n");
	
		System.out.println(getInjuryCounterpart("Pedal cyclist injured in collision with other nonmotor vehicle : passenger injured in traffic accident"));
		System.out.println(getInjuryCounterpart("Pedestrian injured in collision with railway train or railway vehicle, traffic accident"));
		System.out.println(getInjuryCounterpart("Occupant of pick-up truck or van injured in collision with car, pick-up truck or van : unspecified occupant of pick-up truck or van injured in nontraffic accident"));
	
		System.out.println(getInjuryCounterpart("Car occupant injured in collision with fixed or stationary object"));
		System.out.println(getInjuryCounterpart("fixed stationary object as counterpart land transport crash"));
	
		System.out.println(lemmatize("fixed"));
		System.out.println(lemmatize("Large or heavy fixed object as counterpart in land transport crash"));
	
	
		System.out.println(splitByOrAndComma("Antipsychotics [neuroleptics]", true));
		
		System.out.println("abc".split(" ").length);
	}



}
