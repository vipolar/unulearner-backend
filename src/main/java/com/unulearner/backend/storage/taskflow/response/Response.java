package com.unulearner.backend.storage.taskflow.response;

import java.util.ArrayList;
import java.util.UUID;

import com.unulearner.backend.storage.taskflow.Base.Action;
import com.unulearner.backend.storage.taskflow.exception.Option;

public class Response {
    private final UUID task;
    private final String state;
    private final Action action;
    private final ArrayList<Option> options;

    public UUID getTask() {
        return this.task;
    }

    public String getState() {
        return this.state;
    }

    public Action getAction() {
        return this.action;
    }

    public ArrayList<Option> getOptions() {
        return this.options;
    }

    public Response(UUID task, String state, Action action, ArrayList<Option> options) {
        this.task = task;
        this.state = state;
        this.action = action;
        this.options = options;
    }
}
