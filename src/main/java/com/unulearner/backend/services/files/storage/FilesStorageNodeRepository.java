package com.unulearner.backend.services.files.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
//import org.springframework.data.jpa.repository.Query;

import java.util.UUID;
import java.util.List;

public interface FilesStorageNodeRepository extends JpaRepository<FilesStorageNode, UUID>  {
    FilesStorageNode getByParentIsNull(); // Finds the root dirctory (it is the only one that is allowed to have a NULL parent!!!)

    //List<FilesStorageNode> findAllByParentIdAndConfirmedTrueAndReadableTrueAndPhysicalTrueAndMalignantFalseOrderByIsDirectoryDescNameAsc(@Param("parentId") Long parentId);
    //List<FilesStorageNode> findAllByParentIdAndReadableTrueAndPhysicalTrueAndMalignantFalseOrderByIsDirectoryDescNameAsc(@Param("parentId") Long parentId); // Includes unconfirmed
    //List<FilesStorageNode> findAllByParentIdAndPhysicalTrueAndMalignantFalseOrderByIsDirectoryDescNameAsc(@Param("parentId") Long parentId); // Includes unconfirmed, and unreadable
    //List<FilesStorageNode> findAllByParentIdAndMalignantFalseOrderByIsDirectoryDescNameAsc(@Param("parentId") Long parentId); // Includes unconfirmed, unreadable, and unreachable
    List<FilesStorageNode> findAllByParentIdAndSafeTrueOrderByIsDirectoryDescNameAsc(@Param("parentId") UUID parentId);
    List<FilesStorageNode> findAllByParentIdOrderByIsDirectoryDescNameAsc(@Param("parentId") UUID parentId);
}
