package com.unulearner.backend.storage.tasks;

import com.unulearner.backend.storage.tasks.options.OnExceptionOptions;
import com.unulearner.backend.storage.StorageTreeNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Arrays;
import java.util.List;

public class StorageTransferRecursivelyTask {
    private final OnExceptionOptions onExceptionOptions;
    private final OnExceptionOptions onConflictOptions;

    private List<String> transferLog;
    private StorageTreeNode transferTarget;
    private StorageTreeNode transferDestination;
    private Iterator<StorageTreeNode> transferIterator;
    private List<Iterator<StorageTreeNode>> transferIteratorStack;
    private List<StorageTreeNode> transferDestinationStack;

    private static class TransferStack {
        private final StorageTreeNode originalDestination;
        private final List<StorageTreeNode> targetNodes;
        private final StorageTreeNode newDestination;

        public TransferStack(StorageTreeNode originalDestination, StorageTreeNode newDestination, List<StorageTreeNode> targetNodes) {
            this.originalDestination = originalDestination;
            this.newDestination = newDestination;
            this.targetNodes = targetNodes;
        }

        public StorageTreeNode getOriginalDestination() {
            return originalDestination;
        }

        public List<StorageTreeNode> getTargetNodes() {
            return targetNodes;
        }

        public StorageTreeNode getNewDestination() {
            return newDestination;
        }
    }

    public StorageTransferRecursivelyTask(StorageTreeNode targetNode, StorageTreeNode destinationNode) {
        this.transferLog = new ArrayList<>();

        this.onExceptionOptions = new OnExceptionOptions(new ArrayList<>(Arrays.asList(
            new OnExceptionOptions.Option("retry", "Retry", false, true, true),
            new OnExceptionOptions.Option("ignore", "Ignore", true, true, true),
            new OnExceptionOptions.Option("cancel", "Cancel", false, true, true)
        )));

        this.onConflictOptions = new OnExceptionOptions(new ArrayList<>(Arrays.asList(
            new OnExceptionOptions.Option("merge", "Merge", true, false, true),
            new OnExceptionOptions.Option("overwrite", "Overwrite", true, true, false),
            new OnExceptionOptions.Option("rename", "Rename", true, true, true)
        )));

        List<StorageTreeNode> dummyTargetNodes = new ArrayList<>(Arrays.asList(targetNode));
        TransferStack initalTransferStack = new TransferStack(targetNode.getParent(), destinationNode, dummyTargetNodes);
        List<TransferStack> s = new ArrayList<>(Arrays.asList(initalTransferStack));

        this.transferTarget = targetNode;
        this.transferIteratorStack = new ArrayList<>();
        this.transferIterator = targetNode.getChildren().iterator();

        this.transferDestination = destinationNode;
        this.transferDestinationStack = new ArrayList<>();
        this.transferDestinationStack.add(this.transferDestination);
    }

    public Boolean hasNextTarget() {
        return this.transferTarget != null ? true : false;
    }

    public void logTransferAttempt(String message) {
        this.transferLog.add(message);
    }

    public void advanceTransferIterator(StorageTreeNode transferedNode) {
        while (!this.transferIteratorStack.isEmpty() && !this.transferIterator.hasNext()) {
            this.transferIterator = this.transferIteratorStack.get(this.transferIteratorStack.size() - 1);
            this.transferIteratorStack.remove(this.transferIteratorStack.size() - 1);

            this.transferDestination = this.transferDestinationStack.get(this.transferDestinationStack.size() - 1);
            this.transferDestinationStack.remove(this.transferDestinationStack.size() - 1);
        }

        if (this.transferIteratorStack.isEmpty() && !this.transferIterator.hasNext()) {
            this.transferTarget = null;
            return;
        }

        if (this.transferTarget.getChildren() != null && !this.transferTarget.getChildren().isEmpty() && transferedNode != null) {
            this.transferIteratorStack.add(this.transferIterator);
            this.transferIterator = this.transferTarget.getChildren().iterator();

            this.transferDestinationStack.add(this.transferDestination);
            this.transferDestination = transferedNode;
        }
        
        this.transferTarget = this.transferIterator.next();
        return;
    }

    public StorageTreeNode getTransferTarget(){
        return this.transferTarget;
    }

    public StorageTreeNode getTransferDestination(){
        return this.transferDestination;
    }

    public List<String> getTransferLog() {
        return this.transferLog;
    }

    /* All about the exceptions! */
    public String getOnFileException() {
        return this.onExceptionOptions.getOnFileException();
    }
/*
    public Boolean getIsOnFileException(String optionValue) {
        return this.onExceptionOptions.getIsOnFileException(optionValue);
    }
*/
    public void setOnFileException(String optionValue) {
        this.onExceptionOptions.setOnFileException(optionValue);
    }

    public String getOnDirectoryException() {
        return this.onExceptionOptions.getOnDirectoryException();
    }
/*
    public Boolean getIsOnDirectoryException(String optionValue) {
        return this.onExceptionOptions.getIsOnDirectoryException(optionValue);
    }
*/
    public void setOnDirectoryException(String optionValue) {
        this.onExceptionOptions.setOnDirectoryException(optionValue);
    }

    public List<String> getOnExceptionOptions(Boolean isDirectory) {
        return this.onExceptionOptions.getOnExceptionOptions(isDirectory);
    }

    /* All about the conflicts! */
    public String getOnFileConflict() {
        return this.onConflictOptions.getOnFileException();
    }
/*
    public Boolean getIsOnFileConflict(String optionValue) {
        return this.onConflictOptions.getIsOnFileException(optionValue);
    }
*/
    public void setOnFileConflict(String optionValue) {
        this.onConflictOptions.setOnFileException(optionValue);
    }

    public String getOnDirectoryConflict() {
        return this.onConflictOptions.getOnDirectoryException();
    }
/*
    public Boolean getIsOnDirectoryConflict(String optionValue) {
        return this.onConflictOptions.getIsOnDirectoryException(optionValue);
    }
*/
    public void setOnDirectoryConflict(String optionValue) {
        this.onConflictOptions.setOnDirectoryException(optionValue);
    }

    public List<String> getOnConflictOptions(Boolean isDirectory) {
        return this.onConflictOptions.getOnExceptionOptions(isDirectory);
    }
}
