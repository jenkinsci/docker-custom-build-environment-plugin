package com.cloudbees.jenkins.plugins.okidocki;

import hudson.model.BuildBadgeAction;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerBadge implements BuildBadgeAction {

    public final String container;
    public final String image;

    public DockerBadge(String image, String container) {
        this.image = image;
        this.container = container;
    }

    public String getIconFileName() {
        return "plugin/oki-docki/docker-badge.png";
    }

    public String getDisplayName() {
        return "build inside docker container";
    }

    public String getUrlName() {
        return "/docker";
    }
}
