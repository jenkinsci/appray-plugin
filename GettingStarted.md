# App-Ray Mobile Security: Jenkins plugin
Learn more: https://app-ray.co

App-Ray’s Jenkins integration plugin allows users to add a security check step to their Jenkins workflow, to improve their CI/CD pipeline with automated Static and Dynamic Application Analysis. ARM Android and iOS apps are supported.

![App-Ray Mobile Security Jenkins Plugin Screenshot](https://app-ray.co/jenkins/jenkins-01.jpg)

## Table of contents
* [Capabilities](#capabilities)
* [Installation](#installation)
* [Configuration](#configuration)
* [Building](#building)

## Capabilities
- Supporting analysis in the cloud or locally (on-premises)
- Authentication data stored securely in Jenkins credentials store
- Detailed threat findings, References to OWASP, CVE and other vulnerability databases
- Remediation suggestions provided (a.k.a. How to fix)
- Threat finding reports available for detailed documentation
- Output in JUnit and JSON formats
- Sophisticated configuration of success/failure conditions
- Detailed logging in the console output

## Requirements
- The latest stable version of Jenkins is suggested to be used, according to Jenkins recommendations.
- The minimum tested compatible version of Jenkins is: `2.164.3`
- You will need access to a Cloud or On-premises App-Ray instance. Contact us to get started: https://app-ray.co

## Installation
- Locate and install the plugin via Jenkins Plugin Manager. Browse plugins or review documentation at Jenkins Plugins page: https://plugins.jenkins.io/
- Or download and install our plugin directly from App-Ray website: https://app-ray.co/jenkins/appray.hpi
and upload it in Jenkins Plugin Manager (Advanced tab).

## Configuration
2. Configure your App-Ray credentials (email + password) in Jenkins Credentials page.
3. Set up a Jenkins build job, or select your existing one.
4. At section Bindings, bind the previously set App-Ray crendentials to the build job.
5. Add a new build step, select 'App-Ray security check'.
6. Provide your configuration parameters, such as Risk score threshold (0-100), location of binary app file (Jenkins environment variables can be used) and access point of the App-Ray instance you use (local or remote).
7. Run your build, your security results will appear shortly. A security analysis may take a few minutes, depending on your configuration and the complexity of the app.
![App-Ray Mobile Security Jenkins Plugin Screenshot 2](https://app-ray.co/jenkins/jenkins-02.jpg)
8. Click on any of these findings to reveal more information.


# Building

```
mvn hpi:run
```

## Findbugs
```
mvn findbugs:gui
```

## Local Installing
```
mvn clean install
cp target/appray.hpi ~/.jenkins/plugins/
```
Then redeploy Jenkins.

---

_Any questions? We are happy to help! Contact us via email: info (at) app-ray. co_
