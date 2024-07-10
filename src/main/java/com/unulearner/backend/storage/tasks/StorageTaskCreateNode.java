package com.unulearner.backend.storage.tasks;

import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Optional;
import java.util.List;

import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.statics.StorageFileName;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import com.unulearner.backend.storage.exceptions.FileNameGenerationException;
import com.unulearner.backend.storage.exceptions.FileNameValidationException;


public class StorageTaskCreateNode extends StorageTaskBase {
    public StorageTaskCreateNode(StorageTree storageTree, MultipartFile newFile, String newFileDescription, StorageNode destinationStorageTreeNode, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        final StorageCreateNodeTaskAction storageTaskAction = new StorageCreateNodeTaskAction(newFile, newFileDescription, destinationStorageTreeNode);

        storageTaskAction.setActionHeader("Upload '%s' to '%s' directory".formatted(newFile.getOriginalFilename(), destinationStorageTreeNode.getOnDiskURL()));
        storageTaskAction.setMessage("File upload task has been successfully initialized".formatted());

        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    public StorageTaskCreateNode(StorageTree storageTree, String newDirectoryName, String newDirectoryDescription, StorageNode destinationStorageTreeNode, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);
        
        final StorageCreateNodeTaskAction storageTaskAction = new StorageCreateNodeTaskAction(newDirectoryName, newDirectoryDescription, destinationStorageTreeNode);

        storageTaskAction.setActionHeader("Create '%s' directory in '%s' directory".formatted(newDirectoryName, destinationStorageTreeNode.getOnDiskURL()));
        storageTaskAction.setMessage("Directory creation task has been successfully initialized".formatted());
    
        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    @Override
    public synchronized void execute(String updatedName, String onExceptionAction, Boolean onExceptionActionIsPersistent) {
        final StorageCreateNodeTaskAction storageTaskAction = (StorageCreateNodeTaskAction) this.getStorageTaskAction();

        if (storageTaskAction.getNewStorageTreeNode().getId() != null) {
            storageTaskAction.setMessage("%s '%s' directory task finished successfully!".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "Directory creation in" : "File upload to", storageTaskAction.getNewStorageTreeNode().getParent().getOnDiskURL()));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (onExceptionAction != null && onExceptionAction.equals("cancel")) {
            storageTaskAction.setMessage("%s '%s' directory task was cancelled...".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "Directory creation in" : "File upload to", storageTaskAction.getNewStorageTreeNode().getParent().getOnDiskURL()));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            onExceptionActionIsPersistent = onExceptionActionIsPersistent != null ? onExceptionActionIsPersistent : false;
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskAction.getNewStorageTreeNode(), storageTaskAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        storageTaskAction.incrementAttemptCounter();
        while (storageTaskAction.getNewStorageTreeNode().getId() == null) {
            try {
                final String exceptionType = storageTaskAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(exceptionType, storageTaskAction.getNewStorageTreeNode());
                
                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "FileAlreadyExistsException": /* TODO: add option to publish the conflicting node if it isn't published */
                            if (storageTaskAction.getConflictStorageTreeNode() == null) {
                                throw new RuntimeException("%s '%s' could not be %s to directory '%s' due to a supposed conflicting node, existence of which can neither be confirmed nor denied...".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageTreeNode().getOnDiskName(), storageTaskAction.getNewStorageTreeNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageTreeNode().getOnDiskURL()));
                            }

                            switch (exceptionAction) {
                                case "keep":
                                    storageTaskAction.getNewStorageTreeNode().setOnDiskName(StorageFileName.findNextAvailableFileName(storageTaskAction.getNewStorageTreeNode().getOnDiskName()));
                                    storageTaskAction.setConflictStorageTreeNode(null);
                                    break;
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskAction.getNewStorageTreeNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskAction.setConflictStorageTreeNode(null);
                                    break;
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageTreeNode().getOnDiskName(), storageTaskAction.getNewStorageTreeNode().isDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageTreeNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("keep", "Keep both".formatted(), true));
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to a conflicting node already in place.".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageTreeNode().getOnDiskName(), storageTaskAction.getNewStorageTreeNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageTreeNode().getParent().getOnDiskURL()));

                                    
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameValidationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskAction.getNewStorageTreeNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskAction.setConflictStorageTreeNode(null);
                                    break;
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageTreeNode().getOnDiskName(), storageTaskAction.getNewStorageTreeNode().isDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageTreeNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to a provided file name being incompatible.".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageTreeNode().getOnDiskName(), storageTaskAction.getNewStorageTreeNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageTreeNode().getParent().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameGenerationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskAction.getNewStorageTreeNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskAction.setConflictStorageTreeNode(null);
                                    break;
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageTreeNode().getOnDiskName(), storageTaskAction.getNewStorageTreeNode().isDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageTreeNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to a failed name generation attempt.".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageTreeNode().getOnDiskName(), storageTaskAction.getNewStorageTreeNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageTreeNode().getParent().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageTreeNode().getOnDiskName(), storageTaskAction.getNewStorageTreeNode().isDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageTreeNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    List<OnExceptionOption> onPhysicalOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};
        
                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to a persistent I/O exception occurring.".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageTreeNode().getOnDiskName(), storageTaskAction.getNewStorageTreeNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageTreeNode().getParent().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onPhysicalOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageTreeNode().getOnDiskName(), storageTaskAction.getNewStorageTreeNode().isDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageTreeNode().getOnDiskURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    List<OnExceptionOption> onPhysicalOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "directory" : "file"), true));
                                    }};
        
                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to an unexpected exception occurring.".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageTreeNode().getOnDiskName(), storageTaskAction.getNewStorageTreeNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageTreeNode().getParent().getOnDiskURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onPhysicalOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                    }
                }                

                if (storageTaskAction.getNewStorageTreeNode().getNodePath() == null) {
                    storageTaskAction.setNewStorageTreeNode(this.storageTreeExecute().createStorageTreeNode(storageTaskAction.getNewMultipartFile(), storageTaskAction.getNewStorageTreeNode(), storageTaskAction.getDestinationStorageTreeNode()));
                }

                if (storageTaskAction.getNewStorageTreeNode().getId() == null) {
                    this.storageTreeExecute().publishStorageTreeNode(storageTaskAction.getNewStorageTreeNode());
                }

                storageTaskAction.setMessage("%s '%s' has been %s directory '%s' successfully!".formatted(storageTaskAction.getNewStorageTreeNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageTreeNode().getOnDiskURL(), storageTaskAction.getNewStorageTreeNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageTreeNode().getParent().getOnDiskURL()));
                storageTaskAction.setExceptionMessage(null);
                storageTaskAction.setExceptionType(null);
            } catch (FileAlreadyExistsException exception) {
                try { /* Here we attempt to find the conflicting node. And if we can't find it, we try to recover it */
                    final Optional<StorageNode> possibleConflict =  storageTaskAction.getNewStorageTreeNode().getParent().getChildren().stream().filter(entry -> entry.getNodePath().getFileName().toString().equals(storageTaskAction.getNewStorageTreeNode().getOnDiskName())).findFirst();
                    if (possibleConflict.isPresent()) {
                        storageTaskAction.setConflictStorageTreeNode(possibleConflict.get());
                    } else {
                        storageTaskAction.setConflictStorageTreeNode(this.storageTreeExecute().recoverStorageTreeNode(storageTaskAction.getNewStorageTreeNode().getOnDiskName(), storageTaskAction.getNewStorageTreeNode().getParent()));
                    }
                } catch (Exception recoveryException) {
                    storageTaskAction.setConflictStorageTreeNode(null);

                    /* wild territories... */
                    storageTaskAction.setExceptionType(recoveryException.getClass().getSimpleName());
                    storageTaskAction.setExceptionMessage(recoveryException.getMessage());
                    continue;
                }

                storageTaskAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (FileNameValidationException exception) {
                storageTaskAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (FileNameGenerationException exception) {
                storageTaskAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (IOException exception) {
                storageTaskAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (Exception exception) {
                storageTaskAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskAction.setExceptionMessage(exception.getMessage());
            }
        }
    }

    protected class StorageCreateNodeTaskAction extends StorageTaskAction {
        private final MultipartFile newMultipartFile;

        private StorageNode newStorageTreeNode;
        private StorageNode conflictStorageTreeNode;
        private StorageNode destinationStorageTreeNode;

        protected StorageCreateNodeTaskAction(MultipartFile newFile, String newFileDescription, StorageNode destinationStorageTreeNode) {
            super();

            this.newStorageTreeNode = new StorageNode(null, null, null, newFileDescription);
            this.newStorageTreeNode.setOnDiskName(newFile.getOriginalFilename());
            this.destinationStorageTreeNode = destinationStorageTreeNode;
            this.newMultipartFile = newFile;
        }

        protected StorageCreateNodeTaskAction(String newDirectoryName, String newDirectoryDescription, StorageNode destinationStorageTreeNode) {
            super();

            this.newStorageTreeNode = new StorageNode(null, new ArrayList<>(), null, newDirectoryDescription);
            this.destinationStorageTreeNode = destinationStorageTreeNode;
            this.newStorageTreeNode.setOnDiskName(newDirectoryName);
            this.newMultipartFile = null;
        }

        protected MultipartFile getNewMultipartFile() {
            return this.newMultipartFile;
        }

        public StorageNode getNewStorageTreeNode() {
            return this.newStorageTreeNode;
        }

        protected void setNewStorageTreeNode(StorageNode newStorageTreeNode) {
            this.newStorageTreeNode = newStorageTreeNode;
        }

        public StorageNode getConflictStorageTreeNode() {
            return this.conflictStorageTreeNode;
        }

        protected void setConflictStorageTreeNode(StorageNode conflictStorageTreeNode) {
            this.conflictStorageTreeNode = conflictStorageTreeNode;
        }

        public StorageNode getDestinationStorageTreeNode() {
            return this.destinationStorageTreeNode;
        }

        protected void setDestinationStorageTreeNode(StorageNode destinationStorageTreeNode) {
            this.destinationStorageTreeNode = destinationStorageTreeNode;
        }
    }
}
