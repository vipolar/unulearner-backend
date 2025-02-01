package com.unulearner.backend.storage.repositories;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.repository.query.Param;

import com.unulearner.backend.storage.models.Entry;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageRepository extends JpaRepository<Entry, UUID>  {
    Optional<Entry> findByUrl(@Param("url") String url);
    List<Entry> findAllByParent(@Param("parent") Entry parent);
}
