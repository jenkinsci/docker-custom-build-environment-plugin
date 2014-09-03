package com.cloudbees.jenkins.plugins.okidocki;

import hudson.Extension;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

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
        return new Environment() {
        };
    }



    @Override
    public Launcher decorateLauncher(final AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        Docker docker = new Docker(launcher, listener);
        final String image = selector.prepareDockerImage(docker, build);
        build.addAction(new DockerBadge(image));
        final RunInContainer runInContainer = new RunInContainer();
        build.addAction(runInContainer);

        return new Launcher.DecoratedLauncher(launcher) {
            @Override
            public Proc launch(ProcStarter starter) throws IOException {

                if (runInContainer.enabled()) {
                    List<String> cmds = new ArrayList<String>();
                    cmds.add("docker");
                    cmds.add("run");
                    cmds.add("-t");
                    cmds.add("-v");
                    cmds.add(build.getWorkspace().getRemote() + ":/var/workspace:rw");
                    cmds.add(image);
                    cmds.addAll(starter.cmds());
                    starter.cmds(cmds);
                }
                return super.launch(starter);
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
            return true;
        }
    }
}
