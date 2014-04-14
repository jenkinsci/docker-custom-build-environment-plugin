package com.cloudbees.jenkins.plugins.okidocki;

import hudson.model.BuildBadgeAction;
import jenkins.model.Jenkins;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerBadge implements BuildBadgeAction {

    public final String image;

    public DockerBadge(String image) {
        this.image = image;
    }

    public String getIconFileName() {
        return "/plugin/oki-docki/docker-badge.png";
    }

    public String getDisplayName() {
        return "build inside docker container";
    }

    public String getUrlName() {
        return "/docker";
    }
}
