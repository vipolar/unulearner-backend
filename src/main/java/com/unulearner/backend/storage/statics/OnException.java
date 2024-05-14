package com.unulearner.backend.storage.statics;

import java.util.List;

import com.unulearner.backend.storage.StorageTreeNode;

public class OnException {
    private final StorageTreeNode target;
    private final StorageTreeNode conflict;
    private final StorageTreeNode destination;
    private final String exceptionMessage;
    private final String exceptionType;
    private final List<Option> options;

    public OnException(String message, String type, StorageTreeNode target, StorageTreeNode conflict, StorageTreeNode destination, List<Option> options) {        
        this.target = target;
        this.conflict = conflict;
        this.destination = destination;
        this.exceptionMessage = message;
        this.exceptionType = type;
        this.options = options;
    }

    public String getMessage() {
        return this.exceptionMessage;
    }

    public String getExceptionType() {
        return this.exceptionType;
    }

    public StorageTreeNode getTarget() {
        return this.target;
    }

    public StorageTreeNode getConflict() {
        return this.conflict;
    }

    public StorageTreeNode getDestination() {
        return this.destination;
    }

    public List<Option> getOptions() {
        return this.options;
    }

    public static class Option {
        private final String value;
        private final String displayText;
        private final Boolean isPersistable;

        public Option(String value, String displayText, Boolean isPersistable) {
            this.value = value;
            this.displayText = displayText;
            this.isPersistable = isPersistable;
        }

        protected String getValue() {
            return this.value;
        }

        protected String getDisplayText() {
            return this.displayText;
        }

        public Boolean getIsPersistable() {
            return this.isPersistable;
        }
    }
}
