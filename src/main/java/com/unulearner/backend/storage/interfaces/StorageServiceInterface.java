package com.unulearner.backend.storage.interfaces;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.extensions.NodePath;

import java.util.NoSuchElementException;

public interface StorageServiceInterface {
    public StorageNode createNewStorageNode(StorageNode parent, List<StorageNode> children, NodePath nodePath, UUID user, UUID group, String permissions);
    public StorageNode createRootStorageNode(List<StorageNode> children, NodePath storageNodePath);
    public StorageNode retrieveStorageNodeByURL(String url) throws NoSuchElementException;
    public List<StorageNode> retrieveChildrenStorageNodes(StorageNode storageNode);
    //public StorageNode updateStorageNode(StorageNode storageNode);
    public StorageNode saveStorageNode(StorageNode storageNode);
    public void deleteStorageNode(StorageNode storageNode);
    public Comparator<StorageNode> getStorageComparator();
    public NodePath getRootDirectory() throws Exception;
}
