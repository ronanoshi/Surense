package com.surense.infra.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback {@link PatternLayout} that runs {@link LogMasker} over the formatted
 * log line before it is written. Used in dev / test profiles for the
 * human-readable text output.
 */
public class MaskingPatternLayout extends PatternLayout {

    @Override
    public String doLayout(ILoggingEvent event) {
        return LogMasker.mask(super.doLayout(event));
    }
}
