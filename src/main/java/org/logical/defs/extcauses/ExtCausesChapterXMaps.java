package org.logical.defs.extcauses;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtCausesChapterXMaps {
	
	//stores the External Cause top class to suitable Chapter X top class for log def;
	//usually it is Ext Cause -> "Aspect of Ext Cause"
	public static final Map<String, List<String>> extcaus2chapterx = new HashMap<String, List<String>>() {{
		
		//Armed conflict -> Aspects of armed conflict
		put("http://id.who.int/icd/entity/2143219175",
				Arrays.asList("http://id.who.int/icd/entity/699071140", 
							  "http://id.who.int/icd/entity/517417642"));
		
		//Assault -> Aspects of asault and maltreatment
		put("http://id.who.int/icd/entity/73322695",
				Arrays.asList("http://id.who.int/icd/entity/814211882", 
							 // "http://id.who.int/icd/entity/665815018", //Gender of perpetrator
							  "http://id.who.int/icd/entity/1115077034"));
		
		//Intentional self-harm -> Aspects of intentional self-harm
		put("http://id.who.int/icd/entity/851395624",
				Arrays.asList("http://id.who.int/icd/entity/1754695847",
							  "http://id.who.int/icd/entity/1432280426",
							  "http://id.who.int/icd/entity/1814458410"));
		
		//Maltreatment -> Aspects of assault and maltreatment
		put("http://id.who.int/icd/entity/491063206", 
				Arrays.asList("http://id.who.int/icd/entity/814211882", 
						     // "http://id.who.int/icd/entity/665815018", //Gender of perpetrator
						      "http://id.who.int/icd/entity/1115077034"));
		
		//'Causes of healthcare related harm or injury' -> 'Health Devices, Equipment and Supplies'
		put("http://id.who.int/icd/entity/558785723", 
				Arrays.asList("http://id.who.int/icd/entity/1597357976"));
		
	}};
	
	public static final List<String> genericTopXAxes = new ArrayList<>(Arrays.asList(
		//'Aspects of place of injury occurrence'
		//"http://id.who.int/icd/entity/1241761605", //taking it out, because it produces bad matching
		
		//'Objects or living things involved in causing injury'
		"http://id.who.int/icd/entity/632148635"
	));
	
	public static final List<String> allTopXAxes = new ArrayList<>(Arrays.asList(
		"http://id.who.int/icd/entity/847623771", //mechanism
		"http://id.who.int/icd/entity/632148635", //obj causing injury
		"http://id.who.int/icd/entity/1321407960", //substances
		"http://id.who.int/icd/entity/2037136197", //traffic
		"http://id.who.int/icd/entity/1463612101", //traffic 
		"http://id.who.int/icd/entity/594925588",  //traffic
		"http://id.who.int/icd/entity/1075090949", //traffic
		"http://id.who.int/icd/entity/1465681947",
		"http://id.who.int/icd/entity/1847862619",
		"http://id.who.int/icd/entity/699071140",
		"http://id.who.int/icd/entity/517417642",
		"http://id.who.int/icd/entity/814211882",
		//"http://id.who.int/icd/entity/665815018", //Gender of perpetrator
		"http://id.who.int/icd/entity/1115077034",
		"http://id.who.int/icd/entity/1502396257",
		"http://id.who.int/icd/entity/1754695847",
		"http://id.who.int/icd/entity/1432280426",
		"http://id.who.int/icd/entity/1814458410",
		"http://id.who.int/icd/entity/1950392441",
		"http://id.who.int/icd/entity/1295519033",
		"http://id.who.int/icd/entity/1786773723",
		"http://id.who.int/icd/entity/1081404635",
		"http://id.who.int/icd/entity/366561373",
		"http://id.who.int/icd/entity/1879078429",
		"http://id.who.int/icd/entity/1730679517",
		"http://id.who.int/icd/entity/1582350237",
		"http://id.who.int/icd/entity/2030846363",
		"http://id.who.int/icd/entity/167726698"
	));
	
	public static final List<String> excludedTopClsesFromExport = new ArrayList<>(Arrays.asList(
			"http://id.who.int/icd/entity/558785723" //'Causes of healthcare related harm or injury'
	));
}
