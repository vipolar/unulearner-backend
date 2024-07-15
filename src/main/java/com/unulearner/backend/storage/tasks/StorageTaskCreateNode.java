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
    public StorageTaskCreateNode(StorageTree storageTree, MultipartFile newFile, String newFileDescription, StorageNode destinationStorageNode, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        final StorageCreateNodeTaskAction storageTaskAction = new StorageCreateNodeTaskAction(newFile, newFileDescription, destinationStorageNode);

        storageTaskAction.setActionHeader("Upload '%s' to '%s' directory".formatted(newFile.getOriginalFilename(), destinationStorageNode.getOnDiskFormattedURL()));
        storageTaskAction.setMessage("File upload task has been successfully initialized".formatted());

        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    public StorageTaskCreateNode(StorageTree storageTree, String newDirectoryName, String newDirectoryDescription, StorageNode destinationStorageNode, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);
        
        final StorageCreateNodeTaskAction storageTaskAction = new StorageCreateNodeTaskAction(newDirectoryName, newDirectoryDescription, destinationStorageNode);

        storageTaskAction.setActionHeader("Create '%s' directory in '%s' directory".formatted(newDirectoryName, destinationStorageNode.getOnDiskFormattedURL()));
        storageTaskAction.setMessage("Directory creation task has been successfully initialized".formatted());
    
        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    @Override
    public synchronized void execute(String updatedName, String onExceptionAction, Boolean onExceptionActionIsPersistent) {
        final StorageCreateNodeTaskAction storageTaskAction = (StorageCreateNodeTaskAction) this.getStorageTaskAction();

        if (storageTaskAction.getNewStorageNode().getId() != null) {
            storageTaskAction.setMessage("%s '%s' directory task finished successfully!".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory creation in" : "File upload to", storageTaskAction.getNewStorageNode().getParent().getOnDiskFormattedURL()));
            this.setCurrentState(TaskState.COMPLETED);
            return;
        }

        if (onExceptionAction != null && onExceptionAction.equals("cancel")) {
            storageTaskAction.setMessage("%s '%s' directory task was cancelled...".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory creation in" : "File upload to", storageTaskAction.getNewStorageNode().getParent().getOnDiskFormattedURL()));
            this.setCurrentState(TaskState.CANCELLED);
            return;
        }

        if (onExceptionAction != null) {
            onExceptionActionIsPersistent = onExceptionActionIsPersistent != null ? onExceptionActionIsPersistent : false;
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskAction.getNewStorageNode(), storageTaskAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        storageTaskAction.incrementAttemptCounter();
        while (storageTaskAction.getNewStorageNode().getId() == null) {
            try {
                final String exceptionType = storageTaskAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(exceptionType, storageTaskAction.getNewStorageNode());
                
                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "FileAlreadyExistsException": /* TODO: add option to publish the conflicting node if it isn't published */
                            if (storageTaskAction.getConflictStorageNode() == null) {
                                throw new RuntimeException("%s '%s' could not be %s to directory '%s' due to a supposed conflicting node, existence of which can neither be confirmed nor denied...".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getOnDiskName(), storageTaskAction.getNewStorageNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getOnDiskFormattedURL()));
                            }

                            switch (exceptionAction) {
                                case "keep":
                                    storageTaskAction.getNewStorageNode().setOnDiskName(StorageFileName.findNextAvailableFileName(storageTaskAction.getNewStorageNode().getOnDiskName()));
                                    storageTaskAction.setConflictStorageNode(null);
                                    break;
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskAction.getNewStorageNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getOnDiskName(), storageTaskAction.getNewStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageNode().getOnDiskFormattedURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("keep", "Keep both".formatted(), true));
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to a conflicting node already in place.".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getOnDiskName(), storageTaskAction.getNewStorageNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getOnDiskFormattedURL()));

                                    
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameValidationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskAction.getNewStorageNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getOnDiskName(), storageTaskAction.getNewStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageNode().getOnDiskFormattedURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to a provided file name being incompatible.".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getOnDiskName(), storageTaskAction.getNewStorageNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getOnDiskFormattedURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "FileNameGenerationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskAction.getNewStorageNode().setOnDiskName(StorageFileName.validateFileName(updatedName));
                                    storageTaskAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getOnDiskName(), storageTaskAction.getNewStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageNode().getOnDiskFormattedURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    List<OnExceptionOption> onConflictOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("rename", "Rename manually".formatted(), false));
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "directory" : "file"), true));
                                    }};

                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to a failed name generation attempt.".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getOnDiskName(), storageTaskAction.getNewStorageNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getOnDiskFormattedURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onConflictOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getOnDiskName(), storageTaskAction.getNewStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageNode().getOnDiskFormattedURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    List<OnExceptionOption> onPhysicalOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "directory" : "file"), true));
                                    }};
        
                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to a persistent I/O exception occurring.".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getOnDiskName(), storageTaskAction.getNewStorageNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getOnDiskFormattedURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onPhysicalOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getOnDiskName(), storageTaskAction.getNewStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageNode().getOnDiskFormattedURL()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    return;
                                default:
                                    List<OnExceptionOption> onPhysicalOptions = new ArrayList<>() {{
                                        add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "directory" : "file"), true));
                                    }};
        
                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to an unexpected exception occurring.".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getOnDiskName(), storageTaskAction.getNewStorageNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getOnDiskFormattedURL()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onPhysicalOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    return;
                            }
                    }
                }                

                if (storageTaskAction.getNewStorageNode().getNodePath() == null) {
                    storageTaskAction.setNewStorageNode(this.storageTreeExecute().createStorageNode(storageTaskAction.getNewMultipartFile(), storageTaskAction.getNewStorageNode(), storageTaskAction.getDestinationStorageNode()));
                }

                if (storageTaskAction.getNewStorageNode().getId() == null) {
                    this.storageTreeExecute().publishStorageNode(storageTaskAction.getNewStorageNode());
                }

                storageTaskAction.setMessage("%s '%s' has been %s directory '%s' successfully!".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getOnDiskFormattedURL(), storageTaskAction.getNewStorageNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getOnDiskFormattedURL()));
                storageTaskAction.setExceptionMessage(null);
                storageTaskAction.setExceptionType(null);
            } catch (FileAlreadyExistsException exception) {
                try { /* Here we attempt to find the conflicting node. And if we can't find it, we try to recover it */
                    final Optional<StorageNode> possibleConflict =  storageTaskAction.getNewStorageNode().getParent().getChildren().stream().filter(entry -> entry.getNodePath().getFileName().toString().equals(storageTaskAction.getNewStorageNode().getOnDiskName())).findFirst();
                    if (possibleConflict.isPresent()) {
                        storageTaskAction.setConflictStorageNode(possibleConflict.get());
                    } else {
                        storageTaskAction.setConflictStorageNode(this.storageTreeExecute().recoverStorageNode(storageTaskAction.getNewStorageNode().getOnDiskName(), storageTaskAction.getNewStorageNode().getParent()));
                    }
                } catch (Exception recoveryException) {
                    storageTaskAction.setConflictStorageNode(null);

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

        private StorageNode newStorageNode;
        private StorageNode conflictStorageNode;
        private StorageNode destinationStorageNode;

        protected StorageCreateNodeTaskAction(MultipartFile newFile, String newFileDescription, StorageNode destinationStorageNode) {
            super();

            this.newStorageNode = new StorageNode(destinationStorageNode, null, null, newFileDescription);
            this.newStorageNode.setOnDiskName(newFile.getOriginalFilename());
            this.destinationStorageNode = destinationStorageNode;
            this.newMultipartFile = newFile;
        }

        protected StorageCreateNodeTaskAction(String newDirectoryName, String newDirectoryDescription, StorageNode destinationStorageNode) {
            super();

            this.newStorageNode = new StorageNode(destinationStorageNode, new ArrayList<>(), null, newDirectoryDescription);
            this.destinationStorageNode = destinationStorageNode;
            this.newStorageNode.setOnDiskName(newDirectoryName);
            this.newMultipartFile = null;
        }

        protected MultipartFile getNewMultipartFile() {
            return this.newMultipartFile;
        }

        public StorageNode getNewStorageNode() {
            return this.newStorageNode;
        }

        protected void setNewStorageNode(StorageNode newStorageNode) {
            this.newStorageNode = newStorageNode;
        }

        public StorageNode getConflictStorageNode() {
            return this.conflictStorageNode;
        }

        protected void setConflictStorageNode(StorageNode conflictStorageNode) {
            this.conflictStorageNode = conflictStorageNode;
        }

        public StorageNode getDestinationStorageNode() {
            return this.destinationStorageNode;
        }

        protected void setDestinationStorageNode(StorageNode destinationStorageNode) {
            this.destinationStorageNode = destinationStorageNode;
        }
    }
}
