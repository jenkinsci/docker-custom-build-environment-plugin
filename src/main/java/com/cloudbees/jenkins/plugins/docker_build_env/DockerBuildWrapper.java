package com.cloudbees.jenkins.plugins.docker_build_env;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.apache.commons.lang.StringUtils.isEmpty;

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

    private List<Volume> volumes;

    private final boolean privileged;

    private String group;

    private String command;

    private final boolean forcePull;

    private String net;

    private String memory;

    private String cpu;

    private final boolean sudo;

    private String protectedEnvironmentVariables;

    public DockerBuildWrapper(DockerImageSelector selector, String dockerInstallation, DockerServerEndpoint dockerHost, String dockerRegistryCredentials, boolean verbose, boolean privileged,
                              List<Volume> volumes, String group, String command,
                              boolean forcePull,
                              String net, String memory, String cpu) {
        this(selector, dockerInstallation, dockerHost, dockerRegistryCredentials, verbose, privileged, volumes, group, command, forcePull, net, memory, cpu, false, "");
    }

    @DataBoundConstructor
    public DockerBuildWrapper(DockerImageSelector selector, String dockerInstallation, DockerServerEndpoint dockerHost, String dockerRegistryCredentials, boolean verbose, boolean privileged,
                              List<Volume> volumes, String group, String command,
                              boolean forcePull,
                              String net, String memory, String cpu, boolean sudo, String protectedEnvironmentVariables) {
        this.selector = selector;
        this.dockerInstallation = dockerInstallation;
        this.dockerHost = dockerHost;
        this.dockerRegistryCredentials = dockerRegistryCredentials;
        this.verbose = verbose;
        this.privileged = privileged;
        this.volumes = volumes != null ? volumes : Collections.<Volume>emptyList();
        this.group = group;
        this.command = command;
        this.forcePull = forcePull;
        this.net = net;
        this.memory = memory;
        this.cpu = cpu;
        this.sudo = sudo;
        this.protectedEnvironmentVariables = protectedEnvironmentVariables;
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

    public boolean isSudo() {
        return sudo;
    }

    public boolean isPrivileged() {
        return privileged;
    }

    public List<Volume> getVolumes() {
        return volumes;
    }

    public String getGroup() {
        return group;
    }

    public String getCommand() {
        return command;
    }

    public String getProtectedEnvironmentVariables() {
        if (protectedEnvironmentVariables == null) {
            return "";
        }
        return protectedEnvironmentVariables;
    }

    public boolean isForcePull() {
        return forcePull;
    }

    public String getNet() { return net;}

    public String getMemory() { return memory;}

    public String getCpu() { return cpu;}

    @Override
    public Launcher decorateLauncher(final AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        final Docker docker = new Docker(dockerHost, dockerInstallation, dockerRegistryCredentials, build, launcher, listener, verbose, privileged, sudo, protectedEnvironmentVariables);

        final BuiltInContainer runInContainer = new BuiltInContainer(docker);
        build.addAction(runInContainer);

        DockerDecoratedLauncher decorated = new DockerDecoratedLauncher(selector, launcher, runInContainer, build, whoAmI(launcher));
        return decorated;
    }

    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {

        // setUp is executed after checkout, so hook here to prepare and run Docker image to host the build

        BuiltInContainer runInContainer = build.getAction(BuiltInContainer.class);

        // mount slave root in Docker container so build process can access project workspace, tools, as well as jars copied by maven plugin.
        final String root = Computer.currentComputer().getNode().getRootPath().getRemote();
        runInContainer.bindMount(root);

        // mount tmpdir so we can access temporary file created to run shell build steps (and few others)
        String tmp = build.getWorkspace().act(GetTmpdir);
        runInContainer.bindMount(tmp);

        // mount ToolIntallers installation directory so installed tools are available inside container

        for (Volume volume : volumes) {
            runInContainer.bindMount(volume.getHostPath(), volume.getPath());
        }

        runInContainer.getDocker().setupCredentials(build);

        if (runInContainer.container == null) {
            if (runInContainer.image == null) {
                try {
                    runInContainer.image = selector.prepareDockerImage(runInContainer.getDocker(), build, listener, forcePull);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted");
                }
            }

            runInContainer.container = startBuildContainer(runInContainer, build, listener);
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



    private String startBuildContainer(BuiltInContainer runInContainer, AbstractBuild build, BuildListener listener) throws IOException {
        try {
            EnvVars environment = buildContainerEnvironment(build, listener);

            String workdir = build.getWorkspace().getRemote();

            Map<String, String> links = new HashMap<String, String>();

            String[] command = this.command.length() > 0 ? this.command.split(" ") : new String[0];

            return runInContainer.getDocker().runDetached(runInContainer.image, workdir,
                    runInContainer.getVolumes(build), runInContainer.getPortsMap(), links,
                    environment, build.getSensitiveBuildVariables(), net, memory, cpu,
                    command); // Command expected to hung until killed

        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted");
        }
    }

    /**
     * Create the container environment.
     * We can't just pass result of {@link AbstractBuild#getEnvironment(TaskListener)}, as this one do include slave host
     * environment, that may not make any sense inside container (consider <code>PATH</code> for sample).
     */
    private EnvVars buildContainerEnvironment(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
        EnvVars env = build.getEnvironment(listener);
        for (String key : Computer.currentComputer().getEnvironment().keySet()) {
            env.remove(key);
        }
        LOGGER.log(Level.FINE, "reduced environment: {0}", env);
        EnvVars.resolve(env);
        return env;
    }

    private String whoAmI(Launcher launcher) throws IOException, InterruptedException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        launcher.launch().cmds("id", "-u").stdout(bos).quiet(true).join();
        String uid = bos.toString().trim();

        String gid = group;
        if (isEmpty(group)) {
            ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
            launcher.launch().cmds("id", "-g").stdout(bos2).quiet(true).join();
            gid = bos2.toString().trim();
        }
        return uid+":"+gid;

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


    private static final Logger LOGGER = Logger.getLogger(DockerBuildWrapper.class.getName());

    // --- backward compatibility

    private transient boolean exposeDocker;

    private Object readResolve() {
        if (volumes == null) volumes = new ArrayList<Volume>();
        if (exposeDocker) {
            this.volumes.add(new Volume("/var/run/docker.sock","/var/run/docker.sock"));
        }
        if (command == null) command = "/bin/cat";
        return this;
    }
}
