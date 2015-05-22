package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildBadgeAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;

import java.io.IOException;

/**
 * Used to determine if launcher has to be decorated to execute in container, after SCM checkout completed.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class BuiltInContainer implements BuildBadgeAction {

    /* package */ String image;

    /* package */ transient String container;
    private transient boolean enable;
    private final transient Docker docker;

    public BuiltInContainer(Docker docker) {
        this.docker = docker;
    }

    public void enable() {
        enable(true);
    }

    public void enable(boolean enable) {
        this.enable = enable;
    }

    public boolean isEnabled() {
        return enable;
    }

    public String getIconFileName() {
        return "/plugin/cloudbees-docker-custom-build-environment/docker-badge.png";
    }

    public String getImage() {
        return image;
    }

    public String getDisplayName() {
        return "build inside docker container";
    }

    public String getUrlName() {
        return "/docker";
    }

    Docker getDocker() {
        return docker;
    }

    public boolean tearDown() throws IOException, InterruptedException {
        if (container != null) {
            enable = false;
            docker.kill(container);
        }
        return true;

    }

    @Extension
    public static class Listener extends SCMListener {
        @Override
        public void onChangeLogParsed(Run<?, ?> build, SCM scm, TaskListener listener, ChangeLogSet<?> changelog) throws Exception {
            BuiltInContainer runInContainer = build.getAction(BuiltInContainer.class);
            if (runInContainer != null) runInContainer.enable();
        }
    }

}
