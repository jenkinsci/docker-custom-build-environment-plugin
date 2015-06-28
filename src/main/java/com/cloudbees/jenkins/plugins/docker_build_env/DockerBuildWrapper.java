package com.cloudbees.jenkins.plugins.docker_build_env;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.remoting.Callable;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryToken;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Decorate Launcher so that every command executed by a build step is actually ran inside docker container.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerBuildWrapper extends BuildWrapper {

    private final DockerImageSelector selector;

    private final String dockerInstallation;

    private final DockerServerEndpoint dockerHost;

    private final String dockerRegistryCredentials;

    private final boolean verbose;

    private final boolean exposeDocker;


    @DataBoundConstructor
    public DockerBuildWrapper(DockerImageSelector selector, String dockerInstallation, DockerServerEndpoint dockerHost, String dockerRegistryCredentials, boolean verbose, boolean exposeDocker) {
        this.selector = selector;
        this.dockerInstallation = dockerInstallation;
        this.dockerHost = dockerHost;
        this.dockerRegistryCredentials = dockerRegistryCredentials;
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

    public String getDockerRegistryCredentials() {
        return dockerRegistryCredentials;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isExposeDocker() {
        return exposeDocker;
    }

    @Override
    public Launcher decorateLauncher(final AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        final Docker docker = new Docker(dockerHost, dockerInstallation, dockerRegistryCredentials, build, launcher, listener, verbose);
        final BuiltInContainer runInContainer = new BuiltInContainer(docker);
        build.addAction(runInContainer);

        DockerDecoratedLauncher decorated = new DockerDecoratedLauncher(selector, launcher, runInContainer, build);
        return decorated;
    }

    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {

        // setUp is executed after checkout, so hook here to prepare and run Docker image to host the build

        BuiltInContainer runInContainer = build.getAction(BuiltInContainer.class);

        // mount workspace in Docker container
        // use same path in slave and container so `$WORKSPACE` used in scripts will match
        String workdir = build.getWorkspace().getRemote();
        runInContainer.bindMount(workdir);

        // mount tmpdir so we can access temporary file created to run shell build steps (and few others)
        String tmp = build.getWorkspace().act(GetTmpdir);
        runInContainer.bindMount(tmp);

        if (exposeDocker) {
            runInContainer.bindMount("/var/run/docker.sock");
        }

        runInContainer.getDocker().setupCredentials(build);

        if (runInContainer.container == null) {
            if (runInContainer.image == null) {
                try {
                    runInContainer.image = selector.prepareDockerImage(runInContainer.getDocker(), build, listener);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted");
                }
            }

            runInContainer.container = startBuildContainer(runInContainer, build, listener, whoAmI(launcher));
            listener.getLogger().println("Docker container " + runInContainer.container + " started to host the build");
        }

        // We are all set, DockerDecoratedLauncher now can wrap launcher commands with docker-exec
        runInContainer.enable();

        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                return build.getAction(BuiltInContainer.class).tearDown();
            }
        };
    }



    private String startBuildContainer(BuiltInContainer runInContainer, AbstractBuild build, BuildListener listener, String userId) throws IOException {
        try {
            EnvVars environment = build.getEnvironment(listener);

            String workdir = build.getWorkspace().getRemote();

            Map<String, String> links = new HashMap<String, String>();

            return runInContainer.getDocker().runDetached(runInContainer.image, workdir,
                    runInContainer.getVolumesMap(), runInContainer.getPortsMap(), links, environment, userId,
                    "/bin/cat"); // Command expected to hung until killed

        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted");
        }
    }

    private String whoAmI(Launcher launcher) throws IOException, InterruptedException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        launcher.launch().cmds("id", "-u").stdout(bos).quiet(true).join();

        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        launcher.launch().cmds("id", "-g").stdout(bos2).quiet(true).join();
        return bos.toString().trim()+":"+bos2.toString().trim();

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

        public ListBoxModel doFillDockerRegistryCredentialsItems(@AncestorInPath Item item, @QueryParameter String uri) {
            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withMatching(AuthenticationTokens.matcher(DockerRegistryToken.class),
                            CredentialsProvider.lookupCredentials(
                                    StandardCredentials.class,
                                    item,
                                    null,
                                    Collections.<DomainRequirement>emptyList()
                            )
                    );

        }

    }

    private static Callable<String, IOException> GetTmpdir = new MasterToSlaveCallable<String, IOException>() {
        @Override
        public String call() {
            return System.getProperty("java.io.tmpdir");
        }
    };

}
