package org.jenkinsci.plugins.script_executor.pipeline;

import com.google.common.collect.ImmutableSet;
import hudson.*;
import hudson.model.*;
import hudson.util.ListBoxModel;
import hudson.util.VariableResolver;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.script_executor.ExecutionFailureException;
import org.jenkinsci.plugins.script_executor.RuntimeInstallation;
import org.jenkinsci.plugins.script_executor.UniversalScript;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pipeline step to execute the runtime without a script
 */
public final class RawCallStep extends Step {
    /**
     * Name of runtime
     */
    private @Nonnull String runtimeName;
    /**
     * List of parameters
     */
    private String parameters = "";
    /**
     * True if failed execution should not cause an error
     */
    private boolean ignoreFailedExecution = false;

    @DataBoundConstructor
    public RawCallStep(@Nonnull String runtimeName) {
        this.runtimeName = runtimeName;
    }

    @Nonnull
    public String getRuntimeName() {
        return runtimeName;
    }

    public String getParameters() {
        return parameters;
    }

    @DataBoundSetter
    public void setParameters(String parameters) {
        this.parameters = Util.fixNull(parameters);
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


    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        public static final String runtimeName = null;

        @Override
        public String getFunctionName() {
            return "univScriptRuntimeCall";
        }

        @Override
        public String getDisplayName() {
            return "Execute a universal script runtime with a custom command";
        }

        public ListBoxModel doFillRuntimeNameItems() {
            return RuntimeInstallation.getAllInstallations();
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return ImmutableSet.of(FilePath.class, Run.class, Launcher.class, TaskListener.class);
        }
    }

    public static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Integer> {
        private final transient RawCallStep step;

        public Execution(RawCallStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Integer run() throws Exception {
            StepContext context = getContext();
            Run<?, ?> run = context.get(Run.class);
            TaskListener listener = context.get(TaskListener.class);

            if (run == null || listener == null) {
                throw new ExecutionFailureException("Invalid context");
            }

            try {
                FilePath workspace = context.get(FilePath.class);
                Launcher launcher = context.get(Launcher.class);

                // prepare environment variables
                Map<String, String> envVars = context.get(EnvVars.class);

                ArrayList<String> cmdLine = new ArrayList<>();

                // prepare variable resolver - more efficient than calling env.expand(s)
                EnvVars env = run.getEnvironment(listener);

                // get the runtime installation
                RuntimeInstallation installation = UniversalScript.getRuntime(step.getRuntimeName(), context);
                if(installation != null) {
                    Computer computer = Computer.currentComputer();
                    if (computer != null) {
                        installation = installation.forNode(computer.getNode(), listener);
                    }
                    installation = installation.forEnvironment(env);

                    // add runtime to commandline
                    cmdLine.add(installation.getExecutable(workspace.getChannel(), launcher.isUnix()));

                    envVars.put("RUNTIME_HOME", installation.getLocalHome(workspace.getChannel(), launcher.isUnix()));
                    envVars.putAll(installation.getEnvVarMap(envVars, launcher.isUnix()));
                }

                if (cmdLine.isEmpty() || cmdLine.get(0) == null) {
                    listener.getLogger().println("[UNIVERSAL SCRIPT EXECUTOR - ERROR] Runtime executable is NULL, please check your configuration.");
                    throw new ExecutionFailureException("Empty command");
                }


                // build parameters map
                Map<String, String> parameterVariables = new HashMap<>(env);

                // check for parametrized build
                ParametersAction buildParameters = run.getAction(ParametersAction.class);
                if (buildParameters != null) {
                    for (ParameterValue p : buildParameters.getAllParameters()) {
                        if (p.getValue() instanceof String) {
                            parameterVariables.put(p.getName(), (String) p.getValue());
                        }
                    }
                }
                // create variable resolver
                VariableResolver<String> vr = new VariableResolver.ByMap<>(parameterVariables);

                // add runtimeParameters
                if(StringUtils.isNotBlank(step.getParameters())) {
                    String[] args = UniversalScript.parseParams(step.getParameters());
                    for(String arg : args) {
                        cmdLine.add(Util.replaceMacro(arg, vr));
                    }
                }

                // ensure workspace directory exist
                workspace.mkdirs();


                // prepare the runtime for script execution
                Launcher.ProcStarter procStarter = launcher.launch();
                procStarter.cmds(cmdLine.toArray(new String[] {}));
                procStarter.envs(envVars);
                procStarter.stdout(listener);
                procStarter.pwd(workspace);

                // execute the script
                int exitCode = procStarter.join();
                if (exitCode != 0) {
                    throw new ExecutionFailureException("Execution failed", exitCode);
                }

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
