package com.unulearner.backend.storage.tasks;

import com.unulearner.backend.storage.tasks.options.OnExceptionOptions;
import com.unulearner.backend.storage.StorageTreeNode;
import com.unulearner.backend.storage.StorageTree;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Arrays;
import java.util.List;

public class StorageRemoveRecursivelyTask {
    private final List<String> removalLog;
    private final StorageTree activeStorageTree;
    private final StorageTreeNode rootDirectoryNode;
    private final OnExceptionOptions onExceptionOptions;
    private Iterator<StorageTreeNode> removalIterator;
    private List<StorageTreeNode> nodesToRemove;
    private StorageTreeNode removalTarget;
    private HttpStatus exitHttpStatus;
    private String exitMessage;

    public StorageRemoveRecursivelyTask(@NonNull StorageTree storageTree, @NonNull StorageTreeNode targetNode) {
        List<Iterator<StorageTreeNode>> iteratorStack = new ArrayList<>();
        Iterator<StorageTreeNode> iterator = targetNode.getChildren().iterator();
        StorageTreeNode currentNode = iterator.hasNext() ? iterator.next() : null;

        this.activeStorageTree = storageTree;
        this.onExceptionOptions = new OnExceptionOptions(new ArrayList<>(Arrays.asList(
            new OnExceptionOptions.Option("retry", "Retry", false, true, true),
            new OnExceptionOptions.Option("ignore", "Ignore", true, true, true),
            new OnExceptionOptions.Option("cancel", "Cancel", false, true, true)
        )));

        this.rootDirectoryNode = targetNode;
        this.nodesToRemove = new ArrayList<>();
        this.removalLog = new ArrayList<>();
        this.nodesToRemove.add(targetNode);

        while (currentNode != null) {
            this.nodesToRemove.add(currentNode);

            while (!iteratorStack.isEmpty() && !iterator.hasNext()) {
                iterator = iteratorStack.get(iteratorStack.size() - 1);
                iteratorStack.remove(iteratorStack.size() - 1);
            }

            if (iteratorStack.isEmpty() && !iterator.hasNext()) {
                currentNode = null;
                break;
            }

            if (currentNode.getChildren() != null && !currentNode.getChildren().isEmpty()) {
                iteratorStack.add(iterator);
                iterator = currentNode.getChildren().iterator();
            }

            currentNode = iterator.next();
        }

        Collections.reverse(this.nodesToRemove);
        this.removalIterator = this.nodesToRemove.iterator();
        this.removalTarget = this.removalIterator.next();
    }

    public Boolean executedSuccessfully() {
        while (this.removalTarget != null) {
            try {
                this.activeStorageTree.removeStorageTreeNode(this.removalTarget);
                this.removalLog.add(String.format("%s '%s' was removed successfully!", this.removalTarget.getChildren() != null ? "Directory" : "File", this.removalTarget.getOnDiskURL()));
                this.removalTarget = this.removalIterator.hasNext() ? this.removalIterator.next() : null;
            } catch (Exception exception) {
                if (this.onExceptionOptions.getOnExceptionIgnore(this.removalTarget.getChildren() != null) == true) {
                    this.ignoreCurrentTarget();
                    continue;
                }

                this.exitMessage = String.format("%s '%s' could not be removed: %s", this.removalTarget.getChildren() != null ? "Directory" : "File", this.removalTarget.getOnDiskURL(), exception.getMessage());
                this.exitHttpStatus = HttpStatus.UNPROCESSABLE_ENTITY;
                return false;
            }
        }

        this.exitMessage = String.format("Directory '%s' and all of its contents have been removed successfully!", this.rootDirectoryNode.getOnDiskURL());
        this.exitHttpStatus = HttpStatus.OK;
        return true;
    }

    public void ignoreCurrentTarget() {
        this.removalLog.add(String.format("%s '%s' was ignored!", this.removalTarget.getChildren() != null ? "Directory" : "File", this.removalTarget.getOnDiskURL()));
        this.removalTarget = this.removalIterator.hasNext() ? this.removalIterator.next() : null;
    }

    public StorageTreeNode getCurrentTarget() {
        return this.removalTarget;
    }

    public HttpStatus getExitHttpStatus() {
        return this.exitHttpStatus;
    }

    public String getExitMessage() {
        return this.exitMessage;
    }

    public List<String> getLog() {
        return this.removalLog;
    }

    public void setOnException(String optionValue) {
        this.onExceptionOptions.setOnExceptionOption(optionValue, this.removalTarget.getChildren() != null);
    }

    public List<String> getOnExceptionOptions() {
        if (this.removalTarget != null) {
            return this.onExceptionOptions.getOnExceptionOptions(this.removalTarget.getChildren() != null);
        }

        return null;
    }
}
