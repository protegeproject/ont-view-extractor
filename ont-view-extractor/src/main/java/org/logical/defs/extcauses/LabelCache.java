package org.logical.defs.extcauses;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.fma.icd.map.OWLAPIUtil;
import org.fma.icd.map.StringMatcher;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import edu.stanford.nlp.simple.Document;
import edu.stanford.nlp.simple.Sentence;

public class LabelCache {
	
	private OWLOntologyManager ontologyManager;
	private OWLDataFactory df;
	
	private OWLOntology sourceOntology;
	private OWLReasoner reasoner;
	
	private XChapterCache xChapterCache;
	
	private Map<OWLClass,String> cls2Label = new HashMap<OWLClass,String>();
	private Map<OWLClass,String> cls2ShortLabel = new HashMap<OWLClass,String>();
	private Map<OWLClass, List<String>> cls2SplitOrPref = new HashMap<OWLClass, List<String>>();
	private Map<OWLClass, List<String>> cls2SplitOrShort = new HashMap<OWLClass, List<String>>();
	
	private Map<String,String> word2lemma = new HashMap<String,String>(10000, 0.6f);
	private Map<String,String> lemma2pos = new HashMap<String,String>(10000, 0.6f); //this is not perfect, but should work
	
	private Set<String> duplicateLabels = new HashSet<String>();
	
	public LabelCache(OWLOntologyManager manager, OWLOntology sourceOntology, OWLReasoner reasoner, 
			XChapterCache xChapterCache) {
		this.ontologyManager = manager;
		this.df = ontologyManager.getOWLDataFactory();
		this.sourceOntology = sourceOntology;
		this.reasoner = reasoner;
		this.xChapterCache = xChapterCache;
	}
	
	public void init() {
		initLemmaCache();
		precacheLabels();
	}
	
	private void precacheLabels() {
		cachePreprocessedLabels(df.getOWLClass(ExtCausesConstants.CHAPTER_EXT_CAUSES_ID), false); 
		
		for (OWLClass xCls : xChapterCache.getXTopClasses()) {
			cachePreprocessedLabels(xCls, true);
		}
	}
	
	private void cachePreprocessedLabels(OWLClass topCls, boolean findDuplicates) {
		OWLAnnotationProperty shortLabelProp = df.getOWLAnnotationProperty(ExtCausesConstants.SHORT_LABEL_PROP);
		
		Set<String> seenLabels = new HashSet<String>();
		
		for (OWLClass subcls : OWLAPIUtil.getNamedSubclasses(topCls, sourceOntology, reasoner, false)) {
			String prefLabel = OWLAPIUtil.getSKOSPrefLabelValue(sourceOntology, subcls);
			prefLabel = ExtCausesStringMatcher.removeIntent(prefLabel);
			
			if (subcls.getIRI().toString().equals("http://id.who.int/icd/entity/273853705")) {
				System.out.println(prefLabel);
			}
			
			String shortLabel = OWLAPIUtil.getStringAnnotationValue(sourceOntology, subcls, shortLabelProp);
			
			cls2SplitOrPref.put(subcls, splitByOrAndComma(prefLabel, true));
			cls2SplitOrShort.put(subcls, splitByOrAndComma(shortLabel, true));
			
			if (subcls.getIRI().toString().equals("http://id.who.int/icd/entity/384793678")) {
				System.out.println(subcls);
			}
			
			prefLabel = preprocess(prefLabel);
			cls2Label.put(subcls, prefLabel);
		
			shortLabel = preprocess(shortLabel);
			cls2ShortLabel.put(subcls, shortLabel);
			
			
			if (findDuplicates == true) {
				if (seenLabels.contains(shortLabel)) {
					duplicateLabels.add(shortLabel);
				} else {
					seenLabels.add(shortLabel);
				}
			}
		}
	}
	
	//this is defunct code because HashMap<String, String> was horribly slow..
	private void initLemmaCache() {
		Collection<String> labels = new ArrayList<String>();
		labels.addAll(getPrefLabels(df.getOWLClass(ExtCausesConstants.CHAPTER_EXT_CAUSES_ID))); 
		
		for (OWLClass xCls : xChapterCache.getXTopClasses()) {
			labels.addAll(getPrefLabels(xCls));
		}
		fillLemmaCache(getAllLabelsString(labels));
	}
	
	private Collection<String> getPrefLabels(OWLClass topXCls) {
		Collection<String> labels = new ArrayList<String>();
		for (OWLClass subcls : OWLAPIUtil.getNamedSubclasses(topXCls, sourceOntology, reasoner, false)) {
			String prefLabel = OWLAPIUtil.getSKOSPrefLabelValue(sourceOntology, subcls);
			labels.add(prefLabel);
		}
		return labels;
	}

	private void fillLemmaCache(String labels) {
		Document doc = new Document(labels);
		for (Sentence sentence : doc.sentences()) {
			for (int i=0; i < sentence.words().size(); i++) {
				String word = sentence.word(i);
			
				if (word != null && word.length() > 0) {
					String lemma = sentence.lemma(i);
					word2lemma.put(word, lemma); 
					lemma2pos.put(lemma, sentence.posTag(i));
				}
			}
		}
	}
	
	private String getAllLabelsString(Collection<String> labels) {
		//make them into a string
		StringBuffer allLabels = new StringBuffer();
		for (String label : labels) {
			allLabels.append(label);
			allLabels.append("\n");
		}
		return allLabels.toString();
	}
	
	public String preprocess(String str) {
		if (str == null) {
			return null;
		}
		
		str = str.toLowerCase();
		str = ExtCausesStringMatcher.replaceWords(str);
		str = StringMatcher.removeExcludedWords(str);
		str = lemmatize(str);
		
		return str;
	}
	
	
	private String lemmatize(String str) {
	StringBuffer buffer = new StringBuffer();
		
		String[] tokens = str.split("\\s+|,\\s*|\\.\\s*");
		
		for (String token : tokens) {
			token = getLemma(token);
			if (token != null && token.length() > 0) {
				buffer.append(token);
				buffer.append(" ");
			}
		}
		
		String ret = buffer.toString();
		ret = ret.trim();
		return ret;
	}

	public String getLemma(String word) {
		String s = word2lemma.get(word);
		
		if (s == null || s.length() == 0) {
			return word; //TODO: maybe use a backup
		}
		
		return s;
		//return ExtCausesStringMatcher.lemmatize(word);
	}
	
	public List<String> splitByOrAndComma(String str, boolean preprocess) {
		if (str == null) {
			return null;
		}
		
		str = str.replaceAll("\\]", "");
		
		String[] split = str.split("\\s*,\\s*|\\s*:\\s*|\\s*\\bor\\b\\s*|\\s*\\[\\s*");
		List<String> list = Arrays.asList(split);
		List<String> filteredList = list.stream().filter(e -> e != null && e.length() > 0).collect(Collectors.toList());
	
		List<String> interimList = preprocess == true? 
				filteredList.stream().map(s -> preprocess(s)).
							 filter(p -> p != null && p.length() > 0).
							 collect(Collectors.toList()) :
				filteredList;
		
		List<String> retList = new ArrayList<>(interimList);
		
		//add the adjectives in front of each of the or operands
		//e.g. ["hot object", "liquid"] -> ["hot object", "hot liquid"]
		
		String adj = null;
		
		for (String sent : interimList) {
			
			String[] splitSent = sent.split(" ");
			String firstWord = splitSent[0];
			String posTagFirstWord = lemma2pos.get(firstWord);
			
			if (posTagFirstWord != null && posTagFirstWord.contains("NN") && adj != null) {
				retList.add(adj + " " + sent);
			}
			
			for (String tok : splitSent) {
				String postTag = lemma2pos.get(tok);
				if (postTag != null && postTag.contains("JJ")) {
					adj = tok;
				}
			}
		}
		
		return retList;
	
	}
	
	
	
	
	public String getLabel(OWLClass cls) {
		return cls2Label.get(cls);
	}
	
	public String getShortLabel(OWLClass cls) {
		return cls2ShortLabel.get(cls);
	}
	
	public boolean isDuplicatedLabel(String label) {
		return duplicateLabels.contains(label);
	}
	
	public boolean isExcludedLabel(String label) {
		if (label == null) {
			return false;
		}
		return ExtCausesStringMatcher.EXCLUDED_SHORT_LABELS.contains(label);
	}
	
	public boolean shouldIgnoreLabel(String label) {
		if (label == null) {
			return true;
		}
		return isDuplicatedLabel(label) || isExcludedLabel(label);
	}

	
	public boolean hasDuplicatedShortLabel(OWLClass cls) {
		String label = getShortLabel(cls);
		return label == null ? false : isDuplicatedLabel(label);
	}
	
	public List<String> getOrSplitPrefLabel(OWLClass cls) {
		List<String> ret = cls2SplitOrPref.get(cls);
		return ret == null ? new ArrayList<String>() : ret;
	}
	
	public List<String> getOrSplitShortLabel(OWLClass cls) {
		List<String> ret = cls2SplitOrShort.get(cls);
		return ret == null ? new ArrayList<String>() : ret;
	}
	
	/******* Specific methods *************/
	
	public String getModeOfTransport(String str) {
		int index = str.indexOf("injuring");
		if (index == -1) {
			return getModeOfTransportOccupantInjuredIn(str);
		}
		str = str.substring(index + 8);
		
		str = ExtCausesStringMatcher.stripWord(str, "occupant");
		str = ExtCausesStringMatcher.stripWord(str, "user");
		str = ExtCausesStringMatcher.stripWord(str, "the");
		str = ExtCausesStringMatcher.stripWord(str, "of");
		str = ExtCausesStringMatcher.stripWord(str, "an");
		str = ExtCausesStringMatcher.stripWord(str, "a");
		
		str = preprocess(str);
		
		return str;
	}
	
	private String getModeOfTransportOccupantInjuredIn(String str) {
		str = str.toLowerCase();
		int indexOccupant = str.indexOf("occupant of");
		if (indexOccupant == -1) {
			return "";
		}
		int indexInjuredIn = str.indexOf("injured", indexOccupant);
		if (indexInjuredIn == -1) {
			return "";
		}
		String ret = str.substring(indexOccupant + 11, indexInjuredIn);
		ret = ret.trim();
		return ret;
	}
	
	public String getInjuryCounterpart(String str) {
		int index = str.indexOf("collision with");
		if (index == -1) {
			return "";
		}
		
		int indexColon = str.indexOf(":", index + 14);
		
		str = indexColon == -1 ? str.substring(index + 14) : str.substring(index + 14, indexColon);
		
		str = ExtCausesStringMatcher.stripWord(str, "occupant");
		str = ExtCausesStringMatcher.stripWord(str, "user");
		str = ExtCausesStringMatcher.stripWord(str, "the");
		str = ExtCausesStringMatcher.stripWord(str, "of");
		str = ExtCausesStringMatcher.stripWord(str, "an");
		str = ExtCausesStringMatcher.stripWord(str, "a");
		
		str = preprocess(str);
		
		return str;
	}

	/**
	 * Just a quick cheat test if this is a transport class. 
	 * We assume it contains the word traffic in the pref label.
	 * @param cls TODO
	 */
	public boolean isTransportCls(OWLClass cls) {
		String prefLabel = getLabel(cls);
		return  prefLabel != null && (prefLabel.contains("transport") ||
				prefLabel.contains("occupant") ||
				prefLabel.contains("railway") ||
				(prefLabel.contains("collision") && prefLabel.contains("attack") == false));
	}
	

}
