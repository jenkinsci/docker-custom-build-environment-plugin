package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.Extension;
import hudson.Plugin;
import hudson.model.Items;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension
public class DockerCustomBuildEnvironmentPlugin extends Plugin {

    static {
        Items.XSTREAM2.aliasPackage("com.cloudbees.jenkins.plugins.okidocki", "com.cloudbees.jenkins.plugins.docker_build_env");
    }
}
