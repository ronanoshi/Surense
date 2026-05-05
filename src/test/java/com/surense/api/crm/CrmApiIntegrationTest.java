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
    private User customerUserWithoutProfile;
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
        customerUserWithoutProfile =
                userRepository.save(new User("crm_orphan", passwordEncoder.encode("secret"), UserRole.CUSTOMER));

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
        String body = objectMapper.writeValueAsString(new CreateCustomerRequest("mine@crm.test", "Dup", null, null));
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
    void customer_createsTicket_forLinkedProfile_created() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", bearer(endCustomer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"From customer\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("From customer"))
                .andExpect(jsonPath("$.customerId").value(custLinked.getId().intValue()));
    }

    @Test
    void agent_cannotCreateTicket() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", bearer(agent1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Nope\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void admin_createsTicket_withCustomerId_created() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":" + custMine.getId() + ",\"subject\":\"By admin\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("By admin"))
                .andExpect(jsonPath("$.customerId").value(custMine.getId().intValue()));
    }

    @Test
    void admin_postTicket_missingCustomerId_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Missing customer\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void admin_postTicket_unknownCustomerId_returnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":999999,\"subject\":\"Nobody\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void customer_withoutLinkedProfile_cannotCreateTicket() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", bearer(customerUserWithoutProfile))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"No profile\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void customer_updatesLinkedProfile() throws Exception {
        mockMvc.perform(patch("/api/v1/customers/" + custLinked.getId())
                        .header("Authorization", bearer(endCustomer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Renamed\",\"phoneNumber\":\"555-0100\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed"))
                .andExpect(jsonPath("$.phoneNumber").value("555-0100"));
    }

    @Test
    void customer_cannotPatchAnotherCustomersProfile() throws Exception {
        mockMvc.perform(patch("/api/v1/customers/" + custMine.getId())
                        .header("Authorization", bearer(endCustomer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Hax\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void agent_patchesCustomerTheyCreated() throws Exception {
        mockMvc.perform(patch("/api/v1/customers/" + custMine.getId())
                        .header("Authorization", bearer(agent1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Agent edit\",\"phoneNumber\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Agent edit"));
    }

    @Test
    void agent_cannotPatchPeerAgentsCustomer() throws Exception {
        mockMvc.perform(patch("/api/v1/customers/" + custOther.getId())
                        .header("Authorization", bearer(agent1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nope\",\"phoneNumber\":null}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void admin_canModifyPeerAgentsCustomer_andCreateTicketForThem() throws Exception {
        mockMvc.perform(patch("/api/v1/customers/" + custOther.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Admin cross-scope\",\"phoneNumber\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Admin cross-scope"));
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":" + custOther.getId() + ",\"subject\":\"Admin anywhere\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("Admin anywhere"))
                .andExpect(jsonPath("$.customerId").value(custOther.getId().intValue()));
    }

    @Test
    void admin_patchesCustomerProfile() throws Exception {
        mockMvc.perform(patch("/api/v1/customers/" + custMine.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Admin touched\",\"phoneNumber\":\"111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Admin touched"))
                .andExpect(jsonPath("$.phoneNumber").value("111"));
    }

    @Test
    void agent_linksCustomerLogin_viaPatch_orphanCanCreateTicket() throws Exception {
        mockMvc.perform(patch("/api/v1/customers/" + custMine.getId())
                        .header("Authorization", bearer(agent1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Mine\",\"phoneNumber\":null,\"linkedCustomerUsername\":\"crm_orphan\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", bearer(customerUserWithoutProfile))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"After link\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(custMine.getId().intValue()));
    }

    @Test
    void customerLogin_alreadyLinkedConflict() throws Exception {
        mockMvc.perform(patch("/api/v1/customers/" + custMine.getId())
                        .header("Authorization", bearer(agent1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Mine\",\"phoneNumber\":null,\"linkedCustomerUsername\":\"crm_bob\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_LOGIN_ALREADY_LINKED"));
    }

    @Test
    void customer_cannotSetLinkedCustomerUsernameOnSelfPatch() throws Exception {
        mockMvc.perform(patch("/api/v1/customers/" + custLinked.getId())
                        .header("Authorization", bearer(endCustomer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Linked\",\"phoneNumber\":null,\"linkedCustomerUsername\":\"crm_orphan\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void agent_createsCustomer_withLinkedCustomerUsername_thenLoginCanOpenTickets() throws Exception {
        User linkedLogin = userRepository.save(new User("linked_login_user", passwordEncoder.encode("secret"), UserRole.CUSTOMER));
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(agent1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"linked-provision@crm.test\",\"displayName\":\"Linked Co\",\"phoneNumber\":null,\"linkedCustomerUsername\":\"linked_login_user\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("linked-provision@crm.test"));
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", bearer(linkedLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Provisioned\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("Provisioned"));
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
