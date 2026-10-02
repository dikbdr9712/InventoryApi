package com.api.inventory.config;

import com.api.inventory.entity.MarketplaceSettings;
import com.api.inventory.entity.Role;
import com.api.inventory.repository.MarketplaceSettingsRepository;
import com.api.inventory.repository.RoleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On start-up, makes sure the marketplace has what it needs, so nobody has to add rows by hand:
 * the SELLER and RIDER roles, and the one settings row (3% commission, free delivery, Nu. 50 per delivery).
 * Existing rows are never changed.
 */
@Component
public class MarketplaceInitializer implements ApplicationRunner {

    private final RoleRepository roles;
    private final MarketplaceSettingsRepository settings;

    public MarketplaceInitializer(RoleRepository roles, MarketplaceSettingsRepository settings) {
        this.roles = roles;
        this.settings = settings;
    }

    @Override
    public void run(ApplicationArguments args) {
        // (the SELLER and RIDER roles are created by AccessControlService with the other built-in roles)
        if (!settings.existsById(MarketplaceSettings.ID)) {
            settings.save(new MarketplaceSettings());
        }
    }
}
