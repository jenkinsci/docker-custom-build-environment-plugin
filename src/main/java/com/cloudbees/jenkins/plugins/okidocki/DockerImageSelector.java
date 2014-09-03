package com.cloudbees.jenkins.plugins.okidocki;

import hudson.ExtensionPoint;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;

import java.io.IOException;

/**
 * Let user configure a Docker image to run a container
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class DockerImageSelector extends AbstractDescribableImpl<DockerImageSelector> implements ExtensionPoint {

    public abstract String prepareDockerImage(Docker docker, AbstractBuild build) throws IOException, InterruptedException;



}
