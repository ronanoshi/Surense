package com.surense.infra.persistence.tickets.repository;

import com.surense.infra.persistence.tickets.entity.Ticket;
import com.surense.infra.persistence.tickets.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Ticket queries. List methods return rows <strong>newest first</strong> (by {@code createdAt}).
 *
 * <p>Spring Data derived query names pack two ideas into one identifier: <em>where</em> clauses
 * ({@code findByStatus}, {@code findByCustomerId}, …) and an <em>order</em> clause
 * ({@code OrderByCreatedAtDesc}). The {@code OrderBy…} part is <strong>not</strong> another
 * filter on the same property; it only defines sort order.
 */
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("SELECT t FROM Ticket t WHERE t.customer.id = :customerId ORDER BY t.createdAt DESC")
    List<Ticket> listForCustomerNewestFirst(@Param("customerId") Long customerId);

    @Query("SELECT t FROM Ticket t WHERE t.status = :status ORDER BY t.createdAt DESC")
    List<Ticket> listWithStatusNewestFirst(@Param("status") TicketStatus status);

    @Query(
            "SELECT t FROM Ticket t WHERE t.customer.id = :customerId AND t.status = :status "
                    + "ORDER BY t.createdAt DESC")
    List<Ticket> listForCustomerAndStatusNewestFirst(
            @Param("customerId") Long customerId, @Param("status") TicketStatus status);

    @Query("SELECT t FROM Ticket t ORDER BY t.createdAt DESC")
    List<Ticket> listAllNewestFirst();
}
