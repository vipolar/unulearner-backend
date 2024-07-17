package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.services.ExceptionHandler;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.responses.StorageServiceResponse;

public class StorageTaskBase {
    private final ExceptionHandler taskExceptionHandler;
    private final StorageTasksMap taskStorageTasksMap;
    private final StorageTree executiveStorageTree;
    private StorageTaskAction storageTaskAction;
    private final List<String> taskLog;
    private TaskState currentState;
    private final UUID taskUUID;

    protected StorageTaskBase(StorageTree storageTree, StorageTasksMap storageTasksMap) {  
        this.taskExceptionHandler = new ExceptionHandler(); 
        this.taskStorageTasksMap = storageTasksMap;
        this.executiveStorageTree = storageTree;
        this.taskLog = new ArrayList<>();

        /* Having this in the super() constructor is best because... */
        this.taskUUID = this.taskStorageTasksMap.addStorageTask(this);
        this.storageTaskAction = new StorageTaskAction();
        this.currentState = TaskState.EXECUTING;
    }

    /* This is a simple enum to enable a stable communication with the frontend */
    protected static enum TaskState{
        CANCELLED("cancelled"),
        EXCEPTION("exception"),
        EXECUTING("executing"),
        COMPLETED("completed");
    
        private final String value;
        
        TaskState(String v) {
            this.value = v;
        }
    
        @Override
        public String toString() {
            return this.value;
        }
    }

    /* This is the outward facing method, meant to be fired by the service shell or the controller */
    public synchronized void execute(Map<String, Object> taskParameters) {
        return;
    }

    public StorageServiceResponse getStorageServiceResponse() {
        if (this.getCurrentState() == TaskState.EXECUTING || this.getCurrentState() == TaskState.EXCEPTION) {
            this.storageTaskAction.setTimeLeft(this.taskStorageTasksMap.scheduleStorageTaskRemoval(this.taskUUID));
        } else {
            this.storageTaskAction.setTimeLeft(this.taskStorageTasksMap.removeStorageTask(this.taskUUID));
        }

        return new StorageServiceResponse(this.taskUUID, this.currentState.toString(), this.storageTaskAction, this.taskExceptionHandler.getExceptionOptions());
    }

    protected void setStorageTaskAction(StorageTaskAction storageTaskAction) {
        this.taskLog.add(storageTaskAction.message);
        this.storageTaskAction = storageTaskAction;
    }

    protected void setCurrentState(TaskState currentState) {
        this.currentState = currentState;
    }

    protected ExceptionHandler getTaskExceptionHandler() {
        return this.taskExceptionHandler;
    }

    protected StorageTaskAction getStorageTaskAction() {
        return this.storageTaskAction;
    }

    protected StorageTree storageTreeExecute() {
        return this.executiveStorageTree;
    }

    protected TaskState getCurrentState() {
        return this.currentState;
    }

    protected List<String> getTaskLog() {
        return this.taskLog;
    }

    protected UUID getTaskUUID() {
        return this.taskUUID;
    }


    /* Without actions taken where will the tasks ever go? */
    public class StorageTaskAction {
        private String message;
        private Integer timeLeft;
        private String actionHeader;
        private String exceptionType;
        private Integer attemptCounter;
        private String exceptionMessage;        

        protected StorageTaskAction() {
            this.attemptCounter = 0;
        }

        public String getMessage() {
            return this.message;
        }

        protected void setMessage(String message) {
            this.message = message;
        }   

        public Integer getTimeLeft() {
            return this.timeLeft;
        }

        protected void setTimeLeft(Integer timeLeft) {
            this.timeLeft = timeLeft;
        }

        public String getActionHeader() {
            return this.actionHeader;
        }

        protected void setActionHeader(String actionHeader) {
            this.actionHeader = actionHeader;
        }

        public String getExceptionType() {
            return this.exceptionType;
        }

        protected void setExceptionType(String exceptionType) {
            this.exceptionType = exceptionType;
        }

        public Integer getAttemptCounter() {
            return this.attemptCounter;
        }

        protected void incrementAttemptCounter() {
            this.attemptCounter = this.attemptCounter + 1;
        }

        public String getExceptionMessage() {
            return this.exceptionMessage;
        }

        protected void setExceptionMessage(String exceptionMessage) {
            this.exceptionMessage = exceptionMessage;
        }     
    }
}
