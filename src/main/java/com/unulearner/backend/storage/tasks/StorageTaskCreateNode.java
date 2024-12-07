package com.unulearner.backend.storage.tasks;

import java.util.ArrayList;
import java.io.IOException;
import java.util.Map;
import java.io.File;

import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.statics.StorageFileName;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.responses.StorageServiceResponse;

import java.nio.file.FileAlreadyExistsException;
import com.unulearner.backend.storage.exceptions.NodeNameGenerationException;
import com.unulearner.backend.storage.exceptions.NodeNameValidationException;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption.Parameter;

public class StorageTaskCreateNode extends StorageTaskBase {
    public StorageTaskCreateNode(StorageTree storageTree, File newFile, StorageNode newStorageNode, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);
        final StorageTaskCreateNodeAction storageTaskAction = new StorageTaskCreateNodeAction(newFile, newStorageNode);

        storageTaskAction.setActionHeader("Upload '%s' to '%s' directory".formatted(newStorageNode.getName(), newStorageNode.getParent().getUrl()));
        storageTaskAction.setMessage("File upload task has been successfully initialized".formatted());

        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    public StorageTaskCreateNode(StorageTree storageTree, StorageNode newStorageNode, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);
        
        final StorageTaskCreateNodeAction storageTaskAction = new StorageTaskCreateNodeAction(newStorageNode);

        storageTaskAction.setActionHeader("Create '%s' directory in '%s' directory".formatted(newStorageNode.getName(), newStorageNode.getParent().getUrl()));
        storageTaskAction.setMessage("Directory creation task has been successfully initialized".formatted());
    
        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    @Override
    public synchronized StorageServiceResponse execute(Map<String, Object> taskParameters) {
        final StorageTaskCreateNodeAction storageTaskAction = (StorageTaskCreateNodeAction) this.getStorageTaskAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String updatedName = taskParameters != null ? (String) taskParameters.get("updatedName") : null; 
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskAction.getNewStorageNode().getId() != null) {
            storageTaskAction.setMessage("%s '%s' directory task finished successfully!".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory creation in" : "File upload to", storageTaskAction.getNewStorageNode().getParent().getUrl()));
            this.setCurrentState(TaskState.COMPLETED);
            
            return this.getStorageServiceResponse();
        }

        if (cancel != null && cancel == true) {
            storageTaskAction.setMessage("%s '%s' directory task was cancelled...".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory creation in" : "File upload to", storageTaskAction.getNewStorageNode().getParent().getUrl()));
            this.setCurrentState(TaskState.CANCELLED);
            
            return this.getStorageServiceResponse();
        }

        if (onExceptionAction != null) {
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskAction.getNewStorageNode(), storageTaskAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        storageTaskAction.incrementAttemptCounter();
        while (storageTaskAction.getNewStorageNode().getId() == null) {
            try {
                final String exceptionType = storageTaskAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(exceptionType, storageTaskAction.getNewStorageNode());
                
                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "FileAlreadyExistsException":
                            switch (exceptionAction) {
                                case "keep":
                                    storageTaskAction.getNewStorageNode().setName(StorageFileName.findNextAvailableFileName(storageTaskAction.getNewStorageNode().getName()));
                                    storageTaskAction.setConflictStorageNode(null);
                                    break;
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskAction.getNewStorageNode().setName(StorageFileName.validateFileName(updatedName));
                                    storageTaskAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("keep", "Keep both".formatted(),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new OnExceptionOption("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to a conflicting node already in place.".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }
                        case "FileNameValidationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskAction.getNewStorageNode().setName(StorageFileName.validateFileName(updatedName));
                                    storageTaskAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to the provided %s name being invalid.".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getUrl(), storageTaskAction.getNewStorageNode().isDirectory() ? "directory" : "file"));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }
                        case "FileNameGenerationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskAction.getNewStorageNode().setName(StorageFileName.validateFileName(updatedName));
                                    storageTaskAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to a failed name generation attempt.".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to a persistent I/O exception occurring.".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskAction.getNewStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
        
                                    storageTaskAction.setMessage("%s '%s' could not be %s directory '%s' due to an unexpected exception occurring.".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }
                    }
                }                

                if (storageTaskAction.getNewStorageNode().getNodePath() == null) {
                    storageTaskAction.setNewStorageNode(this.storageTreeExecute().createStorageNode(storageTaskAction.getNewFile(), storageTaskAction.getNewStorageNode(), storageTaskAction.getNewStorageNode().getParent()));
                }

                if (storageTaskAction.getNewStorageNode().getId() == null) {
                    this.storageTreeExecute().publishStorageNode(storageTaskAction.getNewStorageNode());
                }

                storageTaskAction.setMessage("%s '%s' has been %s directory '%s' successfully!".formatted(storageTaskAction.getNewStorageNode().isDirectory() ? "Directory" : "File", storageTaskAction.getNewStorageNode().getUrl(), storageTaskAction.getNewStorageNode().isDirectory() ? "created in" : "uploaded to", storageTaskAction.getNewStorageNode().getParent().getUrl()));
                storageTaskAction.setExceptionMessage(null);
                storageTaskAction.setExceptionType(null);

                return this.getStorageServiceResponse();
            } catch (FileAlreadyExistsException exception) {
                try { /* Here we attempt to find the conflicting node and recover it */
                    storageTaskAction.setConflictStorageNode(this.storageTreeExecute().recoverStorageNode(storageTaskAction.getNewStorageNode().getName(), storageTaskAction.getNewStorageNode().getParent()));
                } catch (Exception recoveryException) {
                    /* Wild territories... conflict without a conflicting node? Gonna be fun!!! */
                    storageTaskAction.setConflictStorageNode(null);
                    /* TODO: Simply log this stuff for later and move on... */
                }

                storageTaskAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (NodeNameValidationException exception) {
                storageTaskAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (NodeNameGenerationException exception) {
                storageTaskAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (IOException exception) {
                storageTaskAction.setExceptionType(exception.getClass().getSimpleName());
                storageTaskAction.setExceptionMessage(exception.getMessage());
            } catch (Exception exception) {
                storageTaskAction.setExceptionType("RuntimeException");
                storageTaskAction.setExceptionMessage(exception.getMessage());
            }
        }

        return this.getStorageServiceResponse();
    }

    protected class StorageTaskCreateNodeAction extends StorageTaskAction {
        private final File newFile;

        private StorageNode newStorageNode;
        private StorageNode conflictStorageNode;

        protected StorageTaskCreateNodeAction(File newFile, StorageNode newStorageNode) {
            super();

            newStorageNode.setChildren(null);
            this.newStorageNode = newStorageNode;
            this.newFile = newFile;
        }

        protected StorageTaskCreateNodeAction(StorageNode newStorageNode) {
            super();

            newStorageNode.setChildren(new ArrayList<>());
            this.newStorageNode = newStorageNode;
            this.newFile = null;
        }

        protected File getNewFile() {
            return this.newFile;
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
    }
}
