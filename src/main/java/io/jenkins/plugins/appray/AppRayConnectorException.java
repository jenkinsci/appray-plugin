package io.jenkins.plugins.appray;

public class AppRayConnectorException extends Exception {
    public AppRayConnectorException(String message) {
        super(message);
    }

    public AppRayConnectorException(String message, Throwable e) {
        super(message, e);
    }
}
