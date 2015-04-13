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

import java.util.ArrayList;
import java.util.List;

/**
 * Test suit results object.
 *
 * @author Sergey Myasnikov
 * 
 */
public class TestSuiteResult {	

	private String name;
	private long time;
	private List<TestResult> testResults = new ArrayList<TestResult>();

	/** TestSuiteResult constructor
	 * 
	 * @param name Test suite name
	 */
	public TestSuiteResult(String name) {		
		this.name = name;
		this.time = 0;
	}

	/**
	 * TestSuiteResult constructor
	 * 
	 * @param name Test suite name
	 * @param time Elapsed time
	 */
	public TestSuiteResult(String name, long time) {
		this.name = name;
		this.time = time;
	}
	
	/**
	 * Test Suite time setter.
	 * 
	 * @param time
	 */
	public void setTime(long time) {
		this.time = time;
	}

	/**
	 * Test Suite name getter. Will need it during forming test suites.
	 * 
	 * @return Test Suite name.
	 */
	public String getName() {
		return this.name;
	}

	/** Method to add test result to test suite results.
	 * 
	 * @param testResult TestResult object to add.
	 */
	public void addTestResult(TestResult testResult) {
		this.testResults.add(testResult);
	}

	/** Calculates test suit time as a sum of test case times
	 * 
	 * @return Test suit time
	 */
	private long sumTime() {

		long result = 0;

		for (int i = 0; i < testResults.size(); i++) {			
			result += testResults.get(i).getTime();
		}	
		return result;		
	}

	/** Returns test suit results in JUnit xml format
	 * 
	 * @return Test Suit results in JUnit xml format
	 */
	public String getTestSuitResultXml() {

		String result = "";

		result += "\t<testsuite name=\"" + 
				(this.name.equals("") ? "" : this.name) + "\" tests=\"" + testResults.size() + "\" time=\"" + 
				(this.time == 0 ? sumTime() : this.time) + "\">\n"; //if time is not set, try calculating from test results

		for (int i = 0; i < testResults.size(); i++) {
			result += testResults.get(i).getTestResultXml();
		}

		result += "\t</testsuite>\n";

		return result;
	}

}
