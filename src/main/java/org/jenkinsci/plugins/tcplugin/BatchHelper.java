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

/**
 * BatchHelper class generates batch script based on key parameters.
 *
 * @author Sergey Myasnikov
 * 
 */
public class BatchHelper {

	/** Method generates batch script based on user specified parameters.
	 * @see <a href="http://support.smartbear.com/viewarticle/55587">http://support.smartbear.com/viewarticle/55587</a> and
	 * <a href="http://support.smartbear.com/viewarticle/56560">http://support.smartbear.com/viewarticle/56560</a>.
	 * 
	 * @param testExecuteLocation Location of TestExecute.exe or TestComplete.exe
	 * @param projectLocation Workspace-based location of test project (.mds file)
	 * or project suit (.pjs file)
	 * @param projectName Name of project in the suit. Can be empty.
	 * @param additionalParameters Comma separated list of other cli arguments
	 * @param deleteLogs Delete 'Log' folders before test run?
	 * @param deleteExtender Delete .tcCfgExtender file after test?
	 * 
	 * @return Batch script as a String value
	 */
	public static String getBatch(String testExecuteLocation,
						String projectLocation,
						String projectName,
						String additionalParameters,
						boolean deleteLogs,
						boolean deleteExtender) {

		if (verifyInput(testExecuteLocation, projectLocation).length() != 0) {
			return verifyInput(testExecuteLocation, projectLocation);
		}

		String result = "";

		result += "@echo off\n\n";
		result += "cd %WORKSPACE%\n\n";

		if (deleteLogs) {
			result += "for /d /r . %%d in (Log) do @if exist \"%%d\" rd /s/q \"%%d\"\n\n";
		}

		result += "@echo on\n";

		result +="\"";
		result += testExecuteLocation;
		result+="\" \"";
		result += projectLocation;
		result += "\" /run ";

		if (projectName.length()!=0) {
			result += "\"/project:";
			result += projectName;
			result += "\" ";
		}

		result += additionalParameters;
		result += "\n\n";

		result += "@echo off\n\n";

		result += "IF ERRORLEVEL 1000 GOTO AnotherInstance\n";
		result += "IF ERRORLEVEL 4 GOTO Timeout\n";
		result += "IF ERRORLEVEL 3 GOTO CannotRun\n";
		result += "IF ERRORLEVEL 2 GOTO Errors\n";
		result += "IF ERRORLEVEL 1 GOTO Warnings\n";
		result += "IF ERRORLEVEL 0 GOTO Success\n";
		result += "IF ERRORLEVEL -1 GOTO LicenseFailed\n\n";

		result += ":AnotherInstance\n";
		result += "ECHO Another instance of TestComplete or TestExecute is already running\n";
		result += "GOTO End\n\n";

		result += ":Timeout\n";
		result += "ECHO Timeout elapses\n";
		result += "GOTO End\n\n";

		result += ":CannotRun\n";
		result += "ECHO The script cannot be run\n";
		result += "GOTO End\n\n";

		result += ":Errors\n";
		result += "ECHO There are errors\n";
		result += "GOTO End\n\n";

		result += ":Warnings\n";
		result += "ECHO There are warnings\n";
		result += "GOTO End\n\n";

		result += ":Success\n";
		result += "ECHO No errors\n";
		result += "GOTO End\n\n";

		result += ":LicenseFailed\n";
		result += "ECHO License check failed\n";
		result += "GOTO End\n\n";

		result += ":End\n";

		if (deleteExtender) {
			result += "SET error_value=%errorlevel%\n";
			result += "del /s /q \"*.tcCfgExtender\"\n\n";  		
			result += "exit /b %error_value%\n\n"; 		
		} else {
			result += "exit /b %errorlevel%\n\n";
		}

		return result;    	
	}

	/** Method generates error messages in case mandatory parameters are not given properly
	 * 
	 * @param testExecuteLocation Location of TestExecute.exe or TestComplete.exe
	 * @param projectLocation Workspace-based location of test project (.mds file)
	 * or project suit (.pjs file)
	 * 
	 * @return Error messages as String value
	 */
	private static String verifyInput(String testExecuteLocation, String projectLocation) {

		String result = "";

		if (testExecuteLocation.length() == 0) {
			result += "Specify the location of TestComplete or TestExecute before generating\n\n";
		}

		if (projectLocation.length() == 0) {
			result += "Specify project ot project suit before generating\n";
		}

		return result;   	
	}

}
