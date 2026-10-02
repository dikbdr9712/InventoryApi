package com.api.inventory.repository;

import com.api.inventory.entity.Customer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findFirstByUserId(Long userId);

    Optional<Customer> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);

    Optional<Customer> findFirstByPhoneOrderByIdAsc(String phone);

    @Query("select c from Customer c where lower(c.name) like lower(concat('%', :q, '%')) or c.phone like concat('%', :q, '%') "
            + "or lower(c.email) like lower(concat('%', :q, '%')) order by c.lastSeenAt desc")
    List<Customer> search(@Param("q") String q, Pageable page);

    List<Customer> findAllByOrderByLastSeenAtDesc(Pageable page);
}
