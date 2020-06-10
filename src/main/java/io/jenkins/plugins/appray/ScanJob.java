package io.jenkins.plugins.appray;

public class ScanJob {
    enum Status {
        queued,
        processing,
        finished,
        failed
    }

    public Status status;
    public int progress_total;
    public int progress_finished;
    public int risk_score;
    public String package_name;
    public String label;
    public String version;
    public String platform;
    public String app_hash;
    public String failure_reason;
}
