package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class Volume extends AbstractDescribableImpl<Volume> {

    private final String hostPath;
    private final String path;

    @DataBoundConstructor
    public Volume(String hostPath, String path) {
        this.hostPath = hostPath;
        this.path = path;
    }

    public String getHostPath() {
        return hostPath;
    }

    public String getPath() {
        return path;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Volume> {

        @Override
        public String getDisplayName() {
            return "Volume";
        }
    }
}
