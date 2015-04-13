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
package org.jenkinsci.plugins.tcplugin.results;


/**
 * Simple test result object.
 *
 * @author Sergey Myasnikov
 * 
 */
public class TestResult {

	private String classname;
	private String name;
	private long time;
	private String failureType;
	private String failureDetails;


	/** Test result basic constructor.
	 * 
	 * @param classname Test Case classname.
	 * @param name Test Case name.
	 * @param time Time spent in milliseconds.
	 * @param failureType Test failure type (if any).
	 * @param failureDetails Test failure details (if any).
	 */
	public TestResult(String classname,
			String name,
			long time,
			String failureType,
			String failureDetails) {

		this.classname = classname;
		this.name = name;
		this.time = time;
		this.failureType = failureType;
		this.failureDetails = failureDetails;	
	}

	/** Successful Test result constructor (no failures).
	 * 
	 * @param classname Test Case classname.
	 * @param name Test Case name.
	 * @param time Time spent in milliseconds.
	 */
	public TestResult(String classname,
			String name,
			long time) {

		this.classname = classname;
		this.name = name;
		this.time = time;
		this.failureType = "";
		this.failureDetails = "";
	}

	/** Time field getter for test suite time calculation.
	 * 
	 * @return Test time of the current test.
	 */
	public long getTime() {
		return this.time;
	}

	/** suiteName getter for test suite name definition.
	 * 
	 * @return Name value for TestSuiteResult.
	 */
	public String getSuiteName() {
		if(this.classname.indexOf(".") == -1) {
			return this.classname;
		} 
		return this.classname.substring(0, this.classname.indexOf("."));
	}

	/** Method for TestResult to return itself as a JUnit XML
	 * 
	 * @return TestResult in JUnit xml format
	 */
	public String getTestResultXml() {

		String result = "";

		result += "\t\t<testcase classname=\"" + this.classname + "\" name=\"" + this.name + "\" time=\"" + this.time + "\"";

		if (!failureType.equals("") || !failureDetails.equals("")) {	
			result += ">\n";
			result += "\t\t\t<failure type=\"" + failureType + "\" message=\"" + failureDetails +"\"/>\n";    	
			result += "\t\t</testcase>\n";
		} else {
			result += "/>\n";	
		}

		return result;	
	}
	
}
