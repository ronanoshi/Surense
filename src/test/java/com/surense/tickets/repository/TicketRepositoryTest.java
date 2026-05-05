package com.surense.tickets.repository;

import com.surense.auth.entity.User;
import com.surense.auth.entity.UserRole;
import com.surense.auth.repository.UserRepository;
import com.surense.customers.entity.Customer;
import com.surense.customers.repository.CustomerRepository;
import com.surense.tickets.entity.Ticket;
import com.surense.tickets.entity.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TicketRepositoryTest {

    @Autowired
    private TicketRepository tickets;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private UserRepository users;

    @Test
    void persistsAndFindsByCustomerIdOrder() {
        User agent = users.save(new User("agent-ticket-test", "hash", UserRole.AGENT));
        Customer customer = customers.save(new Customer("ticket-owner@example.com", "Owner", agent));
        Ticket t = new Ticket(customer, "Printer on fire", TicketStatus.OPEN);
        tickets.save(t);

        assertThat(tickets.findByCustomer_IdOrderByCreatedAtDesc(customer.getId()))
                .singleElement()
                .satisfies(x -> {
                    assertThat(x.getSubject()).isEqualTo("Printer on fire");
                    assertThat(x.getStatus()).isEqualTo(TicketStatus.OPEN);
                    assertThat(x.getAssignedToAgent()).isNull();
                });
    }

    @Test
    void findByStatus() {
        User agent = users.save(new User("agent-status", "hash", UserRole.AGENT));
        Customer c = customers.save(new Customer("status@example.com", "S", agent));
        tickets.save(new Ticket(c, "A", TicketStatus.OPEN));
        tickets.save(new Ticket(c, "B", TicketStatus.CLOSED));

        assertThat(tickets.findByStatus(TicketStatus.OPEN)).hasSize(1);
        assertThat(tickets.findByStatus(TicketStatus.CLOSED)).hasSize(1);
    }
}
