package org.logical.defs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.fma.icd.map.OWLAPIUtil;
import org.fma.icd.map.StringMatcher;
import org.fma.icd.map.StringUtils;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;


public class LexicalMatchParentChildICDX {

	private static transient Logger log = Logger.getLogger(LexicalMatchParentChildICDX.class);
	
	private static int FUZZY_MATCH_CUT_OFF = 95;

	public final static String ICD_CATEGORIES = "http://who.int/icd#ICDCategory";
	public final static String X_CHAPTER = "http://who.int/icd#ChapterX";
	public static final String ICD_SYN_PROP = "http://who.int/icd_flattened/synonym";
	
	public static final String PREF_NAME_ABREV = "P";
	public static final String SYN_ABREV = "S";

	private OWLOntologyManager ontManager;
	private OWLReasoner reasoner;
	private OWLOntology icdOnt;
	private OWLAnnotationProperty synProp;

	private Map<OWLClass, String> topCls2label = new HashMap<OWLClass, String>();
	
	private BufferedWriter resultCSVWriter;
	private BufferedReader inputCSVReader;


	public LexicalMatchParentChildICDX(OWLOntologyManager manager, OWLOntology sourceOnt,
			BufferedWriter bufferedWriter, BufferedReader bufferedReader) {
		this.ontManager = manager;
		this.icdOnt = sourceOnt;
		this.resultCSVWriter = bufferedWriter;
		this.inputCSVReader = bufferedReader;
		this.reasoner = initReasoner(icdOnt);
		this.synProp = manager.getOWLDataFactory().getOWLAnnotationProperty(ICD_SYN_PROP);
	}

	public void export() throws IOException {
		precacheTopClses();
		matchClasses();
	}


	private void matchClasses() throws IOException {
		log.info("Starting matching..");

		int count = 0;
		String row = null;
		try {
			while (( row = inputCSVReader.readLine()) != null) {
				processRow(row);
				
				count ++;
				if (count % 100 == 0) {
					log.info("Processed " + count + " lines.");
				}
			}
		} catch (IOException e) {
			log.error("IO Exception at processing row: " + row, e);
		}
		
		resultCSVWriter.flush();
		resultCSVWriter.close();
		inputCSVReader.close();

		log.info("End matching.");
	}

	
	private void processRow(String row) {
		try {
			String[] data = row.split(StringUtils.COL_SEPARATOR);
			
			if (data.length < 10) {
				log.warn("Ignoring row because incomplete: " + row);
				return;
			} 
			
			String icdId = data[0];
			String chapterXId = data[1];
			String icdTitle = data[2];
			String chapterXPrefName = data[3];
			String matchedICDString = data[4];
			String icdMatchType = data[5];
			String matchedChapterXString = data[6];
			String chapterXMatchType = data[7];
			//String score = data[8];
			//String superclses = data[9];
			
			checkMatch(icdId, chapterXId, icdTitle, chapterXPrefName, matchedICDString, 
					icdMatchType, matchedChapterXString, chapterXMatchType);
			
		} catch (Exception e) {
			log.error("Error at processing row: " + row, e);
		}
		
	}


	private void checkMatch(String icdClsId, String xChapterId, String icdTitle, String chapterXPrefName,
			String matchedICDString, String icdMatchType, String matchedChapterXString, String chapterXMatchType) {
		
		OWLClass icdCls = ontManager.getOWLDataFactory().getOWLClass(icdClsId);
		OWLClass xChapterCls = ontManager.getOWLDataFactory().getOWLClass(xChapterId);
		Set<OWLClass> superClses = OWLAPIUtil.getNamedSuperclasses(icdCls, icdOnt, reasoner, true);
		
		for (OWLClass superCls : superClses) {
			String superClsTitle = OWLAPIUtil.getRDFSLabelValue(icdOnt, superCls);
			String topParent = StringUtils.getLabelCollectionString(icdOnt, getTopClsesForCls(superCls));
			String topParentChapterX = StringUtils.getLabelCollectionString(icdOnt, getTopClsesForCls(xChapterCls));
			
			//checking ICD child with parent title
			checkMatchWithString(icdClsId, xChapterId, superCls.getIRI().toString(), icdTitle, chapterXPrefName, 
					superClsTitle, matchedICDString, icdMatchType, matchedChapterXString, chapterXMatchType, 
					superClsTitle, PREF_NAME_ABREV, topParent, topParentChapterX);
			
			//checking ICD child with parent syns
			Collection<String> syns = OWLAPIUtil.getStringAnnotationValues(icdOnt, superCls, synProp);
			for (String syn : syns) {
				checkMatchWithString(icdClsId, xChapterId, superCls.getIRI().toString(), icdTitle, chapterXPrefName, 
						superClsTitle, matchedICDString, icdMatchType, matchedChapterXString, chapterXMatchType, 
						syn, SYN_ABREV, topParent, topParentChapterX);
			}
		}
	}
	
	
	private void checkMatchWithString(String icdClsId, String xChapterId, String parentId, String icdTitle, String chapterXPrefName,
			String parentTitle, String matchedICDString, String icdMatchType, String matchedChapterXString, String chapterXMatchType,
			String parentMatchString, String parentMatchType, String topParent, String topParentChapterX) {
		
		matchedICDString = matchedICDString.toLowerCase();
		matchedChapterXString = matchedChapterXString.toLowerCase();
		
		//String childWithoutXValue = matchedICDString.replace(matchedChapterXString, " ");
		String childWithoutXValue = StringMatcher.replaceStringUsingStems(matchedICDString, matchedChapterXString, " ");
		
		int score = StringMatcher.stemFuzzyMatch(parentMatchString, childWithoutXValue);
		
		if (score >= FUZZY_MATCH_CUT_OFF) {
			writeLine(icdClsId, xChapterId, parentId, icdTitle, chapterXPrefName, parentTitle, matchedICDString, icdMatchType,
					matchedChapterXString, chapterXMatchType, parentMatchString, parentMatchType, 
					Integer.toString(score), topParent, topParentChapterX);
		}
	}

	
	
	private void writeLine(String icdClsId, String xChapterId, String parentId, String icdTitle, String chapterXPrefName,
			String parentTitle, String matchedICDString, String icdMatchType, String matchedChapterXString, String chapterXMatchType,
			String parentMatchString, String parentMatchType, String score, String topParent, String topParentChapterX) {
		try {
			resultCSVWriter.write(icdClsId + StringUtils.COL_SEPARATOR + xChapterId + StringUtils.COL_SEPARATOR +
					parentId + StringUtils.COL_SEPARATOR +
					StringUtils.toCsvField(icdTitle) + StringUtils.COL_SEPARATOR + 
					StringUtils.toCsvField(chapterXPrefName) + StringUtils.COL_SEPARATOR +
					StringUtils.toCsvField(parentTitle) + StringUtils.COL_SEPARATOR +
        			StringUtils.toCsvField(matchedICDString) + StringUtils.COL_SEPARATOR + 
        			icdMatchType + StringUtils.COL_SEPARATOR + 
        			StringUtils.toCsvField(matchedChapterXString) + StringUtils.COL_SEPARATOR + 
        			chapterXMatchType + StringUtils.COL_SEPARATOR + 
        			StringUtils.toCsvField(parentMatchString) + StringUtils.COL_SEPARATOR +
        			parentMatchType + StringUtils.COL_SEPARATOR + 
        			score + StringUtils.COL_SEPARATOR +
        			StringUtils.toCsvField(topParent) + StringUtils.COL_SEPARATOR +
        			StringUtils.toCsvField(topParentChapterX));
        			
			resultCSVWriter.newLine();
    	} 
    	catch (IOException ioe) {
			log.error("Could not export line for: " + icdClsId);
		}
	}
	
	
	private void precacheTopClses() {
		precacheTopClses(ontManager.getOWLDataFactory().getOWLClass(ICD_CATEGORIES));
		
		OWLClass xChapterCls = ontManager.getOWLDataFactory().getOWLClass(X_CHAPTER);
		//this is only necessary because I am using the same cache for top level icd cat and x chapter
		topCls2label.remove(xChapterCls);  
		precacheTopClses(xChapterCls);
	}
	
	private void precacheTopClses(OWLClass topParent) {
		Collection<OWLClass> directSubClses = OWLAPIUtil.getNamedSubclasses(topParent, icdOnt, reasoner, true);
		for (OWLClass subcls : directSubClses) {
			topCls2label.put(subcls, OWLAPIUtil.getRDFSLabelValue(icdOnt, subcls));
		}
	}
	
	private Set<OWLClass> getTopClsesForCls(OWLClass subcls) {
		Set<OWLClass> topClses = new HashSet<OWLClass>();

		Set<OWLClass> superclses = reasoner.superClasses(subcls, false).collect(Collectors.toSet());

		for (OWLClass topCls : topCls2label.keySet()) {
			if (superclses.contains(topCls)) {
				topClses.add(topCls);
			}
		}

		return topClses;
	}
	
	private OWLClass getTopClass() {
		return ontManager.getOWLDataFactory().getOWLClass(ICD_CATEGORIES);
	}

	private OWLReasoner initReasoner(OWLOntology ontology) {
		OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
		OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
		return reasoner;
	}


	public static void main(String[] args) throws OWLOntologyCreationException, IOException {
		if (args.length < 3) {
			log.error("Needs 3 params: (1) ICD OWL file, (2) lexical icd and chapterx mapping CSV file, "
					+ "and (3) output CSV file");
			return;
		}

		OWLOntologyManager man = OWLManager.createOWLOntologyManager();

		File icdOntFile = new File(args[0]);
		OWLOntology sourceOnt = man.loadOntologyFromOntologyDocument(icdOntFile);
		if (sourceOnt == null) {
			log.error("Could not load ICD ontology " + args[0]);
			return;
		}

		BufferedReader inputCSVReader = new BufferedReader(new FileReader(new File(args[1])));
		BufferedWriter resultCSVWriter = new BufferedWriter(new FileWriter(new File(args[2])));

		LexicalMatchParentChildICDX exporter = new LexicalMatchParentChildICDX(man, sourceOnt,
				resultCSVWriter, inputCSVReader);
		exporter.export();

	}

}
