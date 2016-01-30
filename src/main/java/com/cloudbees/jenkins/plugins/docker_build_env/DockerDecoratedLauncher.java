package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.Environment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
* @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
*/
public class DockerDecoratedLauncher extends Launcher.DecoratedLauncher {

    private final DockerImageSelector selector;
    private final BuiltInContainer runInContainer;
    private final AbstractBuild build;
    private final String userId;
    private EnvVars env;
    private final Launcher launcher;

    public DockerDecoratedLauncher(DockerImageSelector selector, Launcher launcher, BuiltInContainer runInContainer,
                                   AbstractBuild build, String userId) throws IOException, InterruptedException {
        super(launcher);
        this.launcher = launcher;
        this.selector = selector;
        this.runInContainer = runInContainer;
        this.build = build;
        this.userId = userId;
    }

    public Proc launch(String[] cmd, boolean[] mask, String[] env, InputStream in, OutputStream out, FilePath workDir) throws IOException {
        return launch(launch().cmds(cmd).masks(mask).envs(env).stdin(in).stdout(out).pwd(workDir));
    }

    @Override
    public Proc launch(ProcStarter starter) throws IOException {

        // Do not decorate launcher until SCM checkout completed
        if (!runInContainer.isEnabled()) return super.launch(starter);

        try {
            EnvVars environment = buildContainerEnvironment();
            runInContainer.getDocker().executeIn(runInContainer.container, userId, starter, environment);
        } catch (InterruptedException e) {
            throw new IOException("Caught InterruptedException", e);
        }

        return super.launch(starter);
    }

    private EnvVars buildContainerEnvironment() throws IOException, InterruptedException {

        if (this.env == null) {
            this.env = runInContainer.getEnvVars();
        }
        return new EnvVars(env);
    }

}
