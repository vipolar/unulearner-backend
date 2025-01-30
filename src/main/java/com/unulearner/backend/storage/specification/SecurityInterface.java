package com.unulearner.backend.storage.specification;

import java.util.UUID;

import com.unulearner.backend.storage.model.Entry;

public interface SecurityInterface {
    public Boolean userHasRequiredPermissions(Entry targetEntry, boolean read, boolean write, boolean execute);
    public Boolean userBelongsToTheOwningGroupOfTheEntry(Entry targetEntry);
    public Boolean userIsTheOwnerOfTheEntry(Entry targetEntry);
    public Boolean userCanInteractWithTheUser(UUID userUUID);
    public Boolean userBelongsToTheGroup(UUID groupUUID);
    public Boolean userHasRootPrivilages();
}
