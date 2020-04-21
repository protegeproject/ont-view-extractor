package org.logical.defs.extcauses;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.fma.icd.map.OWLAPIUtil;
import org.fma.icd.map.StringUtils;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class ExportExtCauseLogDefToCSV {

	private static transient Logger log = Logger.getLogger(ExportExtCauseLogDefToCSV.class);
	
	private OWLOntologyManager ontologyManager;
	private OWLDataFactory df;

	private OWLOntology sourceOntology;
	private OWLReasoner reasoner;

	private XMatchCache xMatchCache;
	private List<OWLClass> axes = new ArrayList<OWLClass>();

	private BufferedWriter csvWriter;

	public ExportExtCauseLogDefToCSV(OWLOntologyManager manager, OWLOntology sourceOntology,
			OWLReasoner reasoner, XMatchCache xMatchCache) {
		this.ontologyManager = manager;
		this.df = ontologyManager.getOWLDataFactory();
		this.sourceOntology = sourceOntology;
		this.reasoner = reasoner;
		this.xMatchCache = xMatchCache;
		
		initAxes();
	}
	
	private void initAxes() {
		for (String axName : ExtCausesChapterXMaps.allTopXAxes) {
			axes.add(df.getOWLClass(axName));
		}
		//remove mechanism, index 0  - we treat it separately
		axes.remove(0);
	}

	public void export(File csvFile) {
		try {
			csvWriter = new BufferedWriter(new FileWriter(csvFile));
			
			//write first line with the headers
			writeLine(getFirstLine());
			
			Set<OWLClass> traversed = new HashSet<OWLClass>();
			exportCls(df.getOWLClass(ExtCausesConstants.CHAPTER_EXT_CAUSES_ID), df.getOWLThing(), traversed, 0);
			 
			csvWriter.close();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} 
	}	
	
	private void exportCls(OWLClass cls, OWLClass parent, Set<OWLClass> traversed, int level) {
		if (traversed.contains(cls) || isExcludedTopCls(cls)) {
			return;
		}
		
		traversed.add(cls);
		
		exportCls(cls, level);
		
		Set<OWLClass> subclses = OWLAPIUtil.getNamedSubclasses(cls, sourceOntology, reasoner, true);
		for (OWLClass subcls : subclses) {
			exportCls(subcls, cls, traversed, level+1);
		}
		
		//writeNewLine();
	}
	
	private static String AXES_COUNT_REPLACE = "REPLACEME";
	
	// writing on cols
	private void exportCls(OWLClass cls, int level) {
		if (OWLAPIUtil.isDeprecated(sourceOntology, cls) == true) {
			return;
		}
		
		String clsLabel = OWLAPIUtil.getSKOSPrefLabelValue(sourceOntology, cls);
		OWLClass mech = xMatchCache.getMechanism(cls);
		String mechLabel = (mech == null) ? "" : OWLAPIUtil.getSKOSPrefLabelValue(sourceOntology, mech) + parentHasMatch(cls, mech);
		
		Intent intent = xMatchCache.getIntent(cls);
		String intentLabel = intent == null ? "" : intent.getShortText();
		
		String toWrite = new String();
		
		String clsPublicId = StringUtils.getPublicBrowserLink(cls.getIRI().toString(), clsLabel);
		String mechPublicId = mech == null ? "" :
				StringUtils.getPublicBrowserLink(cls.getIRI().toString(), mechLabel);
		
		toWrite = toWrite + getLevelString(level) + StringUtils.COL_SEPARATOR + " " + clsPublicId + StringUtils.COL_SEPARATOR;
		toWrite = toWrite + AXES_COUNT_REPLACE + StringUtils.COL_SEPARATOR;
		toWrite = toWrite + intentLabel + StringUtils.COL_SEPARATOR;
		toWrite = toWrite + mechPublicId + StringUtils.COL_SEPARATOR;
		
		int axesCount = mech == null ? 0 : 1;
		
		int index = 0;
		boolean stillRowsToWrite = true;
		while (stillRowsToWrite == true) {
			stillRowsToWrite = false;
			
			if (index > 0) {
				toWrite = StringUtils.COL_SEPARATOR + StringUtils.COL_SEPARATOR + 
						StringUtils.COL_SEPARATOR + StringUtils.COL_SEPARATOR +
						StringUtils.COL_SEPARATOR;
			}
			
			for (OWLClass ax : axes) {
				
				List<OWLClass> vals = getValuesForAxes(cls, ax);
				String text = StringUtils.COL_SEPARATOR;
			
				if (vals.size() > index) {
					OWLClass val = vals.get(index);
					text = StringUtils.getPublicBrowserLink(val.getIRI().toString(), 
							OWLAPIUtil.getSKOSPrefLabelValue(sourceOntology, val) + parentHasMatch(cls, val));
					text = text + StringUtils.COL_SEPARATOR;
					axesCount = axesCount + 1;
					
					if (vals.size() > index + 1) {
						stillRowsToWrite = true;
					}
				}
				
				toWrite = toWrite + text;
			}
			
			if (index == 0) {
				//write the matched axes count
				toWrite = toWrite.replace(AXES_COUNT_REPLACE, Integer.toString(axesCount));
			}
			
			
			writeLine(toWrite);
			
			index = index + 1;
		}
		
	}

	
	private void writeLine(String toWrite) {
		try {
			csvWriter.write(toWrite);
			csvWriter.newLine();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	private List<OWLClass> getValuesForAxes(OWLClass cls, OWLClass axes) {
		List<OWLClass> vals = new ArrayList<OWLClass>();
		
		for (XMatch xMatch : xMatchCache.getMatches(cls)) {
			OWLClass topCls = xMatch.getTopXCls();
			if (topCls.equals(axes)) {
				vals.add(xMatch.getXMatch());
			}
		}
		
		return vals;
	}
	
	private String parentHasMatch(OWLClass cls, OWLClass xCls) {
		for (OWLClass parent : OWLAPIUtil.getNamedSuperclasses(cls, sourceOntology, reasoner, false)) {
			for (XMatch xMatch : xMatchCache.getMatches(parent)) {
				if (xCls.equals(xMatch.getXMatch()) == true) {
					return " (p)";
				}
			}
		}
		
		return "";
	}
	
	private String getFirstLine() {
		String toWrite = new String();
		
		toWrite = toWrite + "Level" + StringUtils.COL_SEPARATOR + "Cls" + StringUtils.COL_SEPARATOR;
		toWrite = toWrite + "Axes #" + StringUtils.COL_SEPARATOR + "Intent" + StringUtils.COL_SEPARATOR;
		toWrite = toWrite + "Mechanism" + StringUtils.COL_SEPARATOR;
				
		for (OWLClass ax : axes) {
			String prefLabelAx = OWLAPIUtil.getSKOSPrefLabelValue(sourceOntology, ax);
			toWrite = toWrite + prefLabelAx + StringUtils.COL_SEPARATOR;
		}
		
		return toWrite;
	}
	
	
	private String getLevelString(int level) {
		String ret = new String();
		if (level < 1) {
			return ret;
		}
		
		for (int i = 0; i < level; i++) {
			ret=ret+"- ";
		}
		ret=ret.trim();
		return ret;
	}
	
	private boolean isExcludedTopCls(OWLClass cls) {
		return ExtCausesChapterXMaps.excludedTopClsesFromExport.contains(cls.getIRI().toString());
	}
	
}
