package com.api.inventory.repository;

import com.api.inventory.entity.SiteText;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteTextRepository extends JpaRepository<SiteText, String> {
}
