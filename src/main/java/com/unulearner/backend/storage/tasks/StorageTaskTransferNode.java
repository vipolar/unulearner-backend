package com.unulearner.backend.storage.tasks;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;

import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.extensions.NodePath;
import com.unulearner.backend.storage.entities.StorageNode;
import com.unulearner.backend.storage.statics.StorageFileName;
import com.unulearner.backend.storage.repository.StorageTasksMap;
import com.unulearner.backend.storage.responses.StorageServiceResponse;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption;
import com.unulearner.backend.storage.services.ExceptionHandler.OnExceptionOption.Parameter;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.DirectoryNotEmptyException;
import com.unulearner.backend.storage.exceptions.NodeNameGenerationException;
import com.unulearner.backend.storage.exceptions.NodeNameValidationException;

public class StorageTaskTransferNode extends StorageTaskBaseBatch {
    private final StorageNode rootDestinationStorageNode;
    private final StorageNode rootTargetStorageNode;

    public StorageTaskTransferNode(StorageTree storageTree, StorageNode targetStorageNode, StorageNode destinationStorageNode, String newName, Boolean persistOriginal, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        final Boolean persistOriginalStorageNode = persistOriginal != null ? persistOriginal : false;
        this.rootDestinationStorageNode = destinationStorageNode;
        this.rootTargetStorageNode = targetStorageNode;

        final StorageTaskTransferNodeCurrentAction storageTaskAction = new StorageTaskTransferNodeCurrentAction(null, targetStorageNode, destinationStorageNode, newName, persistOriginalStorageNode);

        storageTaskAction.setActionHeader("%s %s '%s' to '%s'".formatted(persistOriginalStorageNode == true ? "Copy" : "Move", this.rootTargetStorageNode.isDirectory() ? "directory" : "file", this.rootTargetStorageNode.getUrl(), this.rootDestinationStorageNode.getUrl()));
        storageTaskAction.setMessage("%s transfer task has been successfully initialized".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File"));

        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    public StorageTaskTransferNode(StorageTree storageTree, StorageNode targetStorageNode, String newName, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);

        this.rootDestinationStorageNode = targetStorageNode.getParent();
        this.rootTargetStorageNode = targetStorageNode;

        final StorageTaskTransferNodeCurrentAction storageTaskAction = new StorageTaskTransferNodeCurrentAction(null, targetStorageNode, targetStorageNode.getParent(), newName, false);

        storageTaskAction.setActionHeader("Rename %s '%s' to '%s'".formatted(this.rootTargetStorageNode.isDirectory() ? "directory" : "file", this.rootTargetStorageNode.getName(), newName));
        storageTaskAction.setMessage("%s rename task has been successfully initialized".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File"));

        this.setStorageTaskAction(storageTaskAction);
        this.setCurrentState(TaskState.EXECUTING);
        return;
    }

    @Override
    public synchronized StorageServiceResponse execute(Map<String, Object> taskParameters) {
        this.advanceStorageTask(); /* Will advance task only if the current task is either done or waiting for the children tasks to be done */
        final StorageTaskTransferNodeCurrentAction storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) this.getStorageTaskAction();

        final Boolean cancel = taskParameters != null ? (Boolean) taskParameters.get("cancel") : null;
        final String updatedName = taskParameters != null ? (String) taskParameters.get("updatedName") : null; 
        final String onExceptionAction = taskParameters != null ? (String) taskParameters.get("onExceptionAction") : null;
        final Boolean onExceptionActionIsPersistent = taskParameters != null ? (Boolean) taskParameters.get("setAsDefault") : null;

        if (storageTaskCurrentAction.getParentStorageTaskAction() == null && storageTaskCurrentAction.getNewStorageNode() != null && storageTaskCurrentAction.getNewStorageNode().getId() != null) {
            storageTaskCurrentAction.setMessage("%s transfer task finished successfully!".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File"));
            this.setCurrentState(TaskState.COMPLETED);
            
            return this.getStorageServiceResponse();
        }

        if (cancel != null && cancel == true) {
            storageTaskCurrentAction.setMessage("%s transfer task was cancelled...".formatted(this.rootTargetStorageNode.isDirectory() ? "Directory" : "File"));
            this.setCurrentState(TaskState.CANCELLED);
            
            return this.getStorageServiceResponse();
        }

        if (onExceptionAction != null) {
            this.getTaskExceptionHandler().setOnExceptionAction(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getExceptionType(), onExceptionAction, onExceptionActionIsPersistent);
        }

        Boolean replaceExisting = false;
        storageTaskCurrentAction.incrementAttemptCounter();
        while (storageTaskCurrentAction.getNewStorageNode() == null
            || (storageTaskCurrentAction.getNewStorageNode() != null && storageTaskCurrentAction.getNewStorageNode().getId() == null)
            || (storageTaskCurrentAction.getPersistNode() == false && storageTaskCurrentAction.getChildStorageTaskActions().hasNext() == false && storageTaskCurrentAction.getDeprecatedNodePath() != null && Files.exists(storageTaskCurrentAction.getDeprecatedNodePath().getPath()))) {
            try {
                final String exceptionType = storageTaskCurrentAction.getExceptionType();
                final String exceptionAction = this.getTaskExceptionHandler().getOnExceptionAction(exceptionType, storageTaskCurrentAction.getTargetStorageNode());

                if (exceptionType != null) {
                    switch (exceptionType) {
                        case "FileAlreadyExistsException":
                            if (storageTaskCurrentAction.getConflictStorageNode() == null) {
                                System.out.println("FileAlreadyExistsException (NCN): %s".formatted(storageTaskCurrentAction.getTargetStorageNode().getNodePath().getPath().toString()));
                                return this.getStorageServiceResponse();
                            }
                            switch (exceptionAction) {
                                case "keep":
                                    storageTaskCurrentAction.setOptionalNewName(StorageFileName.findNextAvailableFileName(storageTaskCurrentAction.getOptionalNewName()));
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "merge":
                                    if (storageTaskCurrentAction.getConflictStorageNode() == null) {
                                        throw new FileAlreadyExistsException("Merge option is invalid: node to merge with is invalid!".formatted());
                                    }

                                    if (!storageTaskCurrentAction.getTargetStorageNode().isDirectory() || !storageTaskCurrentAction.getConflictStorageNode().isDirectory()) {
                                        throw new FileAlreadyExistsException("Merge option is invalid: both nodes must be directories!".formatted());
                                    }

                                    storageTaskCurrentAction.setNewStorageNode(storageTaskCurrentAction.getConflictStorageNode());
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing transfer attempt */
                                    storageTaskCurrentAction.setOptionalNewName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "replace":
                                    if (storageTaskCurrentAction.getConflictStorageNode() == null) {
                                        throw new FileAlreadyExistsException("Replace option is invalid: node to replace is invalid!".formatted());
                                    }

                                    if (storageTaskCurrentAction.getTargetStorageNode().isDirectory() || storageTaskCurrentAction.getConflictStorageNode().isDirectory()) {
                                        throw new FileAlreadyExistsException("Replace option is invalid: both nodes must be files!".formatted());
                                    }
                                    
                                    this.storageTreeExecute().deleteStorageNode(storageTaskCurrentAction.getConflictStorageNode());
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    replaceExisting = true;
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copy" : "move", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("keep", "Keep both".formatted(),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    if (storageTaskCurrentAction.getConflictStorageNode() != null && storageTaskCurrentAction.getTargetStorageNode().isDirectory() && storageTaskCurrentAction.getConflictStorageNode().isDirectory()) {
                                        onExceptionOptions.add(new OnExceptionOption("merge", "Merge directories".formatted(),
                                            new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                        ));
                                    }
                                    onExceptionOptions.add(new OnExceptionOption("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    if (storageTaskCurrentAction.getConflictStorageNode() != null && !storageTaskCurrentAction.getTargetStorageNode().isDirectory() && !storageTaskCurrentAction.getConflictStorageNode().isDirectory()) {
                                        onExceptionOptions.add(new OnExceptionOption("replace", "Replace file".formatted(),
                                            new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                        ));
                                    }
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a conflicting node already in place.".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }
                        case "FileNameValidationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskCurrentAction.setOptionalNewName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getOptionalNewName(), storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskCurrentAction.getTargetStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to the provided %s name being invalid.".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getUrl(), storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }
                        case "FileNameGenerationException":
                            switch (exceptionAction) {
                                case "rename":
                                    /* Set a new, manually entered name to be used in the ensuing creation attempt */
                                    storageTaskCurrentAction.setOptionalNewName(StorageFileName.validateFileName(updatedName));
                                    storageTaskCurrentAction.setConflictStorageNode(null);
                                    break;
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getOptionalNewName(), storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "creation in" : "upload to", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("rename", "Rename manually".formatted(),
                                        new Parameter("updatedName", "Updated Name".formatted(), "string"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a failed name generation attempt.".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }
                        case "IOException":
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copy" : "move", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to a persistent I/O exception occurring".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }
                        default:
                            switch (exceptionAction) {
                                case "skip":
                                    storageTaskCurrentAction.setMessage("%s '%s' %s to directory '%s' was skipped...".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copy" : "move", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.setCurrentState(TaskState.EXECUTING);
                                    this.skipStorageTaskCurrentAction();
                                    this.advanceStorageTask();
                                    
                                    return this.getStorageServiceResponse();
                                default:
                                    final ArrayList<OnExceptionOption> onExceptionOptions = new ArrayList<>();
                                    onExceptionOptions.add(new OnExceptionOption("skip", "Skip %s".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "directory" : "file"),
                                        new Parameter("setAsDefault", "Set as Default".formatted(), "boolean")
                                    ));

                                    storageTaskCurrentAction.setMessage("%s '%s' could not be %s to directory '%s' due to an unexpected exception occurring".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                                    this.getTaskExceptionHandler().setExceptionOptions(onExceptionOptions);
                                    this.setCurrentState(TaskState.EXCEPTION);
                                    
                                    return this.getStorageServiceResponse();
                            }   
                    }
                }

                if (storageTaskCurrentAction.getConflictStorageNode() != null) {
                    /* TODO: I have no idea... */
                }

                if (storageTaskCurrentAction.getNewStorageNode() == null || storageTaskCurrentAction.getNewStorageNode().getNodePath() == null) {
                    storageTaskCurrentAction.setNewStorageNode(this.storageTreeExecute().transferStorageNode(storageTaskCurrentAction.getTargetStorageNode(), storageTaskCurrentAction.getDestinationStorageNode(), storageTaskCurrentAction.getOptionalNewName(), storageTaskCurrentAction.getPersistNode(), replaceExisting));
                }

                if (storageTaskCurrentAction.getNewStorageNode().getId() == null) {
                    if (storageTaskCurrentAction.getPersistNode() == false && storageTaskCurrentAction.getDeprecatedNodePath() == null) {
                        storageTaskCurrentAction.setDeprecatedNodePath(storageTaskCurrentAction.getTargetStorageNode().getNodePath());
                        storageTaskCurrentAction.getTargetStorageNode().setParent(storageTaskCurrentAction.getNewStorageNode().getParent());
                        storageTaskCurrentAction.getTargetStorageNode().setNodePath(storageTaskCurrentAction.getNewStorageNode().getNodePath());      
                        
                        storageTaskCurrentAction.setNewStorageNode(storageTaskCurrentAction.getTargetStorageNode());
                    }

                    storageTaskCurrentAction.setNewStorageNode(this.storageTreeExecute().publishStorageNode(storageTaskCurrentAction.getNewStorageNode()));
                }

                /* TODO: remove from the old parent node? */
                if (storageTaskCurrentAction.getPersistNode() == false && storageTaskCurrentAction.getChildStorageTaskActions().hasNext() == false && storageTaskCurrentAction.getDeprecatedNodePath() != null && Files.exists(storageTaskCurrentAction.getDeprecatedNodePath().getPath())) {
                    Files.delete(storageTaskCurrentAction.getDeprecatedNodePath().getPath());

                    if (storageTaskCurrentAction.getChildStorageTaskActions().hasPrevious()) {
                        storageTaskCurrentAction.setMessage("%s '%s' has been cleaned up successfully!".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                        storageTaskCurrentAction.setExceptionMessage(null);
                        storageTaskCurrentAction.setExceptionType(null);
                        this.setCurrentState(TaskState.EXECUTING);

                        return this.getStorageServiceResponse();
                    }
                }

                storageTaskCurrentAction.setMessage("%s '%s' has been %s to directory '%s' successfully!".formatted(storageTaskCurrentAction.getTargetStorageNode().isDirectory() ? "Directory" : "File", storageTaskCurrentAction.getTargetStorageNode().getUrl(), storageTaskCurrentAction.getPersistNode() == true ? "copied" : "moved", storageTaskCurrentAction.getDestinationStorageNode().getUrl()));
                storageTaskCurrentAction.setExceptionMessage(null);
                storageTaskCurrentAction.setExceptionType(null);
                this.setCurrentState(TaskState.EXECUTING);
                
                return this.getStorageServiceResponse();
            } catch (FileAlreadyExistsException exception) {
                try { /* Here we attempt to find the conflicting node and recover it */
                    final String targetName = storageTaskCurrentAction.getOptionalNewName() != null ? storageTaskCurrentAction.getOptionalNewName() : storageTaskCurrentAction.getTargetStorageNode().getName();
                    storageTaskCurrentAction.setConflictStorageNode(this.storageTreeExecute().recoverStorageNode(targetName, storageTaskCurrentAction.getDestinationStorageNode()));
                } catch (Exception recoveryException) {
                    /* Wild territories... conflict without a conflicting node? Gonna be fun!!! */
                    storageTaskCurrentAction.setConflictStorageNode(null);
                    /* TODO: Simply log this stuff for later and move on... */
                }

                storageTaskCurrentAction.setExceptionType("FileAlreadyExistsException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                exception.printStackTrace();
            } catch (NodeNameValidationException exception) {
                storageTaskCurrentAction.setExceptionType("FileNameValidationException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                exception.printStackTrace();
            } catch (NodeNameGenerationException exception) {
                storageTaskCurrentAction.setExceptionType("FileNameGenerationException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                exception.printStackTrace();
            } catch (DirectoryNotEmptyException exception) {
                storageTaskCurrentAction.setExceptionType("DirectoryNotEmptyException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                exception.printStackTrace();
            } catch (IOException exception) {
                storageTaskCurrentAction.setExceptionType("IOException");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                exception.printStackTrace();
            } catch (Exception exception) {
                storageTaskCurrentAction.setExceptionType("Exception");
                storageTaskCurrentAction.setExceptionMessage(exception.getMessage());
                exception.printStackTrace();
            }
        }

        return this.getStorageServiceResponse();
    }

    @Override
    protected void skipStorageTaskCurrentAction() {
        StorageTaskTransferNodeCurrentAction storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) this.getStorageTaskAction();

        if (storageTaskCurrentAction.getParentStorageTaskAction() != null) {
            this.setStorageTaskAction((StorageTaskTransferNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction());
        }
    }

    @Override
    protected void advanceStorageTask() {
        StorageTaskTransferNodeCurrentAction storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) this.getStorageTaskAction();  

        if (storageTaskCurrentAction.getNewStorageNode() == null) {
            return;
        }

        if (storageTaskCurrentAction.getNewStorageNode().getId() == null) {
            return;
        }

        while (!storageTaskCurrentAction.getChildStorageTaskActions().hasNext() && storageTaskCurrentAction.getParentStorageTaskAction() != null) {
            if (storageTaskCurrentAction.getPersistNode() == false && storageTaskCurrentAction.getDeprecatedNodePath() != null && Files.exists(storageTaskCurrentAction.getDeprecatedNodePath().getPath())) {
                break; /* Clean-up required after moving a node */
            }

            storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction();
        }

        if (storageTaskCurrentAction.getChildStorageTaskActions().hasNext()) {
            storageTaskCurrentAction = (StorageTaskTransferNodeCurrentAction) storageTaskCurrentAction.getChildStorageTaskActions().next();
            storageTaskCurrentAction.setDestinationStorageNode(((StorageTaskTransferNodeCurrentAction) storageTaskCurrentAction.getParentStorageTaskAction()).getNewStorageNode());
        }

        this.setStorageTaskAction(storageTaskCurrentAction);
        this.getTaskExceptionHandler().resetOnExceptionAction();
    }

    protected class StorageTaskTransferNodeCurrentAction extends StorageTaskCurrentAction {
        private String optionalNewName;
        private final Boolean persistNode;
        private StorageNode newStorageNode;
        private NodePath deprecatedNodePath;
        private StorageNode targetStorageNode;
        private StorageNode conflictStorageNode;
        private StorageNode destinationStorageNode;

        protected StorageTaskTransferNodeCurrentAction(StorageTaskTransferNodeCurrentAction parentStorageTaskAction, StorageNode targetStorageNode, StorageNode destinationStorageNode, String optionalNewName, Boolean persistOriginal) {
            super(parentStorageTaskAction);

            this.newStorageNode = null;
            this.deprecatedNodePath = null;
            this.persistNode = persistOriginal;
            this.optionalNewName = optionalNewName;
            this.targetStorageNode = targetStorageNode;
            this.destinationStorageNode = destinationStorageNode;

            if (this.targetStorageNode.isDirectory()) {
                for (StorageNode childNode : this.targetStorageNode.getChildren()) {
                    this.getChildStorageTaskActions().add(new StorageTaskTransferNodeCurrentAction(this, childNode, null, null, persistOriginal));
                    this.getChildStorageTaskActions().previous(); /* Required because iterator pushes forward on .add() which is an expected but unwanted behavior */
                }
            }
        }

        protected Boolean getPersistNode() {
            return this.persistNode;
        }

        protected String getOptionalNewName() {
            return this.optionalNewName;
        }

        protected void setOptionalNewName(String optionalNewName) {
            this.optionalNewName = optionalNewName;
        }

        protected StorageNode getNewStorageNode() {
            return this.newStorageNode;
        }

        protected void setNewStorageNode(StorageNode newStorageNode) {
            this.newStorageNode = newStorageNode;
        }

        protected NodePath getDeprecatedNodePath() {
            return this.deprecatedNodePath;
        }

        protected void setDeprecatedNodePath(NodePath deprecatedNodePath) {
            this.deprecatedNodePath = deprecatedNodePath;
        }

        protected StorageNode getTargetStorageNode() {
            return this.targetStorageNode;
        }

        protected void setTargetStorageNode(StorageNode targetStorageNode) {
            this.targetStorageNode = targetStorageNode;
        }

        protected StorageNode getConflictStorageNode() {
            return this.conflictStorageNode;
        }

        protected void setConflictStorageNode(StorageNode conflictStorageNode) {
            this.conflictStorageNode = conflictStorageNode;
        }

        protected StorageNode getDestinationStorageNode() {
            return this.destinationStorageNode;
        }

        protected void setDestinationStorageNode(StorageNode destinationStorageNode) {
            this.destinationStorageNode = destinationStorageNode;
        }
    }
}
