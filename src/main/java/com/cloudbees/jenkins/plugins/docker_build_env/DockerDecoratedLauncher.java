package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.remoting.VirtualChannel;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
* @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
*/
public class DockerDecoratedLauncher extends Launcher.DecoratedLauncher {

    private final DockerImageSelector selector;
    private final BuiltInContainer runInContainer;
    private final AbstractBuild build;
    private final String userId;

    public DockerDecoratedLauncher(DockerImageSelector selector, Launcher launcher, BuiltInContainer runInContainer, AbstractBuild build) throws IOException, InterruptedException {
        super(launcher);
        this.selector = selector;
        this.runInContainer = runInContainer;
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
                runInContainer.image = selector.prepareDockerImage(runInContainer.getDocker(), build, listener);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted");
            }
        }

        if (runInContainer.container == null) {
            startBuildContainer();
            listener.getLogger().println("Docker container " + runInContainer.container + " started to host the build");
        }

        runInContainer.getDocker().executeIn(runInContainer.container, starter);

        return super.launch(starter);
    }

    private void startBuildContainer() throws IOException {
        try {
            EnvVars environment = build.getEnvironment(listener);

            String workdir = build.getWorkspace().getRemote();

            Map<String, String> links = new HashMap<String, String>();

            runInContainer.container =
                    runInContainer.getDocker().runDetached(runInContainer.image, workdir,
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

    private static FilePath.FileCallable<String> GetTmpdir = new FilePath.FileCallable<String>() {
        public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return System.getProperty("java.io.tmpdir");
        }
    };
}
