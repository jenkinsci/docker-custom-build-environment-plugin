package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

/**
 * Define interactions we have with a Docker daemon.
 * <p>
 * Implementation is setup for a specific Build and can't be reused
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public interface Docker extends Closeable {

    public static class Factory {
        public static Docker get(DockerServerEndpoint dockerHost, String dockerInstallation, AbstractBuild build, Launcher launcher, TaskListener listener, boolean verbose)
                throws IOException, InterruptedException {
            return new DockerCli(dockerHost, dockerInstallation, build, launcher, listener, verbose);
        }
    }


    /**
     * Prepare this Docker client according to a specific {@link hudson.model.AbstractBuild} configuration
     */
    void setup(AbstractBuild build) throws IOException, InterruptedException;

    /**
     * Check the specified Docker image is available on docker local cache
     */
    boolean hasImage(String image) throws IOException, InterruptedException;

    /**
     * Pull specified image in the docker local cache
     */
    boolean pullImage(String image) throws IOException, InterruptedException;

    /**
     * Build a fresh new image based on a Dockerfile context
     */
    void buildImage(FilePath context, String tag) throws IOException, InterruptedException;

    /**
     * Kills specified container to ensure clean state after build completion
     */
    void kill(String container) throws IOException, InterruptedException;

    String runDetached(String image, String workdir, Map<String, String> volumes, Map<Integer, Integer> ports, Map<String, String> links, EnvVars environment, String user, String... command) throws IOException, InterruptedException;

    void executeIn(String container, Launcher.ProcStarter starter);

    @Override
    void close() throws IOException;

}
