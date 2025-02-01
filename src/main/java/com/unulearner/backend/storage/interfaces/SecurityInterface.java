package com.unulearner.backend.storage.interfaces;

import java.util.UUID;

import com.unulearner.backend.storage.models.Entry;

public interface SecurityInterface {
    public Boolean userHasRequiredPermissions(Entry targetEntry, boolean read, boolean write, boolean execute);
    public Boolean userBelongsToTheOwningGroupOfTheEntry(Entry targetEntry);
    public Boolean userIsTheOwnerOfTheEntry(Entry targetEntry);
    public Boolean userCanInteractWithTheUser(UUID userUUID);
    public Boolean userBelongsToTheGroup(UUID groupUUID);
    public Boolean userHasRootPrivilages();
}
