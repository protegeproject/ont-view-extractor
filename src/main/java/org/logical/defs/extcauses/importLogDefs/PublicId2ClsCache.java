package org.logical.defs.extcauses.importLogDefs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.RDFSNamedClass;

public class PublicId2ClsCache {
	private static transient Logger log = Logger.getLogger(PublicId2ClsCache.class);
	
	public static final String COL_SEPARATOR = "\t";
	
	private OWLModel owlModel;
	
	//two-level lazy cache
	private Map<String,String> idMap = new HashMap<String,String>();
	private Map<String,RDFSNamedClass> id2ClsMap = new HashMap<String,RDFSNamedClass>();
	
	private static PublicId2ClsCache singleton;
	
	/**
	 * It will always return the same cache, indifferent of the owlModel.
	 * 
	 * To make it right, create a map of owlmodels to singletons, but it is 
	 * not needed here.
	 * 
	 * @param owlModel
	 * @return
	 */
	public static PublicId2ClsCache getCache(OWLModel owlModel) {
		if (singleton == null) {
			singleton = new PublicId2ClsCache(owlModel);
		}
		return singleton;
	}
	
	private PublicId2ClsCache(OWLModel owlModel) {
		this.owlModel = owlModel;
	}
	
	public void initMap(String inputCSV) throws IOException {
		BufferedReader csvReader = null;
		
		csvReader = new BufferedReader(new FileReader(inputCSV));
		
		String row = null;
		try {
			while (( row = csvReader.readLine()) != null) {
				processLine(row);
			}
		} catch (IOException e) {
			log.error("IO Exception at processing row: " + row, e);
		}
		csvReader.close();
	}

	private void processLine(String row) {
		String[] data = row.split(COL_SEPARATOR);
		String publicId = data[0];
		String clsId = data[1];
		
		idMap.put(publicId, clsId);
	}
	
	public RDFSNamedClass getCls(String publicId) {
		RDFSNamedClass cls = id2ClsMap.get(publicId);
		
		if (cls != null) {
			return cls;
		}
				
		String clsid = idMap.get(publicId);
		
		if (clsid == null) {
			log.error("Could not find class id for public id in map: " + publicId);
			return null;
		}
		
		cls = owlModel.getRDFSNamedClass(clsid);
		if (cls == null) {
			log.error("Could not find class for public id: " + publicId);
			return null;
		}
		
		id2ClsMap.put(publicId, cls);
		return cls;
	}
	
}
