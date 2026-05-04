package com.surense.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.encoder.LogstashEncoder;

import java.nio.charset.StandardCharsets;

/**
 * Logstash JSON encoder that runs {@link LogMasker} over the produced JSON
 * before it is written to the appender. Used in the {@code prod} profile for
 * production-ready structured logging.
 *
 * <p>Masking is applied to the entire JSON line so it covers both the
 * {@code message} field and any nested MDC / context fields the encoder
 * decides to include.
 */
public class MaskingLogstashEncoder extends LogstashEncoder {

    @Override
    public byte[] encode(ILoggingEvent event) {
        byte[] raw = super.encode(event);
        if (raw == null || raw.length == 0) {
            return raw;
        }
        String json = new String(raw, StandardCharsets.UTF_8);
        return LogMasker.mask(json).getBytes(StandardCharsets.UTF_8);
    }
}
