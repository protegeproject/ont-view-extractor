package org.logical.defs.extcauses.importLogDefs;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import edu.stanford.bmir.whofic.icd.ICDContentModel;
import edu.stanford.smi.protegex.owl.model.RDFResource;
import edu.stanford.smi.protegex.owl.model.RDFSClass;
import edu.stanford.smi.protegex.owl.model.RDFSNamedClass;

public class KBUtil {
	private static transient Logger log = Logger.getLogger(KBUtil.class);
	
	
	/**
	 * Assume the hierarchy had single parenting, and no cycles, as it is in External Causes.
	 * 
	 * This is not a good method to reuse.
	 * 
	 * @param owlModel
	 * @param cls
	 * @param topParent
	 * @return
	 */
	public static List<RDFSNamedClass> getPathToFirstMMSParent(ICDContentModel cm, RDFSNamedClass cls, RDFSNamedClass topParent) {
		List<RDFSNamedClass> path = new ArrayList<RDFSNamedClass>();
		return getPathToFirstMMSParent(cm, cls, topParent, path);
	}
	
	private static List<RDFSNamedClass> getPathToFirstMMSParent(ICDContentModel cm, RDFSNamedClass cls, RDFSNamedClass topParent, List<RDFSNamedClass> path) {
		//get only first parent, as it is supposed to be single-parented
		RDFSNamedClass dirParent = (RDFSNamedClass) cls.getSuperclasses(false).iterator().next();
		path.add(dirParent);
		
		if (dirParent.equals(topParent)) {
			return path;
		}
		
		if (isIncludedInMMS(cm, dirParent)) {
			return path;
		}
		
		return getPathToFirstMMSParent(cm, dirParent, topParent, path);
	}
	
	public static boolean isIncludedInMMS(ICDContentModel cm, RDFSNamedClass cls) {
		Collection<RDFResource> linSpecs = cm.getLinearizationSpecifications(cls);
		for (RDFResource linSpec : linSpecs) {
			if (cm.getMorbidityLinearizationView().equals(cm.getLinearizationViewFromSpecification(linSpec))) {
				boolean included = (boolean) linSpec.getPropertyValue(cm.getIsIncludedInLinearizationProperty());
				return included;
			}
		}
		return false;
	}
	
	
	public static List<RDFSNamedClass> getNamedSubclasses(RDFSNamedClass cls, boolean transitive) {
		List<RDFSNamedClass> subclses = new ArrayList<RDFSNamedClass>();
		for (RDFSClass subcls : (Collection<RDFSClass>) cls.getSubclasses(transitive)) {
			if (subcls instanceof RDFSNamedClass) {
				subclses.add((RDFSNamedClass)subcls);
			}
		}
		return subclses;
	}
	
	public static Collection<String> getTitles(ICDContentModel cm, Collection<RDFSNamedClass> clses) {
		Collection<String> list = new ArrayList<String>();
		
		for (RDFSNamedClass cls : clses) {
			String title = cm.getTitleLabel(cls);
			title = title == null ? cls.getName() : title;
			list.add(title);
		}
		
		return list;
	}
	
	public static String getPublicIdFromURL(String browserLink) {
		Matcher matcher = Pattern.compile(".*(http%3[^\"]+).*").matcher(browserLink);
		matcher.find();
		String encoderUrl = matcher.group(1);
		
		String ret = null;
		
		try {
			ret = URLDecoder.decode(encoderUrl, "UTF-8");
		} catch (Exception e) {
			log.error("Error at decoding url: " + encoderUrl, e);
		}
		
		return ret;
	}
	
	public static String getTitleFromURL(String browserLink) {
		Matcher matcher = Pattern.compile(".*,\"([^\"]+).*").matcher(browserLink);
		matcher.find();
		String title = matcher.group(1);
		
		title = title.trim();
		
		return title;
	}

	
	public static void main(String[] args) {
		System.out.println(getPublicIdFromURL("=HYPERLINK(\"https://icd.who.int/dev11/f/en#/http%3A%2F%2Fid.who.int%2Ficd%2Fentity%2F297977881\",\"Building, building component, or related fitting\")"));
	
		System.out.println( "\tab fd f  ".matches(".*\\w.*"));
		System.out.println( " \t \t \t \t \t ".matches(".*\\w.*"));
		
		System.out.println(getTitleFromURL("=HYPERLINK(\"https://icd.who.int/dev11/f/en#/http%3A%2F%2Fid.who.int%2Ficd%2Fentity%2F297977881\",\"Building, building component, or related fitting\")"));

		
	}
}
