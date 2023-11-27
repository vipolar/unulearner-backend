package com.unulearner.backend.services.files.storage.helper;

public class FileUploadDataModel {
    private String name;
    private String url;

    public FileUploadDataModel(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}