package com.unulearner.backend.storage.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import com.unulearner.backend.storage.entities.StorageTreeNode;

public interface StorageRepository extends JpaRepository<StorageTreeNode, UUID>  {
    Optional<StorageTreeNode> findByOnDiskURL(@Param("onDiskURL") String onDiskURL);
    List<StorageTreeNode> findAllByParent(@Param("parent") StorageTreeNode parent);
}
