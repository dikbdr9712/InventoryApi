package com.api.inventory.repository;

import com.api.inventory.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findAllByOrderBySortOrderAscIdAsc();

    List<TeamMember> findByVisibleTrueOrderBySortOrderAscIdAsc();
}
