package com.surense.common.logging;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Defence-in-depth log scrubber. Applies a fixed set of regex rules to any
 * string before it leaves the logging subsystem (text or JSON).
 *
 * <p>Rules are deliberately conservative — primary PII protection comes from:
 * <ul>
 *   <li>Never embedding PII into error messages
 *       (see {@code messages.properties}).</li>
 *   <li>Never logging full request / response bodies.</li>
 *   <li>Entity {@code toString()} overrides that exclude PII fields.</li>
 * </ul>
 * This masker is the safety net that catches accidental developer mistakes
 * (e.g. {@code log.info("payload={}", request)}).
 *
 * <p>Masked patterns (applied in this order — order matters):
 * <ol>
 *   <li>JSON-style key/value pairs for the keys
 *       {@code username | password | email | phoneNumber | policyNumber}.</li>
 *   <li>Bare {@code key=value} pairs for the same keys.</li>
 *   <li>Bare email addresses anywhere — replaced fully as {@code ***@***}.</li>
 *   <li>{@code Bearer xxxxx} authorization tokens.</li>
 * </ol>
 *
 * <p>The bare key=value rule fires before the bare-email rule so a string
 * like {@code email=foo@bar.com} becomes {@code email=***} (rather than
 * {@code email=***@***}).
 *
 * <p>This class is stateless and thread-safe.
 */
public final class LogMasker {

    private static final List<MaskRule> RULES = List.of(
            // Rule 1 — sensitive JSON keys: replace value with ***
            new MaskRule(
                    Pattern.compile(
                            "\"(username|password|email|phoneNumber|policyNumber)\"\\s*:\\s*\"([^\"]*)\"",
                            Pattern.CASE_INSENSITIVE),
                    "\"$1\":\"***\""),
            // Rule 2 — sensitive keys in bare key=value form (word-bounded so
            // "passwordHash=..." is NOT masked). Value is consumed up to the
            // next whitespace, comma, semicolon, ampersand, or double-quote
            // so neighbouring fields in URL/log strings stay intact.
            new MaskRule(
                    Pattern.compile(
                            "(?i)\\b(username|password|email|phoneNumber|policyNumber)\\s*=\\s*[^\\s,;&\"]+"),
                    "$1=***"),
            // Rule 3 — bare email anywhere: fully replaced (no domain leak)
            new MaskRule(
                    Pattern.compile("[A-Za-z0-9._+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
                    "***@***"),
            // Rule 4 — Bearer tokens
            new MaskRule(
                    Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._\\-+/=]+"),
                    "Bearer ***"));

    private LogMasker() {
        // utility class
    }

    /**
     * Apply every mask rule in order to {@code input}.
     *
     * @param input any log string; {@code null} is returned unchanged.
     * @return the masked string with all rules applied
     */
    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        for (MaskRule rule : RULES) {
            result = rule.pattern.matcher(result).replaceAll(rule.replacement);
        }
        return result;
    }

    private record MaskRule(Pattern pattern, String replacement) {}
}
