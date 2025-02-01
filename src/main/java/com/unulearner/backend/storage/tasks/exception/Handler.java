package com.unulearner.backend.storage.tasks.exception;

import com.unulearner.backend.storage.models.Entry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Handler { /* TODO: the whole thing might need rework! */
    private ArrayList<Option> exceptionOptions;
    private Map<String, String> onDirectoryException;
    //private Map<String, String> onSymlinkException;
    private Map<String, String> onFileException;
    private String onCurrentExceptionAction;

    public ArrayList<Option> getExceptionOptions() {
        return this.exceptionOptions;
    }

    public void setExceptionOptions(ArrayList<Option> exceptionOptions) {
        this.exceptionOptions = exceptionOptions;
    }

    public void setOnExceptionAction(Entry storageNode, String exceptionType, String exceptionAction, Boolean setOnExceptionActionAsDefault) {
        if (storageNode == null || exceptionType == null || exceptionAction == null) {
            return;
        }

        for (Option option : this.exceptionOptions) {
            if (option.getAction().equals(exceptionAction)) {
                this.onCurrentExceptionAction = exceptionAction;

                if (setOnExceptionActionAsDefault) {
                    if (storageNode.getIsDirectory()) {
                        if (this.onDirectoryException == null) {
                            this.onDirectoryException = new HashMap<>(4);
                        }
                        
                        this.onDirectoryException.put(exceptionType, exceptionAction);
                    } else {
                        if (this.onFileException == null) {
                            this.onFileException = new HashMap<>(4);
                        }
                        
                        this.onFileException.put(exceptionType, exceptionAction);
                    }
                }
            }
        }
    }

    public String getOnExceptionAction(Entry storageNode, String exceptionType) throws IllegalArgumentException {
        if (storageNode == null || exceptionType == null) {
            return ""; /* empty return is handled as 'default' */
        }

        if (this.onCurrentExceptionAction != null) {
            final String exceptionAction = this.onCurrentExceptionAction;
            return exceptionAction != null ? exceptionAction : "";
        }

        if (storageNode.getIsDirectory()) {
            if (this.onDirectoryException == null) {
                return "";
            }

            final String exceptionAction = this.onDirectoryException.get(exceptionType);
            return exceptionAction != null ? exceptionAction : "";
        } else {
            if (this.onFileException == null) {
                return "";
            }

            final String exceptionAction = this.onFileException.get(exceptionType);
            return exceptionAction != null ? exceptionAction : "";
        }
    }

    public void resetOnExceptionAction() {
        this.onCurrentExceptionAction = null;
    }
}
