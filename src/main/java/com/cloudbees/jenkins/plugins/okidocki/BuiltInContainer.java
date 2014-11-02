package com.cloudbees.jenkins.plugins.okidocki;

import hudson.Extension;
import hudson.model.BuildBadgeAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;

/**
 * Used to determine if launcher has to be decorated to execute in container, after SCM checkout completed.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class BuiltInContainer implements BuildBadgeAction {

    boolean enable;

    public String image;
    public String container;

    public void afterSCM() {
        this.enable = true;
    }

    public boolean enabled() {
        return enable;
    }

    public String getIconFileName() {
        return "/plugin/oki-docki/docker-badge.png";
    }

    public String getImage() {
        return image;
    }

    public String getDisplayName() {
        return "build inside docker container";
    }

    public String getUrlName() {
        return "/docker";
    }


    @Extension
    public static class Listener extends SCMListener {
        @Override
        public void onChangeLogParsed(Run<?, ?> build, SCM scm, TaskListener listener, ChangeLogSet<?> changelog) throws Exception {
            BuiltInContainer runInContainer = build.getAction(BuiltInContainer.class);
            if (runInContainer != null) runInContainer.afterSCM();
        }
    }

}
