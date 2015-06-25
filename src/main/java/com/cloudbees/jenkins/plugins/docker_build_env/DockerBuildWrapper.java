package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
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

    private final boolean exposeDocker;


    @DataBoundConstructor
    public DockerBuildWrapper(DockerImageSelector selector, String dockerInstallation, DockerServerEndpoint dockerHost, boolean verbose, boolean exposeDocker) {
        this.selector = selector;
        this.dockerInstallation = dockerInstallation;
        this.dockerHost = dockerHost;
        this.verbose = verbose;
        this.exposeDocker = exposeDocker;
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

    public boolean isExposeDocker() {
        return exposeDocker;
    }

    @Override
    public Launcher decorateLauncher(final AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        final Docker docker = new Docker(dockerHost, dockerInstallation, build, launcher, listener, verbose);
        final BuiltInContainer runInContainer = new BuiltInContainer(docker);
        build.addAction(runInContainer);

        if (exposeDocker) {
            runInContainer.bindMount("/var/run/docker.sock");
        }

        DockerDecoratedLauncher decorated = new DockerDecoratedLauncher(selector, launcher, runInContainer, build);
        return decorated;
    }

    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {

        BuiltInContainer runInContainer = build.getAction(BuiltInContainer.class);

        // mount workspace in Docker container
        // use same path in slave and container so `$WORKSPACE` used in scripts will match
        String workdir = build.getWorkspace().getRemote();
        runInContainer.bindMount(workdir);

        // mount tmpdir so we can access temporary file created to run shell build steps (and few others)
        String tmp = build.getWorkspace().act(GetTmpdir);
        runInContainer.bindMount(tmp);

        runInContainer.getDocker().setupCredentials(build);

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

    private static FilePath.FileCallable<String> GetTmpdir = new FilePath.FileCallable<String>() {
        public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return System.getProperty("java.io.tmpdir");
        }
    };


}
