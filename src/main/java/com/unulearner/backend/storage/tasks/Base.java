package com.unulearner.backend.storage.tasks;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.ListIterator;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.PostConstruct;

import com.unulearner.backend.storage.Storage;
import com.unulearner.backend.storage.tasks.exception.Handler;
import com.unulearner.backend.storage.tasks.response.Response;
import com.unulearner.backend.storage.tasks.constant.TaskState;
import com.unulearner.backend.storage.exceptions.StorageUtilException;

@Component
@Scope("prototype")
public class Base {
    private Handler exceptionHandler;
    private TaskState currentState;
    private Action currentAction;
    private List<String> log;
    private UUID taskUUID;

    @Autowired
    private Storage storageTree;

    /*  */
    private AtomicReference<CompletableFuture<Void>> completionSignalRef;

    @PostConstruct
    private void postConstruct() {
        this.exceptionHandler = new Handler();
        this.completionSignalRef = new AtomicReference<>(new CompletableFuture<>());
        this.currentAction = new Action(null);
        this.currentState = TaskState.EXECUTING;
        this.log = new ArrayList<>();
    }

    public Base initialize(UUID taskUUID) throws StorageUtilException {
        if (this.taskUUID != null) {
            throw new StorageUtilException(null);
        }

        if (this.taskUUID == null && taskUUID == null) {
            throw new StorageUtilException(null);
        }

        this.taskUUID = taskUUID;
        return this;
    }

    public synchronized void execute(Map<String, Object> taskParameters) {
        return;
    }

    public Response getResponse() {
        return new Response(this.taskUUID, this.currentState.toString(), this.currentAction, this.exceptionHandler.getExceptionOptions());
    }

    public void setCurrentAction(Action storageTaskAction) {
        this.log.add(storageTaskAction.message);
        this.currentAction = storageTaskAction;
    }

    public CompletableFuture<Void> getCompletionSignal() {
        return this.completionSignalRef.get();
    }

    public void setCurrentState(TaskState currentState) {
        this.currentState = currentState;
        this.completionSignalRef.get().complete(null);
        this.completionSignalRef.set(new CompletableFuture<>());
    }


    public Handler getExceptionHandler() {
        return this.exceptionHandler;
    }

    public TaskState getCurrentState() {
        return this.currentState;
    }

    public Action getCurrentAction() {
        return this.currentAction;
    }

    protected void skipCurrentAction() {
        return;
    }

    public Storage storageExecutor() {
        return this.storageTree;
    }

    public List<String> getLog() {
        return this.log;
    }

    public UUID getTaskUUID() {
        return this.taskUUID;
    }

    protected void advance() {
        return;
    }

    /* Without actions taken where will the tasks ever go? */
    public class Action {
        private String message;
        private Long validBefore;
        private String actionHeader;
        private String exceptionType;
        private Integer attemptCounter;
        private String exceptionMessage;

        @JsonIgnore
        private Action parentAction;

        @JsonIgnore
        private ListIterator<Action> childActions;

        @JsonIgnore
        private List<Action> childActionsHiddenList; 

        public Action(Action parentAction) {
            this.attemptCounter = 0;
            this.parentAction = parentAction;
            this.childActionsHiddenList =  new ArrayList<>();
            this.childActions = this.childActionsHiddenList.listIterator();
        }

        public String getMessage() {
            return this.message;
        }

        public void setMessage(String message) {
            this.message = message;
        }   

        public Long getValidBefore() {
            return this.validBefore;
        }

        public void setValidBefore(Long timeLeft) {
            this.validBefore = timeLeft;
        }

        public String getActionHeader() {
            return this.actionHeader;
        }

        public void setActionHeader(String actionHeader) {
            this.actionHeader = actionHeader;
        }

        public String getExceptionType() {
            return this.exceptionType;
        }

        public void setExceptionType(String exceptionType) {
            this.exceptionType = exceptionType;
        }

        public Integer getAttemptCounter() {
            return this.attemptCounter;
        }

        public void incrementAttemptCounter() {
            this.attemptCounter = this.attemptCounter + 1;
        }

        public String getExceptionMessage() {
            return this.exceptionMessage;
        }

        public void setExceptionMessage(String exceptionMessage) {
            this.exceptionMessage = exceptionMessage;
        }

        public Action getParentAction() {
            return this.parentAction;
        }

        public ListIterator<Action> getChildActions() {
            return this.childActions;
        }
    }
}
