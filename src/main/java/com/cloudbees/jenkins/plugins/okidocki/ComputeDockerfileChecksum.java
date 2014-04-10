package com.cloudbees.jenkins.plugins.okidocki;

import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ComputeDockerfileChecksum implements hudson.FilePath.FileCallable<String> {

    private final BuildListener listener;

    public ComputeDockerfileChecksum(BuildListener listener) {
        this.listener = listener;
    }

    public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Slave JVM doesn't support SHA-1 MessageDigest");
        }
        File dockerfile = new File(f, "Dockerfile");
        if (!dockerfile.exists()) {
            listener.getLogger().println("Your project is missing a Dockerfile");
            throw new InterruptedException("Your project is missing a Dockerfile");
        }
        byte[] content = FileUtils.readFileToByteArray(dockerfile);
        byte[] digest = md.digest(content);
        Formatter formatter = new Formatter();
        for (byte b : digest) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
}
