package com.surense.api.crm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surense.api.customers.dto.CreateCustomerRequest;
import com.surense.infra.persistence.auth.entity.User;
import com.surense.infra.persistence.auth.entity.UserRole;
import com.surense.infra.persistence.auth.repository.RefreshTokenRepository;
import com.surense.infra.persistence.auth.repository.UserRepository;
import com.surense.infra.persistence.customers.entity.Customer;
import com.surense.infra.persistence.customers.repository.CustomerRepository;
import com.surense.infra.persistence.tickets.entity.Ticket;
import com.surense.infra.persistence.tickets.entity.TicketStatus;
import com.surense.infra.persistence.tickets.repository.TicketRepository;
import com.surense.infra.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-aware customers + tickets API — merged integration coverage for Step 7.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CrmApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtTokenService jwtTokenService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    TicketRepository ticketRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private User admin;
    private User agent1;
    private User agent2;
    private User endCustomer;
    private Customer custMine;
    private Customer custOther;
    private Customer custLinked;
    private Ticket ticketOnMine;
    private Ticket ticketOnLinked;

    @BeforeEach
    void seed() {
        ticketRepository.deleteAll();
        customerRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(new User("crm_admin", passwordEncoder.encode("secret"), UserRole.ADMIN));
        agent1 = userRepository.save(new User("crm_agent1", passwordEncoder.encode("secret"), UserRole.AGENT));
        agent2 = userRepository.save(new User("crm_agent2", passwordEncoder.encode("secret"), UserRole.AGENT));
        endCustomer = userRepository.save(new User("crm_bob", passwordEncoder.encode("secret"), UserRole.CUSTOMER));

        custMine = customerRepository.save(new Customer("mine@crm.test", "Mine", agent1));
        custOther = customerRepository.save(new Customer("other@crm.test", "Other", agent2));
        custLinked = new Customer("linked@crm.test", "Linked", agent1);
        custLinked.setLinkedUser(endCustomer);
        custLinked = customerRepository.save(custLinked);

        ticketOnMine = ticketRepository.save(new Ticket(custMine, "T mine", TicketStatus.OPEN));
        ticketRepository.save(new Ticket(custOther, "T other", TicketStatus.OPEN));
        ticketOnLinked = ticketRepository.save(new Ticket(custLinked, "T linked", TicketStatus.IN_PROGRESS));
    }

    private String bearer(User u) {
        return "Bearer " + jwtTokenService.createAccessToken(u.getId(), u.getUsername(), u.getRole());
    }

    @Test
    void admin_listsAllCustomers() throws Exception {
        mockMvc.perform(get("/api/v1/customers").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void agent_listsOnlyCustomersTheyCreated() throws Exception {
        mockMvc.perform(get("/api/v1/customers").header("Authorization", bearer(agent1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void agent_cannotReadAnotherAgentsCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/customers/" + custOther.getId()).header("Authorization", bearer(agent1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void customer_listsOnlyLinkedCustomerRow() throws Exception {
        mockMvc.perform(get("/api/v1/customers").header("Authorization", bearer(endCustomer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("linked@crm.test"));
    }

    @Test
    void customer_cannotReadUnlinkedCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/customers/" + custMine.getId()).header("Authorization", bearer(endCustomer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void customer_canReadLinkedCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/customers/" + custLinked.getId()).header("Authorization", bearer(endCustomer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("linked@crm.test"));
    }

    @Test
    void createCustomer_duplicateEmail_conflict() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateCustomerRequest("mine@crm.test", "Dup", null));
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(agent1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void agent_createsCustomer_returnsCreated() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(agent1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@crm.test\",\"displayName\":\"New Co\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@crm.test"))
                .andExpect(jsonPath("$.createdByAgentId").value(agent1.getId().intValue()));
    }

    @Test
    void agent_listsTickets_onlyForCustomersTheyCreated() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").header("Authorization", bearer(agent1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void admin_listsAllTickets() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void customer_listsTickets_forLinkedCustomerOnly() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").header("Authorization", bearer(endCustomer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].subject").value("T linked"));
    }

    @Test
    void customer_cannotSpoofAnotherCustomerIdQuery() throws Exception {
        mockMvc.perform(get("/api/v1/tickets")
                        .param("customerId", custMine.getId().toString())
                        .header("Authorization", bearer(endCustomer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void agent_createsTicket_forOwnCustomer_created() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", bearer(agent1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":" + custMine.getId() + ",\"subject\":\"Fresh\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("Fresh"))
                .andExpect(jsonPath("$.customerId").value(custMine.getId().intValue()));
    }

    @Test
    void agent_cannotCreateTicket_forPeerCustomer() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", bearer(agent1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":" + custOther.getId() + ",\"subject\":\"Nope\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchTicket_emptyBody_badRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/" + ticketOnMine.getId())
                        .header("Authorization", bearer(agent1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void patchTicket_assignsToAgent() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/" + ticketOnMine.getId())
                        .header("Authorization", bearer(agent1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedToAgentId\":" + agent2.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToAgentId").value(agent2.getId().intValue()));
    }
}
