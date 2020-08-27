package org.logical.defs.extcauses.importLogDefs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.stanford.bmir.whofic.icd.ICDContentModelConstants;
import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.RDFProperty;

public class PCAxesCache {

	private OWLModel owlModel;
	
	private List<RDFProperty> pcProps = new ArrayList<RDFProperty>();
	private List<RDFProperty> pcPropsNoMech = new ArrayList<RDFProperty>();
	private List<RDFProperty> inheritablePcProps = new ArrayList<RDFProperty>();
	
	private RDFProperty mechProp;
	
	private static PCAxesCache cache;
	
	//ordered props from CSV
	List<String> orderedXChapterEC_PCPropIdList = Arrays.asList(
			ICDContentModelConstants.PC_AXIS_EC_MECHANISM_OF_INJURY,
			ICDContentModelConstants.PC_AXIS_EC_OBJECT_OR_SUBSTANCE_PRODUCING_INJURY,
			ICDContentModelConstants.PC_AXIS_EC_OBJECT_OR_SUBSTANCE_PRODUCING_INJURY,
			ICDContentModelConstants.PC_AXIS_EC_TRANSPORT_EVENT_DESCRIPTOR_MODE_OF_TRANSPORT_OF_THE_INJURED_PERSON,
			ICDContentModelConstants.PC_AXIS_EC_TRANSPORT_EVENT_DESCRIPTOR_COUNTERPART,
			ICDContentModelConstants.PC_AXIS_EC_TRANSPORT_EVENT_DESCRIPTOR_MECHANISM_OF_TRANSPORT_INJURY_WITHOUT_COUNTERPART,
			ICDContentModelConstants.PC_AXIS_EC_TRANSPORT_EVENT_DESCRIPTOR_ROLE_OF_THE_INJURED_PERSON,
			ICDContentModelConstants.PC_AXIS_EC_ACTIVITY_WHEN_INJURED,
			ICDContentModelConstants.PC_AXIS_EC_ALCOHOL_USE, 
			ICDContentModelConstants.PC_AXIS_EC_ARMED_CONFLICT_ROLE_OF_INJURED_PERSON_IN_ARMED_CONFLICT,
			ICDContentModelConstants.PC_AXIS_EC_ARMED_CONFLICT_TYPE_OF_ARMED_CONFLICT,
			ICDContentModelConstants.PC_AXIS_EC_ASSAULT_AND_MALTREATMENT_CONTEXT_OF_ASSAULT_AND_MALTREATMENT,
			ICDContentModelConstants.PC_AXIS_EC_ASSAULT_AND_MALTREATMENT_PERPETRATOR_VICTIM_RELATIONSHIP
			);

	/**
	 * It will always return the same cache, indifferent of the owlModel.
	 * 
	 * To make it right, create a map of owlmodels to singletons, but it is 
	 * not needed here.
	 * 
	 * @param owlModel
	 * @return
	 */
	public static PCAxesCache getCache(OWLModel owlModel) {
		if (cache == null) {
			cache = new PCAxesCache(owlModel);
		}
		return cache;
	}
	
	
	private PCAxesCache(OWLModel owlModel) {
		this.owlModel = owlModel;
		
		initAxesProps();
	}
	
	private void initAxesProps() {
		for (String propId : orderedXChapterEC_PCPropIdList) {
			RDFProperty prop = owlModel.getRDFProperty(propId);
			pcProps.add(prop);
		}
		
		inheritablePcProps = new ArrayList<RDFProperty>(pcProps);
		inheritablePcProps.remove(1); //object causing injury
		inheritablePcProps.remove(2); //substances (same prop)
		
		pcPropsNoMech = new ArrayList<RDFProperty>(pcProps);
		pcPropsNoMech.remove(0); //mechanism
		
		mechProp = owlModel.getRDFProperty(ICDContentModelConstants.PC_AXIS_EC_MECHANISM_OF_INJURY);
	}
	
	public List<RDFProperty> getPCProps() {
		return pcProps;
	}
	
	public List<RDFProperty> getInheritablePCProps() {
		return inheritablePcProps;
	}
	
	public List<RDFProperty> getPcPropsNoMech() {
		return pcPropsNoMech;
	}
	
	public RDFProperty getMechanismProp() {
		return mechProp;
	}
}
