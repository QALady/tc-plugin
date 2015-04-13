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

import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.BatchFile;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Builder to run TestComplete tests.
 *
 * @author Sergey Myasnikov
 */
public class TestCompleteBuilder extends Builder {

	private final String testExecuteLocation;
	private final String projectLocation;
	private final String projectName;
	private final String additionalParameters;
	private final boolean deleteLogs;
	private final boolean deleteExtender;

	@DataBoundConstructor
	public TestCompleteBuilder(String testExecuteLocation,
			String projectLocation,
			String projectName,
			String additionalParameters,
			boolean deleteLogs,
			boolean deleteExtender) {

		this.testExecuteLocation = processPath(testExecuteLocation);
		this.projectLocation = processPath(projectLocation);       		
		this.projectName = projectName.trim(); 	
		this.additionalParameters = additionalParameters;
		this.deleteLogs = deleteLogs;
		this.deleteExtender = deleteExtender;
	}


	public String getTestExecuteLocation() {
		return testExecuteLocation;
	}

	public String getProjectLocation() {
		return projectLocation;
	}

	public String getProjectName() {
		return projectName;
	}

	public String getAdditionalParameters() {
		return additionalParameters;
	}

	public boolean getDeleteLogs() {
		return deleteLogs;
	}
	
	public boolean getDeleteExtender() {
		return deleteExtender;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) 
			throws InterruptedException {

		BatchFile test = new BatchFile(BatchHelper.getBatch(
				testExecuteLocation, 
				projectLocation, 
				projectName, 
				additionalParameters, 
				deleteLogs,
				deleteExtender));
		
		return test.perform(build, launcher, listener);
	}

	/**
	 * Method to sanitize path string.
	 * - Replaces / to \
	 * - Removes ' and "
	 * - Removes leading and trailing whitespaces.
	 * 
	 * @param str Path string.
	 * @return sanitized string.
	 */
	private static String processPath(String str) {
		return str.replace("\"", "")
				.replace("'", "")
				.replace("/", "\\")
				.trim();
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl)super.getDescriptor();
	}

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		
		public String getDisplayName() {
			return "Execute TestComplete tests";
		}
		
		boolean isSuitGiven = false;
		
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}


		/** Method to validate TE/TC executable location.
		 * First process given location.
		 * If meaningful value given, warn if looks incorrect. No strict rules, since executable may be in path.
		 * 
		 * @param project Project
		 * @param value TE/TC value provided by user
		 * @return Form Validation result
		 * @throws IOException
		 */
		public FormValidation doCheckTestExecuteLocation(@AncestorInPath AbstractProject<?, ?> project,
				@QueryParameter String value) throws IOException {
			
			value = processPath(value);
			if (value.length() == 0)
				return FormValidation.error("Please provide path to executable (TestExecute.exe or TestComplete.exe)");
			else if (value.length() < 20 ||
					!(value.toUpperCase().contains("TESTEXECUTE")||
							value.toUpperCase().contains("TESTCOMPLETE")))
				return FormValidation.warning("Path to TestExecute/TestComplete looks incorrect. Ignore it if sure");
			else
				return FormValidation.ok();
		}

		/** Method to validate Project / Project Suit location.
		 * First processes given location.
		 * If meaningful value given, determine whether project or project suit specified. 
		 * 
		 * @param project Project
		 * @param value Project / Project Suit location provided by user
		 * @return FormValidation result
		 * @throws IOException
		 */
		public FormValidation doCheckProjectLocation(@AncestorInPath AbstractProject<?, ?> project,
				@QueryParameter String value) throws IOException {
			
			value = processPath(value);
			if (value.length() == 0 ||
					!(value.toUpperCase().endsWith(".PJS") ||
							value.toUpperCase().endsWith(".MDS"))) {				
				isSuitGiven = false;
				return FormValidation.error("Specify project or project suit (.mds or .pjs file)");
			}
			else{
				if (value.toUpperCase().endsWith(".PJS")) {
					isSuitGiven = true;
				} else {
					isSuitGiven = false;
				}
				return FormValidation.ok();
			}
		}

		/** Method warns user if Project is not specified.
		 * 
		 * @param project Project
		 * @param value Project name provided by user
		 * @return FormValidation result
		 * @throws IOException
		 */
		public FormValidation doCheckProjectName(@AncestorInPath AbstractProject<?, ?> project,
				@QueryParameter String value) throws IOException {
			if (value.length() == 0 && isSuitGiven){	
				return FormValidation.warning("All projects in the suit will be executed");
			} else {
				return FormValidation.ok();
			}
		}
		
		/** Method to generate batch script in plugin config to check it.
		 * 
		 * @param testExecuteLocation TE or TC location given by user
		 * @param projectLocation Project or Project Suit location given by user
		 * @param projectName Project name given by user
		 * @param additionalParameters any other command line parameters given by user
		 * @param deleteLogs Add Log directory deletion?
		 * @param deleteExtender Delete .tcCfgExtender file
		 * @return Result batch script as a FormValidation object
		 * @throws IOException
		 * @throws ServletException
		 */
		public FormValidation doTestBatch(
				@QueryParameter("testExecuteLocation") final String testExecuteLocation,
				@QueryParameter("projectLocation") final String projectLocation,
				@QueryParameter("projectName") final String projectName,
				@QueryParameter("additionalParameters") final String additionalParameters,
				@QueryParameter("deleteLogs") final boolean deleteLogs,
				@QueryParameter("deleteExtender") final boolean deleteExtender) throws IOException, ServletException {				

			return FormValidation.ok(BatchHelper.getBatch(
							processPath(testExecuteLocation), 
							processPath(projectLocation), 
							projectName.trim(), 
							additionalParameters, 
							deleteLogs,
							deleteExtender));
		}

	}
}

