package com.scanx;

import com.scanx.Models.AcademicEvent;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

/** Official events (tests, workshops ...) created by staff. Students can only read them. */
public final class Events {

    private Events() {
    }

    public static final List<String> TYPES = java.util.Arrays.asList(
            "Test", "Exam", "Assignment", "Important Lecture", "Workshop", "Interview", "Placement Event");

    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    static final Db.RowMapper<AcademicEvent> MAPPER = rs -> new AcademicEvent(
            rs.getLong("id"), rs.getString("title"), rs.getString("event_type"),
            Db.date(rs, "event_date"), rs.getString("event_time"), rs.getString("location"),
            Db.longOrNull(rs, "audience_course_id"), Db.intOrNull(rs, "audience_year"));

    /** For staff: every event. For a student: events with no course/year set, or matching their own. */
    public static List<AcademicEvent> list(Connection c, Long studentCourseId, Integer studentYear)
            throws SQLException {
        if (studentCourseId == null) {
            return Db.query(c, "SELECT * FROM academic_events ORDER BY event_date, event_time, id", MAPPER);
        }
        return Db.query(c,
                "SELECT * FROM academic_events WHERE "
                        + "(audience_course_id IS NULL OR audience_course_id = ?) "
                        + "AND (audience_year IS NULL OR audience_year = ?) "
                        + "ORDER BY event_date, event_time, id",
                MAPPER, studentCourseId, studentYear);
    }

    public static AcademicEvent create(Connection c, String title, String type, String date, String time,
                                       String location, Long courseId, Integer year) throws SQLException {
        Fields f = check(c, title, type, date, time, location, courseId, year);

        long id = Db.insert(c,
                "INSERT INTO academic_events (title, event_type, event_date, event_time, location, "
                        + "audience_course_id, audience_year, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                f.title, f.type, f.date, f.time, f.location, f.courseId, f.year, Times.now());

        return Db.queryOne(c, "SELECT * FROM academic_events WHERE id = ?", MAPPER, id);
    }

    public static AcademicEvent update(Connection c, long id, String title, String type, String date,
                                       String time, String location, Long courseId, Integer year)
            throws SQLException {
        Fields f = check(c, title, type, date, time, location, courseId, year);

        int changed = Db.update(c,
                "UPDATE academic_events SET title = ?, event_type = ?, event_date = ?, event_time = ?, "
                        + "location = ?, audience_course_id = ?, audience_year = ? WHERE id = ?",
                f.title, f.type, f.date, f.time, f.location, f.courseId, f.year, id);

        if (changed == 0) {
            throw ApiException.notFound("That event no longer exists.");
        }

        return Db.queryOne(c, "SELECT * FROM academic_events WHERE id = ?", MAPPER, id);
    }

    public static void delete(Connection c, long id) throws SQLException {
        if (Db.update(c, "DELETE FROM academic_events WHERE id = ?", id) == 0) {
            throw ApiException.notFound("That event no longer exists.");
        }
    }

    private static final class Fields {
        final String title;
        final String type;
        final LocalDate date;
        final String time;
        final String location;
        final Long courseId;
        final Integer year;

        Fields(String title, String type, LocalDate date, String time, String location, Long courseId, Integer year) {
            this.title = title;
            this.type = type;
            this.date = date;
            this.time = time;
            this.location = location;
            this.courseId = courseId;
            this.year = year;
        }
    }

    private static Fields check(Connection c, String title, String type, String date, String time,
                                String location, Long courseId, Integer year) throws SQLException {

        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isEmpty() || cleanTitle.length() > 100) {
            throw ApiException.badRequest("Enter an event title (up to 100 characters).");
        }
        if (type == null || !TYPES.contains(type)) {
            throw ApiException.badRequest("Choose a valid event type.");
        }

        LocalDate parsedDate;
        try {
            parsedDate = LocalDate.parse(date == null ? "" : date.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Enter the event date.");
        }

        String cleanTime = time == null ? "" : time.trim();
        if (!cleanTime.isEmpty() && !TIME.matcher(cleanTime).matches()) {
            throw ApiException.badRequest("The time must look like 14:30.");
        }

        String cleanLocation = location == null ? "" : location.trim();
        if (cleanLocation.length() > 100) {
            throw ApiException.badRequest("The location is too long.");
        }

        if (courseId != null && Courses.course(c, courseId) == null) {
            throw ApiException.notFound("That course was not found.");
        }

        return new Fields(cleanTitle, type, parsedDate, cleanTime.isEmpty() ? null : cleanTime,
                cleanLocation.isEmpty() ? null : cleanLocation, courseId, year);
    }

}
