package com.unulearner.backend.storage.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.repository.query.Param;
import com.unulearner.backend.storage.entities.StorageNode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageRepository extends JpaRepository<StorageNode, UUID>  {
    Optional<StorageNode> findByUrl(@Param("url") String url);
    List<StorageNode> findAllByParent(@Param("parent") StorageNode parent);
}
