package com.unulearner.backend.services.files.storage.helper;

public class FileUploadResponseMessage {
  private String message;

  public FileUploadResponseMessage(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

}