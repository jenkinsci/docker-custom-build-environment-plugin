package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.Project;
import org.jenkinsci.plugins.docker.commons.DockerImageExtractor;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension(optional=true)
public class SelectorDockerImageExtractor extends DockerImageExtractor {

    @Nonnull
    @Override
    public Collection<String> getDockerImagesUsedByJob(Job<?, ?> job) {
        if (job instanceof Project) {
            DockerBuildWrapper w = (DockerBuildWrapper) ((Project) job).getBuildWrappersList().get(DockerBuildWrapper.class);
            if (w != null) {
                return w.getSelector().getDockerImagesUsedByJob(job);
            }
        }

        return Collections.emptyList();
    }


}
