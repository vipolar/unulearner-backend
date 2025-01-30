package com.unulearner.backend.storage.taskflow.exception;

import java.util.ArrayList;
import java.util.Arrays;

public final class Option {
    private final String action;
    private final String displayText;
    private final ArrayList<Parameter> parameters;

    public Option(String action, String localizedDisplayText, Parameter... params) {
        this.action = action;
        this.displayText = localizedDisplayText;
        this.parameters = new ArrayList<>(Arrays.asList(params));
    }

    public String getAction() {
        return this.action;
    }

    public String getDisplayText() {
        return this.displayText;
    }

    public ArrayList<Parameter> getParameters() {
        return this.parameters;
    }

    public static class Parameter {
        private final String parameter;
        private final String displayText;
        private final String parameterType;

        public Parameter(String parameter, String displayText, String parameterType) {
            this.parameter = parameter;
            this.displayText = displayText;
            this.parameterType = parameterType;
        }

        public String getParameter() {
            return this.parameter;
        }

        public String getDisplayText() {
            return this.displayText;
        }

        public String getParameterType() {
            return this.parameterType;
        }
    }
}