package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.docker.commons.credentials.KeyMaterial;
import org.jenkinsci.plugins.docker.commons.credentials.KeyMaterialFactory;
import org.jenkinsci.plugins.docker.commons.tools.DockerTool;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class Docker implements Closeable {

    private static boolean debug = Boolean.getBoolean(Docker.class.getName()+".debug");
    private final Launcher launcher;
    private final TaskListener listener;
    private final String dockerExecutable;
    private final DockerServerEndpoint dockerHost;
    private final DockerRegistryEndpoint registryEndpoint;
    private final boolean verbose;

    public Docker(DockerServerEndpoint dockerHost, String dockerInstallation, String credentialsId, AbstractBuild build, Launcher launcher, TaskListener listener, boolean verbose) throws IOException, InterruptedException {
        this.dockerHost = dockerHost;
        this.dockerExecutable = DockerTool.getExecutable(dockerInstallation, Computer.currentComputer().getNode(), listener, build.getEnvironment(listener));
        this.registryEndpoint = new DockerRegistryEndpoint(null, credentialsId);
        this.launcher = launcher;
        this.listener = listener;
        this.verbose = verbose | debug;
    }


    private KeyMaterial dockerEnv;
    private KeyMaterial dockerConfig;

    public void setupCredentials(AbstractBuild build) throws IOException, InterruptedException {
        this.dockerEnv = dockerHost.newKeyMaterialFactory(build).materialize();
        this.dockerConfig = registryEndpoint.newKeyMaterialFactory(build).materialize();
    }


    @Override
    public void close() throws IOException {
        dockerEnv.close();
        dockerConfig.close();
    }

    public boolean hasImage(String image) throws IOException, InterruptedException {
        OutputStream out = verbose ? listener.getLogger() : new ByteArrayOutputStream();
        OutputStream err = verbose ? listener.getLogger() : new ByteArrayOutputStream();

        int status = launcher.launch()
                .envs(dockerEnv.env())
                .cmds(dockerExecutable, "-H", dockerHost.getDockerHost(), "inspect", image)
                .stdout(out).stderr(err).quiet(!verbose).join();
        return status == 0;
    }

    public boolean pullImage(String image) throws IOException, InterruptedException {
        OutputStream out = verbose ? listener.getLogger() : new ByteArrayOutputStream();
        OutputStream err = verbose ? listener.getLogger() : new ByteArrayOutputStream();
        int status = launcher.launch()
                .envs(dockerEnv.env())
                .cmds(dockerExecutable, "-H", dockerHost.getDockerHost(), "pull", image)
                .stdout(out).stderr(err).join();
        return status == 0;
    }


    public void buildImage(FilePath workspace, String dockerfile, String tag) throws IOException, InterruptedException {
        OutputStream out = listener.getLogger();
        OutputStream err = listener.getLogger();

        int status = launcher.launch()
                .envs(dockerEnv.env())
                .cmds(dockerExecutable, "-H", dockerHost.getDockerHost(), "build", "--tag", tag)
                .cmds("--file", dockerfile)
                .cmds(workspace.getRemote())
                .stdout(out).stderr(err).join();
        if (status != 0) {
            throw new RuntimeException("Failed to build docker image from project Dockerfile");
        }
    }

    public void kill(String container) throws IOException, InterruptedException {
        OutputStream out = verbose ? listener.getLogger() : new ByteArrayOutputStream();
        OutputStream err = verbose ? listener.getLogger() : new ByteArrayOutputStream();
        listener.getLogger().println("Stopping Docker container after build completion");
        int status = launcher.launch()
                .envs(dockerEnv.env())
                .cmds(dockerExecutable, "-H", dockerHost.getDockerHost(), "kill", container)
                .stdout(out).stderr(err).quiet(!verbose).join();
        if (status != 0)
            throw new RuntimeException("Failed to stop docker container "+container);

        status = launcher.launch()
                .envs(dockerEnv.env())
                .cmds(dockerExecutable, "-H", dockerHost.getDockerHost(), "rm", container)
                .stdout(out).stderr(err).quiet(!verbose).join();
        if (status != 0)
            throw new RuntimeException("Failed to remove docker container "+container);
    }

    public String runDetached(String image, String workdir, Map<String, String> volumes, Map<Integer, Integer> ports, Map<String, String> links, EnvVars environment, String user, String... command) throws IOException, InterruptedException {

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(dockerExecutable, "-H", dockerHost.getDockerHost(), "run", "--tty", "--detach");
        args.add("--user", user);
        args.add( "--workdir", workdir);
        for (Map.Entry<String, String> volume : volumes.entrySet()) {
            args.add("--volume", volume.getKey() + ":" + volume.getValue() + ":rw" );
        }
        for (Map.Entry<Integer, Integer> port : ports.entrySet()) {
            args.add("--publish", port.getKey() + ":" + port.getValue());
        }
        for (Map.Entry<String, String> link : links.entrySet()) {
            args.add("--link", link.getKey() + ":" + link.getValue());
        }
        for (Map.Entry<String, String> e : environment.entrySet()) {
            if ("HOSTNAME".equals(e.getKey())) {
                continue;
            }
            args.add("--env");
            args.addMasked(e.getKey()+"="+e.getValue());
        }
        args.add(image).add(command);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int status = launcher.launch()
                .envs(dockerEnv.env())
                .cmds(args).stdout(out).quiet(!verbose).stderr(listener.getLogger()).join();

        if (status != 0) {
            throw new RuntimeException("Failed to run docker image");
        }
        return out.toString("UTF-8").trim();
    }

    public void executeIn(String container, Launcher.ProcStarter starter) {
        List<String> originalCmds = starter.cmds();

        ArgumentListBuilder cmdBuilder = new ArgumentListBuilder();
        cmdBuilder.add(dockerExecutable, "-H", dockerHost.getDockerHost(), "exec", "--tty", container);

        boolean[] originalMask = starter.masks();
        for (int i = 0; i < originalCmds.size(); i++) {
            boolean masked = originalMask == null ? false : i < originalMask.length ? originalMask[i] : false;
            cmdBuilder.add(originalCmds.get(i), masked);
        }

        starter.cmds(cmdBuilder);
        starter.envs(dockerEnv.env());
    }
}
