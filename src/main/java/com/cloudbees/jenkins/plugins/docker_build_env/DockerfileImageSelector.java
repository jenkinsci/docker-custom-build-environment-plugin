package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.Extension;
import hudson.FilePath;
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
public class DockerfileImageSelector extends DockerImageSelector {

    private String contextPath;

    private String dockerfile;

    @DataBoundConstructor
    public DockerfileImageSelector(String contextPath, String dockerfile) {
        this.contextPath = contextPath;
        this.dockerfile = dockerfile;
    }

    @Override
    public String prepareDockerImage(Docker docker, AbstractBuild build, TaskListener listener, boolean forcePull) throws IOException, InterruptedException {

        String expandedContextPath = build.getEnvironment(listener).expand(contextPath);
        FilePath filePath = build.getWorkspace().child(expandedContextPath);

        listener.getLogger().println("Build Docker image from " + expandedContextPath + "/Dockerfile ...");
        return docker.buildImage(filePath, dockerfile, forcePull);
    }

    @Override
    public Collection<String> getDockerImagesUsedByJob(Job<?, ?> job) {
        // TODO get last build and parse Dockerfile "FROM"
        return Collections.EMPTY_LIST;
    }

    public String getContextPath() {
        return contextPath;
    }

    public String getDockerfile() {
        return dockerfile;
    }

    private Object readResolve() {
        if (dockerfile == null) dockerfile="Dockerfile";
        return this;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerImageSelector> {

        @Override
        public String getDisplayName() {
            return "Build from Dockerfile";
        }
    }
}
