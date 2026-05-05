package com.surense.infra.persistence.customers.repository;

import com.surense.infra.persistence.auth.entity.User;
import com.surense.infra.persistence.auth.entity.UserRole;
import com.surense.infra.persistence.auth.repository.UserRepository;
import com.surense.infra.persistence.customers.entity.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private UserRepository users;

    @Test
    void persistsAndFindsByEmail() {
        User agent = users.save(new User("agent-customer-test", "hash", UserRole.AGENT));
        Customer c = new Customer("crm@example.com", "CRM Customer", agent);
        customers.save(c);

        assertThat(customers.findByEmail("crm@example.com"))
                .isPresent()
                .get()
                .satisfies(x -> {
                    assertThat(x.getDisplayName()).isEqualTo("CRM Customer");
                    assertThat(x.getCreatedByAgent().getId()).isEqualTo(agent.getId());
                    assertThat(x.getLinkedUser()).isNull();
                });
    }

    @Test
    void existsByEmail() {
        User agent = users.save(new User("agent-exists", "hash", UserRole.AGENT));
        customers.save(new Customer("exists@example.com", "X", agent));
        assertThat(customers.existsByEmail("exists@example.com")).isTrue();
        assertThat(customers.existsByEmail("missing@example.com")).isFalse();
    }
}
