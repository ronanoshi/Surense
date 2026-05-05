package com.surense.infra.persistence.customers.repository;

import com.surense.infra.persistence.customers.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Customer lookups. {@link #findByCreatedByAgentIdOrderByCreatedAtDesc} lists rows created by an
 * agent, newest first (same sort idea as ticket list methods — {@code OrderBy…} is only sort).
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Customer> findByCreatedByAgentIdOrderByCreatedAtDesc(Long agentId);

    Optional<Customer> findByLinkedUserId(Long userId);
}
