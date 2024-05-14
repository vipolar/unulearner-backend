package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.unulearner.backend.storage.StorageTree;
import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.tasks.StorageTask.StorageTaskState.Option;
import com.unulearner.backend.storage.tasks.StorageTask.StorageTaskState.StateCode;

public class StorageTask {
    private final Map<String, OnExceptionActon> OEAMap;
    private final StorageTasksMap taskStorageTasksMap;
    private final StorageTree executiveStorageTree;
    private StorageTaskState storageTaskState;
    private final List<String> taskLog;
    private final UUID taskUUID;

    public StorageTask(StorageTree storageTree, StorageTasksMap storageTasksMap) {   
        this.taskStorageTasksMap = storageTasksMap;
        this.executiveStorageTree = storageTree;
        this.taskLog = new ArrayList<>();
        this.OEAMap = new HashMap<>();

        /* Having this in the super() constructor is best because... */
        this.taskUUID = this.taskStorageTasksMap.addStorageTask(this);
        this.storageTaskState = new StorageTaskState();
    }

    public synchronized void executeTask(String updatedName, String onExceptionAction, Boolean onExceptionActionIsPersistent) {
        return; /* OVERRIDE THE HELL OUT OF THIS EVERYWHERE!!! */
    }

    protected StorageTree storageTreeExecute() {
        return this.executiveStorageTree;
    }

    protected void setStorageTaskState(StorageTaskState storageTaskState) {
        this.taskLog.add(storageTaskState.taskStateLogMessage);
        this.storageTaskState = storageTaskState;
    }

    protected void finalizeStuff() {        
        switch (this.storageTaskState.getTaskState()) {
            case CANCELLED:
                break;
            case EXCEPTION:
                if (this.storageTaskState.getExceptionType() == null) {
                    this.storageTaskState.setExceptionType("general");
                }

                if (this.storageTaskState.getExceptionMessage() == null) {
                    this.storageTaskState.setExceptionMessage("Whoopsie-daisy! We have no idea what's going on...");
                }

                if (this.storageTaskState.getExceptionOptions() == null) {
                    this.storageTaskState.setExceptionOptions(
                        new ArrayList<>() {{
                            add(new Option("skip", "Skip".formatted(), true));
                        }}
                    );
                }
                break;
            case EXECUTING:
                this.storageTaskState.setExceptionType(null);
                this.storageTaskState.setExceptionMessage(null);
                this.storageTaskState.setExceptionOptions(null);
                break;
            case COMPLETED:
                this.storageTaskState.setExceptionType(null);
                this.storageTaskState.setExceptionMessage(null);
                this.storageTaskState.setExceptionOptions(null);
                break;
            default:
                break;
        }
    }

    public StorageTaskState getStorageTaskState() {
        if (this.storageTaskState.getTaskState() == StateCode.EXECUTING || this.storageTaskState.getTaskState() == StateCode.EXCEPTION) {
            this.storageTaskState.setTimeLeft(this.taskStorageTasksMap.scheduleStorageTaskRemoval(this.taskUUID));
        } else {
            this.storageTaskState.setTimeLeft(this.taskStorageTasksMap.removeStorageTask(this.taskUUID));
        }

        return this.storageTaskState;
    }

    protected void advanceTask(Boolean skipChildren) {
        this.resetOnExceptionAction();
        return;
    }

    protected List<String> getTaskLog() {
        return this.taskLog;
    }

    protected UUID getTaskUUID() {
        return this.taskUUID;
    }

    protected void cleanTaskUp() {
        return;
    }

    /* */
    protected class StorageTaskState {
        private Integer timeLeft;
        private String taskHeading;
        private StateCode taskState;
        private String exceptionType;
        private Integer attemptCounter;
        private String exceptionMessage;
        private String taskStateLogMessage;
        private List<Option> exceptionOptions;

        protected StorageTaskState() {
            this.taskState = StateCode.EXECUTING;
            this.attemptCounter = 0;
        }

        public Integer getTimeLeft() {
            return this.timeLeft;
        }

        public void setTimeLeft(Integer timeLeft) {
            this.timeLeft = timeLeft;
        }

        public String getTaskHeading() {
            return taskHeading;
        }

        public void setTaskHeading(String taskHeading) {
            this.taskHeading = taskHeading;
        }

        protected StateCode getTaskState() {
            return this.taskState;
        }

        protected void setTaskState(StateCode taskState) {
            this.taskState = taskState;
        }

        protected String getExceptionType() {
            return this.exceptionType;
        }

        protected void setExceptionType(String exceptionType) {
            this.exceptionType = exceptionType;
        }

        protected Integer getAttemptCounter() {
            return this.attemptCounter;
        }

        protected void incrementAttemptCounter() {
            this.attemptCounter = this.attemptCounter + 1;
        }

        protected String getExceptionMessage() {
            return this.exceptionMessage;
        }

        protected void setExceptionMessage(String exceptionMessage) {
            this.exceptionMessage = exceptionMessage;
        }

        public List<Option> getExceptionOptions() {
            return this.exceptionOptions;
        }

        public void setExceptionOptions(List<Option> exceptionOptions) {
            this.exceptionOptions = exceptionOptions;
        }

        protected String getTaskStateLogMessage() {
            return this.taskStateLogMessage;
        }

        protected void setTaskStateLogMessage(String taskStateLogMessage) {
            this.taskStateLogMessage = taskStateLogMessage;
        }

        @JsonIgnoreProperties
        public static enum StateCode{
            CANCELLED("cancelled"),
            EXCEPTION("exception"),
            EXECUTING("executing"),
            COMPLETED("completed");
        
            private final String value;
            StateCode(String v) {
                this.value = v;
            }
        
            public String getValue() {
                return this.value;
            }
        }

        @JsonIgnoreProperties
        protected static class Option {
            private final String value;
            private final String displayText;
            private final Boolean isPersistable;
    
            protected Option(String value, String displayText, Boolean isPersistable) {
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

    /* Methods to manipulate onException options and actions */
    private class OnExceptionActon {
        private String onCurrentNode;
        private String onDirectory;
        private String onFile;

        public String getOnCurrentNode() {
            return this.onCurrentNode;
        }

        public void setOnCurrentNode(String onCurrentNode) {
            this.onCurrentNode = onCurrentNode;
        }

        public String getOnDirectory() {
            return this.onDirectory;
        }

        public void setOnDirectory(String onDirectory) {
            this.onDirectory = onDirectory;
        }

        public String getOnFile() {
            return this.onFile;
        }

        public void setOnFile(String onFile) {
            this.onFile = onFile;
        }
    }

    protected void setOnExceptionAction(StorageTreeNode exceptionNode, String exceptionType, String onExceptionAction, Boolean onExceptionActionIsPersistent) {
        if (exceptionNode == null || exceptionType == null || onExceptionAction == null || onExceptionActionIsPersistent == null) {
            return;
        }

        OnExceptionActon onExceptionActon = this.OEAMap.putIfAbsent(onExceptionAction, new OnExceptionActon());

        onExceptionActon.setOnCurrentNode(onExceptionAction);
        if (onExceptionActionIsPersistent) {
            if (exceptionNode.isDirectory()) {
                onExceptionActon.setOnDirectory(onExceptionAction);
            } else {
                onExceptionActon.setOnFile(onExceptionAction);
            }
        }
    }

    protected String getOnExceptionAction(String type, StorageTreeNode exceptionNode) {
        final OnExceptionActon onExceptionActon;
        if (type == null || exceptionNode == null || (onExceptionActon = this.OEAMap.get(type)) == null) {
            return "";
        }

        String retValue;
        if ((retValue = onExceptionActon.getOnCurrentNode()) != null) {
            return retValue;
        } else {
            if (exceptionNode.isDirectory()) {
                retValue = onExceptionActon.getOnDirectory();
                return retValue != null ? retValue : "";
            } else {
                retValue = onExceptionActon.getOnFile();
                return retValue != null ? retValue : "";
            }
        }
    }

    protected void resetOnExceptionAction() {
        Set<String> keys = this.OEAMap.keySet();
        for (String key : keys) {
            OnExceptionActon onExceptionActon = this.OEAMap.get(key);
            if (onExceptionActon != null) {
                onExceptionActon.setOnCurrentNode(null);
            }
        }
    }
}
