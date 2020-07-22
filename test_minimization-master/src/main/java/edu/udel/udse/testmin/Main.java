
package edu.udel.udse.testmin;

import edu.udel.elib.Site;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;

import javax.swing.plaf.basic.BasicSplitPaneUI.KeyboardEndHandler;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;

import java.util.Properties;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;

public class Main {

	private static HashSet<String> subjectAppSites;
	private static HashMap<Site, List<String>> sitesCoverageMap; // coverage map for app sites
	private static List<TestCaseApp> testCases; // test cases with execution time
	private static HashSet<String> stmt_list; // list of statements in app (does not include conditional nodes)
	private static File app_main_dir;
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
	
	private static HashMap<String, List<TestCaseApp>> coverageMap; 	// map for app LOC coverage 
	private static HashMap<String, TestCaseApp> mapTestCases; // test cases map for ID, test case obj
	
	private static boolean verbose = true;
	private static boolean verbose_tc = true; //details about testCaseApp tests creation?
	private static boolean verbose_ilp = true; //details about ILP formulation

    private static String app_path;
    private static String maven_cmd;
    private static String build_dir;
    private static String test_dir;
    private static boolean absTMCriteria = false; // test minimization criteria: absolute (exe. time) or relative (coverage only), default is relative
	private static File file_testcases;
	
	public static void main(String[] args) throws SAXException, IOException, ParserConfigurationException{

        app_path=null;
        build_dir="bin";
        maven_cmd="mvn.cmd";
       

		if(args.length < 1 || args==null){
			//System.err.println("Missing input parameters for Test Minimization");

            Properties appProperties = new Properties();
            FileInputStream in = new FileInputStream("test_min.properties");
            
            try{
            	appProperties.load(in);
            	in.close();
            }catch(IOException e){
            	 LOGGER.error("Error with test_min.properties file");
            	 e.printStackTrace();
            }

            file_testcases = new File(appProperties.getProperty("subject.app.testcases.file"));
            app_path = appProperties.getProperty("subject.app.homedir");
            build_dir = appProperties.getProperty("subject.app.builddir");
            maven_cmd = appProperties.getProperty("maven.command");
            test_dir = appProperties.getProperty("subject.app.testdir");
	    absTMCriteria = Boolean.parseBoolean(appProperties.getProperty("test.min.criteria"));
            

           if(app_path==null){
                LOGGER.info("Missing 'subject.application.homedir' in test_min.properties file");

           }else if(!file_testcases.exists()){
        	   LOGGER.info("Missing 'subject.app.testcases.file' in test_min.properties file");
           }else{
                LOGGER.info("Using {} as subject.app.homedir", app_path);
                
                if(build_dir==null)
                    build_dir="bin";

                if(maven_cmd==null)
                    maven_cmd="/usr/local/bin/mvn.cmd";

                if(test_dir==null)
                    test_dir="test";

                LOGGER.info("Using {} as compiled sources directory", build_dir);
                LOGGER.info("Using {} as test directory", test_dir);
                LOGGER.info("Using {} maven command", maven_cmd);
                LOGGER.info("Using {} test cases list", file_testcases.getName());
                LOGGER.info("Using {} test minimization criteria", absTMCriteria);
           }

        }

        if(app_path==null){
            if(args[0]==null){
			    System.err.println("Missing path application's main directory in Test Minimization");
			    return;
            }else{
                app_path = args[0];
            }
            
            if(args[1]==null){
			    System.err.println("Missing path test cases file");
			    return;
            }else{
            	file_testcases =  new File(args[1]);
            }
		}
		
		//path subject app directory
		setPathSubjectApp(new File(app_path));
		if(app_main_dir!=null)
			testCases = setTestCases(file_testcases);
		
		// if information about app sites is available
		if(args.length>1 && !args[1].equals(""))
			subjectAppSites = (HashSet<String>) getSitesSet(args[1]);
		 
		
		//list of app statements
		stmt_list = new HashSet<String>();
		
		if(testCases!=null && app_main_dir!=null ){

			analyzeCoverageForTestCases(testCases, app_main_dir, absTMCriteria);
			
			printTestSuiteCoverage();
			
			// if verbose_ilp print constraints used for Integer Linear Programming formulation of the test minimization problem
			getListOfConstraintsForILP();
			
			//print the whole formulation of the test min. problem as ILP
			//System.out.println(getGeneralILPFormulation(false)); // relative criteria: consider only coverage 
            System.out.println(getILPFormulation()); // absolute criteria: consider exe. time of tests
						
			System.out.println("LPSolve solution:\n =================");
			//use lpsolve to find solution
			String res = executeLPSolve("./res/lp_solve res/test_suite_ILP", new File("."));
			
			printMinimizedTestSuite(res);
			
		}else{
			System.err.println(Main.class.getName()+" Incomplete input parameters for analyzing test cases coverage");
            LOGGER.error("Input Parameter at index 0: {}", args[0]);
        }

	}

	public static boolean printMinimizedTestSuite(String res) {

		boolean suc = false;
		
		if(res==null || res.length()<6){
			LOGGER.error("something went wrong with LPSolve results");
			return suc;
		}
		
		System.out.println("Tests in Minimized Test Suite:");
		
		String[] lines = res.split("\n");
		for(String line : lines){
			if(line.startsWith("t")){
				String sol = line.replaceAll(" ", "");
				String ans = sol.substring(sol.length()-1, sol.length());
				String testID = sol.substring(0, sol.length()-1);
				if(Integer.valueOf(ans).intValue()>0){
					TestCaseApp tc = mapTestCases.get(testID);
					System.out.println(testID + ":\t" + tc.getNamePkg() + "." 
										+ tc.getNameFile()+"#"+ tc.getName());
				}
			}
		}
		
		return true;
		
	}
	

	/**
	 * Prints out test minimization problem formuled as ILP
	 * with relative criteria when @param absCriteria is false.
     * Relative Criteria get the set of test cases that have 
	 * the same coverage that the original test suite.
	 * @param absCriteria true if absolute criteria wants to be consider.
     * Absolute Criteria get set of test cases considering minimum execution
     * time of the test suite.
	 * 
	 * */
	private static String getGeneralILPFormulation(boolean absCriteria) {

		System.out.println("\n ================== \n Printing ILP Formulation: \n");
		HashMap<String, Set<String>> mapConsList = getStmtsCoveredByTestSuite();
		int count = 1;
		
		StringBuffer problemDef = new StringBuffer();
		StringBuffer objFnc = new StringBuffer();
		StringBuffer vbles = new StringBuffer();
		
		vbles.append("bin ");
		objFnc.append("min: ");
		
		for(TestCaseApp test: testCases){
			if(absCriteria){
				double time = test.getExec_time();
				System.out.println("==> Getting exec time for test: "+test.getID()+" is:"+ time);
				objFnc.append(test.getExec_time());
				objFnc.append("*");
            }
			String id = test.getID();
			objFnc.append(id);
			objFnc.append(" + ");
			vbles.append(id);
			vbles.append(", ");
			
		}
		
		vbles.delete(vbles.length()-2,vbles.length());
		vbles.append(";");
		
		objFnc.delete(objFnc.length()-3, objFnc.length());
		objFnc.append(";");
		
		
		problemDef.append(objFnc.toString());
		problemDef.append("\n");

		//System.out.println(objFnc.toString()+"\n");
		objFnc = null;
		StringBuffer constDef = new StringBuffer();

		for(Entry<String, Set<String>> entry: mapConsList.entrySet()){
			
			String stmt = entry.getKey();
			constDef = new StringBuffer();
			
			for(String idTC : entry.getValue()){
				constDef.append(idTC);
				constDef.append("+");
			}
			
			constDef.deleteCharAt(constDef.length()-1);
			constDef.append(" >= 1");
			constDef.append(";");
			
			String constStmt = constDef.toString();
			//System.out.println("s"+count+": "+constStmt);
			problemDef.append("s"+count+": "+constStmt);
			problemDef.append("\n");
			count++;
		}
	
		constDef = null;
		
		problemDef.append(vbles.toString());
		problemDef.append("\n");
		vbles = null;

		String def = problemDef.toString();
		
		File file = new File("res/test_suite_ILP");
		
		PrintWriter pWriter;
		try {
			pWriter = new PrintWriter(file);
			pWriter.print(def);
			pWriter.flush();
			pWriter.close();
			
		} catch (IOException e) {
			LOGGER.error("Cannot write test suite min. problem in ILP format");
			e.printStackTrace();
		}
		
		pWriter = null;
		return def;
		
	}
	
	/**
	 * Prints out test minimization problem formuled as ILP with 
	 * absolute criteria as to get the set of test cases with the minimum execution time (obj function) 
	 * */
	private static String getILPFormulation() {

		return getGeneralILPFormulation(true);
	}

	/**
	 * Print in the console the list of statements along with the list of test
	 * cases that cover each statement.
	 * */
	private static void getListOfConstraintsForILP() {

		if(verbose_ilp)
			System.out.println("Test Cases coverage by Statements:\n");
			
		HashMap<String, Set<String>> mapStmts = getStmtsCoveredByTestSuite();
		
		for(Entry<String, Set<String>> entry: mapStmts.entrySet()){
			
			//System.out.print(entry.getKey() + ":");
			
			StringBuffer list = new StringBuffer();
			for(String idTC : entry.getValue()){
				list.append(idTC);
				list.append(",");
			}
			
			list.deleteCharAt(list.length()-1);
			
			if(verbose_ilp)
				System.out.println("\t"+list.toString());
		}
		
	}

	/**
	 * @return a map containing as keys the name of the statements in the application
	 * and as values the IDs of the test cases that cover each statement
	 * */
	private static HashMap<String, Set<String>> getStmtsCoveredByTestSuite() {

		HashMap<String, Set<String>> mapStmtTC = new HashMap<>();
		int count = 1;
		
		for(TestCaseApp test : testCases){
			
			HashSet<String> setStmts = (HashSet<String>) test.getSetOfCoveredStmts();
			
			for(String stmt : setStmts){
				
				Set<String> tcSet = mapStmtTC.get(stmt);
				
				if(tcSet==null)
					tcSet = new HashSet<String>();
				
				tcSet.add(test.getID());
				
				mapStmtTC.put(stmt, tcSet);
				
			}
		}
		
		return mapStmtTC;
		
	}

	/**
	 * @path subject application's path
	 * Configure path for the subject application for which the test suite minimization test will be created
	 * */
	public static void setPathSubjectApp(File appPath) {

		app_main_dir = null;

		if(appPath!=null && (appPath.exists() && appPath.isDirectory())){
			app_main_dir = appPath;
            LOGGER.info("Subject application homedir: {}", app_main_dir);
        }else
        	LOGGER.error("Appliction homedir does not exist: {}", appPath);

	}


	/**
	 * @throws ParserConfigurationException 
	 * @throws IOException 
	 * @throws SAXException 
	 * */
	public static void analyzeCoverageForTestCases(List<TestCaseApp> tests, File prjDir, boolean collectTestTime ) throws SAXException, IOException, ParserConfigurationException{

		for(TestCaseApp test : tests){
			if(collectTestTime)
				runTestCase(test, prjDir);

			parseCoverageReport(prjDir, test);					
		}

	}
	
	/**
	 * Run test cases and saves their execution time information
	 * */
	public static void analyzeExecutionTimeTestCases(List<TestCaseApp> tests, File prjDir ){
	
		for(TestCaseApp test : tests){
			//parseCoverageReport(prjDir, test);					
			boolean ran = instrumentTestCase(test, prjDir);

			if(!ran){
				LOGGER.error("Unable to run test case: " + test.getNameFile()+"#"+test.getName());
			}
		}

	}

	/**
	 * print information about coverage of test cases and test suite overall
	 * writes a file in res dir with coverage information
	 * */
	private static void printTestSuiteCoverage() {

		System.out.println("\nCoverage by Test Case in Test Suite:");
		
		File coverFile = new File("res/test_suite_coverage.txt");
		
		TreeSet<String> allStmts = new TreeSet<String>();
		StringBuilder sb = new StringBuilder();
		
		if(stmt_list.size()>0){
			for(TestCaseApp test: testCases){
				Set tcStmtSet = test.getSetOfCoveredStmts();
				sb.append("\t");
				sb.append(test.getName());
				sb.append("--> coverage: ");
				sb.append((double) tcStmtSet.size()/stmt_list.size());
				sb.append("\n");
				
				if(!allStmts.containsAll(tcStmtSet))
					allStmts.addAll(tcStmtSet);
			}		
		}else {
			LOGGER.error("Application statement list is empry");
		}
		
		String coverInfo = sb.toString();
		String coverpercentage = "Coverage by Test Suite: " 
				+ (double) allStmts.size()/stmt_list.size();
		System.out.println(coverInfo + "\n" + coverpercentage);
		
		try {
			PrintWriter pw = new PrintWriter(coverFile);
			pw.println(coverInfo);
			pw.print(coverpercentage);
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			LOGGER.error("Error writing coverage file: "+coverFile.getPath());
			e.printStackTrace();
		}

	}

	/***
	 * @prjDir path to subject app main directory
	 * @path_test String with path to Test case class file
	 * 
	 * Analyze the Clover coverage report for the given test case from a subject application
	 * 
	 * */
	public static void parseCoverageReport(File prjDir, TestCaseApp test)
			throws SAXException, IOException, ParserConfigurationException {

		String fileTC = test.getNameFile();
		
		cleanProjectDirectory(prjDir);  //remove previous generated reports

		// execute clover for test case:
		boolean instrumented = instrumentTestCase(test, prjDir);

		if(!instrumented){
			LOGGER.error("Unable to run and instrument app during test case: " + test.getNameFile()+"#"+test.getName() 
								+"\n removing test case");
			return;
		}
		
		if(verbose)
			LOGGER.info("\nTest Case: "+ fileTC);
		
		String path_report;
		// access clover report
		if(build_dir.equals("target/classes"))
			path_report = prjDir.getPath() + "/target/site/clover/clover.xml";
		else
			path_report = prjDir.getPath() + "/" + build_dir + "/site/clover/clover.xml";
		
		File clover_report = new File(path_report);

		if(!clover_report.exists()){
			System.err.println("Unable access clover report in: "+ path_report);
			return;
		}

		Document report_doc = getCoverageReportDocument(clover_report);

		Element root  = report_doc.getDocumentElement();
		NodeList nodes = root.getChildNodes();

		LOGGER.info("Reading XML Report");

		
		for(int i=0; i< nodes.getLength(); i++){
			Node child = nodes.item(i);
			if (child instanceof Element && child.getNodeName().equals("project")){
				analyzeXMLNode((Element) child, test);
			}
		}
	}

	/**
	 * @param nodeElement node obtained from XML coverage report
	 * @param test tes case instance
	 * get coverage information about node in Document
	 * 
	 * */
	private static int[] analyzeXMLNode(Node nodeElement, TestCaseApp test){
		int countCond[] = new int[2];
		int sumNumCond = 0;
		int sumNumCovCond = 0;
		
		NodeList nodes = nodeElement.getChildNodes();
		
		for(int i=0; i< nodes.getLength(); i++){			
			Node child = nodes.item(i);
			
			if (child instanceof Element){
				Element childElement = (Element) child;
				
				if(child.getNodeName().equals("package")){
					
					int[] count = analyzeXMLNode(child, test);
					//post-traversal
					getMetricsForNode(childElement, count);
					
				}else if(child.getNodeName().equals("file")){
								
					int[] countFileCond = analyzeXMLNode(child, test);
					//post-traversal
					int[] coverage_info = getMetricsForNode(childElement, countFileCond);
					
					sumNumCond += countFileCond[0];
					sumNumCovCond += countFileCond[1];
					
					if(child.getNodeName().equals("file") && coverage_info[0] > 0){
						String file = ((Element) child).getAttribute("name").replace(".java", "");
						test.setCoverageStmts(file, coverage_info[0]);
					}
					
				}else if(child.getNodeName().equals("line")){
					
					int countLOCCond[] = getCoverageInfoForLOC(child, test);
					sumNumCond += countLOCCond[0];
					sumNumCovCond += countLOCCond[1];

				} // end line node
				
			}
			
			countCond[0] = sumNumCond;
			countCond[1] = sumNumCovCond;
		}

	
		return countCond;
	}

	/**
	 * Get coverage information about LOC (stmt)
	 * 
	 * */
	public static int[] getCoverageInfoForLOC(Node child, TestCaseApp test) {
		
		int numCond = 0;
		int numCovCond = 0;
		
		Element childElement = (Element) child;
		String numLOC = childElement.getAttribute("num");
		String typeLOC = childElement.getAttribute("type");
		int countLOC;
		
		if(!typeLOC.equals("cond")){ // cond types are included as stmt types too
			countLOC =  Integer.valueOf(childElement.getAttribute("count")).intValue();

			Element parent =  (Element) child.getParentNode();
			StringBuffer sb = new StringBuffer();
			sb.append(parent.getAttribute("name").replaceAll(".java", ""));
			sb.append(":");
			sb.append(numLOC);
		
			String stmt = sb.toString();
			
			// add stmt to set of stmts for this app:
			stmt_list.add(stmt);
			
			// TO-DO: if type == method then signature attr available
			if (countLOC > 0) { // line was covered
				//System.out.print("covered stmt: "+ stmt +"; called: " + countLOC + " times");
				test.addCoveredStmt(stmt);
				
			}
			
		}else {
			
			numCond = 1; // conditional type
			numCovCond = ( Integer.parseInt(childElement.getAttribute("truecount")) > 0
							|| Integer.parseInt(childElement.getAttribute("falsecount")) > 0) ? 1 : 0;
			
		}
		
		return new int[]{numCond, numCovCond};
	}


	/**
	 * Get the information about the coverage metrics for the given node
	 * of the XML document
	 * @param node XML node from the coverage report
	 * @param numCond number of conditional nodes children of @param node
	 * @return int[] with information about covered elements index 0, and total elements index 1
	 * */
	private static int[] getMetricsForNode(Element node, int numCond[]) {

		if(numCond[0] < 0 )
			numCond[0] = 0;
		
		if(numCond[1] < 0)
			numCond[1] = 0;
		
		Element childElement = (Element) node.getFirstChild().getNextSibling();
		String sep = "";

		String elem = childElement.getAttribute("elements");
		String covElem = childElement.getAttribute("coveredelements");
		int numElem = Integer.valueOf(elem).intValue() - numCond[0];
		int numCoveredElem = Integer.valueOf(covElem).intValue() - numCond[1];

		if(numCoveredElem > 0){
			if(verbose)
				LOGGER.info(node.getNodeName() + "\t"+ node.getAttribute("name") +
					"\t elements: "+ (numElem) + "; coveredelements: "+numCoveredElem 
					+ "; condElements: " + (numCond[0]));
		}
		
		return new int[]{numCoveredElem, numElem};
	}



	/**
	 * Obtain the XML document representation of the Covergae report
	 * */
	public static Document getCoverageReportDocument(File reportDir) throws SAXException, IOException, ParserConfigurationException{

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();

		Document doc = builder.parse(reportDir);

		return doc;
	}

	/**
	 * clean app project directory from previous build
	 * */
	public static boolean cleanProjectDirectory(File dir){

		if(dir==null || !dir.isDirectory()){
			System.err.println("\n cleanProjectDirectory method: Directory "+ dir+" does not exist");
			return false;
		}

		if(dir.exists() && 
				(new File(dir.getPath()+"/pom.xml").exists())){
			// delete build directory
			return executeCommand(maven_cmd + " clean", dir, verbose) == 0 ? false : true ;
		}

		return false;
	}


	/**
	 * Instrument test case execution using coverage tool (clover)
	 * @test name of test case to run and instrument with clover 
	 * @prjDir path to the app directory where the test cases are located
	 * 
	 * */
	public static boolean instrumentTestCase(TestCaseApp test, File prjDir){

		String file = test.getNameFile();

		if(file!=null && !file.isEmpty()){
			String test_file = file.replace(".java", "");

			//TO-DO: check if file for test case exist or no
			if(!traverseDirectoryOfTestCases(new File(app_main_dir.getPath() + "/" + test_dir), file)){
				LOGGER.error("file: " +file +".java, was not found in " + app_main_dir.getPath() + "/" + test_dir);
				return false;
			}

			if(verbose)
				LOGGER.info("Test Case name: "+test.getName());		
			
			double eTime = 0d;
			
			
			eTime =  executeCommand(maven_cmd + " clean clover:setup -Dtest="
					+ test_file + "#" + test.getName() + " test clover:aggregate clover:clover", prjDir, verbose);
			
			return eTime == 0 ? false : true;
		}else
			return false;
		
	}

	/**
	 * runs test case and stores it execution time
	 * @param test Test case representation
	 * @param path to subject application's test cases directory
	 * */
	@Deprecated
	public static double runTestCase(TestCaseApp test, File prjDir) {
		
		String test_file = test.getNameFile().replace(".java", "");

		String cmnd = maven_cmd + " test -Dtest="+ test_file + "#" + test.getName();
		double eTimeTC = executeCommand(cmnd, prjDir, verbose);
		System.out.println("===> Execution time for test: "+ eTimeTC);	
		//update execution time for test case
		test.setExec_time(eTimeTC);
		return eTimeTC;
	}


	/**
	 * @path cmnd represents a command that can be executed from the terminal
	 * @return execution time of the command
	 * Execute command in the terminal/console
	 * */
	public static double executeCommand(String cmnd, File dir, boolean verbose){
		
		Process p = null;
		double iTime = System.nanoTime();
		double eTime = iTime;
		
		try {
			p = Runtime.getRuntime().exec(cmnd, null, dir);
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String l=null;
			while((l=br.readLine())!=null){
				if(verbose)
					System.out.println(l); // read buffer to avoid blocking process
			}
			p.waitFor();

			eTime = System.nanoTime();

			if(p.exitValue()!=0){
				p.destroy();
				eTime = iTime;
			}

		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally{
			if(p!=null)
				p.destroy();
		}

		double exeTimeSec = (eTime-iTime)/1.0e9; // in seconds
		
		LOGGER.info("\n exeTime: "+ exeTimeSec + "[s] for command " + cmnd);

		return exeTimeSec;
	}
	

	public static String executeLPSolve(String cmnd, File dir){

		Process p = null;
		StringBuffer bf = new StringBuffer();

		try {
			p = Runtime.getRuntime().exec(cmnd, null, dir);
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String l;
			while((l=br.readLine())!=null){
				bf.append(l); // read buffer to avoid blocking process
				bf.append("\n");
			}
			p.waitFor();


			if(p.exitValue()!=0){
				p.destroy();
			}

		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally{
			if(p!=null)
				p.destroy();
		}

		String resCmnd = bf.toString();
		System.out.println(resCmnd);

		return resCmnd;
	}


	/**
	 * set test cases listed in the given @param filename_testcases
	 * @return list of test cases 
	 * 
	 * */
	public static List<TestCaseApp> setTestCases(File filename_testcases) {

		Set<String> testCasesSet = new HashSet<String>();
		List<TestCaseApp> testCases = new LinkedList<TestCaseApp>();
		mapTestCases = new HashMap<String, TestCaseApp>();

        LOGGER.info("getting list of test cases for application from: "
        			+filename_testcases.getName());
        try {
			FileReader fr = new FileReader(filename_testcases);
			BufferedReader br = new BufferedReader(fr);
			String line;
			String fileName, tcaseName, pkg, pkgName;
			pkgName = "";
			while((line = br.readLine())!=null){
				String[] pairFileTest = line.split(":");
				if(pairFileTest!=null && pairFileTest.length==2){
					pkg = pairFileTest[0].substring(pairFileTest[0].indexOf("test/")+5,
												pairFileTest[0].length());
				
					int index = pkg.lastIndexOf("/");
					if(index > 0)
						pkgName = pkg.substring(0, index).replace("/", ".");	
					
					fileName = pkg.substring(pkg.lastIndexOf('/')+1, pkg.indexOf(".java"));
					tcaseName = pairFileTest[1];
					
					//LOGGER.info("Test Case to include: "+pkgName +"."+ fileName +"#"+ tcaseName);
					
					// duplicated test calls are discarded 
					boolean res = testCasesSet.add(pkgName+"."+fileName+":"+tcaseName);
					
					if(res){
						TestCaseApp test = new TestCaseApp(pkgName, fileName, tcaseName);
						testCases.add(test);
						mapTestCases.put(test.getID(), test);
					}else
						System.err.println("Duplicated test: "+pkgName+"."+fileName+"#"+tcaseName);
						
				}
			}
			
			//save mapping of test cases to file
			saveToFile("res/mapTestCases.txt", mapTestCases);
			
		} catch (FileNotFoundException e) {
			LOGGER.error("Error opening file: "+filename_testcases.getPath());
			e.printStackTrace();
		} catch (IOException e) {
			LOGGER.error("Error reading file: "+filename_testcases.getPath());
			e.printStackTrace();
		}
		
        LOGGER.info("Total Number of test cases: "+testCases.size());
		return testCases;
	}

	public static void setTest_dir(String test_dir) {
		if(test_dir!=null && !test_dir.isEmpty())
			Main.test_dir = test_dir;
		else
			Main.test_dir = "test";

	}
	
	public static void setMaven_cmd(String maven_cmd) {
		if(maven_cmd!=null && !maven_cmd.isEmpty()){
			Main.maven_cmd = maven_cmd;
		}else
			Main.maven_cmd = "mvn.cmd";
	}
	
	public static void setBuild_dir(String build_dir) {
		if(build_dir!=null && !build_dir.isEmpty())
			Main.build_dir = build_dir;
		else
			Main.build_dir = "target";

	}
	
	/**
	 * Save test cases map to file
	 * */
	private static void saveToFile(String filename, HashMap<String, TestCaseApp> mapTestCases) {
		PrintWriter pWriter = null;
		
		try{
			pWriter = new PrintWriter(new File(filename));
			
			for(Map.Entry<String, TestCaseApp> entry: mapTestCases.entrySet()){
				TestCaseApp test = entry.getValue();
				
				pWriter.printf("%1$s, %2$s \n", entry.getKey(), test.getNamePkg()+"." +test.getNameFile()+ "#" +test.getName());
			}
			
			pWriter.flush();
			pWriter.close();
			
		}catch(IOException e){
			LOGGER.error("Cannot write map test cases in res/mapTestCases.txt");
			e.printStackTrace();
		}
	}

	/**
	 * @param path test case directory
	 * @param name of file for test case
	 * Traverse a directory to find if test case file exists
	 * 
	 * */
	private static boolean traverseDirectoryOfTestCases(File path, String fileName) {
		
		for(File file: path.listFiles()){

			if(file.isDirectory()){
				if(traverseDirectoryOfTestCases(file, fileName))
					return true;
			}

			if( file.isFile() && file.getName().contains(fileName)){
				return true;
			}
		}
		
		return false;
	}


	/**
	 *	Create a set with the name of the classes that are part of the list of sites
	 */
	public static Set<String> getSitesSet(String filename) {

		//String sites file
		SiteProcessor sp = new SiteProcessor();
		HashSet<String> set = null;

		try {

			if(verbose)
				System.out.println("Analyzing "+ filename);
			
			Files.readLines(new File( filename), Charsets.UTF_8, sp);
			LinkedList<Site> sites = (LinkedList<Site>)(sp.getResult());
			set = new HashSet<String>(sites.size());

			for(Site loc : sites){
				// loc.className(), fully qualified name with '/' separators
				set.add(loc.className());
			}

		} catch (IOException e) {
			System.err.println("Error reading file "+ filename);
			e.printStackTrace();
		}

		return set;
	}



	public static class SiteProcessor implements LineProcessor<Collection<Site>> {

		private final List<Site> results;

		public SiteProcessor() {
			this.results = new LinkedList<>();
		}

		@Override
		public List<Site> getResult() {
			return results;
		}

		@Override
		public boolean processLine(String line) throws IOException {
			results.add(Site.parse(line));

			return true;
		}
	}

}
