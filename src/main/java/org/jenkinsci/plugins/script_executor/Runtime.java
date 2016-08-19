package org.jenkinsci.plugins.script_executor;

import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.VariableResolver;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.Map.Entry;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A runtime build step
 */
public class Runtime extends Builder {

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
    private String runtimeParameters;
    /**
     * List of script parameters
     */
    private String scriptParameters;

    @DataBoundConstructor
    public Runtime(ScriptSource scriptSource, String runtimeName,
                  String runtimeParameters, String scriptParameters) {
        this.scriptSource = scriptSource;
        this.runtimeName = runtimeName;
        this.runtimeParameters = runtimeParameters;
        this.scriptParameters = scriptParameters;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                           BuildListener listener)
            throws InterruptedException, IOException {

        // check if script is missing
        if (scriptSource == null) {
            listener.fatalError("There is no script configured for this builder");
            return false;
        }

        // try to get script
        FilePath ws = build.getWorkspace();
        FilePath script;
        try {
            script = scriptSource.getScriptFile(ws, build, listener);
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("Unable to produce a script file"));
            return false;
        }

        try {
            // get the command line
            List<String> cmd = buildCommandLine(build, listener, script, launcher.isUnix());

            // check if command creation has failed
            if (cmd == null) {
                return false;
            }

            try {
                // prepare environment variables
                Map<String,String> envVars = build.getEnvironment(listener);
                RuntimeInstallation installation = getRuntime();
                if(installation != null) {
                    installation = installation.forNode(Computer.currentComputer().getNode(), listener);
                    envVars.put("RUNTIME_HOME", installation.getHome());

                    // get runtime environment variables
                    if (StringUtils.isNotBlank(installation.getEnvVar())) {
                        Properties props = new Properties();
                        props.load(new StringReader(installation.getEnvVar()));

                        for (Entry<Object, Object> entry : props.entrySet()) {
                            String value = entry.getValue().toString();

                            envVars.put(
                                    entry.getKey().toString(),
                                    Util.replaceMacro(value, envVars));
                        }
                    }
                }

                // add build variables to environment
                for(Map.Entry<String,String> e : build.getBuildVariables().entrySet()){
                    envVars.put(e.getKey(), e.getValue());
                }

                // prepare the runtime for script execution
                Launcher.ProcStarter procStarter = launcher.launch();
                procStarter.cmds(cmd.toArray(new String[] {}));
                procStarter.envs(envVars);
                procStarter.stdout(listener);
                procStarter.pwd(ws);

                // execute the script
                return procStarter.join() == 0;

            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace( listener.fatalError("command execution failed") );

                return false;
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

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        /**
         * List of available runtime installations
         */
        @CopyOnWrite
        private volatile List<RuntimeInstallation> installations = new ArrayList<RuntimeInstallation>();

        public DescriptorImpl() {
            super(Runtime.class);
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
            for (RuntimeInstallation i : ((DescriptorImpl) Jenkins.getInstance().getDescriptor(Runtime.class)).getInstallations()) {
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
    }

    /**
     * Get the runtime installation of this instance
     * @return RuntimeInstallation
     */
    protected RuntimeInstallation getRuntime() {
        return DescriptorImpl.getRuntime(runtimeName);
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
    private List<String> buildCommandLine(AbstractBuild<?,?> build,
                                          BuildListener listener,
                                          FilePath script, boolean isOnUnix)
            throws IOException, InterruptedException  {

        ArrayList<String> list = new ArrayList<String>();

        // prepare variable resolver - more efficient than calling env.expand(s)
        EnvVars env = build.getEnvironment(listener);
        env.overrideAll(build.getBuildVariables());
        VariableResolver<String> vr = new VariableResolver.ByMap<String>(env);

        // prepare runtime cmd -> null = invalid
        String cmd = null;

        // get the runtime installation
        RuntimeInstallation installation = getRuntime();
        if(installation != null) {
            installation = installation.forNode(Computer.currentComputer().getNode(), listener);
            installation = installation.forEnvironment(env);

            cmd = installation.getExecutable(script.getChannel(), isOnUnix);
        }
        // check if runtime command is valid
        if (null == cmd) {
            listener.getLogger().println("[UNIVERSAL SCRIPT EXECUTOR - ERROR] Runtime executable is NULL, please check your configuration.");
            return null;
        }
        list.add(cmd);


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
            ParametersAction parameters = build.getAction(ParametersAction.class);
            for(String param : params) {
            	// first replace parameter from parametrized build
            	if (parameters != null) {
                    param = parameters.substitute(build, param);
                }
            	// then replace evn vars
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
