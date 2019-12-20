package org.logical.defs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.fma.icd.map.StringUtils;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import edu.stanford.smi.protege.model.Project;
import edu.stanford.smi.protegex.owl.model.OWLModel;
import edu.stanford.smi.protegex.owl.model.RDFSNamedClass;

public class CheckPrePostCoordination {

	private static transient Logger log = Logger.getLogger(CheckPrePostCoordination.class);


	private OWLModel owlModel;
	private PrePostCoordinationUtils pcUtils;

	private BufferedWriter resultCSVWriter;
	private BufferedReader inputCSVReader;

	public CheckPrePostCoordination(OWLModel owlModel, BufferedWriter bufferedWriter, BufferedReader bufferedReader) {
		this.owlModel = owlModel;
		this.pcUtils = new PrePostCoordinationUtils(owlModel);
		this.resultCSVWriter = bufferedWriter;
		this.inputCSVReader = bufferedReader;
	}

	public void export() throws IOException {

		matchClasses();
	}

	private void matchClasses() throws IOException {
		log.info("Starting check..");

		int count = 0;
		String row = null;
		try {
			while ((row = inputCSVReader.readLine()) != null) {
				processRow(row);

				count++;
				if (count % 100 == 0) {
					log.info("Processed " + count + " lines.");
				}
			}
		} catch (IOException e) {
			log.error("IO Exception at processing row: " + row, e);
		}

		resultCSVWriter.flush();
		resultCSVWriter.close();
		inputCSVReader.close();

		log.info("End check.");
	}

	private void processRow(String row) {
		try {
			String[] data = row.split(StringUtils.COL_SEPARATOR);

			if (data.length < 3) {
				log.warn("Ignoring row because incomplete: " + row);
				return;
			}

			String childId = data[0];
			String chapterXId = data[1];
			String parentId = data[2];
			
			RDFSNamedClass childCls = owlModel.getRDFSNamedClass(childId);
			RDFSNamedClass parentCls = owlModel.getRDFSNamedClass(parentId);
			RDFSNamedClass chapterXCls = owlModel.getRDFSNamedClass(chapterXId);
			
			String postCoordResult = pcUtils.checkPostCoordinationValues(chapterXCls, parentCls);
			String logDefResult = pcUtils.checkLogicalDefinition(childCls, chapterXCls, parentCls);
			
			writeLine(row, postCoordResult, logDefResult);

		} catch (Exception e) {
			log.error("Error at processing row: " + row, e);
		}

	}


	private void writeLine(String row, String pcRes, String logDefResult) {
		try {
			resultCSVWriter.write(row + StringUtils.COL_SEPARATOR +
								  StringUtils.toCsvField(pcRes) + StringUtils.COL_SEPARATOR + 
								  StringUtils.toCsvField(logDefResult));
			resultCSVWriter.newLine();
		} catch (IOException ioe) {
			log.error("Could not export line for: " + row);
		}
	}
	

	public static void main(String[] args) throws OWLOntologyCreationException, IOException {
		if (args.length < 3) {
			log.error("Needs 3 params: (1) ICD PPRJ file, (2) lexical and parent child icd chapterx mapping CSV file, "
					+ "and (3) output CSV file");
			return;
		}

		String fileName = args[0];
		List errors = new ArrayList();
		Project prj = Project.loadProjectFromFile(fileName, errors);
		if (errors.size() > 0) {
			log.error("There were errors at loading project: " + fileName);
			System.exit(1);
		}

		OWLModel owlModel = (OWLModel) prj.getKnowledgeBase();

		BufferedReader inputCSVReader = new BufferedReader(new FileReader(new File(args[1])));
		BufferedWriter resultCSVWriter = new BufferedWriter(new FileWriter(new File(args[2])));

		CheckPrePostCoordination exporter = new CheckPrePostCoordination(owlModel, resultCSVWriter, inputCSVReader);
		exporter.export();

	}

}
