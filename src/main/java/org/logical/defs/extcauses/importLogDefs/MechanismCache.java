package org.logical.defs.extcauses.importLogDefs;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import edu.stanford.bmir.whofic.icd.ICDContentModel;
import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.RDFSNamedClass;

public class MechanismCache {
	private static transient Logger log = Logger.getLogger(MechanismCache.class);

	public final static String MECH_TOP_CLS = "http://who.int/icd#ICECI_C.2";
	
	private ICDContentModel cm;
	private OWLModel owlModel;

	private Map<String, RDFSNamedClass> title2cls = new HashMap<String, RDFSNamedClass>();
	
	public MechanismCache(ICDContentModel cm) {
		this.cm = cm;
		this.owlModel = cm.getOwlModel();
	}
	
	public void init() {
		log.info("Starting to initialize mechanism cache..");
		
		RDFSNamedClass mechTopCls = owlModel.getRDFSNamedClass(MECH_TOP_CLS);
		
		for (RDFSNamedClass cls : KBUtil.getNamedSubclasses(mechTopCls, true)) {
			title2cls.put(cm.getTitleLabel(cls).trim(), cls);
		}
		
		log.info("Ended to initialize mechanism cache");
	}
	
	public RDFSNamedClass getMechanism(String title) {
		return title2cls.get(title);
	}
	
}
