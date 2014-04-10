package com.cloudbees.jenkins.plugins.okidocki;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import org.apache.commons.io.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class Docker {
    
    private final Launcher launcher;
    private final TaskListener listener;

    public Docker(Launcher launcher, TaskListener listener) {
        this.launcher = launcher;
        this.listener = listener;
    }

    public boolean hasImage(String image) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = launcher.launch()
                .cmds("docker", "inspect", image)
                .stdout(out).stderr(err).join();
        return status == 0;
    }

    public void buildImage(FilePath workspace, String tag) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = launcher.launch()
                .pwd(workspace.getRemote())
                .cmds("docker", "build", "-t", tag, ".")
                .stdout(out).stderr(err).join();
        listener.getLogger().println(err.toString());
        if (status != 0) {
            listener.getLogger().println(out.toString());
            throw new RuntimeException("Failed to build docker image from project Dockerfile");
        }
    }

    public String run(String tag, FilePath workspace, String mount) throws IOException, InterruptedException {
        FilePath id = workspace.createTempFile("docker", "id");
        id.delete();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = launcher.launch()
                .cmds("docker", "run", "-t",
                        "-v", workspace.getRemote()+":"+mount,
                        "--cidfile="+id.getRemote(),
                        tag, "/bin/sh"
                        )  // --name buildnumber
                .stdout(out).stderr(err).join();
        listener.getLogger().println(out.toString());
        if (status != 0) {
            listener.getLogger().println(err.toString());
            throw new RuntimeException("Failed to start docker container");
        }

        String containerId = id.readToString();
        id.delete();
        return containerId;

    }

    public void stop(String container) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = launcher.launch()
                .cmds("docker", "stop", container)
                .stdout(out).stderr(err).join();
        status = launcher.launch()
                .cmds("docker", "rm", container)
                .stdout(out).stderr(err).join();
    }
}
