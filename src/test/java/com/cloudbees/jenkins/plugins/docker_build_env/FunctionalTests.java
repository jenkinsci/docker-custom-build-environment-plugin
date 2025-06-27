package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.Shell;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;


/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class FunctionalTests {

    @Rule  // @ClassRule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void run_inside_pulled_container() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();

        project.getBuildWrappersList().add(
            new DockerBuildWrapper(
                new PullDockerImageSelector("ubuntu:14.04"),
                "", new DockerServerEndpoint("", ""), "", true, false, Collections.<Volume>emptyList(), null, "cat", false, "bridge", null, null, null, false)
        );
        project.getBuildersList().add(new Shell("lsb_release  -a"));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
        String s = FileUtils.readFileToString(build.getLogFile());
        assertThat(s, containsString("Ubuntu 14.04"));
        jenkins.buildAndAssertSuccess(project);
    }

    @Test
    public void run_inside_built_container() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.setScm(new SingleFileSCM("Dockerfile", "FROM ubuntu:14.04"));

        project.getBuildWrappersList().add(
                new DockerBuildWrapper(
                        new DockerfileImageSelector(".", "Dockerfile"),
                        "", new DockerServerEndpoint("", ""), "", true, false, Collections.<Volume>emptyList(), null, "cat", false, "bridge", null, null, null, true)
        );
        project.getBuildersList().add(new Shell("lsb_release  -a"));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
        String s = FileUtils.readFileToString(build.getLogFile());
        assertThat(s, containsString("Ubuntu 14.04"));
        jenkins.buildAndAssertSuccess(project);
    }

    @Test
    public void run_inside_built_python_pip_container() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        String dockerfile = String.join(
                                        "\n",
                                        "FROM python:3.6.4-alpine3.4",
                                        "RUN echo Successfully built THIS_STRING_SHOULD_NOT_BE_CAPTURED_AS_IMAGE_ID",
                                        "RUN pip install simplejson==3.13.2"
        );
        project.setScm(new SingleFileSCM("Dockerfile", dockerfile));

        project.getBuildWrappersList().add(
                new DockerBuildWrapper(
                        new DockerfileImageSelector(".", "Dockerfile"),
                        "", new DockerServerEndpoint("", ""), "", true, false, Collections.<Volume>emptyList(), null, "cat", false, "bridge", null, null, null, true)
        );
        project.getBuildersList().add(new Shell("python -V"));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.SUCCESS, build);
        String s = FileUtils.readFileToString(build.getLogFile());
        assertThat(s, containsString("Python 3.6.4"));
        jenkins.buildAndAssertSuccess(project);
    }

}
