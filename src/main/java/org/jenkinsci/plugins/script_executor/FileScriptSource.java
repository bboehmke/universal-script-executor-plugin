package org.jenkinsci.plugins.script_executor;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;

import java.io.IOException;
import java.io.InputStream;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Runtime source based on given script file.
 * 
 * @author dvrzalik
 */
public class FileScriptSource extends ScriptSource {

    private String scriptFile;

    @DataBoundConstructor
    public FileScriptSource(String scriptFile) {
        this.scriptFile = scriptFile;
    }

    @Override
    public FilePath getScriptFile(FilePath projectWorkspace, AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException{
    	EnvVars env = build.getEnvironment(listener);
    	String expandedScriptdFile = env.expand(this.scriptFile);
        return new FilePath(projectWorkspace, expandedScriptdFile);
    }

    public String getScriptFile() {
      return scriptFile;
    }

    @Override
    public InputStream getScriptStream(FilePath projectWorkspace, AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException {
        return getScriptFile(projectWorkspace,build,listener).read();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileScriptSource that = (FileScriptSource) o;

        return scriptFile != null ? scriptFile.equals(that.scriptFile) : that.scriptFile == null;
    }

    @Override
    public int hashCode() {
        return scriptFile != null ? scriptFile.hashCode() : 0;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ScriptSource> {

        @Override
        public String getDisplayName() {
            return "Script file";
        }
    }
}
