package com.surense.tickets.repository;

import com.surense.tickets.entity.Ticket;
import com.surense.tickets.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    List<Ticket> findByStatus(TicketStatus status);
}
