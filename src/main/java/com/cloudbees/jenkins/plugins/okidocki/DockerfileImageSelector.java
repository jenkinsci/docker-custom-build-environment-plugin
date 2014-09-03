package com.cloudbees.jenkins.plugins.okidocki;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.remoting.VirtualChannel;
import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerfileImageSelector extends DockerImageSelector {

    private String dockerfile;

    @DataBoundConstructor
    public DockerfileImageSelector(String dockerfile) {
        this.dockerfile = dockerfile;
    }

    @Override
    public String prepareDockerImage(Docker docker, AbstractBuild build) throws IOException, InterruptedException {

        FilePath filePath = build.getWorkspace().child(dockerfile);

        String hash = build.getWorkspace().act(new ComputeChecksum());

        // search for a tagged image with this hash ID
        if (!docker.hasImage(hash)) {
            docker.buildImage(build.getWorkspace(), hash);
        }

        return hash;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerImageSelector> {

        @Override
        public String getDisplayName() {
            return "Build from Dockerfile";
        }
    }

    public static class ComputeChecksum implements hudson.FilePath.FileCallable<String> {

        public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            MessageDigest md = null;
            try {
                md = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Slave JVM doesn't support SHA-1 MessageDigest");
            }
            byte[] content = FileUtils.readFileToByteArray(f);
            byte[] digest = md.digest(content);
            Formatter formatter = new Formatter();
            for (byte b : digest) {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        }
    }
}
