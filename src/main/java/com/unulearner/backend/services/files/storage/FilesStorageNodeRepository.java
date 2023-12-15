package com.unulearner.backend.services.files.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
//import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FilesStorageNodeRepository extends JpaRepository<FilesStorageNode, Long>  {
    //@Query("SELECT node FROM FilesStorageNode node WHERE node.parent.id = :parentId ORDER BY node.isDirectory DESC, node.name")
    List<FilesStorageNode> findAllByParentIdOrderByIsDirectoryDescNameAsc(@Param("parentId") Long parentId);

    FilesStorageNode getByUrl(String url);
}
