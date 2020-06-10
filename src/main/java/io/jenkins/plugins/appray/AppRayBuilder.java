package io.jenkins.plugins.appray;

import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.Result;
import hudson.model.Item;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.security.ACL;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.AncestorInPath;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Date;
import java.util.Collections;

import org.jenkinsci.Symbol;


public class AppRayBuilder extends Builder implements SimpleBuildStep {

    private final String appRayUrl;
    private final String outputFilePath;
    private final Integer waitTimeout;
    private final Integer riskScoreThreshold;
    private final String credentialsId;

    @DataBoundConstructor
    public AppRayBuilder(String appRayUrl, String outputFilePath, Integer waitTimeout, Integer riskScoreThreshold, String credentialsId) {
        this.appRayUrl = appRayUrl;
        this.outputFilePath = outputFilePath;
        this.waitTimeout = waitTimeout;
        this.riskScoreThreshold = riskScoreThreshold;
        this.credentialsId = credentialsId;
    }

    public String getAppRayUrl() {
        return appRayUrl;
    }

    public String getOutputFilePath() {
        return outputFilePath;
    }

    public Integer getWaitTimeout() {
        return waitTimeout;
    }

    public Integer getRiskScoreThreshold() {
        return riskScoreThreshold;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {

        AppRayConnector connector = new AppRayConnector();
        ScanJob job;
        String application = run.getEnvironment(listener).expand(this.outputFilePath);
        FilePath application_path = workspace.child(application);

        // use domain req
        StandardUsernamePasswordCredentials credential = CredentialsProvider.findCredentialById(
              this.credentialsId,
              StandardUsernamePasswordCredentials.class,
              run, Collections.<DomainRequirement> emptyList());

        if (credential == null) {
            listener.error("Credential is missing: " + this.credentialsId);
            run.setResult(Result.FAILURE);
            return;
        }
        if (appRayUrl == null || appRayUrl.length() == 0) {
            listener.fatalError("Required App-Ray url is missing");
            run.setResult(Result.FAILURE);
            return;
        }
        if (!application_path.exists()) {
            listener.error("Application does not exists: " + application_path.getRemote());
            run.setResult(Result.FAILURE);
            return;
        }

        listener.getLogger().println("App-Ray scanning application: " + application_path.getRemote());

        try {
            connector.setup(this.appRayUrl, credential.getUsername(), Secret.toString(credential.getPassword()), Jenkins.get().proxy);
            User user = connector.getUser();
            String organization = connector.getOrganization();

            listener.getLogger().println("App-Ray scanning on " + this.appRayUrl + " -> " + user.name + " (" + user.email + ") @ " + organization);

            String jobId = connector.submitApp(application_path.getRemote());
            job = connector.getJobDetails(jobId);
            String resultUrl = this.appRayUrl + "/scan-details/" + jobId;
            listener.getLogger().println("App-Ray scanning " + job.platform + " application " + job.package_name + " (" + job.label + ") " + job.version + " (SHA1: " + job.app_hash + "), scan job ID: " + jobId);
            listener.getLogger().println("App-Ray scan result details will be available at: " + resultUrl);

            job = this.waitForJob(jobId, connector, listener);

            this.processJobResult(run, resultUrl, job, connector, jobId, workspace, listener);

        } catch (AppRayConnectorException e) {
            connector.close();
            listener.error(e.getMessage());
            run.setResult(Result.FAILURE);
        } catch (Exception e) {
            connector.close();
            listener.error("Exception: " + e.getMessage());
            e.printStackTrace();
            run.setResult(Result.FAILURE);
        } finally {
            connector.close();
        }
    }

    private ScanJob waitForJob(String jobId, AppRayConnector connector, TaskListener listener)
        throws AppRayConnectorException, InterruptedException {

        final long ONE_SEC_IN_MILLIS = 1000;
        final long ONE_MINUTE_IN_MILLIS = 60 * ONE_SEC_IN_MILLIS;
        long sleep = 10;
        ScanJob job;

        Date now = new Date(System.currentTimeMillis());
        Date endDate = new Date(System.currentTimeMillis() + this.waitTimeout * ONE_MINUTE_IN_MILLIS);

        job = connector.getJobDetails(jobId);

        while (now.compareTo(endDate) < 0) {
            if (job.status == ScanJob.Status.queued) {
                listener.getLogger().println("Application is queued for scanning");
                sleep = 20;
            } else if (job.status == ScanJob.Status.processing) {
                listener.getLogger().println("Application is being scanned: " + job.progress_finished + " / " + job.progress_total);
                if ((job.progress_total - job.progress_finished) > 1) {
                    sleep = 10;
                } else {
                    sleep = 5;
                }
            } else {
                break;
            }

            Thread.sleep(sleep * ONE_SEC_IN_MILLIS);
            now = new Date(System.currentTimeMillis());

            job = connector.getJobDetails(jobId);
        }

        return job;
    }

    private void processJobResult(Run<?, ?> run, String resultUrl, ScanJob job, AppRayConnector connector, String jobId, FilePath workspace, TaskListener listener)
        throws AppRayConnectorException, IOException, InterruptedException {

        final String junit_file = "appray.junit.xml";

        if (job.status == ScanJob.Status.finished) {
            run.addAction(new AppRayResultAction(resultUrl, job));

            if (this.riskScoreThreshold < job.risk_score) {
                listener.error("App-Ray scan finished, application has too high risk score: " + job.risk_score);
                run.setResult(Result.FAILURE);
            } else {
                listener.getLogger().println("App-Ray scan finished, application below configure threashold, risk score: " + job.risk_score);
                run.setResult(Result.SUCCESS);
            }

            workspace.child(junit_file).write(connector.getJUnit(jobId), null);

        } else if (job.status == ScanJob.Status.failed) {
            run.addAction(new AppRayResultAction(null, job));
            listener.error("App-Ray scan failed: " + job.failure_reason);
            run.setResult(Result.FAILURE);
        } else {
            run.addAction(new AppRayResultAction(resultUrl, job));
            listener.error("App-Ray scan wait timeout exceeded, try to increase waitTimeout");
            run.setResult(Result.FAILURE);
        }
    }

    @Symbol("appray")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public static final String defaultAppRayUrl = "https://demo.app-ray.co";
        public static final Integer defaultWaitTimeout = 10;
        public static final Integer defaultRiskScoreThreshold = 30;

        public FormValidation doTestConnection(
            @AncestorInPath Item item,
            @QueryParameter("appRayUrl") String appRayUrl, @QueryParameter("credentialsId") final String credentialsId) throws IOException, ServletException {

            AppRayConnector connector = new AppRayConnector();

            // domain req
            StandardUsernamePasswordCredentials credential = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, item, ACL.SYSTEM, Collections.<DomainRequirement> emptyList()),
                CredentialsMatchers.withId(credentialsId));

            if (credential == null)
                return FormValidation.error("Missing credential, maybe configured credential has been deleted or moved?");
            if (appRayUrl.length() == 0)
                return FormValidation.error("App-Ray url is required");

            try {
                connector.setup(appRayUrl, credential.getUsername(), Secret.toString(credential.getPassword()), Jenkins.get().proxy);

                User user = connector.getUser();
                String organization = connector.getOrganization();

                if (user.role != User.Role.FULL) {
                    return FormValidation.error("User must have full-access role: " + credential.getUsername() + " current role: " + user.role);
                }

                return FormValidation.ok("Successfully connected. " + user.name + " (" + user.email + ") @ " + organization);
            } catch (Exception e) {
                connector.close();
                e.printStackTrace();
                return FormValidation.error("Connection error: " + e.getMessage());
            } finally {
                connector.close();
            }
        }

        public ListBoxModel doFillCredentialsIdItems(
            @AncestorInPath Item item,
            @QueryParameter String credentialsId,
            @QueryParameter("appRayUrl") String appRayUrl) {

            StandardListBoxModel result = new StandardListBoxModel();
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                    && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                  return result.includeCurrentValue(credentialsId);
                }
            }

            // use domain name?
            return result
              .includeAs(ACL.SYSTEM, item, StandardUsernamePasswordCredentials.class, Collections.<DomainRequirement> emptyList())
              .includeCurrentValue(credentialsId);
        }

        public FormValidation doCheckCredentialsId(
            @AncestorInPath Item item,
            @QueryParameter String value) {

            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                  return FormValidation.ok();
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                    && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                  return FormValidation.ok();
                }
            }

            if (value.startsWith("${") && value.endsWith("}")) {
                return FormValidation.warning("Cannot validate expression based credentials");
            }

            // use domain name?
            if (CredentialsProvider.listCredentials(StandardUsernamePasswordCredentials.class, item, ACL.SYSTEM,
                                          Collections.<DomainRequirement> emptyList(),
                  CredentialsMatchers.withId(value)
                  ).isEmpty()) {
              return FormValidation.error("Cannot find currently selected credentials, maybe it has been deleted?");
            }

            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.AppRayBuilder_DescriptorImpl_DisplayName();
        }
    }

}
