package org.fma.icd.map;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;


public class ICDFMALexicalMatch {
	private static transient Logger log = Logger.getLogger(ICDFMALexicalMatch.class);
	
	private static int FUZZY_MATCH_CUT_OFF = 70;
	private static int TOP_MATCH_PERCENTAGE = 25;
	
	private static final String COL_SEPARATOR = "\t";
	private static final String VALUE_SEPARATOR = "\\*";
	private static final String QUOTE = "\"";
	
	public static final String PREF_NAME_ABREV = "P";
	public static final String SYN_ABREV = "S";
	
	private static HashMap<String,String> fmaId2fmaPrefName = new HashMap<String, String>();
	private static HashMap<String,List<String>> fmaId2fmaSyns = new HashMap<String, List<String>>();
	
	//per ICD line
	private static int maxMatchScore = 0;
	
	private static int fmaLineCount = 0;
	private static int icdLineCount = 0;
	
	private static int log_print = 100;
	
	private static BufferedWriter resultCSVWriter;

	
	public static void main(String[] args) {
		 if (args.length != 3) {
	            log.error("Needs 3 params: (1) ICD CSV file, (2) FMA CSV file, and (3) output file");
	            return;
	     }
		 
		 try {
			readFMACSV(args[1]);
			
			log.info("Ended reading FMA .. " + fmaLineCount + " lines. " + new Date());
			
			processICDCSV(args[0], args[2]);
			
			
			log.info("Ended mappings. Processed " + icdLineCount + " ICD lines. " + new Date());
		} catch (IOException e) {
			log.error("IO Exception", e);
		}
	}
	
	
	private static void processICDCSV(String icdCSV, String resultCSV) throws IOException {
		BufferedReader csvReader = null;
		
		csvReader = new BufferedReader(new FileReader(icdCSV));
		resultCSVWriter = new BufferedWriter(new FileWriter(new File(resultCSV)));
		
		String row = null;
		try {
			while (( row = csvReader.readLine()) != null) {
				processICDLine(row);
				if (icdLineCount % log_print == 0) {
					log.info("Processed " + icdLineCount + " ICD lines.");
				}
			}
		} catch (IOException e) {
			log.error("IO Exception at processing ICD row: " + row, e);
		}
		resultCSVWriter.flush();
		resultCSVWriter.close();
		csvReader.close();
	}
	
	
	/**
	 * id, title, syns*
	 * @param row
	 */
	private static void processICDLine(String row) {
		String[] data = row.split(COL_SEPARATOR);
		
		if (data.length < 2) {
			log.warn("Ignoring ICD row, because no pref name, or no id: " + row);
			return;
		} 
		
		String id = data[0];
		String title = transformString(data[1]);
		
		String[] syns = null;
		if (data.length > 2) {
			data[2] = transformString(data[2]);
			syns = data[2].split(VALUE_SEPARATOR);
		}
		
		processICDEntity(id, title, syns, resultCSVWriter);
		
		icdLineCount ++ ;
	}


	public static void processICDEntity(String icdId, String icdTitle, String[] syns, BufferedWriter writer) {
		maxMatchScore = 0;
		List<ICDFMAMatchRecord> matchRecs = new ArrayList<ICDFMAMatchRecord>();
		
		
		matchRecs.addAll(processTitle(icdId, icdTitle, true)); //ICD title -> FMA pref name
		matchRecs.addAll(processTitle(icdId, icdTitle, false)); //ICD title -> FMA syns
		matchRecs.addAll(processSyns(icdId, icdTitle, syns, true)); //ICD syn -> FMA pref name
		matchRecs.addAll(processSyns(icdId, icdTitle, syns, false)); //ICD syn -> FMA syn
		
		matchRecs = pruneMatchRecords(matchRecs, maxMatchScore);
		writeMatchRecords(matchRecs, writer);
	}
	

	public static List<ICDFMAMatchRecord> pruneMatchRecords(List<ICDFMAMatchRecord> matchRecs, int maxMatchScore) {
		int cutoff = maxMatchScore - (maxMatchScore * TOP_MATCH_PERCENTAGE/100);
		
		List<ICDFMAMatchRecord> rets = new ArrayList<ICDFMAMatchRecord>();
		
		for (ICDFMAMatchRecord rec : matchRecs) {
			int score = rec.getScore();
			if (score >= cutoff) {
				rets.add(rec);
			}
		}
		
		return rets;
	}


	private static List<ICDFMAMatchRecord> processTitle(String icdId, String icdTitle, boolean processWithFMAPrefName) {
		List<FMAExtractedResult> results = fuzzyQuery(icdTitle, processWithFMAPrefName); //not ideal, but easier
		List<ICDFMAMatchRecord> recs = new ArrayList<ICDFMAMatchRecord>();
		
		for (FMAExtractedResult result : results) {
			recs.add(new ICDFMAMatchRecord(icdId, result.getFmaId(), icdTitle, result.getFmaPrefLabel(),
					icdTitle, PREF_NAME_ABREV,
					result.getString(), processWithFMAPrefName ? PREF_NAME_ABREV : SYN_ABREV,
					result.getScore()));
			
			int score = result.getScore();
			if (score > maxMatchScore) {
				maxMatchScore = score;
			}
		}
		
		return recs;
	}


	private static List<ICDFMAMatchRecord> processSyns(String icdId, String icdTitle, String[] syns, boolean processWithFMAPrefName) {
		List<ICDFMAMatchRecord> recs = new ArrayList<ICDFMAMatchRecord>();
		
		if (syns == null || syns.length == 0) {
			return  recs;
		}
		
		for (int i = 0; i < syns.length; i++) {
			String syn = syns[i];
			List<FMAExtractedResult> results = fuzzyQuery(syn, processWithFMAPrefName);
			for (FMAExtractedResult result : results) {
				recs.add(new ICDFMAMatchRecord(icdId, result.getFmaId(), icdTitle, result.getFmaPrefLabel(),
						syn, SYN_ABREV,
						result.getString(), processWithFMAPrefName ? PREF_NAME_ABREV : SYN_ABREV,
						result.getScore()));
				
				int score = result.getScore();
				if (score > maxMatchScore) {
					maxMatchScore = score;
				}
			}
		}
		
		return recs;
	}
	
	
	public static void writeMatchRecords(List<ICDFMAMatchRecord> matchRecs, BufferedWriter writer) {
		for (ICDFMAMatchRecord rec : matchRecs) {
			writeLine(rec.getIcdId(), rec.getFmaId(), rec.getIcdTitle(), rec.getFmaPrefLabel(),
					rec.getMatchedICDString(), rec.getIcdMatchType(), rec.getMatchedFMAString(),
					rec.getFmaMatchType(), Integer.toString(rec.getScore()), writer);
		}
		
	}
	
	
	private static void writeLine(String icdId, String fmaId, String icdTitle, String fmaPrefName,
			String matchedICDString, String icdMatchType,
			String matchedFMAString, String fmaMatchType, 
			String score, BufferedWriter writer) {
		try {
			writer.write(icdId + COL_SEPARATOR + fmaId + COL_SEPARATOR +
        			toCsvField(icdTitle) + COL_SEPARATOR + fmaPrefName + COL_SEPARATOR +
        			toCsvField(matchedICDString) + COL_SEPARATOR + icdMatchType + COL_SEPARATOR + 
        			toCsvField(matchedFMAString) + COL_SEPARATOR + fmaMatchType + COL_SEPARATOR + 
        			score);
        			
			writer.newLine();
    	} 
    	catch (IOException ioe) {
			log.error("Could not export line for: " + icdId);
		}
		
	}


	private static List<FMAExtractedResult> fuzzyQuery(String query, boolean isPrefName) {
		List<FMAExtractedResult> results = new ArrayList<FMAExtractedResult>();
				
		for (String fmaid : fmaId2fmaPrefName.keySet()) {
			results.addAll(fuzzyQuery(fmaid, query, isPrefName));
		}
		return results;
	}
	
	
	public static List<FMAExtractedResult> fuzzyQuery(String fmaid, String query, boolean isPrefName) {
		List<FMAExtractedResult> results = new ArrayList<FMAExtractedResult>();
		
		String prefName = fmaId2fmaPrefName.get(fmaid);
		if (isPrefName == true) { //prefName
			int score = fuzzyQuery(prefName, query);
			if (score > FUZZY_MATCH_CUT_OFF) {
				results.add(new FMAExtractedResult(prefName, score, 0, fmaid, prefName));
			}
		} else { //syns
			Collection<String> syns = fmaId2fmaSyns.get(fmaid);
			if (syns != null) {
				for (String syn : syns) {
					int score = fuzzyQuery(syn, query);
					if (score > FUZZY_MATCH_CUT_OFF) {
						results.add(new FMAExtractedResult(syn, score, 0, fmaid, prefName));
					}
				}
			}
		}
		return results;
	}
	

	private static int fuzzyQuery(String s1, String s2) {
		//return FuzzySearch.tokenSetPartialRatio(s1, s2);
		//return FuzzySearch.ratio(s1, s2);
		//return FuzzySearch.tokenSortRatio(s1, s2);
		return StringMatcher.stemFuzzyMatch(s1, s2);
	}
	
	public static void readFMACSV(String fmaCSVFile) throws IOException {
		BufferedReader csvReader = null;
		
		csvReader = new BufferedReader(new FileReader(fmaCSVFile));
		
		String row = null;
		try {
			while (( row = csvReader.readLine()) != null) {
				processFMALine(row);
			}
		} catch (IOException e) {
			log.error("IO Exception at processing FMA row: " + row, e);
		}
		csvReader.close();
	}
	
	
	/**
	 * FMA ID IRI, pref name, synonym
	 * @param row
	 */
	private static void processFMALine(String row) {
		String[] data = row.split(COL_SEPARATOR);
		
		if (data.length < 2) {
			log.warn("Ignoring FMA row, because no pref name, or no id: " + row);
			return;
		} 
		
		String prefName = transformString(data[1]);
		fmaId2fmaPrefName.put(data[0], prefName);
		
		if (data.length > 2) {
			String syn = transformString(data[2]);
			addFmaSyn(data[0], syn);
		}
		
		fmaLineCount ++ ;
	}
	
	private static void addFmaSyn(String id, String syn) {
		List<String> syns = fmaId2fmaSyns.get(id);
		if (syns == null) {
			syns = new ArrayList<String>();
		}
		syns.add(syn);
		fmaId2fmaSyns.put(id, syns);
	}
	

	private static String transformString(String str) {
		if (str == null) {
			return str;
		}
		
		if (str.startsWith(QUOTE) && str.endsWith(QUOTE)) {
			str = str.substring(1, str.length()-1);
		}
		
		return str.toLowerCase();
	}
	
	private static String toCsvField(Object o) {
		String res = (o == null ? "" : o.toString());
		if (res.contains("\n") || res.contains(COL_SEPARATOR) || res.contains(VALUE_SEPARATOR) || res.contains(QUOTE)) {
			res = res.replaceAll(QUOTE, QUOTE + QUOTE);
			res = QUOTE + res + QUOTE;
		}
		return res;
	}

	
	public static String getFMAPrefLabel(String fmaId) {
		return fmaId2fmaPrefName.get(fmaId);
	}
	
	public static List<String> getFMASysn(String fmaId) {
		return fmaId2fmaSyns.get(fmaId);
	}
	
}
