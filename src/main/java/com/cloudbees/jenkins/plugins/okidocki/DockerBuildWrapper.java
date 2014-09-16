package com.cloudbees.jenkins.plugins.okidocki;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Decorate Launcher so that every command executed by a build step is actually ran inside docker container.
 * TODO run docker container during setup, then use docker-enter to attach command to existing container
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerBuildWrapper extends BuildWrapper {

    public DockerImageSelector selector;

    @DataBoundConstructor
    public DockerBuildWrapper(DockerImageSelector selector) {
        this.selector = selector;
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        return new Environment() { };
    }


    @Override
    public Launcher decorateLauncher(final AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        final RunInContainer runInContainer = new RunInContainer();
        build.addAction(runInContainer);

        return new Launcher.DecoratedLauncher(launcher) {
            @Override
            public Proc launch(ProcStarter starter) throws IOException {

                if (!runInContainer.enabled()) return super.launch(starter);

                // TODO only run the container first time, then ns-enter for next commands to execute.

                Docker docker = new Docker(launcher, listener);
                if (runInContainer.image == null) {
                    listener.getLogger().println("Prepare Docker image to host the build environment");
                    try {
                        runInContainer.image = selector.prepareDockerImage(docker, build, listener);
                        build.addAction(new DockerBadge(runInContainer.image));
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Interrupted");
                    }
                }

                String tmp;
                try {
                    tmp = build.getWorkspace().act(GetTmpdir);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted");
                }

                // TODO start the container with a long running command, then use docker-enter
                // would be great to use docker exec as described on https://github.com/docker/docker/issues/1437

                List<String> cmds = new ArrayList<String>();
                cmds.add("docker");
                cmds.add("run");
                cmds.add("-rm");
                cmds.add("-it");
                // mount workspace under same path in Docker container
                cmds.add("-v");
                cmds.add(build.getWorkspace().getRemote() + ":/var/workspace:rw");
                // mount tmpdir so we can access temporary file created to run shell build steps (and few others)
                cmds.add("-v");
                cmds.add(tmp + ":" + tmp + ":rw");
                cmds.add(runInContainer.image);
                cmds.addAll(starter.cmds());
                starter.cmds(cmds);
                return super.launch(starter);
            }
        };
    }

    private static FilePath.FileCallable<String> GetTmpdir = new FilePath.FileCallable<String>() {
        @Override
        public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return System.getProperty("java.io.tmpdir");
        }
    };

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
