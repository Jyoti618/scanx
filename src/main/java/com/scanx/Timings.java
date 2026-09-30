package com.scanx;

import com.scanx.Models.CalendarDay;
import com.scanx.Models.Timing;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * College timings and the calendar.
 *  - a half-day cut-off time can change over the year, and can differ for a course or a year
 *    (for example seniors stay later than juniors)
 *  - the calendar marks holidays, and days with a different end time (half-day college, exams)
 */
public final class Timings {

    private Timings() {
    }

    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    public static final List<String> DAY_KINDS = java.util.Arrays.asList("HOLIDAY", "SPECIAL");

    static final Db.RowMapper<Timing> TIMING_MAPPER = rs -> new Timing(
            rs.getLong("id"), Db.date(rs, "effective_from"), rs.getString("half_day_time"),
            Db.longOrNull(rs, "course_id"), Db.intOrNull(rs, "year_no"));

    static final Db.RowMapper<CalendarDay> DAY_MAPPER = rs -> new CalendarDay(
            rs.getLong("id"), Db.date(rs, "day_date"), rs.getString("kind"), rs.getString("end_time"),
            rs.getString("note"), Db.longOrNull(rs, "course_id"), Db.intOrNull(rs, "year_no"));

    private static String checkTime(String time, String what) {
        String clean = time == null ? "" : time.trim();
        if (!TIME.matcher(clean).matches()) {
            throw ApiException.badRequest("Enter " + what + " as a 24-hour time, for example 14:50.");
        }
        return clean;
    }

    private static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Enter a valid date.");
        }
    }

    // =====================================================
    //  Timings (half-day cut-off)
    // =====================================================

    public static List<Timing> allTimings(Connection c) throws SQLException {
        return Db.query(c, "SELECT * FROM college_timings ORDER BY effective_from DESC, id DESC", TIMING_MAPPER);
    }

    public static long addTiming(Connection c, String effectiveFrom, String halfDayTime,
                                 Long courseId, Integer yearNo) throws SQLException {
        LocalDate from = parseDate(effectiveFrom);
        String time = checkTime(halfDayTime, "the half-day time");
        if (courseId != null && Courses.course(c, courseId) == null) {
            throw ApiException.notFound("That course was not found.");
        }
        return Db.insert(c,
                "INSERT INTO college_timings (effective_from, half_day_time, course_id, year_no) VALUES (?, ?, ?, ?)",
                from, time, courseId, yearNo);
    }

    public static void deleteTiming(Connection c, long id) throws SQLException {
        if (Db.update(c, "DELETE FROM college_timings WHERE id = ?", id) == 0) {
            throw ApiException.notFound("That timing was not found.");
        }
    }

    /**
     * The half-day cut-off in force for this student on this date:
     * the newest timing that (a) started on or before the date, and
     * (b) matches the student's course and year, or is a general default.
     * Falls back to Config.DEFAULT_HALF_DAY_TIME if nothing was ever set.
     */
    public static LocalTime halfDayCutoff(Connection c, long courseId, int yearNo, LocalDate date)
            throws SQLException {

        String specific = Db.queryOne(c,
                "SELECT half_day_time FROM college_timings "
                        + "WHERE effective_from <= ? AND course_id = ? AND year_no = ? "
                        + "ORDER BY effective_from DESC, id DESC LIMIT 1",
                rs -> rs.getString(1), date, courseId, yearNo);

        if (specific != null) {
            return LocalTime.parse(specific);
        }

        String general = Db.queryOne(c,
                "SELECT half_day_time FROM college_timings "
                        + "WHERE effective_from <= ? AND course_id IS NULL AND year_no IS NULL "
                        + "ORDER BY effective_from DESC, id DESC LIMIT 1",
                rs -> rs.getString(1), date);

        return LocalTime.parse(general != null ? general : Config.DEFAULT_HALF_DAY_TIME);
    }

    // =====================================================
    //  Calendar: holidays and special days
    // =====================================================

    public static List<CalendarDay> calendarBetween(Connection c, LocalDate from, LocalDate to)
            throws SQLException {
        return Db.query(c,
                "SELECT * FROM calendar_days WHERE day_date BETWEEN ? AND ? ORDER BY day_date",
                DAY_MAPPER, from, to);
    }

    public static long addCalendarDay(Connection c, String date, String kind, String endTime, String note,
                                      Long courseId, Integer yearNo) throws SQLException {
        LocalDate day = parseDate(date);
        if (!DAY_KINDS.contains(kind)) {
            throw ApiException.badRequest("The day type must be HOLIDAY or SPECIAL.");
        }
        String time = "SPECIAL".equals(kind) ? checkTime(endTime, "the end time for this day") : null;
        String cleanNote = note == null ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 100) {
            throw ApiException.badRequest("The note is too long (up to 100 characters).");
        }
        if (courseId != null && Courses.course(c, courseId) == null) {
            throw ApiException.notFound("That course was not found.");
        }
        return Db.insert(c,
                "INSERT INTO calendar_days (day_date, kind, end_time, note, course_id, year_no) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                day, kind, time, cleanNote, courseId, yearNo);
    }

    public static void deleteCalendarDay(Connection c, long id) throws SQLException {
        if (Db.update(c, "DELETE FROM calendar_days WHERE id = ?", id) == 0) {
            throw ApiException.notFound("That calendar entry was not found.");
        }
    }

    /** Is this date a working day for this student (not a weekend, not a holiday)? */
    public static boolean isWorkingDay(Connection c, LocalDate date, long courseId, int yearNo)
            throws SQLException {

        if (!Settings.workingDays(c).contains(date.getDayOfWeek().getValue())) {
            return false;
        }

        return !Db.exists(c,
                "SELECT 1 FROM calendar_days WHERE day_date = ? AND kind = 'HOLIDAY' "
                        + "AND (course_id IS NULL OR course_id = ?) AND (year_no IS NULL OR year_no = ?)",
                date, courseId, yearNo);
    }

    /** The end-of-day time (for computing full/half attendance) on this date, honouring SPECIAL days. */
    public static LocalTime endOfDay(Connection c, LocalDate date, long courseId, int yearNo)
            throws SQLException {

        String special = Db.queryOne(c,
                "SELECT end_time FROM calendar_days WHERE day_date = ? AND kind = 'SPECIAL' "
                        + "AND (course_id IS NULL OR course_id = ?) AND (year_no IS NULL OR year_no = ?) "
                        + "ORDER BY course_id IS NULL, year_no IS NULL LIMIT 1",
                rs -> rs.getString(1), date, courseId, yearNo);

        return special != null ? LocalTime.parse(special) : null;
    }

}
