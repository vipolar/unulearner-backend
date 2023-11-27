package com.unulearner.backend.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "files.storage.service")
public class StorageProperties {

	private String rootDirectory = "uploads";

    public String getRootDirectory() {
		return rootDirectory;
	}

	public void setRootDirectory(String rootDirectory) {
		this.rootDirectory = rootDirectory;
	}
}