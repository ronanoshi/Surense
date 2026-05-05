package com.surense.api.dev;

import com.surense.infra.error.exception.BadRequestException;
import com.surense.infra.error.exception.ConflictException;
import com.surense.infra.error.exception.NotImplementedException;
import com.surense.infra.error.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * <strong>TEMPORARY — REMOVE IN STEP 7.</strong>
 *
 * <p>Dev-only controller used to exercise every branch of the global error
 * pipeline before any real business endpoints exist. Each {@code ?type=}
 * value triggers a different exception path so the resulting
 * {@code ErrorResponse} can be inspected end-to-end.
 *
 * <p>Activated only when {@code surense.dev.boom-endpoint.enabled=true}, which is
 * set in {@code application-dev.yml}. In test and prod profiles the property
 * is absent and the controller is never instantiated.
 *
 * <p>Lives under {@code /__test__/boom} — the deliberately ugly path makes it
 * obvious this is not production code.
 */
@RestController
@RequestMapping("/__test__/boom")
@ConditionalOnProperty(name = "surense.dev.boom-endpoint.enabled", havingValue = "true")
public class BoomController {

    @GetMapping
    public Map<String, Object> boom(@RequestParam(name = "type", defaultValue = "ok") String type) {
        switch (type.toLowerCase()) {
            case "ok":
                return Map.of("ok", true, "type", "ok");
            case "notfound":
                throw ResourceNotFoundException.user(42);
            case "ticketnotfound":
                throw ResourceNotFoundException.ticket(7);
            case "conflict":
                throw ConflictException.usernameTaken();
            case "badrequest":
                throw BadRequestException.generic();
            case "notimplemented":
                throw new NotImplementedException();
            case "forbidden":
                throw new AccessDeniedException("denied");
            case "unauthenticated":
                throw new BadCredentialsException("bad creds");
            case "unhandled":
                // Deliberately includes "PII-like" words so chunk 5's masker has
                // something to redact, and so the sanitization in the handler
                // is visible: this text MUST NOT reach the client.
                throw new IllegalStateException(
                        "internal error: connection lost for password=secret123 email=victim@example.com");
            default:
                return Map.of("ok", true, "type", type, "note", "unknown type, returning ok");
        }
    }

    @PostMapping(value = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> validate(@RequestBody @Valid SampleBoomRequest req) {
        return Map.of("ok", true, "received", req);
    }

    /** Toy request shape used to demonstrate validation failures. */
    public record SampleBoomRequest(
            @NotBlank String name,
            @Email String email) {}
}
