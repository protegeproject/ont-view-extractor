package org.logical.defs;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import edu.stanford.bmir.whofic.icd.ICDContentModel;
import edu.stanford.bmir.whofic.icd.ICDContentModelConstants;
import edu.stanford.smi.protegex.owl.model.OWLHasValue;
import edu.stanford.smi.protegex.owl.model.OWLIntersectionClass;
import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.OWLSomeValuesFrom;
import edu.stanford.smi.protegex.owl.model.RDFProperty;
import edu.stanford.smi.protegex.owl.model.RDFResource;
import edu.stanford.smi.protegex.owl.model.RDFSClass;
import edu.stanford.smi.protegex.owl.model.RDFSNamedClass;


public class PrePostCoordinationUtils {
	
	private static transient Logger log = Logger.getLogger(PrePostCoordinationUtils.class);
			
	public static final String PC_RES_MISSING = "MISSING_POSTCOORD";
	public static final String PC_RES_NO_VALUESET = "NO_VALUESET";
	public static final String PC_RES_NO_SCALE = "NO_SCALE";
	public static final String PC_RES_MATCH = "MATCH";
	public static final String PC_RES_NO_MATCH = "NO_MATCH";
	public static final String PC_RES_UNKNOWN = "UNKNOWN"; //this is an error, if it occurs
	public static final String PC_RES_UNKNOWN_PROP = "UNKNOWN_PROP";
	
	public static final String LOG_DEF_MISSING = "MISSING_LOG_DEF";
	public static final String LOG_DEF_MATCH_NS = "MATCH_NS";
	public static final String LOG_DEF_MATCH_N = "MATCH_N";
	public static final String LOG_DEF_MISSING_FILLER = "MISSING_FILLER";
	public static final String LOG_DEF_NO_MATCH_FILLER = "NO_MATCH_FILLER";
	public static final String LOG_DEF_NO_MATCH_SUPER = "NO_MATCH_SUPER";

	private enum CheckFillerResults {
		EMPTY_FILLERS, FOUND, NOT_FOUND;
	}
	
	
	private OWLModel owlModel;
	private ICDContentModel cm;
	private ICDPostCoordinationMaps pcMaps;
	
	
	public PrePostCoordinationUtils(OWLModel owlModel) {
		this.owlModel = owlModel;
		this.cm = new ICDContentModel(owlModel);
		this.pcMaps = new ICDPostCoordinationMaps(owlModel);
	}
	
	
	public String checkPostCoordinationValues(RDFProperty pcProp, RDFSNamedClass chapterXCls, RDFSNamedClass parentCls) {
		pcProp = getAssocPostCoordinationProperty(chapterXCls);
		if (pcProp == null) {
			return PC_RES_UNKNOWN_PROP;
		}
		
		List<RDFProperty> selectedPCAxes = cm.getAllSelectedPostcoordinationAxes(parentCls, false);
		if (selectedPCAxes.contains(pcProp) == false) { //not a selected postcoordination prop
			return PC_RES_MISSING;
		}

		String actualPostCoordPropName = ICDContentModelConstants.PC_AXIS_PROP_TO_VALUE_SET_PROP.get(pcProp.getName());
		RDFProperty actualPostCoordProp = owlModel.getRDFProperty(actualPostCoordPropName);

		String pcRes = PC_RES_UNKNOWN;

		if (pcMaps.isFixedScalePCProp(pcProp)) {
			pcRes = checkFixedScalePCProp(actualPostCoordProp, chapterXCls);
		} else if (pcMaps.isScalePCProp(pcProp)) {
			pcRes = checkScalePCProp(actualPostCoordProp, chapterXCls, parentCls);
		} else if (pcMaps.isHierarchicalPCProp(pcProp)) {
			pcRes = checkHierarchicalPCProp(actualPostCoordProp, chapterXCls, parentCls);
		} else {
			pcRes = PC_RES_UNKNOWN_PROP;
		}

		return pcRes;
	}

	// Theoretically, there could be multiple pc props, but there is no way to say, which one it is,
	// as they have the same top class, so we return only one
	public RDFProperty getAssocPostCoordinationProperty(RDFSNamedClass chapterXCls) {
		Collection<RDFSClass> chapterXSuperClses = chapterXCls.getSuperclasses(true);
		
		Set<RDFSNamedClass> topChapterXClses = pcMaps.getChapterXTopParents();
		
		for (RDFSNamedClass topChapterXParent : topChapterXClses) {
			if (chapterXSuperClses.contains(topChapterXParent)) {
				return pcMaps.getPostCoordPropForXChapterTopParent(topChapterXParent);
			}
		}
		
		return null;
		
	}
	
	private String checkFixedScalePCProp(RDFProperty pcProp, RDFSNamedClass chapterXCls) {
		RDFSNamedClass topChapterXParent = pcMaps.getChapterXTopParent(pcProp);
		if (chapterXCls.isSubclassOf(topChapterXParent) == true) {
			return PC_RES_MATCH;
		} else {
			return PC_RES_NO_MATCH;
		}
	}
	
	
	private String checkHierarchicalPCProp(RDFProperty pcProp, RDFSNamedClass chapterXCls, RDFSNamedClass parentCls) {
		Collection<RDFResource> pcVals = parentCls.getPropertyValues(pcProp);
		if (pcVals == null || pcVals.size() == 0) {
			log.warn("No value set for PC. " + parentCls + ", " + pcProp);
			return PC_RES_NO_VALUESET;
		}
	
		boolean found = false;
		
		Iterator<RDFResource> it = pcVals.iterator();
		while (found == false && it.hasNext()) {
			RDFResource pcRefTerm = it.next();
			RDFResource referencedTerm = (RDFResource) pcRefTerm.getPropertyValue(cm.getReferencedValueProperty());
			if (referencedTerm == null || referencedTerm instanceof RDFSNamedClass == false) {
				log.warn("Invalid referenced value for " + parentCls + ", " + pcProp + 
						" PC inst: " + pcRefTerm + ", reference: " + referencedTerm);
			} else {
				RDFSNamedClass chapterXPCParent = (RDFSNamedClass) referencedTerm;
				if (chapterXCls.isSubclassOf(chapterXPCParent)) {
					found = true;
				}
			}
		}
		
		return found == true ? PC_RES_MATCH : PC_RES_NO_MATCH;
	}
	
	
	private String checkScalePCProp(RDFProperty pcProp, RDFSNamedClass chapterXCls, RDFSNamedClass parentCls) {
		RDFResource pcVal = (RDFResource) parentCls.getPropertyValue(pcProp);
		if (pcVal == null) {
			log.warn("No scale adapted for PC. " + parentCls + ", " + pcProp);
			return PC_RES_NO_SCALE;
		}
	
		boolean found = false;
		
		Iterator<RDFResource> it = pcVal.getPropertyValues(cm.getHasScaleValueProperty()).iterator();
		while (found == false && it.hasNext()) {
			RDFResource pcScaleVal = it.next();
			RDFResource referencedTerm = (RDFResource) pcScaleVal.getPropertyValue(cm.getReferencedValueProperty());
			if (referencedTerm == null || referencedTerm instanceof RDFSNamedClass == false) {
				log.warn("Invalid referenced value for " + parentCls + ", " + pcProp + 
						" PC inst: " + pcScaleVal + ", reference: " + referencedTerm);
			} else {
				if (chapterXCls.equals(referencedTerm)) {
					found = true;
				}
			}
		}
		
		return found == true ? PC_RES_MATCH : PC_RES_NO_MATCH;
	}

	
	private String getPCResultString(RDFProperty prop, String val) {
		return prop.getName() + "->" + val;
	}


	public String checkLogicalDefinition(RDFSNamedClass childCls, RDFSNamedClass chapterXCls, RDFSNamedClass parentCls) {
		//check parent
		RDFSNamedClass preCoordSupercls = cm.getPreecoordinationSuperclass(childCls);
		if (preCoordSupercls == null) {
			return LOG_DEF_MISSING;
		}
		
		if (parentCls.equals(preCoordSupercls) == false) {
			 return LOG_DEF_NO_MATCH_SUPER; 
		}
		
		Collection<RDFSClass> eqClses = childCls.getEquivalentClasses();
		CheckFillerResults checkEqFillers = checkFillers(chapterXCls, eqClses);
		if (checkEqFillers == CheckFillerResults.FOUND) {
			return LOG_DEF_MATCH_NS;
		}
		
		Collection<RDFSClass> superClses = childCls.getSuperclasses(false);
		CheckFillerResults checkSuperFillers = checkFillers(chapterXCls, superClses);
		if (checkSuperFillers == CheckFillerResults.FOUND) {
			return LOG_DEF_MATCH_N;
		}
		
		return (checkEqFillers == CheckFillerResults.EMPTY_FILLERS && checkSuperFillers == CheckFillerResults.EMPTY_FILLERS) ?
					LOG_DEF_MISSING_FILLER : LOG_DEF_NO_MATCH_FILLER;
	}


	private CheckFillerResults checkFillers(RDFSNamedClass chapterXCls, Collection<RDFSClass> clses) {
		boolean fillersFound = false;
		
		for (RDFSClass cls : clses) {
			if (cls instanceof OWLIntersectionClass) {
				Collection<RDFSNamedClass> fillers = getFillers((OWLIntersectionClass) cls);
				if (fillers.isEmpty() == false) {
					fillersFound = true;
				}
				if (fillers.contains(chapterXCls)) {
					return CheckFillerResults.FOUND;
				}
			}
		}
		return fillersFound ? CheckFillerResults.NOT_FOUND : CheckFillerResults.EMPTY_FILLERS;
	}
	
	private Collection<RDFSNamedClass> getFillers(OWLIntersectionClass intCls) {
		Set<RDFSNamedClass> fillers = new HashSet<RDFSNamedClass>();
		Collection<RDFSClass> ops = intCls.getOperands();
		for (RDFSClass op : ops) {
			if (op instanceof OWLHasValue || op instanceof OWLSomeValuesFrom) { //that's how log defs are encoded
				Object filler = op instanceof OWLHasValue ? ((OWLHasValue) op).getHasValue() : ((OWLSomeValuesFrom)op).getFiller();
				if (filler instanceof RDFSNamedClass) {
					fillers.add((RDFSNamedClass) filler);
				}
				//TODO: we should check also for the instances of reference terms..
			}
			
		}
		
		return fillers;
	}

	public ICDContentModel getICDContentModel() {
		return cm;
	}
	
	public String getPublicId(RDFSNamedClass cls) {
		return cm.getPublicId(cls);
	}
	
}
