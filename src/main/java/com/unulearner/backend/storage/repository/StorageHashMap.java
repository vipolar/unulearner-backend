package com.unulearner.backend.storage.repository;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.nio.file.Path;

import com.unulearner.backend.storage.StorageTreeNode;

public class StorageHashMap {
    private Map<UUID, StorageTreeNode> uuidKeyMap;
    private Map<Path, StorageTreeNode> pathKeyMap;
    private Map<String, StorageTreeNode> rurlKeyMap;

    public StorageHashMap() {
        this.uuidKeyMap = new HashMap<>();
        this.rurlKeyMap = new HashMap<>();
        this.pathKeyMap = new HashMap<>();
    }

    /**
     * @param uuidKey
     * @param rurlKey
     * @param pathKey
     * @param value
     */
    public StorageTreeNode put(UUID uuidKey, String rurlKey, Path pathKey, StorageTreeNode value) {
        this.uuidKeyMap.put(uuidKey, value);
        this.rurlKeyMap.put(rurlKey, value);
        this.pathKeyMap.put(pathKey, value);

        return null;
        //TODO: all the checks!
    }

    public StorageTreeNode get(UUID uuidKey) {
        return this.uuidKeyMap.get(uuidKey);
    }

    public StorageTreeNode get(String rurlKey) {
        return this.rurlKeyMap.get(rurlKey);
    }

    public StorageTreeNode get(Path pathKey) {
        return this.pathKeyMap.get(pathKey);
    }

    // Other methods as needed
}
