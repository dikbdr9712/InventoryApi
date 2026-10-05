package com.api.inventory.repository;

import com.api.inventory.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByAreaAndName(String area, String name);

    boolean existsByAreaAndName(String area, String name);
}
