package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.Shell;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class DockerEnvironmentTests {
    @Rule  // @ClassRule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void overridesDefaultWorkdirToMatchJenkins() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();

        project.getBuildWrappersList().add(
                new DockerBuildWrapper(
                        new PullDockerImageSelector("ubuntu:14.04"),
                        "", new DockerServerEndpoint("", ""), "", true, false, Collections.<Volume>emptyList(), null, "cat", false, "bridge", null, null, false, true)
        );
        project.getBuildersList().add(new Shell("pwd"));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
        String logFile = FileUtils.readFileToString(build.getLogFile());
        assertThat(logFile, containsString(String.format("--workdir %s", build.getWorkspace().getRemote())));
        jenkins.buildAndAssertSuccess(project);
    }

    @Test
    public void usesDefaultWorkdirectoryWithinTheContainer() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();

        project.getBuildWrappersList().add(
                new DockerBuildWrapper(
                        new PullDockerImageSelector("ubuntu:14.04"),
                        "", new DockerServerEndpoint("", ""), "", true, false, Collections.<Volume>emptyList(), null, "cat", false, "bridge", null, null, false, false)
        );
        project.getBuildersList().add(new Shell("pwd"));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
        String buildLog = FileUtils.readFileToString(build.getLogFile());
        assertThat(buildLog, not(containsString("--workdir")));
        jenkins.buildAndAssertSuccess(project);
    }
}
