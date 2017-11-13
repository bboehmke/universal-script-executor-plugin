package org.jenkinsci.plugins.script_executor.pipeline;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.script_executor.FileScriptSource;
import org.jenkinsci.plugins.script_executor.RuntimeInstallation;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Set;

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
    public static final class DescriptorImpl extends StepDescriptor {

        public static final String runtimeName = null;

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

        @Override
        public Set<Class<?>> getRequiredContext() {
            return ImmutableSet.of(FilePath.class, Run.class, Launcher.class, TaskListener.class);
        }
    }
}
