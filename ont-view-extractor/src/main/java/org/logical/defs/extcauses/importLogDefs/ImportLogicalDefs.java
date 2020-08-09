package org.logical.defs.extcauses.importLogDefs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import edu.stanford.bmir.whofic.icd.ICDContentModel;
import edu.stanford.smi.protege.model.Project;
import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.RDFProperty;
import edu.stanford.smi.protegex.owl.model.RDFSNamedClass;

/**
 * Import the reviewed logical definitions from a CSV file into
 * iCAT.
 * 
 * @author ttania
 *
 */
public class ImportLogicalDefs {
	private static transient Logger log = Logger.getLogger(ImportLogicalDefs.class);
	
	public static final String COL_SEPARATOR = "\t";
	public static final String VALUE_SEPARATOR = "; *|\\\\n";
	
	public final static String CHAPTER_EXT_CAUSES = "http://who.int/icd#XX";
	public final static String CHAPTER_EXT_CAUSES_RETIRED = "http://who.int/icd#440_6bc3f235_b24a_493e_a8a3_60cb9fb52dbd";
	
	private OWLModel owlModel;
	private ICDContentModel cm;
	
	private RDFSNamedClass extCausesTopCls;
	
	private PublicId2ClsCache publicId2ClsCache;
	private PCAxesCache pcAxesCache;
	private MechanismCache mechanismCache;
	
	private RDFSNamedClass currentlyProcessedCls;
	
	private Map<RDFSNamedClass, ClsLogicalDefinition> cls2logDefDesc = new HashMap<RDFSNamedClass, ClsLogicalDefinition>();
	
	
	public ImportLogicalDefs(OWLModel owlModel) {
		this.owlModel = owlModel;
		this.cm = new ICDContentModel(owlModel);
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			log.error("Expected 3 arguments: (1) PPRJ file to import into; "
					+ "(2) CSV file with publicid - cls id maps;"
					+ "(3) CSV file with logical definitions to import;");
			System.exit(1);
		}
		
		Project prj = Project.loadProjectFromFile(args[0], new ArrayList());
		OWLModel owlModel = (OWLModel) prj.getKnowledgeBase();
		
		if (owlModel == null) {
			log.error("Could not load project file: " + args[0]);
			System.exit(1);
		}
		
		ImportLogicalDefs importer = new ImportLogicalDefs(owlModel);
		importer.init(args[1]);
		importer.importClses(args[0], args[2]);
	}
	

	private void init(String publicIdMapFile) throws IOException {
		extCausesTopCls = owlModel.getRDFSNamedClass(CHAPTER_EXT_CAUSES);
		currentlyProcessedCls = extCausesTopCls; //just to avoid a NPE, no effect
		
		initPublicIdCache(publicIdMapFile); //needs to come first
		pcAxesCache = PCAxesCache.getCache(owlModel);
		
		mechanismCache = new MechanismCache(cm);
		mechanismCache.init();
	}


	private void initPublicIdCache(String publicIdMapFile) throws IOException {
		log.info("Started init public id cache..");
		
		publicId2ClsCache = PublicId2ClsCache.getCache(owlModel);
		publicId2ClsCache.initMap(publicIdMapFile);
		
		log.info("End init public id cache");
	}


	public void importClses(String prjName, String csvFilePath) throws IOException {
		
		readCSV(csvFilePath);
		
		log.info("Read " + cls2logDefDesc.size() + " logical definitions from CSV file");
		
		LogicalDefinitionCreator creator = new LogicalDefinitionCreator(cm, cls2logDefDesc);
		creator.createLogicalDefinitions();
		
		log.info("Done with import.");
	}


	protected void processLine(String row) {
		
		if (isEmptyRow(row) == true) {
			return;
		}
		
		String[] data = row.split(COL_SEPARATOR);
		
		String level = getString(data, 0);
		String clsBrowserLink = getString(data, 1);
		
		if (ignoreRow(level) == true || isEmptyVal(clsBrowserLink) == true) {
			//remove from map the current cls
			cls2logDefDesc.remove(currentlyProcessedCls);
			
			log.warn("IGNORE: logical definition for\t" + cm.getTitleLabel(currentlyProcessedCls) + "\t" + cm.getPublicId(currentlyProcessedCls));
			return;
		}
		
		RDFSNamedClass cls = getClsFromBrowserLink(clsBrowserLink);
		
		currentlyProcessedCls = cls;
		
		//Special handling of the mechanism, because public id links in the CSV file are wrong
		//and we need to figure out the mechanism cls in a different way
		setMechanismFiller(cls, getString(data, 4));
		
		int i = 5;
		for (RDFProperty pcProp : pcAxesCache.getPcPropsNoMech()) {
			String val = getString(data, i);
			if (isEmptyVal(val) == false) {
				RDFSNamedClass fillerCls = getClsFromBrowserLink(val);
				addPCPropFiller(cls, pcProp, fillerCls);
			}
			i ++;
		}
		
	}


	private void setMechanismFiller(RDFSNamedClass cls, String browserlink) {
		if (browserlink == null || browserlink.length() == 0) {
			return;
		}
		
		String title = KBUtil.getTitleFromURL(browserlink);
		
		if (title == null || title.length() == 0) {
			return;
		}
		
		RDFSNamedClass mechCls = mechanismCache.getMechanism(title);
		if (mechCls == null) {
			log.error("NOT FOUND: Cannot find mechanism class for: " + title);
			return;
		}
		
		addPCPropFiller(cls, pcAxesCache.getMechanismProp(), mechCls);
	}

	private void addPCPropFiller(RDFSNamedClass cls, RDFProperty pcProp, RDFSNamedClass fillerCls) {
		ClsLogicalDefinition clsLogicalDef = cls2logDefDesc.get(cls);
		
		if (clsLogicalDef == null) {
			clsLogicalDef = new ClsLogicalDefinition(owlModel, cls);
			clsLogicalDef.setInheritancePath(KBUtil.getPathToFirstMMSParent(cm, cls, extCausesTopCls));
		}
		
		clsLogicalDef.setProp2Filler(pcProp, fillerCls);
		
		cls2logDefDesc.put(cls, clsLogicalDef);
	}


	private boolean ignoreRow(String level) {
		return level == null || level.length() == 0 || "x".equalsIgnoreCase(level);
	}

	private boolean isEmptyVal(String val) {
		return val == null || val.length() == 0;
	}

	private boolean isEmptyRow(String row) {
		return row.matches(".*\\w.*") == false;
	}

	/****************** Bulk methods *******************/
	
	private void readCSV(String inputCSV) throws IOException {
		BufferedReader csvReader = null;
		
		csvReader = new BufferedReader(new FileReader(inputCSV));
		
		int lineCount = 0;
		
		String row = null;
		try {
			while (( row = csvReader.readLine()) != null) {
				processLine(row);
				if (lineCount % 100 == 0) {
					log.info("Processed " + lineCount + " lines");
				}
				lineCount ++;
			}
		} catch (IOException e) {
			log.error("IO Exception at processing row: " + row, e);
		}
		csvReader.close();
	}
	
	
	protected String getString(String[] data, int index) {
		if (data.length <= index) {
			return null;
		}
		String text = data[index];
		
		return text == null ? null : text.trim();
	}
	
	
	public RDFSNamedClass getClsFromBrowserLink(String browserLink) {
		String publicId = KBUtil.getPublicIdFromURL(browserLink);
		if (publicId == null) {
			log.error("Could not get public id from browser link: " + browserLink);
			return null;
		}
		
		return publicId2ClsCache.getCls(publicId);
	}
	
	
	public ICDContentModel getCm() {
		return cm;
	}
	
	public OWLModel getOwlModel() {
		return owlModel;
	}
	
}
