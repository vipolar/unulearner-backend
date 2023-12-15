package com.unulearner.backend.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "files.storage.service")
public class StorageProperties {

	private Integer maxNameLength = 255;
	private String rootDirectory = "uploads";
	private String rootDirectoryDescription = "Example description...";

	public Integer getMaxNameLength() {
		return maxNameLength;
	}

	public void setMaxNameLength(Integer maxNameLength) {
		this.maxNameLength = maxNameLength;
	}

	public String getRootDirectory() {
		return rootDirectory;
	}

	public void setRootDirectory(String rootDirectory) {
		this.rootDirectory = rootDirectory;
	}

	public String getRootDirectoryDescription() {
		return rootDirectoryDescription;
	}

	public void setRootDirectoryDescription(String rootDirectoryDescription) {
		this.rootDirectoryDescription = rootDirectoryDescription;
	}
}