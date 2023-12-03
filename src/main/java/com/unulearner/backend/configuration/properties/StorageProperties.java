package com.unulearner.backend.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "files.storage.service")
public class StorageProperties {

	private String rootDirectory = "uploads";
	private String metaDataFileName = ".metadata.yml";

	public String getRootDirectory() {
		return rootDirectory;
	}

	public void setRootDirectory(String rootDirectory) {
		this.rootDirectory = rootDirectory;
	}

	public String getMetaDataFileName() {
		return metaDataFileName;
	}

	public void setMetaDataFileName(String metaDataFileName) {
		this.metaDataFileName = metaDataFileName;
	}
}