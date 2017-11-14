package org.jenkinsci.plugins.script_executor;

import hudson.AbortException;

public class ExecutionFailureException extends AbortException {

    private int exitCode = 0;

    public ExecutionFailureException(String message) {
        super(message);
    }

    public ExecutionFailureException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    public int getExitCode() {
        return exitCode;
    }
}
