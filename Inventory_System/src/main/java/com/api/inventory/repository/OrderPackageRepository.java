package com.api.inventory.repository;

import com.api.inventory.entity.OrderPackage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderPackageRepository extends JpaRepository<OrderPackage, Long> {

    List<OrderPackage> findByOrderIdOrderByIdAsc(Long orderId);

    List<OrderPackage> findBySellerIdOrderByIdDesc(Long sellerId);

    List<OrderPackage> findByRiderIdOrderByIdDesc(Long riderId);

    List<OrderPackage> findByStatusOrderByIdAsc(String status);

    List<OrderPackage> findByStatusInOrderByIdDesc(Collection<String> statuses);

    List<OrderPackage> findAllByOrderByIdDesc();

    /** Locks the row, so two riders pressing "Accept" at the same moment cannot both get the job. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from OrderPackage p where p.id = :id")
    Optional<OrderPackage> findByIdForUpdate(@Param("id") Long id);
}
