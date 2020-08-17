package org.logical.defs.extcauses.importLogDefs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import edu.stanford.bmir.whofic.icd.ICDContentModel;
import edu.stanford.smi.protegex.owl.model.OWLIntersectionClass;
import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.OWLNamedClass;
import edu.stanford.smi.protegex.owl.model.OWLSomeValuesFrom;
import edu.stanford.smi.protegex.owl.model.RDFProperty;
import edu.stanford.smi.protegex.owl.model.RDFResource;
import edu.stanford.smi.protegex.owl.model.RDFSClass;
import edu.stanford.smi.protegex.owl.model.RDFSNamedClass;

public class LogicalDefinitionCreator {
	private static transient Logger log = Logger.getLogger(LogicalDefinitionCreator.class);
	
	public final static String CHAPTER_EXT_CAUSES = "http://who.int/icd#XX";
	public final static String CHAPTER_EXT_CAUSES_RETIRED = "http://who.int/icd#440_6bc3f235_b24a_493e_a8a3_60cb9fb52dbd";
	public final static String FOUNDATION_LIN_VIEW = "http://who.int/icd#FoundationComponent";
	
	private ICDContentModel cm;
	private OWLModel owlModel;
	
	private RDFSNamedClass extCausesTopCls;
	private RDFSNamedClass extCausesRetiredTopCls;
	private RDFResource foundationLinView;
	
	private Map<RDFSNamedClass, ClsLogicalDefinition> cls2logDefDesc = new HashMap<RDFSNamedClass, ClsLogicalDefinition>();
	
	private Map<RDFSNamedClass, ClsLogicalDefinition> cls2inhLogDefDesc = new HashMap<RDFSNamedClass, ClsLogicalDefinition>();
	
	private PCAxesCache pcAxesCache;
	
	private int logDefCount = 0;
	
	public LogicalDefinitionCreator(ICDContentModel cm, Map<RDFSNamedClass, ClsLogicalDefinition> cls2logDefDesc) {
		this.cm = cm;
		this.owlModel = cm.getOwlModel();
		this.cls2logDefDesc = cls2logDefDesc;
		
		this.extCausesTopCls = owlModel.getRDFSNamedClass(CHAPTER_EXT_CAUSES);
		this.extCausesRetiredTopCls = owlModel.getRDFSNamedClass(CHAPTER_EXT_CAUSES_RETIRED);
		this.foundationLinView = owlModel.getRDFResource(FOUNDATION_LIN_VIEW);
		
		this.pcAxesCache = PCAxesCache.getCache(owlModel);
	}

	public void createLogicalDefinitions() {
		//add the EC top class to the map to make processing easier
		initTopLevelMap();
		
		//fill the cls2inhLogDefDesc map
		propagateInheritedFillers();
		
		//remove one of the "object or substance" axes, as there are two; 
		//the values of the two same axes are mutually exclusive
		//so removing one should be safe
		//pcAxesCache.getPCProps().remove(1);
		
		for (RDFSNamedClass cls : cls2logDefDesc.keySet()) {
			createLogicalDefinitions(cls);
		}
		
		log.info("Created " + logDefCount + " logical definitions");
		//print for testing
		//printLogDefs();
	}


	private void createLogicalDefinitions(RDFSNamedClass cls) {
		//if MMS precoord parent has the filler already, then don't include it in the log def
		purgeRedundantFiller(cls);
		
		ClsLogicalDefinition clsLogDef = cls2inhLogDefDesc.get(cls);
		
		if (clsLogDef == null) {
			return;
		}
		
		enablePostCoordinationAxesForParent(clsLogDef);
		
		createLogicalDefinition(clsLogDef);
	}
	
	
	private void createLogicalDefinition(ClsLogicalDefinition clsLogDef) {
		OWLNamedClass cls = (OWLNamedClass) clsLogDef.getCls();
		RDFSNamedClass precoordParent = clsLogDef.getPrecoordParent();
		
		//check if cls already has the precoord parent as a direct superclass
		boolean hasPrecoordParentAsDirSupercls = checkIfDirectParent(cls, precoordParent);
		
		Collection<RDFSClass> operands = new ArrayList<RDFSClass>();
		operands.add(precoordParent);
		
		for (RDFProperty pcProp : pcAxesCache.getPCProps()) {
			RDFSNamedClass filler = clsLogDef.getFiller(pcProp);
			if (filler != null) {
				OWLSomeValuesFrom operand = owlModel.createOWLSomeValuesFrom(pcProp, filler);
				operands.add(operand);
			}
		}
		
		if (operands.size() == 1) { //just the parent
			log.warn("LOG DEF NOT CREATED, NO FILLERS\t" + cm.getTitleLabel(cls) );
			return;
		}
		
		OWLIntersectionClass intCls = owlModel.createOWLIntersectionClass(operands);
		cls.addEquivalentClass(intCls);
		
		//setting also the precoordination parent
		cm.setPrecoordinationSuperclass(cls, precoordParent);
		
		//if the cls did not have the precoord parent as dir superclass,
		//then, remove it now. (Protege has add it because it is an operand
		//in the logical definition.
		if (hasPrecoordParentAsDirSupercls == false) {
			cls.removeSuperclass(precoordParent);
		}
		
		logDefCount ++;
	}
	

	private boolean checkIfDirectParent(OWLNamedClass cls, RDFSNamedClass parent) {
		Collection<RDFSClass> dirParents = cls.getSuperclasses(false);
		return dirParents.contains(parent);
	}

	private void enablePostCoordinationAxesForParent(ClsLogicalDefinition clsLogDef) {
		RDFSNamedClass parent = clsLogDef.getPrecoordParent();
		
		if (parent == null) {
			return;
		}
		
		RDFResource pcSpec = cm.getPostCoordinationSpecification(parent, foundationLinView);
		
		if (pcSpec == null) { //should not happen
			log.warn("Class " + cm.getTitleLabel(parent) + " does not have a postcoordination specification"
					+ "for the Foundation linearization. Creating one.");
			pcSpec = cm.createPostcoordinationSpecification(parent, foundationLinView);
		}
		
		Collection<RDFProperty> toEnableProps = clsLogDef.getPropertiesWithFillers();
		Collection<RDFProperty> allowedProps = cm.getAllowedPostCoordinationProperties(pcSpec);
		
		for (RDFProperty prop : toEnableProps) {
			if (allowedProps.contains(prop) == false) {
				pcSpec.addPropertyValue(cm.getAllowedPostcoordinationAxisPropertyProperty(), prop);
				//System.out.println("ENABLE PC PROP for\t" + cm.getTitleLabel(parent) + "\t" + prop.getBrowserText());
			}
		}
	}
	
	
	
	//*************** preprocessing ******************/



	private void initTopLevelMap() {
		ClsLogicalDefinition extCausesTopClsLogDef = new ClsLogicalDefinition(owlModel, extCausesTopCls);
		cls2logDefDesc.put(extCausesTopCls, extCausesTopClsLogDef);
	}

	private void propagateInheritedFillers() {
		log.info("Started propagating fillers ..");
		
		cls2inhLogDefDesc = new HashMap<RDFSNamedClass, ClsLogicalDefinition>(cls2logDefDesc);
	
		Set<RDFSNamedClass> visited = new HashSet<RDFSNamedClass>();
		visited.add(extCausesRetiredTopCls);
		propagateIngeritedFillers(extCausesTopCls, visited);
		
		log.info("Ended propagating fillers");
	}

	private void propagateIngeritedFillers(RDFSNamedClass cls, Set<RDFSNamedClass> visited) {
		if (visited.contains(cls)) {
			return;
		}
		
		visited.add(cls);
		
		addInheritedFillersToSubclasses(cls);
		
		for (RDFSNamedClass subcls : KBUtil.getNamedSubclasses(cls, false)) {
			propagateIngeritedFillers(subcls, visited);
		}
	}

	@SuppressWarnings("deprecation")
	private void addInheritedFillersToSubclasses(RDFSNamedClass cls) {
		for (RDFSNamedClass subcls : KBUtil.getNamedSubclasses(cls, false)) {
			for (RDFProperty prop : pcAxesCache.getInheritablePCProps()) {
				
				ClsLogicalDefinition clsLogicalDefinition = cls2inhLogDefDesc.get(cls);
				ClsLogicalDefinition subclsLogicalDefinition = cls2inhLogDefDesc.get(subcls);
				
				//happens for retired clses
				if (clsLogicalDefinition == null || subclsLogicalDefinition == null) {
					continue;
				}
				
				RDFSNamedClass clsFiller = clsLogicalDefinition.getFiller(prop);
				RDFSNamedClass subclassFiller = subclsLogicalDefinition.getFiller(prop);
				
				if (clsFiller == null) {
					continue;
				}
				
				//subclass filler is more generic than the parent filler
				if (subclassFiller == null || clsFiller.hasSuperclass(subclassFiller) ) {
					subclsLogicalDefinition.setProp2Filler(prop, clsFiller);
				}
			}
		}
	}
	
	private void purgeRedundantFiller(RDFSNamedClass cls) {
		ClsLogicalDefinition clsLogDef = cls2inhLogDefDesc.get(cls);
		RDFSNamedClass preCoordParent = clsLogDef.getPrecoordParent();
		
		ClsLogicalDefinition precoordParentLogDef = cls2inhLogDefDesc.get(preCoordParent);
		
		//it happens if the logical definition for the parent was deleted in the CSV file
		if (precoordParentLogDef == null) {
			return;
		}
		
		for (RDFProperty prop : pcAxesCache.getPCProps()) {
			RDFSNamedClass filler = clsLogDef.getFiller(prop);
			RDFSNamedClass preCoordFiller = precoordParentLogDef.getFiller(prop);
			
			if (filler != null && preCoordFiller != null && filler.equals(preCoordFiller)) { //maybe we need subclass 
				clsLogDef.removePropFiller(prop);
			}
		}
	}	
	
	
	private void printLogDefs() {
		Set<RDFSNamedClass> visited = new HashSet<RDFSNamedClass>();
		visited.add(extCausesRetiredTopCls);
		visited.add(owlModel.getRDFSNamedClass("http://who.int/icd#83_984d1092_4b56_4a7c_89ea_b16a49816573")); //Causes of healthcare related harm or injury
		printLogDefs(extCausesTopCls, visited, 0);	
	}

	private void printLogDefs(RDFSNamedClass cls, Set<RDFSNamedClass> visited, int level) {
		if (visited.contains(cls)) {
			return;
		}
		
		visited.add(cls);
		
		printLogDefs(cls, level);
		
		for (RDFSNamedClass subcls : KBUtil.getNamedSubclasses(cls, false)) {
			printLogDefs(subcls, visited, level + 1);
		}
	}
	
	private void printLogDefs(RDFSNamedClass cls, int level) {
		
		if (cm.isObsoleteCls(cls) == true) {
			return;
		}
		
		ClsLogicalDefinition clsLogDef = cls2inhLogDefDesc.get(cls);
		
		if (clsLogDef == null) {
			String str = level + "\tXXX\t" + cm.getTitleLabel(cls) + "\t(no logical definition)";
			System.out.println(str);
			
			return;
		}
		
		String shouldImportStr = clsLogDef.hasAnyFillers() ? "" : "XXX";
		
		String str = level + "\t" + shouldImportStr + "\t" +
						cm.getTitleLabel(cls) + "\t" + 
						cm.getTitleLabel(clsLogDef.getPrecoordParent()) + "\t";
		
		for (RDFProperty prop : pcAxesCache.getPCProps()) {
			RDFSNamedClass filler = clsLogDef.getFiller(prop);
			str = str + (filler == null ? "" : cm.getTitleLabel(filler)) + "\t";
		}
		
		System.out.println(str);
	}

}
