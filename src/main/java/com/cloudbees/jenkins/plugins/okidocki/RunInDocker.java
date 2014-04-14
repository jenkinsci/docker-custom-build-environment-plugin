package com.cloudbees.jenkins.plugins.okidocki;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.Builder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class RunInDocker extends Builder {

    private final String script;

    private final String user;

    @DataBoundConstructor
    public RunInDocker(String script, String user) {
        this.script = script;
        this.user = user;
    }

    public String getScript() {
        return script;
    }

    public String getUser() {
        return user;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        String hash = build.getWorkspace().act(new ComputeDockerfileChecksum(listener));
        final Docker docker = new Docker(launcher, listener);

        // search for a tagged image with this hash ID
        if (!docker.hasImage(hash)) {
            listener.getLogger().println("Building a Docker image from Dockerfile");
            docker.buildImage(build.getWorkspace(), hash);
        }

        build.addAction(new DockerBadge(hash));
        listener.getLogger().println("Starting a Docker container to host the build");
        docker.run(hash, build.getWorkspace(), user, script);

        return true;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Builder> {

        @Override
        public String getDisplayName() {
            return "Run in Docker container";
        }
    }

}
