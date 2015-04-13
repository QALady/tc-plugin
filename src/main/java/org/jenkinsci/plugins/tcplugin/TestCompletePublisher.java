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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import htmlpublisher.HtmlPublisher;
import htmlpublisher.HtmlPublisherTarget;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.model.Hudson;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.junit.TestDataPublisher;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.test.TestResultProjectAction;
import hudson.util.DescribableList;
import hudson.util.FormValidation;

/**
 * Recorder to store test results
 *
 * @author Sergey Myasnikov
 * 
 */
public class TestCompletePublisher extends Recorder implements Constants {
	
	final String MHT = ".mht";
	final String PNG = ".png";
	final String REPLACE_WHAT = "http://localhost";
	final String REPLACE_WITH = ".";
	
	final String HTML_RESULT_NAME = "Test Results";
	final String INDEX_HTML = "index.htm";

	private final String resultLocation;
	private final boolean isMhtFile;
	private final boolean publishHtml;
	private final boolean publishJunit;
	private final boolean publishAtrifacts;
	private final boolean publishScreenshots;


	@DataBoundConstructor
	public TestCompletePublisher(String resultLocation,
			boolean publishHtml,
			boolean publishJunit,
			boolean publishArtifacts,
			boolean publishScreenshots) {

		this.resultLocation = resultLocation;
		this.isMhtFile = resultLocation.toLowerCase().endsWith(MHT);
		
		this.publishHtml = publishHtml;
		this.publishJunit = publishJunit;
		this.publishAtrifacts = publishArtifacts;
		this.publishScreenshots = publishScreenshots;
	}

	public String getResultLocation() {
		return resultLocation;
	}
	
	public boolean getIsMhtFile() {
		return isMhtFile;
	}

	public boolean getPublishHtml() {
		return publishHtml;
	}

	public boolean getPublishJunit() {
		return publishJunit;
	}
	
	public boolean getPublishArtifacts() {
		return publishAtrifacts;
	}
	
	public boolean getPublishScreenshots() {
		return publishScreenshots;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}


	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
		
		try {
			//========== I. INITIAL CHECK AND PREPARE ==========
		
			//Should be Windows node...
			if (launcher.isUnix()) {
				echo("----> ERROR: Only Windows node is supported", listener);
				//FATAL
				build.setResult(Result.FAILURE);
				return true;
			}

			//...and location should be specified
			if (resultLocation.length() == 0) {
				echo("----> SKIPPED: Result location is not specified", listener);
				//FATAL
				build.setResult(Result.FAILURE);
				return true;
			}

			FilePath workspace = build.getWorkspace();
	
			//========== II. CLEAN-UP ==========			
			try {
				FilePath dir = workspace.child(MHT_PARSE_DESTINATION);
				FilePath report = workspace.child(JUNIR_REPORT);
				
				if (dir.exists() || report.exists()) {
					echo("----> INFO: Deleting previous results", listener);
					dir.deleteRecursive();
					report.delete();			
				}
			} catch (IOException e) {
				echo("----> WARN: Failed to cleanup "+ MHT_PARSE_DESTINATION + ": " + e.getMessage(), listener);
				//NON-FATAL
			}			
			
			//========== III. DECOMPRESS MHTML FILE ==========			
			if (isMhtFile) {
				
				echo("----> INFO: Parsing MHTML file " + workspace + "\\" + resultLocation, listener);
				echo("----> INFO: Parsing to " + workspace + "\\" + MHT_PARSE_DESTINATION, listener);
				try {
					echo("----> INFO: Decompressing MHTML file", listener);
					decompress(build, listener, workspace);
				} catch (IOException e1) {
					echo("----> ERROR: Failed to parse MHTML file: " + e1.getMessage(), listener);
					//FATAL
					build.setResult(Result.FAILURE);
					return true;
				} catch (NullPointerException e2) {
					echo("----> ERROR: Failed to parse MHTML file: " + e2.getMessage(), listener);
					//FATAL
					build.setResult(Result.FAILURE);
					return true;
				}				
			}
		
			//========== IV. GENERATE NEW JUNIT RESULT FILE ==========
			try {
				echo("----> INFO: Generating JUnit xml", listener);
				if (isMhtFile) {
					ResultParser.generateJUnitXML(workspace, MHT_PARSE_DESTINATION);
				} else {
					ResultParser.generateJUnitXML(workspace, resultLocation);
				}
			} catch (IOException e) {
				echo("----> ERROR: Failded to generate JUnit xml file: " + e.getMessage(), listener);
				//FATAL
				build.setResult(Result.FAILURE);
				return true;
			}
			
			//========== V. PUBLISH JUNIT RESULTS (MANDATORY) ==========
			echo("----> INFO: JUnit publish started", listener);	
			DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers = 
					new DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>>(Saveable.NOOP);
			JUnitResultArchiver publisher = new JUnitResultArchiver(JUNIR_REPORT, true, testDataPublishers);
		
			try {
				publisher.perform(build, launcher, listener);				
			} catch (IOException e) {
				echo("----> ERROR: Failded to publish JUnit results: " + e.getMessage(), listener);
				//FATAL
				build.setResult(Result.FAILURE);
				return true;
			}

			//========== VI. PUBLISH MHTML AS ARTIFACT (OPTION) ==========
			if (publishAtrifacts && isMhtFile) {
				echo("----> INFO: Publish MHTML as artifact", listener);
				ArtifactArchiver artArch1 = new ArtifactArchiver(resultLocation, null, true, true);
				artArch1.perform(build, launcher, listener);							
			}
			
			if (publishScreenshots) {			
				if (isMhtFile) {
					ArtifactArchiver artArch2 = new ArtifactArchiver(MHT_PARSE_DESTINATION + "//*.png", null, true, true);
					artArch2.perform(build, launcher, listener);	
				} else {
					ArtifactArchiver artArch3 = new ArtifactArchiver(resultLocation + "//*.png", null, true, true);
					artArch3.perform(build, launcher, listener);	
				}
			}
	
			//========== VI. PUBLISH HTML RESULTS (OPTION) ==========	
			if (publishHtml) {
				
				//Change relative paths
				echo("----> INFO: Changing paths", listener);
				try {
					changePaths(listener, workspace);
				} catch (IOException e) {
					echo("----> WARN: Failed to configure paths in " + ROOT_XML + ", HTML may be broken: " + e.getMessage(), listener);
					//NON-FATAL
				}
					
				echo("----> INFO: Publish decompressed HTML", listener);
				if (Hudson.getInstance().getPlugin("htmlpublisher") != null) {

					List<HtmlPublisherTarget> list = new ArrayList<HtmlPublisherTarget>();
					list.add(new HtmlPublisherTarget(
							HTML_RESULT_NAME,
							MHT_PARSE_DESTINATION,
							INDEX_HTML,
							true,
							true));
					HtmlPublisher htmlPublisher = new HtmlPublisher(list);
					htmlPublisher.perform(build, launcher, listener);
				} else {
					echo("----> ERROR: Cannot find 'HTML Publisher' plugin", listener);
					build.setResult(Result.UNSTABLE);
					return true;
				}
			}
		} catch (InterruptedException e){
			echo("----> WARN: Step execution was interrupted", listener);
			//build.getExecutor().abortResult();
			build.setResult(Result.ABORTED);
		}
		return true;
	}

	/**
	 * Write string to build log.
	 * 
	 * @param string
	 * @param listener
	 */
	private void echo(String string, BuildListener listener){		
		listener.getLogger().println(string);
	}
	
	/**
	 * Method to decompress *.mht file to folder.
	 * 
	 * @param build
	 * @param listener
	 * @param workspace
	 * @throws InterruptedException 
	 * @throws NullPointerException 
	 * @throws Exception
	 */
	private void decompress(AbstractBuild<?, ?> build,
							BuildListener listener,
							FilePath workspace) throws IOException, NullPointerException, InterruptedException {

		MHTParser mhtparser = new MHTParser(		
			workspace.child(resultLocation),
			workspace.child(MHT_PARSE_DESTINATION));	
		mhtparser.decompress();
	}
	
	/**
	 * Replace localhost-based paths to relative paths.
	 * 
	 * @param listener
	 * @param workspace
	 * @throws IOException
	 * @throws InterruptedException 
	 */
	private void changePaths(BuildListener listener, FilePath workspace) throws IOException, InterruptedException {
		
		FilePath path = workspace.child(MHT_PARSE_DESTINATION).child(ROOT_XML);

		String content = "";
		content = path.readToString();
		content = content.replaceAll(REPLACE_WHAT, REPLACE_WITH);
		path.write(content, null);
	}
	
	
	@Override
	public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {

		ArrayList<Action> actions = new ArrayList<Action>();

		actions.add(new TestResultProjectAction(project));
		
		if (this.getPublishHtml()) {
			actions.add(new ReportAction());
			if (project instanceof MatrixProject && ((MatrixProject) project).getActiveConfigurations() != null){
				for (MatrixConfiguration mc : ((MatrixProject) project).getActiveConfigurations()){
					try {
						mc.onLoad(mc.getParent(), mc.getName());
					}
					catch (IOException e){
						//Could not reload the configuration.
					}
				}
			}
		}
		return actions;
	}
	

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		@Override
		public boolean isApplicable(
				final Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Publish TestComplete result report";
		}

		//'Publish HTML' check throws warning
//		public FormValidation doCheckPublishHtml(@QueryParameter boolean value) {
//			if (value)
//				return FormValidation.warning(
//						"NOTE: Decompressed HTML result may not look perfert in some browsers");
//			else
//				return FormValidation.ok();
//		}
		
		public FormValidation doCheckResultLocation(@AncestorInPath AbstractProject project,
				@QueryParameter String value) throws IOException {
			if (value.length() == 0)
				return FormValidation.error("Please specify the location of result MHTML file");
			else
				return FilePath.validateFileMask(project.getSomeWorkspace(), value);
				//return FormValidation.ok();
		}
		
//		public FormValidation doTestMHTML(@AncestorInPath AbstractProject project, 
//				@QueryParameter("resultLocation") final String resultLocation)
//						throws IOException, ServletException {
//
//			return FilePath.validateFileMask(project.getSomeWorkspace(), resultLocation);
//		}
		
	}

}