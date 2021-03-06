package org.jenkinsci.plugins.script_executor;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.*;
import hudson.util.FormValidation;

import java.io.*;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.SystemUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Runtime script specified by command string.
 * 
 * @author dvrzalik
 */
public class StringScriptSource extends ScriptSource {

    private String command;

    @DataBoundConstructor
    public StringScriptSource(String command) {
        this.command = command;
    }

    @Override
    public InputStream getScriptStream(FilePath projectWorkspace, Run<?, ?> build, TaskListener listener) {
        return new ByteArrayInputStream(command.getBytes(Charsets.UTF_8));
    }

    @Override
    public FilePath getScriptFile(FilePath projectWorkspace,
                                  Run<?, ?> build,
                                  TaskListener listener)
            throws IOException, InterruptedException {

        return projectWorkspace.createTextTempFile("jenkins", ".use", command, true);
    }

    public String getCommand() {
        return command;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StringScriptSource that = (StringScriptSource) o;

        return command != null ? command.equals(that.command) : that.command == null;

    }

    @Override
    public int hashCode() {
        return command != null ? command.hashCode() : 0;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ScriptSource> {

        @Override
        public String getDisplayName() {
            return "Script command";
        }

        public FormValidation doCheckScript(@QueryParameter String command,
                                            @QueryParameter String runtimeName) {

            // check if command is empty
            if (command == null || command.trim().isEmpty())
                return FormValidation.error("Script seems to be empty string!");

            // try to get the runtime
            RuntimeInstallation runtime =
                    UniversalScript.DescriptorImpl.getRuntime(runtimeName);

            // runtime invalid
            if (runtime == null) {
                return FormValidation.error("Failed to get runtime");
            }

            // test if syntax check command refers to other instance of runtime
            // this is needed to use check command if executor is delivered in an automated tool install
            while (UniversalScript.DescriptorImpl.getRuntime(runtime.getCheckCommand().trim()) != null) {
                runtime = UniversalScript.DescriptorImpl.getRuntime(runtime.getCheckCommand().trim());
                if (runtime.getName().equals(runtimeName)) {
                    break;
                }
            }

            // check if check command dis given
            if (runtime.getCheckCommand().isEmpty()) {
                return FormValidation.error("No syntax check available!");
            }

            // get check command line
            List<String> cmd = runtime.getCheckCommandLine();
            if (cmd == null) {
                return FormValidation.error("Invalid syntax check executable!");
            }

            // create temp file
            File scriptFile;
            try {
                scriptFile = File.createTempFile("script", "use");
            } catch (IOException e) {
                return FormValidation.error("Failed to create temporary script file:\n" + e);
            }

            try {
                // write temp file
                PrintWriter out = new PrintWriter(scriptFile);
                out.println(command);
                out.close();

                // add script file path to command
                cmd.add(scriptFile.getAbsolutePath());

                // get runtime environment variables
                Map<String,String> envVars = EnvVars.masterEnvVars;
                envVars.put("RUNTIME_HOME", runtime.getHome());

                envVars.putAll(runtime.getEnvVarMap(envVars, SystemUtils.IS_OS_LINUX));

                // check the syntax of the script
                ProcessBuilder builder = new ProcessBuilder(cmd);
                Map<String, String> environment = builder.environment();
                environment.putAll(envVars);
                builder.redirectErrorStream(true);
                Process process = builder.start();

                if (process.waitFor() != 0) {
                    String err = IOUtils.toString(process.getInputStream());
                    return FormValidation.error(err.replace(scriptFile.getAbsolutePath(), "script.use"));
                } else {
                    return FormValidation.ok("So far so good");
                }

            } catch (IOException e) {
                return FormValidation.error("Failed to check Syntax:\n" + e);

            } catch (InterruptedException e) {
                return FormValidation.error("Failed to check Syntax:\n" + e);

            } finally {
                scriptFile.delete();
            }
        }
    }
}
