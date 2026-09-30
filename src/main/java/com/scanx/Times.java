package com.scanx;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** One place that knows the current time and the college's time zone. */
public final class Times {

    private Times() {
    }

    private static final ZoneId ZONE = ZoneId.of(Config.TIMEZONE);

    public static Instant now() {
        return Instant.now();
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDate dateOf(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    /** The moment a date and clock time happen at the college. */
    public static Instant at(LocalDate date, java.time.LocalTime time) {
        return date.atTime(time).atZone(ZONE).toInstant();
    }

    /** The clock time (at the college) of a moment. */
    public static java.time.LocalTime timeOf(Instant instant) {
        return instant.atZone(ZONE).toLocalTime();
    }

    public static String zoneName() {
        return ZONE.getId();
    }

    public static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

}
