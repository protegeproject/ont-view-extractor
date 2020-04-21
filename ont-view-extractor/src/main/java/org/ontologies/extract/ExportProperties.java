package org.ontologies.extract;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

import org.apache.log4j.Logger;

public class ExportProperties {

	private static transient Logger log = Logger.getLogger(ExportProperties.class);

	public static final String PROPERTY_FILE_NAME = "export.properties";

	public static final String SOURCE_ONTOLOGY_FILE = "source.ontology.file";
	public static final String TARGET_ONTOLOGY_FILE = "target.ontology.file";
	public static final String TARGET_ONTOLOGY_NAME = "target.ontology.name";
	public static final String TOP_CLASSES ="export.top.classes";
	public static final String EXPORT_CSV_FILE = "export.csv.file";
	
	public static final String APPEND_PROPERTY = "append.existing.ontology";
	public static final String EXPORT_ANNOTATIONS_ON_ANNOTATIONS = "export.annotations.on.annotations";
	
	
	public static final String ANNOTATION_PROPERTIES_TO_EXPORT = "export.annotation.properties";

	public static final String LOG_COUNT_PROPERTY = "log.count";
	public static final String SAVE_COUNT_PROPERTY = "save.count";

	
	private static Collection<String> topClasses;
	private static Collection<String> exportProps;
	

	private static Properties p = new Properties();
	static {
		try {
			p.load(new FileInputStream(new File(PROPERTY_FILE_NAME)));
		} catch (IOException ioe) {
			log.error("Could not load properties file " + PROPERTY_FILE_NAME, ioe);
		}
	}

	public static String getTargetOntologyFileLocation() {
		return p.getProperty(TARGET_ONTOLOGY_FILE);
	}
	
	public static String getSourceOntologyFileLocation() {
		return p.getProperty(SOURCE_ONTOLOGY_FILE);
	}

	public static String getTargetOntologyName() {
		return p.getProperty(TARGET_ONTOLOGY_NAME);
	}
	
	public static String getExportCSVFileLocation() {
		return p.getProperty(EXPORT_CSV_FILE);
	}

	public static boolean getAppendOntologyFile() {
		String appendPropertyValue = p.getProperty(APPEND_PROPERTY);
		return !(appendPropertyValue == null || !appendPropertyValue.toLowerCase().equals("true"));
	}

	public static Collection<String> getExportProperties() {
		if (exportProps == null) {
			String allProps = p.getProperty(ANNOTATION_PROPERTIES_TO_EXPORT);
			if (allProps == null) {
				return new ArrayList<String>();
			}
			String[] allPropsArray = allProps.split(",");
			exportProps = Arrays.asList(allPropsArray);
		}
		return exportProps;
	}
	
	public static Collection<String> getTopClasses() {
		if (topClasses == null) {
			String allProps = p.getProperty(TOP_CLASSES);
			if (allProps == null) {
				return new ArrayList<String>();
			}
			String[] allPropsArray = allProps.split(",");
			topClasses = Arrays.asList(allPropsArray);
		}
		return topClasses;
	}
	
	public static boolean getExportAnnotationsOnAnnotations(boolean defaultValue) {
		String c = p.getProperty(EXPORT_ANNOTATIONS_ON_ANNOTATIONS);
		if (c == null) {
			return defaultValue;
		}
		boolean exportAnnotations = defaultValue;
		try {
			exportAnnotations = Boolean.parseBoolean(c);
		} catch (Throwable e) {
		}
		return exportAnnotations;
	}

	public static int getLogCount(int defaultValue) {
		String c = p.getProperty(LOG_COUNT_PROPERTY);
		if (c == null) {
			return defaultValue;
		}
		int count = 0;
		try {
			count = Integer.parseInt(c);
		} catch (Throwable e) {
		}
		return count;
	}

	public static int getSaveCount(int defaultValue) {
		String c = p.getProperty(SAVE_COUNT_PROPERTY);
		if (c == null) {
			return defaultValue;
		}
		int count = 0;
		try {
			count = Integer.parseInt(c);
		} catch (Throwable e) {
		}
		return count;
	}

}
