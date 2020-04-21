package org.logical.defs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.fma.icd.map.FMAExtractedResult;
import org.fma.icd.map.ICDFMAMatchRecord;
import org.fma.icd.map.OWLAPIUtil;
import org.fma.icd.map.StringMatcher;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

public class ICDXChapterMatcher {
	
	private static transient Logger log = Logger.getLogger(ICDXChapterMatcher.class);
	
	private static final String COL_SEPARATOR = "\t";
	private static final String VALUE_SEPARATOR = "*";
	private static final String QUOTE = "\"";
	
	public static final String PREF_NAME_ABREV = "P";
	public static final String SYN_ABREV = "S";
	
	private static int FUZZY_MATCH_CUT_OFF = 80;
			
	//public final static String ICD_CATEGORIES = "http://who.int/icd#ICDCategory";
	//public final static String X_CHAPTER = "http://who.int/icd#ChapterX";
	//public static final String ICD_SYN_PROP = "http://who.int/icd_flattened/synonym";
	
	//this is actually the top class; using now External Causes; can be anything
	public final static String ICD_CATEGORIES = "http://id.who.int/icd/entity/850137482";
	public final static String X_CHAPTER = "http://id.who.int/icd/entity/979408586";
	
	
	public static final String ICD_SYN_PROP = "http://www.w3.org/2004/02/skos/core#altLabel";
	
	private OWLOntologyManager ontManager;
	private OWLReasoner reasoner;
	private OWLOntology icdOnt;
	private OWLAnnotationProperty synProp;
	
	private BufferedWriter resultCSVWriter;
	
	private Map<OWLClass,OWLClsWithSyns> chapterXClses = new HashMap<OWLClass,OWLClsWithSyns>();
	
	
	public ICDXChapterMatcher(OWLOntologyManager manager, OWLOntology sourceOnt, BufferedWriter bufferedWriter) {
		this.ontManager = manager;
		this.icdOnt = sourceOnt;
		this.resultCSVWriter = bufferedWriter;
		this.reasoner = initReasoner(icdOnt);
		this.synProp = manager.getOWLDataFactory().getOWLAnnotationProperty(ICD_SYN_PROP);
	}

	private void match() throws IOException {
		cacheChapterX();
		matchICDClasses();
		closeResultWriter();
	}

	
	private void matchICDClasses() {
		Collection<OWLClass> icdClses = getICDClasses();
		
		int counter = 0;
		
		for (OWLClass icdCls : icdClses) {
			
			try {
				matchICDClass(icdCls);
				
				counter ++;
				if (counter % 100 == 0) {
					log.info("Processed " + counter + " ICD classes.");
				}
				
			} catch (Exception e) {
				log.warn("Error at processing class " + icdCls, e);
			}
		}
	}

	
	private void matchICDClass(OWLClass icdCls) {
		List<ICDFMAMatchRecord> matchRecs = new ArrayList<ICDFMAMatchRecord>();
		
		String icdId = icdCls.getIRI().toString();
		String icdTitle = getTitle(icdCls);
		Collection<String> syns = OWLAPIUtil.getStringAnnotationValues(icdOnt, icdCls, synProp);
		
		
		matchRecs.addAll(processTitle(icdId, icdTitle, true)); //ICD title -> FMA pref name
		//matchRecs.addAll(processTitle(icdId, icdTitle, false)); //ICD title -> FMA syns
		//matchRecs.addAll(processSyns(icdId, icdTitle, syns, true)); //ICD syn -> FMA pref name
		//matchRecs.addAll(processSyns(icdId, icdTitle, syns, false)); //ICD syn -> FMA syn
		
		writeMatchRecords(matchRecs, resultCSVWriter);
	}
	
	private  List<ICDFMAMatchRecord> processTitle(String icdId, String icdTitle, boolean processWithFMAPrefName) {
		List<FMAExtractedResult> results = fuzzyQuery(icdTitle, processWithFMAPrefName); //not ideal, but easier
		List<ICDFMAMatchRecord> recs = new ArrayList<ICDFMAMatchRecord>();
		
		for (FMAExtractedResult result : results) {
			recs.add(new ICDFMAMatchRecord(icdId, result.getFmaId(), icdTitle, result.getFmaPrefLabel(),
					icdTitle, PREF_NAME_ABREV,
					result.getString(), processWithFMAPrefName ? PREF_NAME_ABREV : SYN_ABREV,
					result.getScore()));
			
		}
		
		return recs;
	}
	
	private  List<ICDFMAMatchRecord> processSyns(String icdId, String icdTitle, Collection<String> syns, boolean processWithFMAPrefName) {
		List<ICDFMAMatchRecord> recs = new ArrayList<ICDFMAMatchRecord>();
		
		if (syns == null || syns.size() == 0) {
			return  recs;
		}
		
		for (String syn : syns) {
			List<FMAExtractedResult> results = fuzzyQuery(syn, processWithFMAPrefName);
			for (FMAExtractedResult result : results) {
				recs.add(new ICDFMAMatchRecord(icdId, result.getFmaId(), icdTitle, result.getFmaPrefLabel(),
						syn, SYN_ABREV,
						result.getString(), processWithFMAPrefName ? PREF_NAME_ABREV : SYN_ABREV,
						result.getScore()));
				
			}
		}
		
		return recs;
	}
	
	private  List<FMAExtractedResult> fuzzyQuery(String query, boolean isPrefName) {
		List<FMAExtractedResult> results = new ArrayList<FMAExtractedResult>();
				
		for (OWLClass fmaid : chapterXClses.keySet()) {
			results.addAll(fuzzyQuery(fmaid, query, isPrefName));
		}
		return results;
	}
	
	
	public  List<FMAExtractedResult> fuzzyQuery(OWLClass fmaid, String query, boolean isPrefName) {
		List<FMAExtractedResult> results = new ArrayList<FMAExtractedResult>();
		
		String prefName = chapterXClses.get(fmaid).getLabel();
		if (isPrefName == true) { //prefName
			int score = fuzzyQuery(query,prefName);
			if (score > FUZZY_MATCH_CUT_OFF) {
				results.add(new FMAExtractedResult(prefName, score, 0, fmaid.getIRI().toString(), prefName));
			}
		} else { //syns
			Collection<String> syns = chapterXClses.get(fmaid).getSyns();
			if (syns != null) {
				for (String syn : syns) {
					int score = fuzzyQuery(query,syn);
					if (score > FUZZY_MATCH_CUT_OFF) {
						results.add(new FMAExtractedResult(syn, score, 0, fmaid.getIRI().toString(), prefName));
					}
				}
			}
		}
		return results;
	}
	
	private int fuzzyQuery(String icdClsString, String xChapterString) {
		//return FuzzySearch.tokenSetPartialRatio(s1, s2);
		//return FuzzySearch.ratio(s1, s2);
		//return FuzzySearch.tokenSortRatio(s1, s2);
		//return StringMatcher.stemFuzzyMatch(s1, s2);
		//return FuzzySearch.tokenSortPartialRatio(s1,s2);
		boolean isMatch = StringMatcher.contains(icdClsString, xChapterString);
		return (isMatch == true) ? 100 : 0;
		//return FuzzySearch.tokenSetPartialRatio(icdClsString, xChapterString);
		//return FuzzySearch.tokenSetRatio(StringMatcher.preprocessString(icdClsString), StringMatcher.preprocessString(xChapterString));
	}
	
	
	private void writeMatchRecords(List<ICDFMAMatchRecord> matchRecs, BufferedWriter writer) {
		for (ICDFMAMatchRecord rec : matchRecs) {
			OWLClass icdCls = ontManager.getOWLDataFactory().getOWLClass(rec.getIcdId());
			String superclses = getCollectionString(OWLAPIUtil.getNamedSuperclasses(icdCls, icdOnt, reasoner, true));
			writeLine(rec.getIcdId(), rec.getFmaId(), rec.getIcdTitle(), rec.getFmaPrefLabel(),
					rec.getMatchedICDString(), rec.getIcdMatchType(), rec.getMatchedFMAString(),
					rec.getFmaMatchType(), Integer.toString(rec.getScore()), superclses, writer);
		}
		
	}
	
	private void writeLine(String icdId, String xId, String icdTitle, String xPrefName,
			String matchedICDString, String icdMatchType,
			String matchedXString, String xMatchType, 
			String score, String superclses, BufferedWriter writer) {
		try {
			writer.write(icdId + COL_SEPARATOR + xId + COL_SEPARATOR +
        			toCsvField(icdTitle) + COL_SEPARATOR + xPrefName + COL_SEPARATOR +
        			toCsvField(matchedICDString) + COL_SEPARATOR + icdMatchType + COL_SEPARATOR + 
        			toCsvField(matchedXString) + COL_SEPARATOR + xMatchType + COL_SEPARATOR + 
        			score + COL_SEPARATOR + superclses);
        			
			writer.newLine();
    	} 
    	catch (IOException ioe) {
			log.error("Could not export line for: " + icdId);
		}
		
	}

	private Collection<OWLClass> getICDClasses() {
		OWLClass icdCatCls = ontManager.getOWLDataFactory().getOWLClass(ICD_CATEGORIES);
		Set<OWLClass> allClses = OWLAPIUtil.getNamedSubclasses(icdCatCls, icdOnt, reasoner, false);
		allClses.removeAll(chapterXClses.keySet());
		return allClses;
	}
	
	private void cacheChapterX() {
		log.info("Started caching X Chapter.."); 
		
		OWLClass chapterXTopClass = ontManager.getOWLDataFactory().getOWLClass(X_CHAPTER);
		Set<OWLClass> allSubclses = OWLAPIUtil.getNamedSubclasses(chapterXTopClass, icdOnt, reasoner, false);
		for (OWLClass subcls : allSubclses) {
			//String label = OWLAPIUtil.getRDFSLabelValue(icdOnt, subcls);
			String label = getTitle(subcls);
			
			Set<OWLClass> superClses = OWLAPIUtil.getNamedSuperclasses(subcls, icdOnt, reasoner, true);
			List<String> syns = OWLAPIUtil.getStringAnnotationValues(icdOnt, subcls, synProp);
			chapterXClses.put(subcls,new OWLClsWithSyns(subcls, label, syns, superClses));
		}
		
		log.info("Ended caching X Chapter");
	}
	
	private String getTitle(OWLClass cls) {
		return OWLAPIUtil.getSKOSPrefLabelValue(icdOnt, cls);
		//return OWLAPIUtil.getRDFSLabelValue(icdOnt, cls);
	}
	
	
	private static OWLReasoner initReasoner(OWLOntology ontology) {
		OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
		OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
		return reasoner;
	}
	
	private void closeResultWriter() throws IOException {
		resultCSVWriter.flush();
		resultCSVWriter.close();
	}
	
	private String toCsvField(Object o) {
		String res = (o == null ? "" : o.toString());
		if (res.contains("\n") || res.contains(COL_SEPARATOR) || res.contains(VALUE_SEPARATOR) || res.contains(QUOTE)) {
			res = res.replaceAll(QUOTE, QUOTE + QUOTE);
			res = QUOTE + res + QUOTE;
		}
		return res;
	}
	
	private static String getCollectionString(Collection<OWLClass> clses) {
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

	public static void main(String[] args) throws OWLOntologyCreationException, IOException {
		 if (args.length < 2) {
	            log.error("Needs 2 params: (1) ICD OWL file, (2) output CSV file");
	            return;
	     }
		 
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();

		File icdOntFile = new File(args[0]);
		OWLOntology sourceOnt = man.loadOntologyFromOntologyDocument(icdOntFile);
		if (sourceOnt == null) {
			log.error("Could not load ICD ontology " + args[0]);
			return;
		}
		
		BufferedWriter resultCSVWriter = new BufferedWriter(new FileWriter(new File(args[1])));
		
		ICDXChapterMatcher matcher = new ICDXChapterMatcher(man, sourceOnt, resultCSVWriter);
		matcher.match();
		
	}

	
}
