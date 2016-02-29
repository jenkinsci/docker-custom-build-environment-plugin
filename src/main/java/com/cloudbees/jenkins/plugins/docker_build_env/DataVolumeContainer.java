package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class DataVolumeContainer extends AbstractDescribableImpl<DataVolumeContainer> {

    private final String name;

    @DataBoundConstructor
    public DataVolumeContainer(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DataVolumeContainer> {

        @Override
        public String getDisplayName() {
            return "DataVolumeContainer";
        }
    }
}
