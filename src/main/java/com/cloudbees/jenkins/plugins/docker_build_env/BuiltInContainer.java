package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildBadgeAction;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Used to determine if launcher has to be decorated to execute in container, after SCM checkout completed.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class BuiltInContainer implements BuildBadgeAction, EnvironmentContributingAction {

    /* package */ String image;

    /* package */ transient String container;
    private transient boolean enable;
    private final transient Docker docker;
    private List<Integer> ports = new ArrayList<Integer>();
    private List<String> volumes = new ArrayList<String>();

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
        return "built inside docker container";
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

    public List<Integer> getPorts() {
        return ports;
    }

    public List<String> getVolumes() {
        return volumes;
    }

    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
        if (enable && container != null) {
            env.put("BUILD_CONTAINER_ID", container);
        }
    }

    public void bindMount(String path) {
        volumes.add(path);
    }

    public Map<String, String> getVolumesMap() {
        Map<String, String> map = new HashMap<String, String>();
        for (String path : volumes) {
            map.put(path, path);
        }
        return map;
    }


    public Map<Integer, Integer> getPortsMap() {
        Map<Integer, Integer> map = new HashMap<Integer, Integer>();
        for (Integer port : ports) {
            map.put(port, port);
        }
        return map;
    }

}
