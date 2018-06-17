package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.ExtensionPoint;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Job;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.Collection;

/**
 * Let user configure a Docker image to run a container
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class DockerImageSelector extends AbstractDescribableImpl<DockerImageSelector> implements ExtensionPoint {

    public abstract String prepareDockerImage(Docker docker, AbstractBuild build, TaskListener listener, boolean forcePull, boolean noCache) throws IOException, InterruptedException;

    public abstract Collection<String> getDockerImagesUsedByJob(Job<?, ?> job);
}
