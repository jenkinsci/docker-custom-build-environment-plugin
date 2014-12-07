package com.cloudbees.jenkins.plugins.okidocki;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class Docker {

    private static boolean debug = Boolean.getBoolean(Docker.class.getName()+".debug");
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
                .stdout(out).stderr(err).quiet(!debug).join();
        return status == 0;
    }

    public boolean pullImage(String image) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = launcher.launch()
                .cmds("docker", "pull", image)
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

    public void kill(String container) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = launcher.launch()
                .cmds("docker", "kill", container)
                .stdout(out).stderr(err).quiet(!debug).join();
        if (status != 0)
            throw new RuntimeException("Failed to stop docker container "+container);

        status = launcher.launch()
                .cmds("docker", "rm", container)
                .stdout(out).stderr(err).quiet(!debug).join();
        if (status != 0)
            throw new RuntimeException("Failed to remove docker container "+container);
    }

    public String runDetached(String image, String workdir, Map<String, String> volumes, Collection<String> volumesFrom, EnvVars environment, String user, String... command) throws IOException, InterruptedException {

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("docker", "run", "-t", "-d", "-u", user, "-w", workdir);
        for (Map.Entry<String, String> volume : volumes.entrySet()) {
            args.add("-v", volume.getKey() + ":" + volume.getValue() + ":rw" );
        }
        for (Map.Entry<String, String> e : environment.entrySet()) {
            args.add("-e");
            args.addMasked(e.getKey()+"="+e.getValue());
        }
        for (String container : volumesFrom) {
            args.add("--volumes-from");
            args.add(container);
        }
        args.add(image).add(command);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int status = launcher.launch()
                .cmds(args).stdout(out).quiet(!debug).stderr(listener.getLogger()).join();

        if (status != 0) {
            throw new RuntimeException("Failed to run docker image");
        }
        return out.toString("UTF-8").trim();
    }
}
