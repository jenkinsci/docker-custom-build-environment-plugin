package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Collection;

/**
 * Decorate Launcher so that every command executed by a build step is actually ran inside docker container.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerBuildWrapper extends BuildWrapper {

    private final DockerImageSelector selector;

    private final String dockerInstallation;

    private final DockerServerEndpoint dockerHost;

    private final boolean verbose;


    @DataBoundConstructor
    public DockerBuildWrapper(DockerImageSelector selector, String dockerInstallation, DockerServerEndpoint dockerHost, boolean verbose) {
        this.selector = selector;
        this.dockerInstallation = dockerInstallation;
        this.dockerHost = dockerHost;
        this.verbose = verbose;
    }

    public DockerImageSelector getSelector() {
        return selector;
    }

    public String getDockerInstallation() {
        return dockerInstallation;
    }

    public DockerServerEndpoint getDockerHost() {
        return dockerHost;
    }

    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public Launcher decorateLauncher(final AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        final Docker docker = Docker.Factory.get(dockerHost, dockerInstallation, build, launcher, listener, verbose);
        final BuiltInContainer runInContainer = new BuiltInContainer(docker);
        build.addAction(runInContainer);

        DockerDecoratedLauncher decorated = new DockerDecoratedLauncher(selector, launcher, runInContainer, build);
        return decorated;
    }

    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {

        build.getAction(BuiltInContainer.class).getDocker().setup(build);

        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                return build.getAction(BuiltInContainer.class).tearDown();
            }
        };
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        @Override
        public String getDisplayName() {
            return "Build inside a Docker container";
        }

        public Collection<Descriptor<DockerImageSelector>> selectors() {
            return Jenkins.getInstance().getDescriptorList(DockerImageSelector.class);
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            if (Jenkins.getInstance().getPlugin("maven-plugin") != null)
                return ! hudson.maven.AbstractMavenProject.class.isInstance(item);
            return true;
        }

    }

}
