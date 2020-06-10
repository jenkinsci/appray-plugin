package io.jenkins.plugins.appray;

import hudson.model.Action;


public class AppRayResultAction implements Action {
    private String resultUrl;
    private ScanJob job;

    public AppRayResultAction(String resultUrl, ScanJob job) {
        this.resultUrl = resultUrl;
        this.job = job;
    }

    @Override
    public String getIconFileName() {
      if (this.resultUrl != null) {
        return "/plugin/appray/appray.png";
      } else {
        return null;
      }
    }

    @Override
    public String getDisplayName() {
      if (this.resultUrl != null) {
        return "Result @ App-Ray";
      } else {
        return null;
      }
    }

    @Override
    public String getUrlName() {
        return this.resultUrl;
    }

    public ScanJob getJob() {
        return this.job;
    }

    public String getUrl() {
        return this.resultUrl;
    }
}
