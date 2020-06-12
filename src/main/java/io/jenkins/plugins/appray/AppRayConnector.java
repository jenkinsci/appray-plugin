package io.jenkins.plugins.appray;

import java.io.File;

import hudson.ProxyConfiguration;

import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import kong.unirest.HttpResponse;
import kong.unirest.MultipartBody;
import kong.unirest.JsonNode;
import kong.unirest.UnirestParsingException;


public class AppRayConnector {
    private UnirestInstance unirest;

    public void setup(String url, String email, String password, ProxyConfiguration proxy) throws AppRayConnectorException {
        UnirestInstance auth_unirest = Unirest.spawnInstance();

        MultipartBody request = auth_unirest.post(url + "/api/v1/authentication")
                                                        .field("username", email)
                                                        .field("password", password)
                                                        .field("grant_type", "password");

        if (proxy != null) {
            request.proxy(proxy.name, proxy.port);
        }

        HttpResponse<JsonNode> response = request.asJson();

        auth_unirest.shutDown();

        if (response.getStatus() == 401) {
            throw new AppRayConnectorException("Authentication failure (" + email + ")");
        }
        if (response.getStatus() == 404) {
            throw new AppRayConnectorException("Authentication endpoint is missing, specify a valid URL; " + request.getUrl());
        }

        if (!response.isSuccess()) {
            throw new AppRayConnectorException("Authentication failure, status code: " + response.getStatus());
        }

        this.unirest = Unirest.spawnInstance();
        this.unirest.config()
                .setDefaultHeader("Accept", "application/json")
                .addDefaultHeader("Authorization", "Bearer " + response.getBody().getObject().getString("access_token"))
                .defaultBaseUrl(url);

        if (proxy != null) {
            this.unirest.config().proxy(proxy.name, proxy.port);
        }
    }

    public void close() {
        if (this.unirest != null) {
            this.unirest.shutDown();
        }
    }

    public String getOrganization() throws AppRayConnectorException {
        HttpResponse<JsonNode> response = this.unirest.get("/api/v1/organization").asJson();
        this.checkResponse(response);

        return response.getBody().getObject().getString("name");
    }

    public User getUser() throws AppRayConnectorException {
        User user = new User();

        HttpResponse<JsonNode> response = this.unirest.get("/api/v1/user").asJson();
        this.checkResponse(response);

        user.name = response.getBody().getObject().getString("name");
        user.email = response.getBody().getObject().getString("email");
        switch (response.getBody().getObject().getString("role")) {
          case "full-access":
            user.role = User.Role.FULL;
            break;
          case "read-only":
            user.role = User.Role.READONLY;
            break;
          case "observer":
            user.role = User.Role.OBSERVER;
            break;
          default:
            user.role = User.Role.UNKNOWN;
        }

        return user;
    }

    public String submitApp(String application) throws AppRayConnectorException {
        HttpResponse<String> response = this.unirest.post("/api/v1/jobs").field("app_file", new File(application)).asString();
        String result = response.getBody();

        if (response.getStatus() > 400) {
            JsonNode error = new JsonNode(result);
            
            throw new AppRayConnectorException("Error(" + response.getStatus() +") submitting application for scanning: " + error.getObject().getString("title") + " " + error.getObject().getString("detail"));
        }

        return result.substring(1, result.length() - 2);
    }

    public String getJUnit(String jobId) throws AppRayConnectorException {
        HttpResponse<String> response = this.unirest.get("/api/v1/jobs/{job_id}/junit")
                                                        .routeParam("job_id", jobId)
                                                        .asString();
        String result = response.getBody();

        if (response.getStatus() > 400) {
            JsonNode error = new JsonNode(result);

            throw new AppRayConnectorException("Error(" + response.getStatus() +") fetching JUnit results; " + error.getObject().getString("title") + " " + error.getObject().getString("detail"));
        }

        return result;
    }

    public ScanJob getJobDetails(String jobId) throws AppRayConnectorException {
        ScanJob job = new ScanJob();
        HttpResponse<JsonNode> response = this.unirest.get("/api/v1/jobs/{job_id}")
                                                        .routeParam("job_id", jobId)
                                                        .asJson();

        this.checkResponse(response);

        job.status = ScanJob.Status.valueOf(response.getBody().getObject().getString("status"));
        job.progress_total = response.getBody().getObject().optInt("progress_total", 0);
        job.progress_finished = response.getBody().getObject().optInt("progress_finished", 0);
        job.risk_score = response.getBody().getObject().optInt("risk_score", 0);
        job.package_name= response.getBody().getObject().getString("package");
        job.label= response.getBody().getObject().getString("label");
        job.version = response.getBody().getObject().getString("version");
        job.platform = response.getBody().getObject().getString("platform");
        job.app_hash = response.getBody().getObject().getString("app_hash");
        job.failure_reason= response.getBody().getObject().optString("failure_reason");

        return job;
    }

    private void checkResponse(HttpResponse<JsonNode> response) throws AppRayConnectorException {
        if (!response.isSuccess()) {
            if (response.getBody() == null) {
                UnirestParsingException ex = response.getParsingError().get();
                throw new AppRayConnectorException("Error(" + response.getStatus() + ") response: " + ex.getMessage() + ", body: " + ex.getOriginalBody());
            } else {
                throw new AppRayConnectorException("Error(" + response.getStatus() + ") response: " + response.getBody().getObject().getString("title") + " " + response.getBody().getObject().getString("detail"));
            }
        }
    }
}
