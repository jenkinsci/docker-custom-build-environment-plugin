package com.cloudbees.jenkins.plugins.okidocki;

import hudson.EnvVars;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                return build.getAction(BuiltInContainer.class).tearDown();
            }
        };
    }


    @Override
    public Launcher decorateLauncher(final AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        final Docker docker = new Docker(launcher, listener);
        final BuiltInContainer runInContainer = new BuiltInContainer(docker);
        build.addAction(runInContainer);

        final String userId = whoAmI(launcher);

        return new Launcher.DecoratedLauncher(launcher) {

            @Override
            public Proc launch(ProcStarter starter) throws IOException {

                if (!runInContainer.enabled()) return super.launch(starter);

                // TODO only run the container first time, then ns-enter for next commands to execute.

                if (runInContainer.image == null) {
                    try {
                        runInContainer.image = selector.prepareDockerImage(docker, build, listener);
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Interrupted");
                    }
                }

                if (runInContainer.container == null) {
                    startBuildContainer();
                    listener.getLogger().println("Docker container " + runInContainer.container + " started to host the build");
                }

                // TODO need some way to know the command execution status, see https://github.com/docker/docker/issues/8703
                List<String> cmds = new ArrayList<String>();
                cmds.add("docker");
                cmds.add("exec");
                cmds.add("-t");
                cmds.add(runInContainer.container);
                cmds.addAll(starter.cmds());
                starter.cmds(cmds);
                return super.launch(starter);
            }

            private void startBuildContainer() throws IOException {
                try {
                    String tmp = build.getWorkspace().act(GetTmpdir);
                    EnvVars environment = build.getEnvironment(listener);

                    Map<String, String> volumes = new HashMap<String, String>();
                    // mount workspace in Docker container
                    String workdir = build.getWorkspace().getRemote();
                    volumes.put(workdir, "/var/workspace");
                    // mount tmpdir so we can access temporary file created to run shell build steps (and few others)
                    volumes.put(tmp,tmp);

                    runInContainer.container =
                        docker.runDetached(runInContainer.image, workdir, volumes, environment, userId,
                                "sleep", "100000"); // TODO use a better long running command

                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted");
                }
            }
        };
    }

    private String whoAmI(Launcher launcher) throws IOException, InterruptedException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        launcher.launch().cmds("id", "-u").stdout(bos).quiet(true).join();

        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        launcher.launch().cmds("id", "-g").stdout(bos2).quiet(true).join();
        return bos.toString().trim()+":"+bos2.toString().trim();

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
