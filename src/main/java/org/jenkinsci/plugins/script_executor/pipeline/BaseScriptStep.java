package org.jenkinsci.plugins.script_executor.pipeline;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.script_executor.ExecutionFailureException;
import org.jenkinsci.plugins.script_executor.ScriptSource;
import org.jenkinsci.plugins.script_executor.UniversalScript;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

/**
 * Base class for pipeline steps
 */
public abstract class BaseScriptStep extends Step {
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

    /**
     * True if failed execution should not cause an error
     */
    private boolean ignoreFailedExecution = false;

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

    public boolean isIgnoreFailedExecution() {
        return ignoreFailedExecution;
    }

    @DataBoundSetter
    public void setIgnoreFailedExecution(boolean ignoreFailedExecution) {
        this.ignoreFailedExecution = ignoreFailedExecution;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    public static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Integer> {
        private final transient BaseScriptStep step;

        public Execution(BaseScriptStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Integer run() throws Exception {
            StepContext context = getContext();

            // get universal script instance
            UniversalScript script = new UniversalScript(step.getScriptSource(), step.getRuntimeName());

            // set context
            script.setCustomContext(context);

            // set parameters
            script.setRuntimeParameters(step.getRuntimeParameters());
            script.setScriptParameters(step.getScriptParameters());

            Run<?, ?> run = context.get(Run.class);
            TaskListener listener = context.get(TaskListener.class);

            try {
                // run script
                script.perform(
                        run,
                        context.get(FilePath.class),
                        context.get(Launcher.class),
                        listener);

            } catch (ExecutionFailureException e) {
                // handle failure result
                if (step.isIgnoreFailedExecution()) {
                    listener.error("[UNIVERSAL SCRIPT EXECUTOR] " + e.getMessage());
                    return e.getExitCode();
                } else {
                    throw e;
                }
            }
            return 0;
        }
    }
}
