package com.unulearner.backend.storage.interfaces;

import java.util.Comparator;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.unulearner.backend.storage.models.Entry;
import com.unulearner.backend.storage.models.utility.EntryPath;

public interface StorageInterface {
    public Entry createNewEntry(Entry parent, List<Entry> children, EntryPath nodePath, UUID user, UUID group, String permissions);
    public Optional<Entry> searchEntryByURL(String relativePath);
    public List<Entry> retrieveChildEntries(Entry entry);
    public Entry createRootEntry(EntryPath entryPath);
    public Entry persistEntry(Entry entry);
    public void deleteEntry(Entry entry);
    public Comparator<Entry> getStorageComparator();
    public EntryPath getRootDirectoryPath() throws Exception;
}
