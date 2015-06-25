package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;

import java.io.IOException;
import java.util.Map;

/**
 * Uses <a href="https://github.com/docker-java/docker-java">Docker-java</a> client to interact with docker daemon so there's
 * no need to get docker executable installed on jenkins nodes
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerJava implements Docker {

    @Override
    public void setup(AbstractBuild build) throws IOException, InterruptedException {

    }

    @Override
    public boolean hasImage(String image) throws IOException, InterruptedException {
        return false;
    }

    @Override
    public boolean pullImage(String image) throws IOException, InterruptedException {
        return false;
    }

    @Override
    public void buildImage(FilePath context, String tag) throws IOException, InterruptedException {

    }

    @Override
    public void kill(String container) throws IOException, InterruptedException {

    }

    @Override
    public String runDetached(String image, String workdir, Map<String, String> volumes, Map<Integer, Integer> ports, Map<String, String> links, EnvVars environment, String user, String... command) throws IOException, InterruptedException {
        return null;
    }

    @Override
    public void executeIn(String container, Launcher.ProcStarter starter) {

    }

    @Override
    public void close() throws IOException {

    }
}
