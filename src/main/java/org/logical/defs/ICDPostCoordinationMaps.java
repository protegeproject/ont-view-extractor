package org.logical.defs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import edu.stanford.bmir.whofic.icd.ICDContentModelConstants;
import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.RDFProperty;
import edu.stanford.smi.protegex.owl.model.RDFSNamedClass;

public class ICDPostCoordinationMaps {
	
	private static transient Logger log = Logger.getLogger(ICDPostCoordinationMaps.class);
 
	//map XChapter top parent id -> post coordination axis property id
	public static final Map<String, String> xChapterTopParentId2postCoordPropId = new HashMap<String, String>() {{
		
		//***** Scales *****//
		
		put("http://who.int/icd#SeverityScaleValue", ICDContentModelConstants.PC_AXIS_HAS_SEVERITY);
		// ignoring for now
		//put("http://who.int/icd#SeverityScaleValue", ICDContentModelConstants.PC_AXIS_HAS_ALT_SEVERITY1); 
		//put("http://who.int/icd#SeverityScaleValue", ICDContentModelConstants.PC_AXIS_HAS_ALT_SEVERITY2);
		put("http://who.int/icd#Temporality_Course", ICDContentModelConstants.PC_AXIS_TEMPORALITY_COURSE);
		put("http://who.int/icd#Temporality_Onset", ICDContentModelConstants.PC_AXIS_TEMPORALITY_PATTERN_AND_ONSET);
		put("http://who.int/icd#Temporality_Pattern_Activity_Clinical_Status", ICDContentModelConstants.PC_AXIS_TEMPORALITY_PATTERN_AND_ONSET);
		
		
		//***** Hierarchy *****//
		
		put("http://who.int/icd#Temporality_Time_in_Life", ICDContentModelConstants.PC_AXIS_TEMPORALITY_TIME_IN_LIFE);
		put("http://who.int/icd#Etiology_17_590d00ff_eb38_468e_99da_649f0328ce5a", ICDContentModelConstants.PC_AXIS_ETIOLOGY_INFECTIOUS_AGENT);
		put("http://who.int/icd#Chemicals_0_a3172770_2d87_4087_aff6_7a140a289bf1", ICDContentModelConstants.PC_AXIS_ETIOLOGY_CHEMICAL_AGENT);
		put("http://who.int/icd#Medicaments_0_f3b7cb4e_72bc_44e9_a4f3_4c945803be1b", ICDContentModelConstants.PC_AXIS_ETIOLOGY_MEDICATION);
		
		put("http://who.int/icd#Histopathology", ICDContentModelConstants.PC_AXIS_HISTOPATHOLOGY);
		put("http://who.int/icd#SpecificAnatomicLocation_1000", ICDContentModelConstants.PC_AXIS_SPECIFIC_ANATOMY);
		
		//Currently not detecting these; also would be a problem with this map, as it is the same key
		//put("http://who.int/icd#ICDCategory", ICDContentModelConstants.PC_AXIS_HAS_CAUSING_CONDITION);
		//put("http://who.int/icd#ICDCategory", ICDContentModelConstants.PC_AXIS_HAS_MANIFESTATION);
		//put("http://who.int/icd#ICDCategory", ICDContentModelConstants.PC_AXIS_ASSOCIATED_WITH);
		
		
		//***** Fixed scales *****//
		
		put("http://who.int/icd#Etiology_0_590d00ff_eb38_468e_99da_649f0328ce5a", ICDContentModelConstants.PC_AXIS_ETIOLOGY_CAUSALITY);
		put("http://who.int/icd#Topology_Laterality", ICDContentModelConstants.PC_AXIS_TOPOLOGY_LATERALITY);
		put("http://who.int/icd#Topology_Relational", ICDContentModelConstants.PC_AXIS_TOPOLOGY_RELATIONAL);
		put("http://who.int/icd#Topology_Distribution", ICDContentModelConstants.PC_AXIS_TOPOLOGY_DISTRIBUTION);
		put("http://who.int/icd#Topology_Regional", ICDContentModelConstants.PC_AXIS_TOPOLOGY_REGIONAL);
		
		// Ignoring serotype and genomic and chromosomal anomalies, as there are no value set defined yet
		
		put("http://who.int/icd#71_3d82a1df_19f1_424f_afc8_23e964a7d7ef", ICDContentModelConstants.PC_AXIS_INJURY_QUALIFIER_FRACTURE_QUALIFIER_FRACTURE_SUBTYPE);
		put("http://who.int/icd#9540_81655b5c_debe_4590_b8ad_ea6448685723", ICDContentModelConstants.PC_AXIS_INJURY_QUALIFIER_FRACTURE_QUALIFIER_OPEN_OR_CLOSED);
		put("http://who.int/icd#3225_81655b5c_debe_4590_b8ad_ea6448685723", ICDContentModelConstants.PC_AXIS_INJURY_QUALIFIER_FRACTURE_QUALIFIER_JOINT_INVOLVEMENT_IN_FRACTURE_SUBTYPE);
		put("http://who.int/icd#3569_b42302c6_9ff1_412e_a40f_2f411ad4c932", ICDContentModelConstants.PC_AXIS_INJURY_QUALIFIER_TYPE_OF_INJURY);
		
		// Using only the superproperty for the burns
		put("http://who.int/icd#353_8485ca45_4e2f_4137_b90e_effee792d3d2", ICDContentModelConstants.PC_AXIS_INJURY_QUALIFIER_BURN_QUALIFIER);
		
		//duration of coma currently retired, did not identify
		put("http://who.int/icd#Consciousness_88_938ccdf1_0c6e_4de7_b72a_5e4ac69565c6", ICDContentModelConstants.PC_AXIS_CONSCIOUSNESS_MEASURE_DURATION_OF_COMA);
		
		// Using only the superproperty for Consciousness
		put("http://who.int/icd#Consciousness", ICDContentModelConstants.PC_AXIS_LEVEL_OF_CONSCIOUSNESS);
		
		// Ignoring Diagnosis confirmed by, because no value set
		
		/***** External causes axes - ignored for now *****/
		
	}};

	
	private OWLModel owlModel;
	
	private Map<RDFSNamedClass, RDFProperty> xChapterTopParent2postCoordProp = new HashMap<RDFSNamedClass, RDFProperty>();
	private Map<RDFProperty, RDFSNamedClass> postCoordProp2xChapterTopParent = new HashMap<RDFProperty, RDFSNamedClass>();
	
	private List<RDFProperty> fixedScalePCProps = new ArrayList<RDFProperty>();
	private List<RDFProperty> scalePCProps = new ArrayList<RDFProperty>();
	private List<RDFProperty> hierarchyPCProps = new ArrayList<RDFProperty>();
	
	public ICDPostCoordinationMaps(OWLModel owlModel) {
		this.owlModel = owlModel;
		fillMaps();
	}

	private void fillMaps() {
		fixedScalePCProps = fillMap(ICDContentModelConstants.FIXED_SCALE_PC_AXES_PROPERTIES_LIST);
		scalePCProps = fillMap(ICDContentModelConstants.SCALE_PC_AXES_PROPERTIES_LIST);
		hierarchyPCProps = fillMap(ICDContentModelConstants.HIERARCHICAL_PC_AXES_PROPERTIES_LIST);
		
		fillXChapter2PcPropMap();
	}
	
	private List<RDFProperty> fillMap(List<String> propIdMap) {
		List<RDFProperty> propList = new ArrayList<RDFProperty>();
		for (String propId : propIdMap) {
			RDFProperty prop = owlModel.getRDFProperty(propId);
			if (prop == null) {
				log.warn("Ignore " + prop + " when creating post-coordination maps. Property not found");
			} else {
				propList.add(prop);
			}
		}
		return propList;
	}

	private void fillXChapter2PcPropMap() {
		for (String xChapterTopParentId : xChapterTopParentId2postCoordPropId.keySet()) {
			RDFSNamedClass xChapterTopParent = owlModel.getRDFSNamedClass(xChapterTopParentId);
			if (xChapterTopParent == null) {
				log.warn("Ignore " + xChapterTopParentId + " when creating post-coordination axes map, because could not find class");
			} else {
				String propId = xChapterTopParentId2postCoordPropId.get(xChapterTopParentId);
				RDFProperty postCoordAxisProp = owlModel.getRDFProperty(propId);
				if (postCoordAxisProp == null) {
					log.warn("Ignore " + xChapterTopParentId + " when creating post-coordination axes map, because could not find property " + propId);
				} else {
					xChapterTopParent2postCoordProp.put(xChapterTopParent, postCoordAxisProp);
					postCoordProp2xChapterTopParent.put(postCoordAxisProp, xChapterTopParent);
				}
			}
		}
	}
	
	public RDFProperty getPostCoordPropForXChapterTopParent(RDFSNamedClass xChapterTopParent) {
		return xChapterTopParent2postCoordProp.get(xChapterTopParent);
	}
	
	public Set<RDFSNamedClass> getChapterXTopParents() {
		return xChapterTopParent2postCoordProp.keySet();
	}
	
	public RDFSNamedClass getChapterXTopParent(RDFProperty prop) {
		return postCoordProp2xChapterTopParent.get(prop);
	}
	
	public boolean isFixedScalePCProp(RDFProperty prop) {
		return fixedScalePCProps.contains(prop);
	}
	
	public boolean isScalePCProp(RDFProperty prop) {
		return scalePCProps.contains(prop);
	}
	
	public boolean isHierarchicalPCProp(RDFProperty prop) {
		return hierarchyPCProps.contains(prop);
	}
}
