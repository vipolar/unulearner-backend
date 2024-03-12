package com.unulearner.backend.storage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
//import org.springframework.data.jpa.repository.Query;

import com.unulearner.backend.storage.StorageTreeNode;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface StorageRepository extends JpaRepository<StorageTreeNode, UUID>  {
    //StorageNode getByParentIsNull(); // Finds the root dirctory (it is the only one that is allowed to have a NULL parent!!!)

    //List<FilesStorageNode> findAllByParentIdAndConfirmedTrueAndReadableTrueAndPhysicalTrueAndMalignantFalseOrderByIsDirectoryDescNameAsc(@Param("parentId") Long parentId);
    //List<FilesStorageNode> findAllByParentIdAndReadableTrueAndPhysicalTrueAndMalignantFalseOrderByIsDirectoryDescNameAsc(@Param("parentId") Long parentId); // Includes unconfirmed
    //List<FilesStorageNode> findAllByParentIdAndPhysicalTrueAndMalignantFalseOrderByIsDirectoryDescNameAsc(@Param("parentId") Long parentId); // Includes unconfirmed, and unreadable
    //List<FilesStorageNode> findAllByParentIdAndMalignantFalseOrderByIsDirectoryDescNameAsc(@Param("parentId") Long parentId); // Includes unconfirmed, unreadable, and unreachable
    //List<StorageNode> findAllByParentIdAndSafeTrueOrderByIsDirectoryDescNameAsc(@Param("parentId") UUID parentId);
    //List<StorageNode> findAllByParentIdOrderByIsDirectoryDescNameAsc(@Param("parentId") UUID parentId);

    //Optional<StorageNode> findByParentAndName(@Param("parent") StorageNode parent, @Param("name") String name);
    //Optional<StorageNode> findByParentIdAndName(@Param("parentId") UUID parentId, @Param("name") String name);
    //Optional<StorageTreeNode> findByUrl(@Param("url") String url);
    Optional<StorageTreeNode> findByOnDiskURL(@Param("onDiskURL") String onDiskURL);
    List<StorageTreeNode> findAllByParent(@Param("parent") StorageTreeNode parent);
}
