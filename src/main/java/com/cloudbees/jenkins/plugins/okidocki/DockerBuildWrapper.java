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

import java.io.ByteArrayOutputStream;
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
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {

        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                BuiltInContainer action = build.getAction(BuiltInContainer.class);
                if (action.container != null) {
                    action.enable = false;
                    listener.getLogger().println("Killing build container");
                    launcher.launch().cmds("docker", "kill", action.container).join();
                    launcher.launch().cmds("docker", "rm", action.container).join();
                }
                return true;
            }
        };
    }


    @Override
    public Launcher decorateLauncher(final AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        final BuiltInContainer runInContainer = new BuiltInContainer();
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
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Interrupted");
                    }
                }

                if (runInContainer.container == null) {
                    try {
                        String tmp = build.getWorkspace().act(GetTmpdir);

                        List<String> cmds = new ArrayList<String>();
                        cmds.add("docker");
                        cmds.add("run");
                        cmds.add("-d");
                        // mount workspace under same path in Docker container
                        cmds.add("-v");
                        cmds.add(build.getWorkspace().getRemote() + ":/var/workspace:rw");
                        // mount tmpdir so we can access temporary file created to run shell build steps (and few others)
                        cmds.add("-v");
                        cmds.add(tmp + ":" + tmp + ":rw");
                        cmds.add(runInContainer.image);
                        cmds.add("sleep"); cmds.add("100000"); // find some infinite command to run
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        int s = launcher.launch().cmds(cmds).stdout(bos).join();
                        if (s != 0) {
                            throw new IOException("failed to run container");
                        }
                        runInContainer.container = bos.toString().trim();
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Interrupted");
                    }
                }

                List<String> cmds = new ArrayList<String>();
                cmds.add("docker");
                cmds.add("exec");
                cmds.add("-t");
                cmds.add(runInContainer.container);
                cmds.addAll(starter.cmds());
                starter.cmds(cmds);
                return super.launch(starter);
            }
        };
    }

    private static FilePath.FileCallable<String> GetTmpdir = new FilePath.FileCallable<String>() {
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
