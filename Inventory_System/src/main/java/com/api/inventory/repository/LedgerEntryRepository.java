package com.api.inventory.repository;

import com.api.inventory.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByPartyTypeAndPartyIdOrderByIdDesc(String partyType, Long partyId);

    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.partyType = :type and e.partyId = :id")
    BigDecimal balanceOf(@Param("type") String partyType, @Param("id") Long partyId);

    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.partyType = :type and e.partyId = :id and e.entryType = :entryType")
    BigDecimal sumOf(@Param("type") String partyType, @Param("id") Long partyId, @Param("entryType") String entryType);

    boolean existsByPackageIdAndPartyTypeAndEntryType(Long packageId, String partyType, String entryType);

    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.packageId = :packageId and e.partyType = :type and e.entryType = :entryType")
    BigDecimal sumOfPackage(@Param("packageId") Long packageId, @Param("type") String partyType, @Param("entryType") String entryType);
}
