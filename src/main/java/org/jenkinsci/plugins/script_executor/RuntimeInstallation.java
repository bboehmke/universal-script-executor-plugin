package org.jenkinsci.plugins.script_executor;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.TaskListener;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import hudson.slaves.NodeSpecific;
import hudson.tools.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;

import org.apache.commons.exec.CommandLine;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Installation of a runtime
 */
public class RuntimeInstallation
        extends ToolInstallation
        implements EnvironmentSpecific<RuntimeInstallation>,
                   NodeSpecific<RuntimeInstallation> {
    private static final long serialVersionUID = 1L;

    /**
     * Path to the executor for windows (based on home)
     */
    private String winExecutor;
    /**
     * Path to the executor for linux (based on home)
     */
    private String nixExecutor;
    /**
     * Command to check the script syntax (based on home)
     */
    private String checkCommand;

    /**
     * List of environment variables for the runtime
     */
    private String envVar;

    /**
     * Create runtime installation
     * @param name Name of the installation
     * @param home Home directory of the installation
     * @param winExecutor Executor for windows
     * @param nixExecutor Executor for linux
     * @param checkCommand Command to check the syntax
     * @param envVar Environment variables
     * @param properties Tool installation properties
     */
    @DataBoundConstructor
    public RuntimeInstallation(String name, String home, String winExecutor,
                               String nixExecutor, String checkCommand, String envVar,
                               List<? extends ToolProperty<?>> properties){
    	super(name,home,properties);

        this.winExecutor = winExecutor;
        this.nixExecutor = nixExecutor;
        this.checkCommand = checkCommand;
        this.envVar = envVar;
    }

    /**
     * Get the executor for windows
     * @return Executor for windows
     */
    public String getWinExecutor() {
        return winExecutor;
    }

    /**
     * Get the executor for linux
     * @return Executor for linux
     */
    public String getNixExecutor() {
        return nixExecutor;
    }

    /**
     * Get the command to check the syntax
     * @return Command to check the syntax
     */
    public String getCheckCommand() {
        return checkCommand;
    }
    /**
     * Get the environment variables
     * @return Environment variables
     */
    public String getEnvVar() {
        return envVar;
    }

    /**
     * Gets the executable path of this runtime
     * @param channel Channel of node
     * @param isOnUnix True if run on unix
     * @return Path to executable or null
     * @throws IOException
     * @throws InterruptedException
     */
    public String getExecutable(VirtualChannel channel, final boolean isOnUnix) throws IOException, InterruptedException {
        return channel.call(new MasterToSlaveCallable<String, IOException>() {
            public String call() throws IOException {
                // replace macros in home path
                String home = Util.replaceMacro(getHome(), EnvVars.masterEnvVars);

                // check if on unix
                File exe;
                if (isOnUnix) {
                    exe = new File(home, getNixExecutor());
                } else {
                    exe = new File(home, getWinExecutor());
                }

                // check if executor exist
                if (exe.exists()) {
                    return exe.getPath();
                }
                return null;
            }

            private static final long serialVersionUID = 1L;
        });
    }

    /**
     * Get the command line for the syntax check
     * @return Command line
     */
    public List<String> getCheckCommandLine() {
        // replace macros in home path
        String home = Util.replaceMacro(getHome(), EnvVars.masterEnvVars);

        ArrayList<String> cmd = new ArrayList<String>();

        // parse command line
        CommandLine cmdLine = CommandLine.parse(home + "/" + checkCommand);

        // check if executable exist
        if (!new File(cmdLine.getExecutable()).exists()) {
            return null;
        }

        cmd.add(cmdLine.getExecutable());
        cmd.addAll(Arrays.asList(cmdLine.getArguments()));
        return cmd;
    }

    /**
     * Get the runtime installation from environment variables
     * @param environment Environment variables
     * @return RuntimeInstallation
     */
    public RuntimeInstallation forEnvironment(EnvVars environment) {
        return new RuntimeInstallation(getName(), environment.expand(getHome()),
                getWinExecutor(), getNixExecutor(), getCheckCommand(), getEnvVar(),
                getProperties().toList());
    }

    /**
     * Get the runtime installation for a node
     * @param node Node
     * @param log TaskListener
     * @return RuntimeInstallation
     */
    public RuntimeInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new RuntimeInstallation(getName(), translateFor(node, log),
                getWinExecutor(), getNixExecutor(), getCheckCommand(), getEnvVar(),
                getProperties().toList());
    }

    /**
     * Tool description for runtime installations
     */
    @Extension
    public static class DescriptorImpl extends ToolDescriptor<RuntimeInstallation> {

        public DescriptorImpl() {
        }

        @Override
        public String getDisplayName() {
            return "Universal Script Executor";
        }

        @Override
        public RuntimeInstallation[] getInstallations() {
            return Jenkins.getInstance().getDescriptorByType(Runtime.DescriptorImpl.class).getInstallations();
        }

        @Override
        public void setInstallations(RuntimeInstallation... installations) {
            Jenkins.getInstance().getDescriptorByType(Runtime.DescriptorImpl.class).setInstallations(installations);
        }

    }


}
