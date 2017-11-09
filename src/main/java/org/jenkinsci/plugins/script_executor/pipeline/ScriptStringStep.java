package org.jenkinsci.plugins.script_executor.pipeline;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.script_executor.FileScriptSource;
import org.jenkinsci.plugins.script_executor.RuntimeInstallation;
import org.jenkinsci.plugins.script_executor.StringScriptSource;
import org.jenkinsci.plugins.script_executor.UniversalScript;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.inject.Inject;

/**
 * Pipeline step to execute a universal script from string
 */
public final class ScriptStringStep extends BaseScriptStep {
    /**
     * Script content
     */
    private @Nonnull String script;

    @DataBoundConstructor
    public ScriptStringStep(@Nonnull String script, @Nonnull String runtimeName) {
        super(runtimeName, new StringScriptSource(script));
        this.script = script;
    }

    @Nonnull
    public String getScript() {
        return script;
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public static final String runtimeName = null;

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "univScriptExec";
        }

        @Override
        public String getDisplayName() {
            return "Execute a universal script";
        }


        public ListBoxModel doFillRuntimeNameItems() {
            return RuntimeInstallation.getAllInstallations();
        }
    }
}
