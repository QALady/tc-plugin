tcplugin
========

This is an alternative Jenkins plugin to launch [TestComplete](http://en.wikipedia.org/wiki/TestComplete) tests.
It consists of two blocks:
* **Test runner**. A build step which runs TestComplete tests.
* **Result publisher**. Post-build action which allows to publish test results to Jenkins.

###Test Runner
Its main feature is to launch TestComplete test project or project suite. It helps to generate correct batch script.

###Test Publisher
Features include:
* Storing test results and integrating it to Jenkins
* Works with .mht files
* Storing initial html report and screenshots if needed.

###Installing plugin
* Download plugin .hpi file
* In Jenkins go to ```Manage Jenkin``` > ```Manage Plugins``` > ```Advanced```
* In ```Upload Plugin``` section click ```Choose file```, select and upload .hpi file
* Finally restart Jenkins after installation is complete.

###Configuring plugin
#####Test runner:
* Make sure TestComplete or TestExecute is installed on the node machine
* Give path to TC or TE executable, project suite or project location and other optional arguments. Check advanced options as well:

![alt tag](https://github.com/sergey-myasnikov/tc-plugin/blob/master/images/TestRunner.png)
* Click ```Generate Script``` button and verify result batch script.

For more details see article [TestComplete command line](http://support.smartbear.com/viewarticle/55587) and [Batch file example](http://support.smartbear.com/viewarticle/56560).

#####Result publisher
* Depending on result format you use
 * set the location of result .mht file or
 * set path to directory with index.htm, root.xml and other result files
* You can select other storing options as well:

![alt tag](https://github.com/sergey-myasnikov/tc-plugin/blob/master/images/ResultPublisher.png)

###Alternative plugins
* [TestComplete Support Plugin](https://wiki.jenkins-ci.org/display/JENKINS/TestComplete+Support+Plugin)
* [TestComplete xUnit Plugin](https://wiki.jenkins-ci.org/display/JENKINS/TestComplete+xUnit+Plugin)
