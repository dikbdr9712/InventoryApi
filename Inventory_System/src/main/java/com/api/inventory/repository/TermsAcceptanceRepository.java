package com.api.inventory.repository;

import com.api.inventory.entity.TermsAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptance, Long> {
    Optional<TermsAcceptance> findFirstByUserEmailIgnoreCaseAndTermsTypeOrderByVersionDescIdDesc(String userEmail, String termsType);
    List<TermsAcceptance> findByUserEmailIgnoreCaseOrderByIdDesc(String userEmail);
    long countByTermsTypeAndVersion(String termsType, Integer version);
}
