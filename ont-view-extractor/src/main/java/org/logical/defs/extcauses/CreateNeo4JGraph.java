package org.logical.defs.extcauses;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.log4j.Logger;
import org.fma.icd.map.OWLAPIUtil;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;


public class CreateNeo4JGraph {
	private static transient Logger log = Logger.getLogger(CreateNeo4JGraph.class);
	
	public static final String GR_NAME_PROP = "name";
	public static final String GR_PREF_LABEL_PROP = "prefLabel";
	
	private enum RelTypes implements RelationshipType {
		IS_A, FILLER, MECH
	}
	
	public static final String NEO4J_DB_FOLDER = "/Users/ttania/work/neo4jdb/icd_ext_causes_fillers.db";
	public static final String ICD_ONT_PATH = "/Users/ttania/work/projects/icd-fma-mapping/logical-defs-ext-causes/_running/icd-external-causes-with-fillers.owl";
	
	private static OWLOntologyManager man;
	private static OWLDataFactory df;
	private static OWLReasoner reasoner;

	private static GraphDatabaseService graphDb;

	private static Map<OWLClass, Node> cls2node = new HashMap<OWLClass, Node>();
	
	private static int count = 0;
	
	public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException {
		man = OWLManager.createOWLOntologyManager();
		df = man.getOWLDataFactory();

		graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(new File(NEO4J_DB_FOLDER));
		registerShutdownHook(graphDb);

		OWLOntology ont = man.loadOntologyFromOntologyDocument(new File(ICD_ONT_PATH));
		reasoner = initReasoner(ont);
		
		log.info("Generate External Causes tree");
		generateGraphNodes(ont, "ExtCauses", df.getOWLClass(ExtCausesConstants.CHAPTER_EXT_CAUSES_ID));
		
		log.info("Generate Chapter X tree");
		generateGraphNodes(ont, "ChapterX", df.getOWLClass(ExtCausesConstants.CHAPTER_X_ID));
		
		log.info("Started the generation of maps ..");
		generateMaps(ont, df.getOWLClass(ExtCausesConstants.CHAPTER_EXT_CAUSES_ID));
		log.info("Finished the generation of maps");
		
		graphDb.shutdown();

	}
	
	
	private static void generateGraphNodes(OWLOntology ont, String ontName, OWLClass topCls) {
		log.info("Started creating " + ontName + " classes ..");
		createClasses(ont, ontName, topCls);
		log.info("Created " + count + " " + ontName + " classes");
		
		log.info("Started creating " + ontName + " relations ..");
		createRels(ont, topCls);
		log.info("Finished creating " + ontName + " relations.");
	}
	

	private static void createClasses(OWLOntology ont, String ontName, OWLClass topCls) {
		createCls(ont, ontName, topCls);
		for (OWLClass cls : getAllClses(ont, topCls)) {
			createCls(ont, ontName, cls);
		}
	}

	private static Node createCls(OWLOntology ont, String ontName, OWLClass cls) {
		Node clsNode = null;

		try (Transaction tx = graphDb.beginTx()) {
			clsNode = createNode(cls, ontName);
			cls2node.put(cls, clsNode);
			
			addAnnotations(ont, cls, clsNode);
			
			count++;
			logClassCount();
			
			tx.success();
		} catch (Exception e) {
			log.warn("Exception at creating class " + cls + ", " + e.getMessage(), e);
		}

		return clsNode;
	}

	private static Node createNode(OWLClass cls, String ontName) {
		Node clsNode = null;

		clsNode = graphDb.createNode();
		clsNode.addLabel(Label.label(ontName + "Class"));
		clsNode.setProperty(GR_NAME_PROP, cls.getIRI().toString());

		return clsNode;
	}

	private static void addAnnotations(OWLOntology ont, OWLClass cls, Node clsNode) {
		String prefLabel = OWLAPIUtil.getSKOSPrefLabelValue(ont, cls);
		if (prefLabel != null) {
			clsNode.setProperty(GR_PREF_LABEL_PROP, prefLabel);
		}
	}

	private static void createRels(OWLOntology ont, OWLClass topCls) {
		for (OWLClass cls : getAllClses(ont, topCls)) {
			createSuperAndRelClses(ont, cls, cls2node.get(cls));
		}
	}

	private static void createSuperAndRelClses(OWLOntology ont, OWLClass cls, Node clsNode) {
		Set<OWLClass> superclses = OWLAPIUtil.getNamedSuperclasses(cls, ont, reasoner, true);
		for (OWLClass supercls : superclses) {
			addSuperNode(cls, clsNode, (OWLClass) supercls);
		}
	}

	private static void addMechanism(OWLOntology ont, OWLClass cls) {
		IRI mechIri = OWLAPIUtil.getIRIAnnotationValue(ont, cls.getIRI(), df.getOWLAnnotationProperty(ExtCausesConstants.MECHANISM_PROP));
		if (mechIri == null) {
			return;
		}
		
		Node clsNode = cls2node.get(cls);
		
		Node mechNode = cls2node.get(df.getOWLClass(mechIri));
		
		if (mechNode == null) {
			log.warn("Could not find mechanism node for " + cls);
			return;
		}
		
		try (Transaction tx = graphDb.beginTx()) {
			clsNode.createRelationshipTo(mechNode, RelTypes.MECH);
			tx.success();
		} catch (Exception e) {
			log.warn("Exception at adding mechanism for class " + cls, e);
		}
		
	}


	private static void addSuperNode(OWLClass cls, Node clsNode, OWLClass supercls) {
		Node superNode = cls2node.get(supercls);
		if (superNode == null) {
			log.error("Error at creating parents for " + cls + ". Could not find node for " + supercls );
			return;
		}
		try (Transaction tx = graphDb.beginTx()) {
			clsNode.createRelationshipTo(superNode, RelTypes.IS_A);
			tx.success();
		} catch (Exception e) {
			log.warn("Exception at adding supercls for class " + cls + " supercls: " + supercls, e);
		}
	}
	
	
	
	private static void generateMaps(OWLOntology ont, OWLClass topCls) {
		OWLAnnotationProperty fillerProp = df.getOWLAnnotationProperty(ExtCausesConstants.FILLER_PROP);
		Iterator<OWLClass> it = getAllClses(ont, topCls).iterator();
		
		
		while (it.hasNext()) {
			OWLClass cls = it.next();
			
			addMechanism(ont, cls);
			
			//add fillers
			Stream<OWLAnnotationAssertionAxiom> anns = ont.annotationAssertionAxioms(cls.getIRI());
			if (anns != null) {
				anns.filter(annAss -> annAss.getProperty().equals(fillerProp)).forEach(ann -> map(cls, ann));
			}
		}
	}
	
	
	private static void map(OWLClass cls, OWLAnnotationAssertionAxiom annAss) {
		IRI xmap = annAss.annotationValue().asIRI().get();
		IRI topXCls = annAss.annotations(df.getOWLAnnotationProperty(ExtCausesConstants.MATCH_X_TOP_CLS_PROP)).findFirst().get().
			annotationValue().asIRI().get();
		createMap(cls, df.getOWLClass(xmap), df.getOWLClass(topXCls));
	}


	private static void createMap(OWLClass cls, OWLClass xCls, OWLClass topxCls) {
		Node clsNode = cls2node.get(cls);
		if (clsNode == null) {
			log.warn("Could not find node " + cls);
			return;
		}
		
		Node xClsNode = cls2node.get(xCls);
		if (xClsNode == null) {
			log.warn("Could not find node " + xCls);
			return;
		}
		
		
		try (Transaction tx = graphDb.beginTx()) {
			Relationship rel = clsNode.createRelationshipTo(xClsNode, RelTypes.FILLER);
			
			rel.setProperty("topXParent", topxCls.getIRI().toString());
			
			tx.success();
		} catch (Exception e) {
			log.warn("Exception at creating map for " + cls + " and " + xCls, e);
		}
	}

	
	private static void logClassCount() {
		if (count % 1000 == 0) {
			log.info("Created " + count + " classes");
		}
	}
	
	
	private static Set<OWLClass> getAllClses(OWLOntology ont, OWLClass topCls) {
		Set<OWLClass> subclses = OWLAPIUtil.getNamedSubclasses(topCls, ont, reasoner, false);
		//remove "Causes of healthcare related harm
		OWLClass healthcareharmCls = df.getOWLClass("http://id.who.int/icd/entity/558785723");
		subclses.remove(healthcareharmCls);
		subclses.removeAll(OWLAPIUtil.getNamedSubclasses(healthcareharmCls, ont, reasoner, false));
		return subclses;
	}
	
	
	private static OWLReasoner initReasoner(OWLOntology ontology) {
		OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
		OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
		return reasoner;
	}
	
	private static void registerShutdownHook(GraphDatabaseService graphDb) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				graphDb.shutdown();
			};
		});
	}

}
