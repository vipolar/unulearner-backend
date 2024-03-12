package com.unulearner.backend.storage.tasks.options;

import java.util.ArrayList;
import java.util.List;

public class OnExceptionOptions {
    private final List<Option> onExceptionOptions;
    private Boolean onDirectoryExceptionIgnore;
    private String onDirectoryExceptionOption;
    private Boolean onFileExceptionIgnore;
    private String onFileExceptionOption;

    public OnExceptionOptions(List<Option> onExceptionOptions) {
        this.onExceptionOptions = onExceptionOptions;
    }

    public List<String> getOnExceptionOptions(Boolean isDirectory) {
        final List<String> onExceptionOptionsList = new ArrayList<>();

        for (Option option : this.onExceptionOptions) {
            if (isDirectory && option.isApplicableToDirectories()) {
                onExceptionOptionsList.add(option.value);
            } else if (!isDirectory && option.isApplicableToFiles()) {
                onExceptionOptionsList.add(option.value);
            }
        }

        return onExceptionOptionsList;
    }

    public void setOnExceptionOption(String optionValue, Boolean isDirectory) {
        for (Option option : this.onExceptionOptions) {
            if (option.getValue().equals(optionValue) && option.isPersistable()) {
                if (isDirectory == true && option.isApplicableToDirectories()) {
                    this.onDirectoryExceptionOption = optionValue;
                    if (optionValue.equals("ignore")) {
                        this.onDirectoryExceptionIgnore = true;
                    }
                    return;
                }

                if (isDirectory == false && option.isApplicableToFiles()) {
                    this.onFileExceptionOption = optionValue;
                    if (optionValue.equals("ignore")) {
                        this.onFileExceptionIgnore = true;
                    }
                    return;
                }
            }
        }
    }

    public Boolean getOnExceptionIgnore(Boolean isDirectory) {
        if (isDirectory) {
            return this.onDirectoryExceptionIgnore;
        } else {
            return this.onFileExceptionIgnore;
        }
    }

    public String getOnExceptionOption(Boolean isDirectory) {
        if (isDirectory) {
            return this.onDirectoryExceptionOption;
        } else {
            return this.onFileExceptionOption;
        }
    }

    /* Subclass to represent an option with all of its flags */
    public static class Option {
        private final String value;
        private final String displayText;
        private final boolean isPersistable;
        private final boolean applicableToFiles;
        private final boolean applicableToDirectories;

        public Option(String value, String displayText, boolean isPersistable, boolean applicableToFiles, boolean applicableToDirectories) {
            this.value = value;
            this.displayText = displayText;
            this.isPersistable = isPersistable;
            this.applicableToFiles = applicableToFiles;
            this.applicableToDirectories = applicableToDirectories;
        }

        public String getValue() {
            return this.value;
        }

        public String getDisplayText() {
            return displayText;
        }

        public boolean isPersistable() {
            return this.isPersistable;
        }

        public boolean isApplicableToFiles() {
            return this.applicableToFiles;
        }

        public boolean isApplicableToDirectories() {
            return this.applicableToDirectories;
        }
    }
}