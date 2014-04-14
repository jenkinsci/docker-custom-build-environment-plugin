package com.cloudbees.jenkins.plugins.okidocki;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerBuildWrapper extends BuildWrapper {

    public final String mountPoint;

    @DataBoundConstructor
    public DockerBuildWrapper(String mountPoint) {
        this.mountPoint = mountPoint;
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {

        String hash = build.getWorkspace().act(new ComputeDockerfileChecksum(listener));
        LOGGER.fine("Dockerfile hash is " + hash);
        final Docker docker = new Docker(launcher, listener);

        // search for a tagged image with this hash ID
        if (!docker.hasImage(hash)) {
            listener.getLogger().println("Building a Docker image from Dockerfile");
            docker.buildImage(build.getWorkspace(), hash);
        }

        listener.getLogger().println("Starting a Docker container to host the build");
        // final Container container = docker.run(hash, build.getWorkspace());

        return null;
     }

    @Extension
    public final static class DescriptorImpl extends BuildWrapperDescriptor {

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Setup build environment from Dockerfile";
        }
    }

    public static final Logger LOGGER = Logger.getLogger(DockerBuildWrapper.class.getName());
}
