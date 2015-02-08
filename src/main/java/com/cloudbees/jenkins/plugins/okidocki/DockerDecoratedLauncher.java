package com.cloudbees.jenkins.plugins.okidocki;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.remoting.VirtualChannel;
import hudson.util.ArgumentListBuilder;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
*/
public class DockerDecoratedLauncher extends Launcher.DecoratedLauncher {

    private final DockerImageSelector selector;
    private final BuiltInContainer runInContainer;
    private final Docker docker;
    private final AbstractBuild build;
    private final String userId;

    public DockerDecoratedLauncher(DockerImageSelector selector, Launcher launcher, BuiltInContainer runInContainer, Docker docker, AbstractBuild build) throws IOException, InterruptedException {
        super(launcher);
        this.selector = selector;
        this.runInContainer = runInContainer;
        this.docker = docker;
        this.build = build;
        this.userId = whoAmI(launcher);
    }


    public Proc launch(String[] cmd, boolean[] mask, String[] env, InputStream in, OutputStream out, FilePath workDir) throws IOException {
        return launch(launch().cmds(cmd).masks(mask).envs(env).stdin(in).stdout(out).pwd(workDir));
    }

    @Override
    public Proc launch(ProcStarter starter) throws IOException {

        if (!runInContainer.isEnabled()) return super.launch(starter);

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
        ArgumentListBuilder cmdBuilder = new ArgumentListBuilder();
        cmdBuilder.add("docker", "exec", "-t", runInContainer.container);

        List<String> originalCmds = starter.cmds();
        boolean[] originalMask = starter.masks();
        for (int i = 0; i < originalCmds.size(); i++) {
            boolean masked = originalMask == null ? false : i < originalMask.length ? originalMask[i] : false;
            cmdBuilder.add(originalCmds.get(i), masked);
        }

        starter.cmds(cmdBuilder);
        return super.launch(starter);
    }

    private void startBuildContainer() throws IOException {
        try {
            String tmp = build.getWorkspace().act(GetTmpdir);
            EnvVars environment = build.getEnvironment(listener);

            Map<String, String> volumes = new HashMap<String, String>();

            // mount workspace in Docker container
            // use same path in slave and container so `$WORKSPACE` used in scripts will match
            String workdir = build.getWorkspace().getRemote();
            volumes.put(workdir, workdir);

            // mount tmpdir so we can access temporary file created to run shell build steps (and few others)
            volumes.put(tmp,tmp);

            runInContainer.container =
                docker.runDetached(runInContainer.image, workdir, volumes, environment, userId,
                        "cat"); // Command expected to hung until killed

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

    private static FilePath.FileCallable<String> GetTmpdir = new FilePath.FileCallable<String>() {
        public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return System.getProperty("java.io.tmpdir");
        }
    };
}
