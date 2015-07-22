package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerfileImageSelector extends DockerImageSelector {

    private String contextPath;

    private String dockerfile;

    @DataBoundConstructor
    public DockerfileImageSelector(String contextPath, String dockerfile) {
        this.contextPath = contextPath;
        this.dockerfile = dockerfile;
    }

    private FilePath getDockerFilePath(FilePath ctxPath, Docker docker, AbstractBuild build, TaskListener listener) throws IOException, InterruptedException {

        FilePath dfPath = null;

        if (dockerfile != null) {

            String expandedDockerFile = build.getEnvironment(listener).expand(dockerfile);
            dfPath = build.getWorkspace().child(expandedDockerFile);

        } else {

            dfPath = ctxPath.child("DockerFile");

        }

        return dfPath;
    }

    private String getAbsPath(FilePath fp) throws IOException, InterruptedException {

        File f = new File(fp.toURI());

        return f.getAbsolutePath();
    }

    @Override
    public String prepareDockerImage(Docker docker, AbstractBuild build, TaskListener listener) throws IOException, InterruptedException {

        String expandedContextPath = build.getEnvironment(listener).expand(contextPath);
        FilePath ctxPath = build.getWorkspace().child(expandedContextPath);

        FilePath dfPath = getDockerFilePath(ctxPath, docker, build, listener);

        String dfAbsPath = getAbsPath(dfPath);

        String hash = dfPath.getParent().act(new ComputeDockerfileChecksum());

        // search for a tagged image with this hash ID
        //MR: we don't want to do this check
        //MR: The docker build will already cheerfully cache each line item int he docker file
        //MR: but if you use ADD or COPY to bring ina  resource, this check will miss and ignore any
        //MR: changes to  that file.
        //if (!docker.hasImage(hash)) {
            listener.getLogger().println("Build Docker image from "+dfAbsPath+" ...");
            docker.buildImage(ctxPath, dfAbsPath, hash);
        //}

        return hash;
    }

    @Override
    public Collection<String> getDockerImagesUsedByJob(Job<?, ?> job) {
        // TODO get last build and parse Dockerfile "FROM"
        return Collections.EMPTY_LIST;
    }

    public String getContextPath() {
        return contextPath;
    }

    public String getDockerfile() {
        return dockerfile;
    }

    private Object readResolve() {
        if (dockerfile == null) dockerfile="Dockerfile";
        return this;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerImageSelector> {

        @Override
        public String getDisplayName() {
            return "Build from Dockerfile";
        }
    }

    public static class ComputeDockerfileChecksum implements hudson.FilePath.FileCallable<String> {

        public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            MessageDigest md = null;
            try {
                md = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Slave JVM doesn't support SHA-1 MessageDigest");
            }
            // TODO should consider all files in context, not just Dockerfile
            byte[] content = FileUtils.readFileToByteArray(new File(f, "Dockerfile"));
            byte[] digest = md.digest(content);
            Formatter formatter = new Formatter();
            for (byte b : digest) {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        }
    }
}
