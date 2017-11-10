package org.jenkinsci.plugins.script_executor.pipeline;


import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.script_executor.ScriptSource;
import org.jenkinsci.plugins.script_executor.UniversalScript;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.inject.Inject;

/**
 * Base class for pipeline steps
 */
public abstract class BaseScriptStep extends AbstractStepImpl {
    /**
     * Name of runtime
     */
    private @Nonnull String runtimeName;
    /**
     * Script source to use
     */
    private @Nonnull ScriptSource scriptSource;
    /**
     * List of runtime Parameters
     */
    private String runtimeParameters = "";
    /**
     * List of script parameters
     */
    private String scriptParameters = "";

    BaseScriptStep(@Nonnull String runtimeName, @Nonnull ScriptSource scriptSource) {
        this.runtimeName = runtimeName;
        this.scriptSource = scriptSource;
    }

    @Nonnull
    public String getRuntimeName() {
        return runtimeName;
    }

    @Nonnull
    public ScriptSource getScriptSource() {
        return scriptSource;
    }

    public String getRuntimeParameters() {
        return runtimeParameters;
    }

    @DataBoundSetter
    public void setRuntimeParameters(String runtimeParameters) {
        this.runtimeParameters = Util.fixNull(runtimeParameters);
    }

    public String getScriptParameters() {
        return scriptParameters;
    }

    @DataBoundSetter
    public void setScriptParameters(String scriptParameters) {
        this.scriptParameters = Util.fixNull(scriptParameters);
    }


    public static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        private final transient BaseScriptStep step;

        @StepContextParameter
        private transient Run<?, ?> run;
        @StepContextParameter
        private transient TaskListener listener;
        @StepContextParameter
        private transient FilePath workspace;
        @StepContextParameter
        private transient Launcher launcher;

        @Inject
        public Execution(BaseScriptStep step) {
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            // get universal script instance
            UniversalScript script = new UniversalScript(step.getScriptSource(), step.getRuntimeName());

            // set parameters
            script.setRuntimeParameters(step.getRuntimeParameters());
            script.setScriptParameters(step.getScriptParameters());

            // run script
            script.perform(run, workspace, launcher, listener);
            return null;
        }
    }
}
