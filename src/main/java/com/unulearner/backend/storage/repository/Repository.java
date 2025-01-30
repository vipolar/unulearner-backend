package com.unulearner.backend.storage.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.repository.query.Param;

import com.unulearner.backend.storage.model.Entry;

import org.springframework.data.jpa.repository.JpaRepository;

public interface Repository extends JpaRepository<Entry, UUID>  {
    Optional<Entry> findByUrl(@Param("url") String url);
    List<Entry> findAllByParent(@Param("parent") Entry parent);
}
