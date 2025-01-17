package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import com.unulearner.backend.storage.Storage;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.responses.StorageServiceResponse;
import com.unulearner.backend.storage.tasks.exception.Handler;

@Component
@Scope("prototype")
public class StorageTaskBase {
    private Handler taskExceptionHandler;
    private StorageTaskAction storageTaskAction;
    private TaskState currentState;
    private List<String> taskLog;
    private UUID taskUUID;

    @Autowired
    private StorageTasksMap storageTasksMap;

    @Autowired
    private Storage storageTree;

    @PostConstruct
    private void init() {
        this.taskExceptionHandler = new Handler();
        this.taskUUID = this.storageTasksMap.addStorageTask(this);
        this.storageTaskAction = new StorageTaskAction();
        this.currentState = TaskState.EXECUTING;
        this.taskLog = new ArrayList<>();
    }

    /* This is a simple enum to enable a stable communication with the frontend */
    protected static enum TaskState{
        CANCELLED("cancelled"),
        EXCEPTION("exception"),
        EXECUTING("executing"),
        COMPLETED("completed");
    
        private final String value;
        
        TaskState(String value) {
            this.value = value;
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
            this.storageTaskAction.setTimeLeft(this.storageTasksMap.scheduleStorageTaskRemoval(this.taskUUID));
        } else {
            this.storageTaskAction.setTimeLeft(this.storageTasksMap.removeStorageTask(this.taskUUID));
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

    protected Handler getTaskExceptionHandler() {
        return this.taskExceptionHandler;
    }

    protected StorageTaskAction getStorageTaskAction() {
        return this.storageTaskAction;
    }

    protected Storage storageTreeExecute() {
        return this.storageTree;
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
