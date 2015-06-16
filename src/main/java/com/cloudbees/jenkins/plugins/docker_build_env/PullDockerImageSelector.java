package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class PullDockerImageSelector extends DockerImageSelector {

    public String image;

    @DataBoundConstructor
    public PullDockerImageSelector(String image) {
        this.image = image;
    }

    @Override
    public String prepareDockerImage(Docker docker, AbstractBuild build, TaskListener listener) throws IOException, InterruptedException {
        String expandedImage = build.getEnvironment(listener).expand(image);
        if (!docker.hasImage(expandedImage)) {
            listener.getLogger().println("Pull Docker image "+expandedImage+" from repository ...");
            docker.pullImage(expandedImage);
        }
        return expandedImage;
    }

    @Override
    public Collection<String> getDockerImagesUsedByJob(Job<?, ?> job) {
        return Collections.singleton(image);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerImageSelector> {

        @Override
        public String getDisplayName() {
            return "Pull docker image from repository";
        }
    }
}
