package com.surense.api.tickets;

import com.surense.api.tickets.dto.CreateTicketRequest;
import com.surense.api.tickets.dto.TicketResponse;
import com.surense.api.tickets.dto.UpdateTicketRequest;
import com.surense.infra.persistence.tickets.entity.TicketStatus;
import com.surense.service.tickets.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@Validated
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    public List<TicketResponse> listTickets(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) TicketStatus status) {
        return ticketService.listTickets(customerId, status);
    }

    @GetMapping("/{id}")
    public TicketResponse getTicket(@PathVariable long id) {
        return ticketService.getTicket(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest req) {
        TicketResponse body = ticketService.createTicket(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public TicketResponse updateTicket(@PathVariable long id,
                                       @Valid @RequestBody UpdateTicketRequest req) {
        return ticketService.updateTicket(id, req);
    }
}
