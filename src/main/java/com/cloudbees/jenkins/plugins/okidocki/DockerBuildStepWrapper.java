package com.cloudbees.jenkins.plugins.okidocki;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerBuildStepWrapper extends Builder {

    private final DockerImageSelector selector;

    private final List<BuildStep> builders;

    @DataBoundConstructor
    public DockerBuildStepWrapper(DockerImageSelector selector, List<BuildStep> builders) {
        this.selector = selector;
        this.builders = builders;
    }

    public DockerImageSelector getSelector() {
        return selector;
    }

    public List<BuildStep> getBuilders() {
        return builders;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        final Docker docker = new Docker(launcher, listener);
        BuiltInContainer runInContainer = new BuiltInContainer(docker);
        runInContainer.enable();
        DockerDecoratedLauncher l = new DockerDecoratedLauncher(selector, launcher, runInContainer, docker, build);

        for (BuildStep builder : builders) {
            builder.perform(build, l, listener);
        }

        runInContainer.tearDown();
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Run build steps inside a Docker container";
        }

        public Collection<Descriptor<DockerImageSelector>> getSelectors() {
            return Jenkins.getInstance().getDescriptorList(DockerImageSelector.class);
        }

        public List<BuildStepDescriptor> getBuilderDescriptors(AbstractProject<?, ?> project) {
            List<BuildStepDescriptor> descriptors = new ArrayList<BuildStepDescriptor>();
            for (Descriptor<Builder> d : Builder.all()) {
                if (d instanceof DescriptorImpl) continue;
                if (!(d instanceof BuildStepDescriptor)) continue;
                BuildStepDescriptor bd = (BuildStepDescriptor) d;

                if (bd.isApplicable(project.getClass())) {
                    descriptors.add(bd);
                }
            }
            return descriptors;
        }


    }
}
