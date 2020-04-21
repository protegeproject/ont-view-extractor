package org.logical.defs.extcauses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fma.icd.map.OWLAPIUtil;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class XChapterCache {
	
	public static final String DIM_OF_EXT_CAUSES_CLS_ID = "http://id.who.int/icd/entity/1004338415";
	public static final String DIM_OF_INJUY_CLS_ID = "http://id.who.int/icd/entity/870996432";
	public static final String SUBSTANCES_CLS_ID = "http://id.who.int/icd/entity/1321407960";
	
	//ASPECTS OF TRANSPORT
	public final static String MECH_TRANSPORT_CLS_ID = "http://id.who.int/icd/entity/1851145654";
	public final static String ASPECTS_OF_TRAFFIC_INJURY_EVENTS_CLS_ID = "http://id.who.int/icd/entity/1564873651";
	public final static String MODE_OF_TRANSPORT_CLS_ID = "http://id.who.int/icd/entity/2037136197";
	public final static String COUNTERPART_CLS_ID = "http://id.who.int/icd/entity/1463612101";
	public final static String NO_COUNTERPART_CLS_ID = "http://id.who.int/icd/entity/594925588";
	public final static String USER_ROLE_CLS_ID = "http://id.who.int/icd/entity/1075090949";
	
	//ASPECTS OF MECHANISM
	public final static String ASPECTS_OF_MECHANISM_CLS_ID = "http://id.who.int/icd/entity/847623771";

	//SUBSTANCES MECHANISM
	public final static String ASPECTS_OF_SUBSTANCES_MECHANISM = "http://id.who.int/icd/entity/864114369";
	
	//ASPECTS OF DEVICES
	public final static String ASPECTS_OF_DEVICES = "http://id.who.int/icd/entity/1502396257";
	
	//HEATH DEVICES - should be ignored for the logical definitions
	public final static String HEALTH_DEVICES_CLS_ID = "http://id.who.int/icd/entity/1597357976";
	
	//OBJS OR LIVING THINGS INVOLVED IN CAUSING INJURY
	public final static String OBJ_CAUSING_INURY_CLS_ID = "http://id.who.int/icd/entity/632148635";
	

	
	private OWLOntologyManager ontologyManager;
	private OWLDataFactory df;
	
	private OWLOntology sourceOntology;
	private OWLReasoner reasoner;
	
	private Map<OWLClass, Set<OWLClass>> xTopCls2children = new HashMap<OWLClass, Set<OWLClass>>();
	
	private Set<OWLClass> genericTopClses = new HashSet<OWLClass>();
	
	private Map<OWLClass, List<OWLClass>> extCause2topXCls = new HashMap<OWLClass, List<OWLClass>>();
	
	public XChapterCache(OWLOntologyManager manager, OWLOntology sourceOntology, OWLReasoner reasoner) {
		this.ontologyManager = manager;
		this.df = ontologyManager.getOWLDataFactory();
		this.sourceOntology = sourceOntology;
		this.reasoner = reasoner;
	}
	
	public void init() {
		precacheDimExtCausesTopClasses();
		
		//precacheTopCls(DIM_OF_INJUY_CLS_ID);
		precacheTopCls(SUBSTANCES_CLS_ID);
		
		//no big hope from these mappings, but adding them just in case
		precacheTopCls(HEALTH_DEVICES_CLS_ID); 
		
		precacheTopCls(MECH_TRANSPORT_CLS_ID);
		//precacheTopCls(MODE_OF_TRANSPORT_CLS_ID);
		//precacheTopCls(COUNTERPART_CLS_ID);
		//precacheTopCls(NO_COUNTERPART_CLS_ID);
		//precacheTopCls(USER_ROLE_CLS_ID);
		//precacheTopCls(ASPECTS_OF_MECHANISM_CLS_ID);
		
		initGenericTopClasses();
		initExtCause2ChapterXMaps();
	}
	

	private void initGenericTopClasses() {
		for (String clsId : ExtCausesChapterXMaps.genericTopXAxes) {
			genericTopClses.add(df.getOWLClass(clsId));
		}
	}
	
	private void initExtCause2ChapterXMaps() {
		for (String extCauseClsId : ExtCausesChapterXMaps.extcaus2chapterx.keySet()) {
			OWLClass extCauseCls = df.getOWLClass(extCauseClsId);
			
			List<OWLClass> clses = new ArrayList<OWLClass>();
			for (String valId : ExtCausesChapterXMaps.extcaus2chapterx.get(extCauseClsId)) {
				clses.add(df.getOWLClass(valId));
			}
			
			extCause2topXCls.put(extCauseCls, clses);
		}
	}
	
	private void precacheDimExtCausesTopClasses() {
		/*Set<OWLClass> topClses = OWLAPIUtil.getNamedSubclasses(
				df.getOWLClass(DIM_OF_EXT_CAUSES_CLS_ID), //Dimensions of external causes
				sourceOntology, reasoner, true);
		for (OWLClass topCls : topClses) {
			precacheTopCls(topCls);
		}
		*/
		
		for (String topXCls : ExtCausesChapterXMaps.allTopXAxes) {
			precacheTopCls(topXCls);
		}
		
		//remove the aspects of devices entry, aspects of transport injury
		//xTopCls2children.remove(df.getOWLClass(ASPECTS_OF_DEVICES));
		xTopCls2children.remove(df.getOWLClass(ASPECTS_OF_TRAFFIC_INJURY_EVENTS_CLS_ID));
		
		//remove Gender of perpretator tree, because only bad matches
		//xTopCls2children.remove(df.getOWLClass("http://id.who.int/icd/entity/665815018")); 
				
		removeTreeFromCache(df.getOWLClass(OBJ_CAUSING_INURY_CLS_ID), df.getOWLClass(HEALTH_DEVICES_CLS_ID));
		removeTreeFromCache(getAspectsOfMechanismTopXCls(), getTransportMechanismTopXCls());
	}
	
	private void removeTreeFromCache(OWLClass topXParent, OWLClass branchParentToRemove) {
		Set<OWLClass> allChildren = xTopCls2children.get(topXParent);
		Set<OWLClass> childrenToRemove = OWLAPIUtil.getNamedSubclasses(branchParentToRemove,
				sourceOntology, reasoner, false);
		allChildren.removeAll(childrenToRemove);
		xTopCls2children.put(topXParent, allChildren);
	}

	private void precacheTopCls(String topClsName) {
		precacheTopCls(df.getOWLClass(topClsName));
	}
	
	private void precacheTopCls(OWLClass topCls) {
		xTopCls2children.put(topCls, OWLAPIUtil.getNamedSubclasses(df.getOWLClass(topCls),
				sourceOntology, reasoner, false));
	}
	
	public Set<OWLClass> getXTopClasses() {
		return Collections.unmodifiableSet(xTopCls2children.keySet());
	}
	
	public Set<OWLClass> getXTopClassesNoMech() {
		Set<OWLClass> allTopClses = new HashSet<OWLClass>();
		allTopClses.addAll(xTopCls2children.keySet());
		allTopClses.remove(getAspectsOfMechanismTopXCls());
		return allTopClses;
	}
	
	public Set<OWLClass> getGenericXTopClasses() {
		return Collections.unmodifiableSet(genericTopClses);
	}
	
	public Set<OWLClass> getXTopClasses(OWLClass topExtCauseCls) {
		Set<OWLClass> topClses = new HashSet<OWLClass>(genericTopClses);
		List<OWLClass> specificClses = extCause2topXCls.get(topExtCauseCls);
		
		if (specificClses != null) {
			topClses.addAll(specificClses);
		}
		
		return topClses;
	}
	
	public Set<OWLClass> getXChildren(OWLClass xTopClass) {
		return Collections.unmodifiableSet(xTopCls2children.get(xTopClass));
	}
	
	public OWLClass getAspectsOfTransportInjuryEventsTopXCls() {
		return df.getOWLClass(ASPECTS_OF_TRAFFIC_INJURY_EVENTS_CLS_ID);
	}
	
	public OWLClass getAspectsOfMechanismTopXCls() {
		return df.getOWLClass(ASPECTS_OF_MECHANISM_CLS_ID);
	}
	
	public OWLClass getModeOfTransportTopXCls() {
		return df.getOWLClass(MODE_OF_TRANSPORT_CLS_ID);
	}
	
	public OWLClass getCounterpartTopXCls() {
		return df.getOWLClass(COUNTERPART_CLS_ID);
	}
	
	public OWLClass getNoCounterpartTopXCls() {
		return df.getOWLClass(NO_COUNTERPART_CLS_ID);
	}
	
	public OWLClass getUserRoleTopXCls() {
		return df.getOWLClass(USER_ROLE_CLS_ID);
	}
	
	public OWLClass getTransportMechanismTopXCls() {
		return df.getOWLClass(MECH_TRANSPORT_CLS_ID);
	}
	
	public OWLClass getSubstancesMechanismTopXCls() {
		return df.getOWLClass(ASPECTS_OF_SUBSTANCES_MECHANISM);
	}
	
	public OWLClass getSubstancesTopXCls() {
		return df.getOWLClass(SUBSTANCES_CLS_ID);
	}
	
	public OWLClass getObjectsCausingInjuryTopXCls() {
		return df.getOWLClass(OBJ_CAUSING_INURY_CLS_ID);
	}
}

