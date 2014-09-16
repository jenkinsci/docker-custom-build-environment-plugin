package com.cloudbees.jenkins.plugins.okidocki;

import hudson.Extension;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;

/**
 * Used to determine if launcher has to be decorated to execute in container, after SCM checkout completed.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class RunInContainer extends InvisibleAction {

    private boolean enable;

    public String image;

    public void afterSCM() {
        this.enable = true;
    }

    public boolean enabled() {
        return enable;
    }

    @Extension
    public static class Listener extends SCMListener {
        @Override
        public void onChangeLogParsed(Run<?, ?> build, SCM scm, TaskListener listener, ChangeLogSet<?> changelog) throws Exception {
            RunInContainer runInContainer = build.getAction(RunInContainer.class);
            if (runInContainer != null) runInContainer.afterSCM();
        }
    }
}
