package com.surense.service.tickets;

import com.surense.api.tickets.dto.CreateTicketRequest;
import com.surense.api.tickets.dto.TicketResponse;
import com.surense.api.tickets.dto.UpdateTicketRequest;
import com.surense.infra.error.exception.BadRequestException;
import com.surense.infra.error.exception.ResourceNotFoundException;
import com.surense.infra.persistence.auth.entity.User;
import com.surense.infra.persistence.auth.entity.UserRole;
import com.surense.infra.persistence.auth.repository.UserRepository;
import com.surense.infra.persistence.customers.entity.Customer;
import com.surense.infra.persistence.customers.repository.CustomerRepository;
import com.surense.infra.persistence.tickets.entity.Ticket;
import com.surense.infra.persistence.tickets.entity.TicketStatus;
import com.surense.infra.persistence.tickets.repository.TicketRepository;
import com.surense.infra.security.SecurityUtils;
import com.surense.infra.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<TicketResponse> listTickets(Long customerIdFilter, TicketStatus statusFilter) {
        UserPrincipal p = SecurityUtils.requireCurrentUser();
        if (p.role() == UserRole.CUSTOMER) {
            return customerRepository.findByLinkedUserId(p.id())
                    .map(cust -> listTicketsForCustomerScope(cust.getId(), customerIdFilter, statusFilter))
                    .orElse(List.of());
        }
        List<Ticket> rows = queryTicketsForStaff(customerIdFilter, statusFilter);
        if (p.role() == UserRole.AGENT) {
            rows = rows.stream()
                    .filter(t -> t.getCustomer().getCreatedByAgent().getId().equals(p.id()))
                    .toList();
        }
        return rows.stream().map(this::toResponse).toList();
    }

    private List<TicketResponse> listTicketsForCustomerScope(long ownCustomerId,
                                                             Long customerIdFilter,
                                                             TicketStatus statusFilter) {
        if (customerIdFilter != null && !customerIdFilter.equals(ownCustomerId)) {
            throw new AccessDeniedException("Cannot query another customer's tickets");
        }
        List<Ticket> rows;
        if (statusFilter != null) {
            rows = ticketRepository.listForCustomerAndStatusNewestFirst(ownCustomerId, statusFilter);
        } else {
            rows = ticketRepository.listForCustomerNewestFirst(ownCustomerId);
        }
        return rows.stream().map(this::toResponse).toList();
    }

    private List<Ticket> queryTicketsForStaff(Long customerIdFilter, TicketStatus statusFilter) {
        if (customerIdFilter != null && statusFilter != null) {
            return ticketRepository.listForCustomerAndStatusNewestFirst(customerIdFilter, statusFilter);
        }
        if (customerIdFilter != null) {
            return ticketRepository.listForCustomerNewestFirst(customerIdFilter);
        }
        if (statusFilter != null) {
            return ticketRepository.listWithStatusNewestFirst(statusFilter);
        }
        return ticketRepository.listAllNewestFirst();
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicket(long id) {
        Ticket t = ticketRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.ticket(id));
        assertCanReadTicket(t);
        return toResponse(t);
    }

    private void assertCanReadTicket(Ticket t) {
        UserPrincipal p = SecurityUtils.requireCurrentUser();
        if (p.role() == UserRole.CUSTOMER) {
            Customer cust = t.getCustomer();
            if (cust.getLinkedUser() == null || !cust.getLinkedUser().getId().equals(p.id())) {
                throw new AccessDeniedException("Ticket not accessible");
            }
            return;
        }
        if (p.role() == UserRole.AGENT
                && !t.getCustomer().getCreatedByAgent().getId().equals(p.id())) {
            throw new AccessDeniedException("Ticket not accessible");
        }
    }

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest req) {
        UserPrincipal p = SecurityUtils.requireCurrentUser();
        if (p.role() != UserRole.ADMIN && p.role() != UserRole.AGENT) {
            throw new AccessDeniedException("Cannot create tickets");
        }
        Customer customer = customerRepository.findById(req.customerId())
                .orElseThrow(() -> ResourceNotFoundException.customer(req.customerId()));
        assertStaffCanAccessCustomerForTicket(customer, p);
        Ticket tick = new Ticket(customer, req.subject(), TicketStatus.OPEN);
        if (req.body() != null && !req.body().isBlank()) {
            tick.setBody(req.body());
        }
        return toResponse(ticketRepository.save(tick));
    }

    private void assertStaffCanAccessCustomerForTicket(Customer customer, UserPrincipal p) {
        if (p.role() == UserRole.ADMIN) {
            return;
        }
        if (!customer.getCreatedByAgent().getId().equals(p.id())) {
            throw new AccessDeniedException("Cannot open tickets for this customer");
        }
    }

    @Transactional
    public TicketResponse updateTicket(long id, UpdateTicketRequest req) {
        UserPrincipal p = SecurityUtils.requireCurrentUser();
        if (p.role() != UserRole.ADMIN && p.role() != UserRole.AGENT) {
            throw new AccessDeniedException("Cannot update tickets");
        }
        if (req.subject() == null && req.body() == null && req.status() == null && req.assignedToAgentId() == null) {
            throw BadRequestException.generic();
        }
        Ticket t = ticketRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.ticket(id));
        assertStaffCanModifyTicket(t, p);
        if (req.subject() != null) {
            t.setSubject(req.subject());
        }
        if (req.body() != null) {
            t.setBody(req.body().isBlank() ? null : req.body());
        }
        if (req.status() != null) {
            t.setStatus(req.status());
        }
        if (req.assignedToAgentId() != null) {
            User assignee = userRepository.findById(req.assignedToAgentId())
                    .orElseThrow(() -> ResourceNotFoundException.user(req.assignedToAgentId()));
            if (assignee.getRole() != UserRole.AGENT && assignee.getRole() != UserRole.ADMIN) {
                throw BadRequestException.generic();
            }
            t.setAssignedToAgent(assignee);
        }
        return toResponse(ticketRepository.save(t));
    }

    private void assertStaffCanModifyTicket(Ticket t, UserPrincipal p) {
        if (p.role() == UserRole.ADMIN) {
            return;
        }
        if (!t.getCustomer().getCreatedByAgent().getId().equals(p.id())) {
            throw new AccessDeniedException("Cannot modify this ticket");
        }
    }

    private TicketResponse toResponse(Ticket t) {
        Long assigneeId = t.getAssignedToAgent() == null ? null : t.getAssignedToAgent().getId();
        return new TicketResponse(
                t.getId(),
                t.getCustomer().getId(),
                t.getSubject(),
                t.getBody(),
                t.getStatus(),
                assigneeId,
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
