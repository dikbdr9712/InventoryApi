package com.api.inventory.repository;

import com.api.inventory.entity.LegalTerms;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LegalTermsRepository extends JpaRepository<LegalTerms, Long> {
    Optional<LegalTerms> findFirstByTermsTypeOrderByVersionDesc(String termsType);
    Optional<LegalTerms> findByTermsTypeAndVersion(String termsType, Integer version);
    List<LegalTerms> findByTermsTypeOrderByVersionDesc(String termsType);
}
