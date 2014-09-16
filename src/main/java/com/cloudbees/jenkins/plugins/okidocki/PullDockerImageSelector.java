package com.cloudbees.jenkins.plugins.okidocki;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

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
        if (!docker.hasImage(image)) {
            listener.getLogger().println("Pull Docker image "+image+" from repository ...");
            docker.pullImage(image);
        }
        return image;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerImageSelector> {

        @Override
        public String getDisplayName() {
            return "Pull docker image";
        }
    }
}
