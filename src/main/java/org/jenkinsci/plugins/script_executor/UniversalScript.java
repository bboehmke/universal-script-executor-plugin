package org.jenkinsci.plugins.script_executor;

import hudson.*;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.*;
import hudson.slaves.NodeSpecific;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.util.ListBoxModel;
import hudson.util.VariableResolver;


import java.io.File;
import java.io.IOException;
import java.util.*;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;

/**
 * A runtime build step
 */
public class UniversalScript extends Builder implements SimpleBuildStep {

    /**
     * Script source for the runtime
     */
    private ScriptSource scriptSource;
    /**
     * Name of the used runtime
     */
    private String runtimeName;
    /**
     * List of runtime Parameters
     */
    private String runtimeParameters = "";
    /**
     * List of script parameters
     */
    private String scriptParameters = "";

    /**
     * Custom step context
     */
    private transient StepContext customContext = null;

    @DataBoundConstructor
    public UniversalScript(ScriptSource scriptSource, String runtimeName) {
        this.scriptSource = scriptSource;
        this.runtimeName = runtimeName;
    }

    public void setCustomContext(StepContext context) {
        this.customContext = context;
    }

    @DataBoundSetter
    public void setRuntimeParameters(String runtimeParameters) {
        this.runtimeParameters = Util.fixNull(runtimeParameters);
    }

    @DataBoundSetter
    public void setScriptParameters(String scriptParameters) {
        this.scriptParameters = Util.fixNull(scriptParameters);
    }

    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath workspace,
                        @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws InterruptedException, IOException {

        // check if script is missing
        if (scriptSource == null) {
            listener.fatalError("There is no script configured for this builder");
            return;
        }

        // try to get script
        FilePath script;
        try {
            script = scriptSource.getScriptFile(workspace, build, listener);
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("Unable to produce a script file"));
            build.setResult(Result.FAILURE);
            return;
        }

        try {
            // get the command line
            List<String> cmd = buildCommandLine(build, listener, script, launcher.isUnix());

            // check if command creation has failed
            if (cmd == null) {
                build.setResult(Result.FAILURE);
                return;
            }

            try {
                // prepare environment variables
                Map<String, String> envVars = build.getEnvironment(listener);

                // get pipeline env vars
                if (customContext != null) {
                    EnvVars vars = customContext.get(EnvVars.class);
                    if (vars != null) {
                        envVars = vars;
                    }
                }

                RuntimeInstallation installation = getRuntime();
                if(installation != null) {
                    Computer computer = Computer.currentComputer();
                    if (computer != null) {
                        installation = installation.forNode(computer.getNode(), listener);
                    }
                    envVars.put("RUNTIME_HOME", installation.getLocalHome(script.getChannel(), launcher.isUnix()));

                    envVars.putAll(installation.getEnvVarMap(envVars, launcher.isUnix()));
                }

                // add build variables to environment
                //for(Map.Entry<String,String> e : build.getBuildVariables().entrySet()){
                //    envVars.put(e.getKey(), e.getValue());
                //}

                // ensure workspace directory exist
                workspace.mkdirs();

                // prepare the runtime for script execution
                Launcher.ProcStarter procStarter = launcher.launch();
                procStarter.cmds(cmd.toArray(new String[] {}));
                procStarter.envs(envVars);
                procStarter.stdout(listener);
                procStarter.pwd(workspace);

                // execute the script
                if (procStarter.join() != 0) {
                    build.setResult(Result.FAILURE);
                }

            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace( listener.fatalError("command execution failed") );
                build.setResult(Result.FAILURE);
            }

        } finally {
            // try to remove temporary script files
            try {
                if(scriptSource instanceof StringScriptSource && script != null){
                	script.delete();
                }
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace( listener.fatalError("Unable to delete script file " + script) );
            }
        }
    }
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        /**
         * List of available runtime installations
         */
        @CopyOnWrite
        private volatile List<RuntimeInstallation> installations = new ArrayList<RuntimeInstallation>();

        public DescriptorImpl() {
            super(UniversalScript.class);
            load();
        }

        /**
         * Get the name of the build step
         * @return Name of Build step
         */
        @Override
        public String getDisplayName() {
            return "Execute Universal Script";
        }

        /**
         * Enable this build step
         * @param jobType Project
         * @return True
         */
        @Override
        @SuppressWarnings("rawtypes")
        public boolean isApplicable(Class<? extends AbstractProject> jobType){
            return true;
        }

        /**
         * Get a array of all runtime installation
         * @return Runtime installation array
         */
        public RuntimeInstallation[] getInstallations() {
            RuntimeInstallation[] installs = new RuntimeInstallation[installations.size()];
            return installations.toArray(installs);
        }

        /**
         * Get the runtime installation with the given name
         * @param runtimeName Name of the runtime installation
         * @return Runtime installation instance or null
         */
        public static RuntimeInstallation getRuntime(String runtimeName) {
            for (RuntimeInstallation i : ((DescriptorImpl) Jenkins.getInstance().getDescriptor(UniversalScript.class)).getInstallations()) {
                if(runtimeName != null && i.getName().equals(runtimeName)) {
                    return i;
                }
            }
            return null;
        }

        /**
         * Set available runtime installations
         * @param installations List of runtime installations
         */
        public void setInstallations(RuntimeInstallation... installations) {
            List<RuntimeInstallation> installations2 = new ArrayList<RuntimeInstallation>();
            Collections.addAll(installations2, installations);
            this.installations = installations2;
            save();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws hudson.model.Descriptor.FormException {
            save();
            return true;
        }


        public ListBoxModel doFillRuntimeNameItems() {
            return RuntimeInstallation.getAllInstallations();
        }

        @Initializer(before = InitMilestone.PLUGINS_STARTED)
        public static void addAliases() {
            Items.XSTREAM2.addCompatibilityAlias("org.jenkinsci.plugins.script_executor.Runtime", UniversalScript.class);
        }
    }

    /**
     * Get the runtime installation of this instance
     * @return RuntimeInstallation
     */
    protected RuntimeInstallation getRuntime() throws IOException, InterruptedException {
        for (ToolDescriptor<?> desc : ToolInstallation.all()) {
            for (ToolInstallation inst : desc.getInstallations()) {
                // skip other installations
                if (!(inst instanceof RuntimeInstallation)) {
                    continue;
                }

                if (inst.getName().equals(runtimeName)) {

                    // handle special context (pipeline step)
                    if (customContext != null) {
                        inst = (ToolInstallation) ((NodeSpecific<?>) inst).forNode(customContext.get(Node.class), customContext.get(TaskListener.class));
                        inst = (ToolInstallation) ((EnvironmentSpecific<?>) inst).forEnvironment(customContext.get(EnvVars.class));
                    }

                    return (RuntimeInstallation)inst;
                }
            }
        }

        return null;
    }

    /**
     * Build the command line for script execution
     * @param build Build instance
     * @param listener Build listener
     * @param script File path to the script
     * @param isOnUnix True if executed on linux
     * @return Command line for script execution
     * @throws IOException
     * @throws InterruptedException
     */
    private List<String> buildCommandLine(Run<?,?> build,
                                          TaskListener listener,
                                          FilePath script, boolean isOnUnix)
            throws IOException, InterruptedException  {

        ArrayList<String> list = new ArrayList<>();

        // prepare variable resolver - more efficient than calling env.expand(s)
        EnvVars env = build.getEnvironment(listener);

        // prepare runtime cmd -> null = invalid
        String cmd = null;

        // get the runtime installation
        RuntimeInstallation installation = getRuntime();
        if(installation != null) {
            Computer computer = Computer.currentComputer();
            if (computer != null) {
                installation = installation.forNode(computer.getNode(), listener);
            }
            installation = installation.forEnvironment(env);

            cmd = installation.getExecutable(script.getChannel(), isOnUnix);
        }
        // check if runtime command is valid
        if (null == cmd) {
            listener.getLogger().println("[UNIVERSAL SCRIPT EXECUTOR - ERROR] Runtime executable is NULL, please check your configuration.");
            return null;
        }
        list.add(cmd);

        // build parameters map
        Map<String, String> parameterVariables = new HashMap<>(env);

        // check for parametrized build
        ParametersAction parameters = build.getAction(ParametersAction.class);
        if (parameters != null) {
            for (ParameterValue p : parameters.getAllParameters()) {
                if (p.getValue() instanceof String) {
                    parameterVariables.put(p.getName(), (String) p.getValue());
                }
            }
        }
        // create variable resolver
        VariableResolver<String> vr = new VariableResolver.ByMap<>(parameterVariables);

        // add runtimeParameters
        if(StringUtils.isNotBlank(runtimeParameters)) {
            String[] args = parseParams(runtimeParameters);
            for(String arg : args) {
                list.add(Util.replaceMacro(arg, vr));
            }
        }

        // add script path
        list.add(script.getRemote());

        // add script runtimeParameters
        if(StringUtils.isNotBlank(scriptParameters)) {
            String[] params = parseParams(scriptParameters);
            for(String param : params) {
            	param = Util.replaceMacro(param, vr);
                list.add(param);
            }
        }

        return list;

    }

    /**
     * Parse a parameters line to an array
     * @param line Line with parameters
     * @return Array with parameters
     */
    private String[] parseParams(String line) {
        // JENKINS-24870 CommandLine.getExecutable tries to fix file separators,
        // so if the first param contains slashes, it can cause problems
        // Adding some placeholder instead of executable
        CommandLine cmdLine = CommandLine.parse("executable_placeholder " + line);
        String[] parsedArgs = cmdLine.getArguments();
        String[] args = new String[parsedArgs.length];
        if(parsedArgs.length > 0) {
            System.arraycopy(parsedArgs, 0, args, 0, parsedArgs.length);
        }
        return args;
    }

    /**
     * Get the script source
     * @return ScriptSource
     */
    public ScriptSource getScriptSource() {
        return scriptSource;
    }
    /**
     * Get the runtime name
     * @return Name of the runtime
     */
    public String getRuntimeName() {
        return runtimeName;
    }

    /**
     * Get the runtime parameters
     * @return Runtime parameters
     */
    public String getRuntimeParameters() {
        return runtimeParameters;
    }

    /**
     * Get the script parameters
     * @return Script parameters
     */
    public String getScriptParameters() {
        return scriptParameters;
    }

}
