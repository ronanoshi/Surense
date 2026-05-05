package com.surense.service.customers;

import com.surense.api.customers.dto.CreateCustomerRequest;
import com.surense.api.customers.dto.CustomerResponse;
import com.surense.api.customers.dto.UpdateCustomerRequest;
import com.surense.infra.error.ErrorCode;
import com.surense.infra.error.exception.BadRequestException;
import com.surense.infra.error.exception.ConflictException;
import com.surense.infra.error.exception.ResourceNotFoundException;
import com.surense.infra.persistence.auth.entity.User;
import com.surense.infra.persistence.auth.entity.UserRole;
import com.surense.infra.persistence.auth.repository.UserRepository;
import com.surense.infra.persistence.customers.entity.Customer;
import com.surense.infra.persistence.customers.repository.CustomerRepository;
import com.surense.infra.security.SecurityUtils;
import com.surense.infra.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CustomerResponse> listCustomers() {
        UserPrincipal p = SecurityUtils.requireCurrentUser();
        return switch (p.role()) {
            case ADMIN -> customerRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                    .map(this::toResponse)
                    .toList();
            case AGENT -> customerRepository.findByCreatedByAgentIdOrderByCreatedAtDesc(p.id()).stream()
                    .map(this::toResponse)
                    .toList();
            case CUSTOMER -> customerRepository.findByLinkedUserId(p.id()).stream()
                    .map(this::toResponse)
                    .toList();
        };
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(long id) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.customer(id));
        assertCanReadCustomer(c);
        return toResponse(c);
    }

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest req) {
        UserPrincipal p = SecurityUtils.requireCurrentUser();
        if (p.role() != UserRole.ADMIN && p.role() != UserRole.AGENT) {
            throw new AccessDeniedException("Customers may not create customer records");
        }
        if (customerRepository.existsByEmail(req.email())) {
            throw ConflictException.emailTaken();
        }
        User agent = userRepository.getReferenceById(p.id());
        Customer c = new Customer(req.email(), req.displayName(), agent);
        if (req.phoneNumber() != null && !req.phoneNumber().isBlank()) {
            c.setPhoneNumber(req.phoneNumber());
        }
        if (req.linkedCustomerUsername() != null && !req.linkedCustomerUsername().isBlank()) {
            linkCustomerLogin(c, req.linkedCustomerUsername().trim());
        }
        return toResponse(customerRepository.save(c));
    }

    @Transactional
    public CustomerResponse updateCustomer(long id, UpdateCustomerRequest req) {
        UserPrincipal p = SecurityUtils.requireCurrentUser();
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.customer(id));
        if (p.role() == UserRole.ADMIN) {
            applyCustomerPatch(c, req, p);
            return toResponse(customerRepository.save(c));
        }
        if (p.role() == UserRole.AGENT) {
            if (!c.getCreatedByAgent().getId().equals(p.id())) {
                throw new AccessDeniedException("Not allowed to modify this customer");
            }
            applyCustomerPatch(c, req, p);
            return toResponse(customerRepository.save(c));
        }
        if (p.role() == UserRole.CUSTOMER) {
            if (c.getLinkedUser() == null || !c.getLinkedUser().getId().equals(p.id())) {
                throw new AccessDeniedException("Customer profile not accessible");
            }
            applyCustomerPatch(c, req, p);
            return toResponse(customerRepository.save(c));
        }
        throw new AccessDeniedException("Not allowed to update customer records");
    }

    /**
     * Links {@code c} to an existing {@link UserRole#CUSTOMER} login ({@code users.username}).
     * Idempotent if {@code c} is already linked to the same user.
     */
    private void linkCustomerLogin(Customer c, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException(ErrorCode.CUSTOMER_LOGIN_NOT_FOUND));
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new BadRequestException(ErrorCode.CUSTOMER_LOGIN_WRONG_ROLE);
        }
        customerRepository.findByLinkedUserId(user.getId()).ifPresent(other -> {
            if (!other.getId().equals(c.getId())) {
                throw ConflictException.customerLoginAlreadyLinked();
            }
        });
        c.setLinkedUser(user);
    }

    private void applyCustomerPatch(Customer c, UpdateCustomerRequest req, UserPrincipal actor) {
        c.setDisplayName(req.displayName());
        if (req.phoneNumber() != null) {
            c.setPhoneNumber(req.phoneNumber().isBlank() ? null : req.phoneNumber());
        }
        if (req.linkedCustomerUsername() != null && !req.linkedCustomerUsername().isBlank()) {
            if (actor.role() == UserRole.CUSTOMER) {
                throw new AccessDeniedException("Cannot change linked customer login on self-service update");
            }
            linkCustomerLogin(c, req.linkedCustomerUsername().trim());
        }
    }

    private void assertCanReadCustomer(Customer c) {
        UserPrincipal p = SecurityUtils.requireCurrentUser();
        if (p.role() == UserRole.CUSTOMER) {
            if (c.getLinkedUser() == null || !c.getLinkedUser().getId().equals(p.id())) {
                throw new AccessDeniedException("Customer profile not accessible");
            }
            return;
        }
        if (p.role() == UserRole.AGENT && !c.getCreatedByAgent().getId().equals(p.id())) {
            throw new AccessDeniedException("Customer record not accessible");
        }
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(
                c.getId(),
                c.getEmail(),
                c.getDisplayName(),
                c.getPhoneNumber(),
                c.getCreatedByAgent().getId(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
