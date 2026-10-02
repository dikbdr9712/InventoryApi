package com.api.inventory.repository;

import com.api.inventory.entity.PosShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PosShiftRepository extends JpaRepository<PosShift, Long> {

    Optional<PosShift> findFirstByCashierEmailAndStatus(String cashierEmail, String status);

    List<PosShift> findTop100ByCashierEmailOrderByIdDesc(String cashierEmail);

    List<PosShift> findTop200ByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PosShift s where s.id = :id")
    Optional<PosShift> findByIdForUpdate(@Param("id") Long id);
}
