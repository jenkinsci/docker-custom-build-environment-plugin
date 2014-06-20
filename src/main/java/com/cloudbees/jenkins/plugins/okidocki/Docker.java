package com.cloudbees.jenkins.plugins.okidocki;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import org.apache.commons.io.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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

        int status = launcher.launch()
                .pwd(workspace.getRemote())
                .cmds("docker", "build", "-t", tag, ".")
                .stdout(listener.getLogger()).stderr(listener.getLogger()).join();
        if (status != 0) {
            throw new RuntimeException("Failed to build docker image from project Dockerfile");
        }
    }

    public void run(String image, FilePath workspace, String user, String command) throws IOException, InterruptedException {

        int status = launcher.launch()
                .cmds("docker", "run", "-t",
                        "-v", workspace.getRemote()+":/var/workspace:rw",
                        image, "/var/workspace/" + command
                        )
                .writeStdin().stdout(listener.getLogger()).stderr(listener.getLogger()).join();

        if (status != 0) {
            throw new RuntimeException("Failed to run docker image");
        }
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
