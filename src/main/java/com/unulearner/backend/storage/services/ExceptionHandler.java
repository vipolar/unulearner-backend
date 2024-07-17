package com.unulearner.backend.storage.services;

import com.unulearner.backend.storage.entities.StorageNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ExceptionHandler {
    private Map<String, OnExceptionOption> exceptionOptions;
    private final Map<String, OnExceptionActon> OEAMap;

    public ExceptionHandler() {
        this.OEAMap = new HashMap<>();
    }

    /* TODO: basically everything down there */
    public static class OnExceptionOption {
        private final String displayText;
        private final Map<String, String> parameters;

        public OnExceptionOption(String displayText, Map<String, String> params) {
            this.parameters = params;
            this.displayText = displayText;
        }

        public String getDisplayText() {
            return this.displayText;
        }

        public Map<String, String> getParameters() {
            return this.parameters;
        }
    }

    public Map<String, OnExceptionOption> getExceptionOptions() {
        return this.exceptionOptions;
    }

    public void setExceptionOptions(Map<String, OnExceptionOption> exceptionOptions) {
        this.exceptionOptions = exceptionOptions;
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

    public void setOnExceptionAction(StorageNode exceptionNode, String exceptionType, String onExceptionAction, Boolean onExceptionActionIsPersistent) {
        if (exceptionNode == null || exceptionType == null || onExceptionAction == null) {
            return;
        }

        final Boolean setOnExceptionActionAsDefault = onExceptionActionIsPersistent != null ? onExceptionActionIsPersistent : false;
        final OnExceptionActon onExceptionActon = this.OEAMap.putIfAbsent(onExceptionAction, new OnExceptionActon());

        onExceptionActon.setOnCurrentNode(onExceptionAction);
        if (setOnExceptionActionAsDefault) {
            if (exceptionNode.isDirectory()) {
                onExceptionActon.setOnDirectory(onExceptionAction);
            } else {
                onExceptionActon.setOnFile(onExceptionAction);
            }
        }
    }

    public String getOnExceptionAction(String type, StorageNode exceptionNode) {
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

    public void resetOnExceptionAction() {
        Set<String> keys = this.OEAMap.keySet();
        for (String key : keys) {
            OnExceptionActon onExceptionActon = this.OEAMap.get(key);
            if (onExceptionActon != null) {
                onExceptionActon.setOnCurrentNode(null);
            }
        }
    }
}
