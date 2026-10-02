package com.api.inventory.repository;

import com.api.inventory.entity.MarketplaceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceSettingsRepository extends JpaRepository<MarketplaceSettings, Long> {
}
