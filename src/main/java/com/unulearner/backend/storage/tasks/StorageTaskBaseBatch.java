package com.unulearner.backend.storage.tasks;

import java.util.ListIterator;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.unulearner.backend.storage.data.StorageTree;
import com.unulearner.backend.storage.repository.StorageTasksMap;

public class StorageTaskBaseBatch extends StorageTaskBase {

    public StorageTaskBaseBatch(StorageTree storageTree, StorageTasksMap storageTasksMap) {
        super(storageTree, storageTasksMap);
    }

    protected void advanceStorageTask() {
        return;
    }

    protected void skipStorageTaskCurrentAction() {
        return;
    }
    
    public class StorageTaskCurrentAction extends StorageTaskAction {
        @JsonIgnore
        private final StorageTaskCurrentAction parentStorageTaskAction;

        @JsonIgnore
        private final ListIterator<StorageTaskCurrentAction> childStorageTaskActions;

        @JsonIgnore
        private final List<StorageTaskCurrentAction> childStorageTaskActionsHiddenList;

        protected StorageTaskCurrentAction(StorageTaskCurrentAction parentStorageTaskAction) {
            super();

            this.parentStorageTaskAction = parentStorageTaskAction;
            this.childStorageTaskActionsHiddenList =  new ArrayList<>();
            this.childStorageTaskActions = this.childStorageTaskActionsHiddenList.listIterator();
        }

        protected StorageTaskCurrentAction getParentStorageTaskAction() {
            return this.parentStorageTaskAction;
        }

        protected ListIterator<StorageTaskCurrentAction> getChildStorageTaskActions() {
            return this.childStorageTaskActions;
        }
    }
}
