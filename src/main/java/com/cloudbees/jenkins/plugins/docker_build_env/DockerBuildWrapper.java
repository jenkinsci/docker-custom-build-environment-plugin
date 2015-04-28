package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Collection;

/**
 * Decorate Launcher so that every command executed by a build step is actually ran inside docker container.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerBuildWrapper extends BuildWrapper {

    private final DockerImageSelector selector;

    @DataBoundConstructor
    public DockerBuildWrapper(DockerImageSelector selector) {
        this.selector = selector;
    }

    public DockerImageSelector getSelector() {
        return selector;
    }

    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {

        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                return build.getAction(BuiltInContainer.class).tearDown();
            }
        };
    }

    @Override
    public Launcher decorateLauncher(final AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {

        final Docker docker = new Docker(launcher, listener);
        final BuiltInContainer runInContainer = new BuiltInContainer(docker);
        build.addAction(runInContainer);

        DockerDecoratedLauncher decorated = new DockerDecoratedLauncher(selector, launcher, runInContainer, docker, build);
        return decorated;
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
            return true;
        }
    }

}
