package com.cloudbees.jenkins.plugins.okidocki;

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.TaskListener;
import hudson.Launcher;
import hudson.remoting.VirtualChannel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Let user configure a Docker image to run a container
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class DockerImageSelector extends AbstractDescribableImpl<DockerImageSelector> implements ExtensionPoint {

    public abstract String prepareDockerImage(Docker docker, AbstractBuild build, TaskListener listener) throws IOException, InterruptedException;

    public String runContainerFortgeBuild(Docker docker, AbstractBuild build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {

        String image = prepareDockerImage(docker, build, listener);

        String tmp;
        try {
            tmp = build.getWorkspace().act(GetTmpdir);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted");
        }

        List<String> cmds = new ArrayList<String>();
        cmds.add("docker");
        cmds.add("run");
        cmds.add("--rm");
        cmds.add("-t");
        // mount workspace under same path in Docker container
        cmds.add("-v");
        cmds.add(build.getWorkspace().getRemote() + ":/var/workspace:rw");
        // mount tmpdir so we can access temporary file created to run shell build steps (and few others)
        cmds.add("-v");
        cmds.add(tmp + ":" + tmp + ":rw");
        cmds.add("sleep 100000"); // find some infinite command to run
        cmds.add(image);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int s = launcher.launch().cmds(cmds).stdout(bos).join();
        if (s == 0) {
            return bos.toString();
        }
        throw new IOException("failed to run container");
    }

    private static FilePath.FileCallable<String> GetTmpdir = new FilePath.FileCallable<String>() {
        @Override
        public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return System.getProperty("java.io.tmpdir");
        }
    };


}
