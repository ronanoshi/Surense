package com.surense.infra.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskerTest {

    // ------ Rule 1: JSON-style sensitive keys ------

    @Test
    void masksJsonStyleUsername() {
        String result = LogMasker.mask("login: {\"username\":\"admin\"}");
        assertThat(result).isEqualTo("login: {\"username\":\"***\"}");
    }

    @Test
    void masksJsonStylePassword() {
        String result = LogMasker.mask("creds: {\"password\":\"secret123\"}");
        assertThat(result).isEqualTo("creds: {\"password\":\"***\"}");
    }

    @Test
    void masksJsonStyleEmail() {
        String result = LogMasker.mask("user data: {\"email\":\"john@example.com\",\"name\":\"John\"}");
        assertThat(result).contains("\"email\":\"***\"");
        assertThat(result).doesNotContain("john@example.com");
        assertThat(result).contains("\"name\":\"John\"");
    }

    @Test
    void masksJsonStylePhoneNumber() {
        String result = LogMasker.mask("{\"phoneNumber\":\"+972-50-1234567\"}");
        assertThat(result).isEqualTo("{\"phoneNumber\":\"***\"}");
    }

    @Test
    void masksJsonStylePolicyNumber() {
        String result = LogMasker.mask("{\"policyNumber\":\"POL-12345\"}");
        assertThat(result).isEqualTo("{\"policyNumber\":\"***\"}");
    }

    @Test
    void jsonKeyMaskingIsCaseInsensitive() {
        String result = LogMasker.mask("{\"Password\":\"secret\",\"EMAIL\":\"x@y.com\",\"USERNAME\":\"joe\"}");
        assertThat(result).doesNotContain("secret");
        assertThat(result).doesNotContain("x@y.com");
        assertThat(result).doesNotContain("joe\"");
    }

    @Test
    void leavesNonSensitiveJsonKeysAlone() {
        String input = "{\"id\":42,\"role\":\"AGENT\",\"status\":\"OPEN\"}";
        assertThat(LogMasker.mask(input)).isEqualTo(input);
    }

    // ------ Rule 2: bare key=value ------

    @Test
    void masksBareKeyValue_password() {
        assertThat(LogMasker.mask("attempt password=secret123 ok"))
                .isEqualTo("attempt password=*** ok");
    }

    @Test
    void masksBareKeyValue_username() {
        assertThat(LogMasker.mask("attempt username=admin ok"))
                .isEqualTo("attempt username=*** ok");
    }

    @Test
    void masksBareKeyValue_email() {
        assertThat(LogMasker.mask("contact email=foo@bar.com end"))
                .isEqualTo("contact email=*** end");
    }

    @Test
    void masksBareKeyValue_phoneNumber() {
        assertThat(LogMasker.mask("dial phoneNumber=+972501234567 end"))
                .isEqualTo("dial phoneNumber=*** end");
    }

    @Test
    void masksBareKeyValue_policyNumber() {
        assertThat(LogMasker.mask("ref policyNumber=POL-12345 end"))
                .isEqualTo("ref policyNumber=*** end");
    }

    @Test
    void bareKeyValueMaskingIsCaseInsensitive() {
        assertThat(LogMasker.mask("Password=Secret EMAIL=x@y.com Username=Joe"))
                .isEqualTo("Password=*** EMAIL=*** Username=***");
    }

    @Test
    void bareKeyValueAllowsWhitespaceAroundEquals() {
        assertThat(LogMasker.mask("password = secret123"))
                .isEqualTo("password=***");
    }

    @Test
    void bareKeyValueDoesNotMatchPrefixedKeys() {
        // "passwordHash=..." and "userEmail=..." are NOT in our sensitive list
        // and must remain untouched (otherwise we'd over-mask schema/log noise).
        String input = "passwordHash=abc123 userEmail=foo@bar.com mypassword=hidden";
        String result = LogMasker.mask(input);
        // passwordHash kept, but the email inside userEmail is still caught by
        // the bare-email rule (rule 3) — that's defense-in-depth, not a bug.
        assertThat(result).contains("passwordHash=abc123");
        assertThat(result).contains("userEmail=***@***");
        assertThat(result).contains("mypassword=hidden");
    }

    @Test
    void masksMultipleBareKeyValuesPreservingOtherFields() {
        String input = "username=joe,password=secret,id=42";
        String result = LogMasker.mask(input);
        assertThat(result).isEqualTo("username=***,password=***,id=42");
    }

    // ------ Rule 3: bare email — full replacement ------

    @Test
    void masksBareEmailFully() {
        assertThat(LogMasker.mask("logging in user@example.com today"))
                .isEqualTo("logging in ***@*** today");
    }

    @Test
    void masksMultipleBareEmails() {
        assertThat(LogMasker.mask("from a@x.com to b@y.org"))
                .isEqualTo("from ***@*** to ***@***");
    }

    @Test
    void masksEmailWithDotsAndPlusInLocalPart() {
        assertThat(LogMasker.mask("contact: john.doe+filter@example.com"))
                .isEqualTo("contact: ***@***");
    }

    @Test
    void emailMaskingDoesNotLeakDomain() {
        // Hardening: even ".gov.il", ".bank.co", or any custom TLD must be hidden.
        String result = LogMasker.mask("user@subdomain.example.gov.il sent it");
        assertThat(result).doesNotContain("example");
        assertThat(result).doesNotContain("gov");
        assertThat(result).doesNotContain("subdomain");
        assertThat(result).contains("***@***");
    }

    // ------ Rule 4: Bearer tokens ------

    @Test
    void masksBearerToken() {
        String result = LogMasker.mask("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.abc.def");
        assertThat(result).isEqualTo("Authorization: Bearer ***");
    }

    @Test
    void bearerMaskingIsCaseInsensitive() {
        assertThat(LogMasker.mask("header: bearer abc123")).contains("Bearer ***");
        assertThat(LogMasker.mask("header: BEARER abc123")).contains("Bearer ***");
    }

    // ------ Combinations ------

    @Test
    void appliesAllRulesToCombinedInput() {
        String input = "auth failed for victim@example.com using Bearer xyz123 "
                + "with {\"password\":\"hunter2\"} and password=hunter3 username=victim";
        String result = LogMasker.mask(input);

        assertThat(result).contains("***@***");
        assertThat(result).contains("Bearer ***");
        assertThat(result).contains("\"password\":\"***\"");
        assertThat(result).contains("password=***");
        assertThat(result).contains("username=***");

        assertThat(result).doesNotContain("victim@example.com");
        assertThat(result).doesNotContain("example.com");
        assertThat(result).doesNotContain("xyz123");
        assertThat(result).doesNotContain("hunter2");
        assertThat(result).doesNotContain("hunter3");
        assertThat(result).doesNotContain("victim ");
    }

    @Test
    void bareEmailAndKeyValueDoNotConflict() {
        // email=foo@bar.com → key=value rule fires first, giving email=***
        // (NOT email=***@***).
        assertThat(LogMasker.mask("email=foo@bar.com")).isEqualTo("email=***");
    }

    // ------ Edge cases ------

    @Test
    void handlesNullInput() {
        assertThat(LogMasker.mask(null)).isNull();
    }

    @Test
    void handlesEmptyInput() {
        assertThat(LogMasker.mask("")).isEqualTo("");
    }

    @Test
    void preservesStringWithoutSensitiveData() {
        String input = "request.end status=200 durationMs=14 path=/api/v1/tickets";
        assertThat(LogMasker.mask(input)).isEqualTo(input);
    }

    @Test
    void technicalEmailPatternsAlsoFullyMasked_thisIsAcceptedTradeoff() {
        // Documented limitation: any email-shaped string is masked. Acceptable
        // because we never log technical addresses (e.g. git@github.com).
        assertThat(LogMasker.mask("repo: git@github.com:owner/repo.git"))
                .contains("***@***");
    }
}
