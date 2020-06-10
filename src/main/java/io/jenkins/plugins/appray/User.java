package io.jenkins.plugins.appray;

public class User {
    enum Role {
          FULL,
          READONLY,
          OBSERVER,
          UNKNOWN
    }

    public String name;
    public String email;
    public Role role;
}
