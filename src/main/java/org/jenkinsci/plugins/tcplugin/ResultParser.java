/**
 * The MIT License
 * Copyright (c) 2015 Sergey Myasnikov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.tcplugin;

import hudson.FilePath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.jenkinsci.plugins.tcplugin.results.TestResult;
import org.jenkinsci.plugins.tcplugin.results.TestSuiteResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Result parser class. Works with decompressed results to generate Junit .xml file.
 *
 * @author Sergey Myasnikov
 * 
 */
public class ResultParser implements Constants {

	private static final String TEST_RESULT_PROVIDER_XPATH = ".//Provider[contains(@href, '_TestLog.xml')]/../../Provider";
	private static final String RUN_TIME_XPATH = ".//RunTime";
	private static final String MESSAGE_XPATH = ".//Message";
	
	private static final String DEFAULT_FAILURE_TYPE = "Failure";
	
	private static final int MAX_CLASS_DEPTH = 10;


	/**
	 * Parse String time to long.
	 * 
	 * @param sTime Time string.
	 * @return Time long value.
	 */
	private static long getLongTime(String sTime) {

		long lTime = 0;
		try {
			String[] aTime = sTime.split(":");
			lTime = Long.parseLong(aTime[0])*3600 + Long.parseLong(aTime[1])*60 + Long.parseLong(aTime[2]);
		} catch (Exception e) {
			e.printStackTrace();
		}	
		return lTime;
	}

	/**
	 * Remove spaces from classname string.
	 * 
	 * @param className classname string.
	 * @return Sanitized classname.
	 */
	private static String sanitizeClassName(String className) {
		return className.replace(" ", "_").trim();
	}

	/**
	 * Get the name of log .xml file from href attribute value.
	 * 
	 * @param href href attribute value.
	 * @return The name of log .xml file.
	 */
	private static String getLogFileNameFromHref(String href){
		return  href.substring(href.lastIndexOf("/") + 1, href.length());
	}

	/**
	 * Returns xml-document by FilePath.
	 * 
	 * @param path FilePath of the .xml file.
	 * @return
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	private static Document getDocument(FilePath path) 
			throws ParserConfigurationException, SAXException, IOException {
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(path.read());	
		return doc;
	}

	/**
	 * Get NodeList object from .xml file by xPath
	 * 
	 * @param xmlPath .xml file location.
	 * @param xPath xPath value.
	 * @return Node list object. May return null.
	 */
	public static NodeList getNodesByXPath(FilePath xmlPath, String xPath) {

		NodeList nList = null;

		try {
			Document doc = getDocument(xmlPath);
			doc.normalize();

			XPathFactory xPathfactory = XPathFactory.newInstance();
			XPath xpath = xPathfactory.newXPath();
			XPathExpression expr = xpath.compile(xPath);
			nList = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

		} catch (Exception e) {
			e.printStackTrace();
		}
		return nList;		
	}

	/**
	 * Get name attribute value of the root element.
	 * 
	 * @param xmlPath .xml file location.
	 * @return Name attribute value of the root element.
	 */
	public static String getRootLogDataName(FilePath xmlPath) {

		String result = "";
		try {
			Document doc = getDocument(xmlPath);
			doc.normalize();
			Node root = doc.getFirstChild();

			if (root.getNodeType() == Node.ELEMENT_NODE) {
				result = ((Element) root).getAttribute("name");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;		
	}

	/**
	 * Method generates test result "class name" by adding parent names separated with "." symbol.
	 * @param node Test result Node.
	 * @return TestResult "class name".
	 */
	private static String getClassName(Node node) {

		String result = "";
		String previous = "";
		Node parent = node;

		for (int i = 0; i < MAX_CLASS_DEPTH; i++) {		
			try {
				parent = parent.getParentNode();
				if (parent.getNodeType() == Node.ELEMENT_NODE) {
					if (result.length() == 0) {
						result = previous + result;
					} else {
						result = previous + "." + result;
					}
					previous = ((Element) parent).getAttribute("name");
				}	
			} catch (Exception e) {
				break;
			}	
		}
		return sanitizeClassName(result);
	}

	/**
	 * Convert Node with test result data to TestResult object.
	 * 
	 * @param node Test result Node
	 * @param basePath Location of root.xml and other result .xml files.
	 * @return TestResult object.
	 */
	private static TestResult getTestResultFromNode(Node node, FilePath basePath) {

		TestResult result = null;

		String testName = "";
		String testClass = "";
		NodeList testTimeNodes = null;
		String testTime = "";
		long longTestTime = 0;
		String testStatus = "";
		boolean isSuccess = false;
		String projectLogName = getLogFileNameFromHref(((Element) node).getAttribute("href"));
		String testFailureMessage = "";
		NodeList testMessageNodes = null;

		Node parent = node.getParentNode();
		if (parent.getNodeType() == Node.ELEMENT_NODE) {		
			Element parentElement = (Element) parent;

			testName = parentElement.getAttribute("name");		
			testStatus = parentElement.getAttribute("status");
			isSuccess = testStatus.equalsIgnoreCase("0") || testStatus.equalsIgnoreCase("1");

			testTimeNodes = getNodesByXPath(basePath.child(projectLogName), RUN_TIME_XPATH);
			testTime = testTimeNodes.item(0).getTextContent();
			longTestTime = getLongTime(testTime);

			testClass = getClassName(parent);

			if (isSuccess) {
				result = new TestResult(testClass, testName, longTestTime);
			} else {
				try {
					testMessageNodes = getNodesByXPath(basePath.child(projectLogName), MESSAGE_XPATH);
					testFailureMessage = testMessageNodes.item(0).getTextContent();
				} catch (Exception e) {
					// Ignore: may not have a message.
				}				
				result = new TestResult(testClass, testName, longTestTime, DEFAULT_FAILURE_TYPE, testFailureMessage);
			}
		}
		return result;			
	}

	/**
	 * Method to get  a List of ALL TestResults from root.xml.
	 * 
	 * @param basePath Location of root.xml and other result .xml files.
	 * @return A List of TestResult objects.
	 */
	private static List<TestResult> getAllTestResults(FilePath basePath) {

		List<TestResult> testResults = new ArrayList<TestResult>();
		NodeList nList = getNodesByXPath(basePath.child(ROOT_XML), TEST_RESULT_PROVIDER_XPATH);

		for (int i = 0; i < nList.getLength(); i++) {

			Node node = nList.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				testResults.add(getTestResultFromNode(node, basePath));
			}
		}
		return testResults;
	}

	/**
	 * Method to get a List of ALL TestSuiteResults.
	 * 
	 * @param basePath Location of root.xml and other result .xml files.
	 * @return A List of TestSuiteResult objects.
	 */
	private static List<TestSuiteResult> getAllTestSuites(FilePath basePath) {

		List<TestResult> testResults = getAllTestResults(basePath);
		List<TestSuiteResult> testSuiteResults = new ArrayList<TestSuiteResult>();
		boolean found = false;

		for (int i = 0; i < testResults.size(); i++) {
			found = false;
			TestResult testResult = testResults.get(i);
			String suitName = testResult.getSuiteName();

			//Check if such test suite is in in the list
			for (int j = 0; j < testSuiteResults.size(); j++) {
				TestSuiteResult testSuiteResult = testSuiteResults.get(j);
				String name = testSuiteResult.getName();
				if (name.equalsIgnoreCase(suitName)) {
					//Found in the list. Add TestResult to TestSuiteResult.
					testSuiteResult.addTestResult(testResult);
					found = true;
					break;
				}
			}
			//Not found case:
			//Create new TestSuiteResult, add TestResult to the suite and add TestSuiteResult to the list.
			if (!found) {
				TestSuiteResult res = new TestSuiteResult(suitName);
				res.addTestResult(testResult);
				testSuiteResults.add(res);
			}
		}
		return testSuiteResults;		
	}

	/**
	 * Method to generate results XML as a string.
	 * 
	 * @param logPath Log directory.
	 * @return JUnit XML results as a string.
	 */
	public static String getResultXml(FilePath basePath) {

		List<TestSuiteResult> testSuiteResults = getAllTestSuites(basePath);
		String testSuitesName = getRootLogDataName(basePath.child(ROOT_XML));


		String result = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
				"<testsuites" + 
				((testSuitesName.length()==0 ? "" : " name=\"" + testSuitesName + "\"")) +
				">\n";

		for (int i = 0; i < testSuiteResults.size(); i++) {
			result += testSuiteResults.get(i).getTestSuitResultXml();
		}

		result += "</testsuites>";

		return result;
	}

	/**
	 * Generate JUnit XML file. Use platform default encoding.
	 * 
	 * @param workspace Path to home (job workspace)
	 * @throws InterruptedException 
	 * @throws IOException 
	 */
	public static void generateJUnitXML(FilePath workspace, String resultLocation) throws IOException, InterruptedException {

		String str = getResultXml(workspace.child(resultLocation));
		FilePath out = new FilePath(workspace, JUNIR_REPORT);
		out.write(str, null);
	}

}
