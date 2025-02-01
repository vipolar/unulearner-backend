package com.unulearner.backend.storage.tasks.constant;

public enum TaskState{
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