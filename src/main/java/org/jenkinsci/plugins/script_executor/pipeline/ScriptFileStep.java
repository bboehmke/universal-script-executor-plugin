package org.jenkinsci.plugins.script_executor.pipeline;

import hudson.Extension;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.script_executor.FileScriptSource;
import org.jenkinsci.plugins.script_executor.RuntimeInstallation;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

/**
 * Pipeline step to execute a universal script from file
 */
public final class ScriptFileStep extends BaseScriptStep {
    /**
     * Path to script file
     */
    private @Nonnull String filePath;


    @DataBoundConstructor
    public ScriptFileStep(@Nonnull String filePath, @Nonnull String runtimeName) {
        super(runtimeName, new FileScriptSource(filePath));
        this.filePath = filePath;
    }

    @Nonnull
    public String getFilePath() {
        return filePath;
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public static final String runtimeName = null;

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "univScriptExecFile";
        }

        @Override
        public String getDisplayName() {
            return "Execute a universal script from file";
        }

        public ListBoxModel doFillRuntimeNameItems() {
            return RuntimeInstallation.getAllInstallations();
        }
    }
}
