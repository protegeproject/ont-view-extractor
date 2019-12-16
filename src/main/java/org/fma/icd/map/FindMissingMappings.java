package org.fma.icd.map;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

public class FindMissingMappings {
	private static transient Logger log = Logger.getLogger(FindMissingMappings.class);

	public static final String ICD_OWL_path = "/Users/ttania/work/projects/icd-fma-mapping/_running/icd-anatomy.owl";
	public static final String FMA_CSV_path = "/Users/ttania/work/projects/icd-fma-mapping/_running/2019.11.13.fma_extraction-v2.csv";

	public static final String MISSING_MAPPING_PATH = "/Users/ttania/work/projects/icd-fma-mapping/_running/missing-members-mappings.csv";
	public static final String INPUT_MISSING_IDS_PATH = "/Users/ttania/work/projects/icd-fma-mapping/_running/missing_memberof_sets_distinct_with_common_icd_parent_no_left_right_only_ids.csv";
	
	public static final String ICD_SYN_PROP = "http://who.int/icd_flattened/anatomy/synonym";

	private static final String COL_SEPARATOR = ",";
	
	private static OWLOntology icdOnt;

	private static OWLOntologyManager man;
	private static OWLAnnotationProperty icdSynProp;

	private static BufferedWriter resultCSVWriter;
	
	private static int maxMatchScore;
	

	public static void main(String[] args)
			throws OWLOntologyCreationException, OWLOntologyStorageException, IOException {

		man = OWLManager.createOWLOntologyManager();

		icdOnt = man.loadOntologyFromOntologyDocument(new File(ICD_OWL_path));
		
		icdSynProp = man.getOWLDataFactory().getOWLAnnotationProperty(ICD_SYN_PROP);

		log.info("Loaded the ICD ontology. Start processing FMA CSV..");

		ICDFMALexicalMatch.readFMACSV(FMA_CSV_path);

		log.info("Processed FMA CSV file");

		resultCSVWriter = new BufferedWriter(new FileWriter(new File(MISSING_MAPPING_PATH)));

		readMissingFileCSV(new File(INPUT_MISSING_IDS_PATH));

		resultCSVWriter.flush();
		resultCSVWriter.close();


		log.info("Finished");
	}

	private static void readMissingFileCSV(File csvFile) throws IOException {
		BufferedReader csvReader = null;

		csvReader = new BufferedReader(new FileReader(csvFile));

		String row = null;
		try {
			while ((row = csvReader.readLine()) != null) {
				processLine(row);
			}
		} catch (IOException e) {
			log.error("IO Exception at processing FMA row: " + row, e);
		}
		csvReader.close();
	}

	private static void processLine(String row) {
		String[] data = row.split(COL_SEPARATOR);
		
		if (data.length < 2) {
			log.warn("Ignoring invalid CSV row: " + row);
			return;
		} 
		
		try {
			String icdId = data[0]; //ICD parent
			String fmacId = data[1]; //unmapped child from FMA
			fmacId = "<"+fmacId+">"; //that's how it is in the fma csv
			
			findMissingMappings(icdId, fmacId, ICDFMALexicalMatch.getFMAPrefLabel(fmacId), ICDFMALexicalMatch.getFMASysn(fmacId));
		} catch (Exception e) {
			log.error("Error at processing row: " + row, e);
		}
	}
	
	public static void processICDEntity(String fmaid, String fmaPrefLabel, String[] fmaSyns,
			String icdId, String icdTitle, String[] icdSyns, BufferedWriter resultWriter) {
		maxMatchScore = 0;
		List<ICDFMAMatchRecord> matchRecs = new ArrayList<ICDFMAMatchRecord>();
		
		
		matchRecs.addAll(processTitle(fmaid, fmaPrefLabel, fmaSyns, icdId, icdTitle, true)); //ICD title -> FMA pref name
		matchRecs.addAll(processTitle(fmaid, fmaPrefLabel, fmaSyns, icdId, icdTitle, false)); //ICD title -> FMA syns
		matchRecs.addAll(processSyns(fmaid, fmaPrefLabel, fmaSyns, icdId, icdTitle, icdSyns, true)); //ICD syn -> FMA pref name
		matchRecs.addAll(processSyns(fmaid, fmaPrefLabel, fmaSyns, icdId, icdTitle, icdSyns, false)); //ICD syn -> FMA syn
		
		matchRecs = ICDFMALexicalMatch.pruneMatchRecords(matchRecs, maxMatchScore);
		ICDFMALexicalMatch.writeMatchRecords(matchRecs, resultWriter);
	}
	
	

	private static void findMissingMappings(String icdParentId, String fmaId, String fmaPrefLabel, List<String> fmaSyns) {
		OWLDataFactory df = man.getOWLDataFactory();
		OWLClass icdCls = man.getOWLDataFactory().getOWLClass(icdParentId);
		
		String[] fmaSynsArray = fmaSyns == null ? null : fmaSyns.toArray(new String[fmaSyns.size()]);
		
		List<OWLClassExpression> subclses = OWLAPIUtil.getSubclasses(icdOnt, icdCls);
		for (OWLClassExpression subcls : subclses) {
			if (subcls.isNamed() == true) {
				OWLClass owlSubCls = (OWLClass) subcls;
				
				String icdTitle = OWLAPIUtil.getStringAnnotationValue(icdOnt, owlSubCls,
						df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()));
				String[] icdSyns = OWLAPIUtil.getStringAnnotationValuesArray(icdOnt, icdCls, icdSynProp);
				
				processICDEntity(fmaId, fmaPrefLabel, fmaSynsArray, owlSubCls.getIRI().toString(), icdTitle, icdSyns, resultCSVWriter);
			}
		}
	}
	
	
	private static List<ICDFMAMatchRecord> processTitle(String fmaid, String fmaPrefLabel, String[] fmaSyns, 
			String icdId, String icdTitle, boolean processWithFMAPrefName) {
		List<FMAExtractedResult> results = ICDFMALexicalMatch.fuzzyQuery(fmaid, icdTitle, processWithFMAPrefName); //not ideal, but easier
		List<ICDFMAMatchRecord> recs = new ArrayList<ICDFMAMatchRecord>();
		
		for (FMAExtractedResult result : results) {
			recs.add(new ICDFMAMatchRecord(icdId, result.getFmaId(), icdTitle, result.getFmaPrefLabel(),
					icdTitle, ICDFMALexicalMatch.PREF_NAME_ABREV,
					result.getString(), processWithFMAPrefName ? ICDFMALexicalMatch.PREF_NAME_ABREV : ICDFMALexicalMatch.SYN_ABREV,
					result.getScore()));
			
			int score = result.getScore();
			if (score > maxMatchScore) {
				maxMatchScore = score;
			}
		}
		
		return recs;
	}


	private static List<ICDFMAMatchRecord> processSyns(String fmaid, String fmaPrefLabel, String[] fmaSyns, 
			String icdId, String icdTitle, String[] icdSyns, boolean processWithFMAPrefName) {
		List<ICDFMAMatchRecord> recs = new ArrayList<ICDFMAMatchRecord>();
		
		if (icdSyns == null || icdSyns.length == 0) {
			return  recs;
		}
		
		for (int i = 0; i < icdSyns.length; i++) {
			String syn = icdSyns[i];
			List<FMAExtractedResult> results = ICDFMALexicalMatch.fuzzyQuery(fmaid, syn, processWithFMAPrefName);
			for (FMAExtractedResult result : results) {
				recs.add(new ICDFMAMatchRecord(icdId, result.getFmaId(), icdTitle, result.getFmaPrefLabel(),
						syn, ICDFMALexicalMatch.SYN_ABREV,
						result.getString(), processWithFMAPrefName ? ICDFMALexicalMatch.PREF_NAME_ABREV : ICDFMALexicalMatch.SYN_ABREV,
						result.getScore()));
				
				int score = result.getScore();
				if (score > maxMatchScore) {
					maxMatchScore = score;
				}
			}
		}
		
		return recs;
	}

}
