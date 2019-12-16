package org.fma.icd.map;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

public class CreateNeo4JGraph {

	private static transient Logger log = Logger.getLogger(CreateNeo4JGraph.class);

	public static final String GR_NAME_PROP = "name";
	public static final String GR_PREF_LABEL_PROP = "prefLabel";
	public static final String GR_ICD_MAP = "icdMap";
	public static final String GR_PROP_TYPE = "relProp";

	private enum RelTypes implements RelationshipType {
		IS_A, PART_OF, MAP, CONST_PART_OF, MEMBER_OF, REG_PART_OF, BRANCH_OF, TRIBUTARY_OF
	}

	public static final Map<String, RelTypes> FMA_PROP_2_REL = new HashMap<String, RelTypes>() {
		{
			put("http://purl.org/sig/ont/fma/part_of", RelTypes.PART_OF);
			put("http://purl.org/sig/ont/fma/constitutional_part_of", RelTypes.CONST_PART_OF);
			put("http://purl.org/sig/ont/fma/member_of", RelTypes.MEMBER_OF);
			put("http://purl.org/sig/ont/fma/regional_part_of", RelTypes.REG_PART_OF);
			put("http://purl.org/sig/ont/fma/branch_of", RelTypes.BRANCH_OF);
			put("http://purl.org/sig/ont/fma/tributary_of", RelTypes.TRIBUTARY_OF);
		}
	};

	public static final String ICD_MAPPED_PROP_NAME = "http://purl.org/sig/ont/fma.owl#ICDMap";
	public static final String FMA_MAPPED_PROP_NAME = "http://who.int/icd_flattened/anatomy#FMAmap";
	

	public static final String NEO4J_DB_FOLDER = "/Users/ttania/work/neo4jdb/icdfma.db";
	
	public static final String FMA_ONT_PATH = "/Users/ttania/work/projects/icd-fma-mapping/_running/fma_5.0.0-functional.owl";
	public static final String ICD_ONT_PATH = "/Users/ttania/work/projects/icd-fma-mapping/_running/icd-anatomy.owl";
	
	private static OWLOntologyManager man;
	private static OWLDataFactory df;

	private static GraphDatabaseService graphDb;

	private static List<OWLProperty> props = new ArrayList<>();

	private static Map<OWLClass, Node> cls2node = new HashMap<OWLClass, Node>();

	private static int count = 0;

	public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException {
		man = OWLManager.createOWLOntologyManager();
		df = man.getOWLDataFactory();
		props = createPropList(FMA_PROP_2_REL.keySet());

		graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(new File(NEO4J_DB_FOLDER));
		registerShutdownHook(graphDb);

		OWLOntology icdOnt = man.loadOntologyFromOntologyDocument(new File(ICD_ONT_PATH));
		log.info("Loaded the ICD ontology");
		generateGraphNodes(icdOnt, "ICD");
		
		count = 0;
		
		OWLOntology fmaOnt = man.loadOntologyFromOntologyDocument(new File(FMA_ONT_PATH));
		log.info("Loaded the FMA ontology");
		generateGraphNodes(fmaOnt, "FMA");
		
		log.info("Started the generation of ICD-FMA maps ..");
		generateIcdFmaMap(icdOnt, fmaOnt);
		log.info("Finished the generation of ICD-FMA maps");
		
		graphDb.shutdown();

	}
	


	private static void generateGraphNodes(OWLOntology ont, String ontName) {
		log.info("Started creating " + ontName + " classes ..");
		createClasses(ont, ontName);
		log.info("Created " + count + " " + ontName + " classes");
		
		log.info("Started creating " + ontName + " relations ..");
		createRels(ont);
		log.info("Finished creating " + ontName + " relations.");
	}
	

	private static void createClasses(OWLOntology ont, String ontName) {
		ont.classesInSignature().forEach(c -> createCls(ont, ontName, c));
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
		String rdfsLabelVal = OWLAPIUtil.getStringAnnotationValue(ont, cls,
				df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()));
		if (rdfsLabelVal != null) {
			clsNode.setProperty(GR_PREF_LABEL_PROP, rdfsLabelVal);
		}
	
		/*
		String[] icdMaps = getStringAnnotationValues(ont, cls,
				man.getOWLDataFactory().getOWLAnnotationProperty(ICD_MAPPED_PROP_NAME));
		if (icdMaps != null) {
			clsNode.setProperty(GR_ICD_MAP, icdMaps);
		}
		*/
	}

	private static void createRels(OWLOntology ont) {
		ont.classesInSignature().forEach(c -> createSuperAndRelClses(ont, c, cls2node.get(c)));
	}

	private static void createSuperAndRelClses(OWLOntology ont, OWLClass cls, Node clsNode) {
		List<OWLClassExpression> superclses = OWLAPIUtil.getSuperclses(ont, cls);
		for (OWLClassExpression supercls : superclses) {
			if (supercls instanceof OWLClass) { // proper parent
				addSuperNode(cls, clsNode, (OWLClass) supercls);
			} else if (supercls instanceof OWLObjectSomeValuesFrom) { // find the partOf
				OWLProperty prop = (OWLProperty) ((OWLObjectSomeValuesFrom) supercls).getProperty(); // not safe, but it
																										// should work
				if (isPropertyOfInterest(prop) == true) {
					OWLClassExpression fillerEx = ((OWLObjectSomeValuesFrom) supercls).getFiller();
					if (fillerEx.isOWLClass() == true) {
						addRelNode(cls, clsNode, prop, (OWLClass) fillerEx);
					}
				}
			}
		}
	}

	private static void addRelNode(OWLClass cls, Node clsNode, OWLProperty prop, OWLClass filler) {
		Node fillerNode = cls2node.get(filler);
		if (fillerNode == null) {
			log.error("Error at creating relations for " + cls + ". Could not find node for " + filler );
			return;
		}

		try (Transaction tx = graphDb.beginTx()) {
			Relationship rel = clsNode.createRelationshipTo(fillerNode, RelTypes.PART_OF);
			rel.setProperty(GR_PROP_TYPE, FMA_PROP_2_REL.get(prop.getIRI().toString()).name());

			tx.success();
		} catch (Exception e) {
			log.warn("Exception at adding rel for class " + cls + " prop:" + prop + "filler: " + filler, e);
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

	private static List<OWLProperty> createPropList(Set<String> propNames) {
		return propNames.stream().map(x -> df.getOWLObjectProperty(x)).collect(Collectors.toList());
	}

	private static boolean isPropertyOfInterest(OWLProperty prop) {
		return props.contains(prop);
	}
	
	
	private static void generateIcdFmaMap(OWLOntology icdOnt, OWLOntology fmaOnt) {
		OWLAnnotationProperty fmaMapProp = df.getOWLAnnotationProperty(FMA_MAPPED_PROP_NAME);
		Iterator<OWLClass> it = icdOnt.classesInSignature().iterator();
		while (it.hasNext()) {
			OWLClass icdCls = it.next();
			String[] anns = OWLAPIUtil.getStringAnnotationValuesArray(icdOnt, icdCls, fmaMapProp);
			if (anns != null) {
				for (String ann : anns) {
					createFmaIcdMap(df.getOWLClass(ann), icdCls);
				}
			}
		}
	}
	

	private static void createFmaIcdMap(OWLClass fmaCls, OWLClass icdCls) {
		Node fmaNode = cls2node.get(fmaCls);
		if (fmaNode == null) {
			log.warn("Could not find node for FMA class " + fmaCls);
			return;
		}
		
		Node icdNode = cls2node.get(icdCls);
		if (icdNode == null) {
			log.warn("Could not find node for ICD class " + icdCls);
			return;
		}
		
		try (Transaction tx = graphDb.beginTx()) {
			fmaNode.createRelationshipTo(icdNode, RelTypes.MAP);
			
			tx.success();
		} catch (Exception e) {
			log.warn("Exception at creating FMA-ICD map for " + fmaCls + " and " + icdCls, e);
		}
	}


	private static void logClassCount() {
		if (count % 1000 == 0) {
			log.info("Created " + count + " classes");
		}
	}
	
	
	private static void registerShutdownHook(GraphDatabaseService graphDb) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				graphDb.shutdown();
			};
		});
	}

}
