package com.api.inventory.repository;

import com.api.inventory.entity.PartnerDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartnerDocumentRepository extends JpaRepository<PartnerDocument, Long> {
    List<PartnerDocument> findByPartnerTypeAndPartnerIdAndCurrentTrueOrderByIdDesc(String partnerType, Long partnerId);
    List<PartnerDocument> findByPartnerTypeAndPartnerIdAndKindAndCurrentTrue(String partnerType, Long partnerId, String kind);
}
