package org.jenkinsci.plugins.script_executor.pipeline;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.script_executor.RuntimeInstallation;
import org.jenkinsci.plugins.script_executor.StringScriptSource;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Set;

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
    public static final class DescriptorImpl extends StepDescriptor {

        public static final String runtimeName = null;


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

        @Override
        public Set<Class<?>> getRequiredContext() {
            return ImmutableSet.of(FilePath.class, Run.class, Launcher.class, TaskListener.class);
        }
    }
}
