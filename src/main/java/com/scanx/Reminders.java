package com.scanx;

import com.scanx.Models.Reminder;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Reminders a student writes for themselves. Only that student can change them. */
public final class Reminders {

    private Reminders() {
    }

    static final Db.RowMapper<Reminder> MAPPER = rs -> new Reminder(
            rs.getLong("id"), rs.getString("student_id"), rs.getString("reminder_text"),
            Db.date(rs, "remind_date"), rs.getBoolean("done"));

    public static List<Reminder> forStudent(Connection c, String studentId) throws SQLException {
        return Db.query(c, "SELECT * FROM reminders WHERE student_id = ? ORDER BY id", MAPPER, studentId);
    }

    public static Reminder add(Connection c, String studentId, String text, String date) throws SQLException {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            throw ApiException.badRequest("Enter a reminder.");
        }
        if (clean.length() > 120) {
            throw ApiException.badRequest("The reminder is too long (up to 120 characters).");
        }

        LocalDate parsed = null;
        if (!Util.isBlank(date)) {
            try {
                parsed = LocalDate.parse(date.trim());
            } catch (DateTimeParseException e) {
                throw ApiException.badRequest("The reminder date is not valid.");
            }
        }

        long id = Db.insert(c,
                "INSERT INTO reminders (student_id, reminder_text, remind_date, done, created_at) "
                        + "VALUES (?, ?, ?, 0, ?)",
                studentId, clean, parsed, Times.now());

        return Db.queryOne(c, "SELECT * FROM reminders WHERE id = ?", MAPPER, id);
    }

    public static void toggle(Connection c, String studentId, long id) throws SQLException {
        int changed = Db.update(c,
                "UPDATE reminders SET done = NOT done WHERE id = ? AND student_id = ?", id, studentId);
        if (changed == 0) {
            throw ApiException.notFound("That reminder was not found.");
        }
    }

    public static void delete(Connection c, String studentId, long id) throws SQLException {
        int changed = Db.update(c, "DELETE FROM reminders WHERE id = ? AND student_id = ?", id, studentId);
        if (changed == 0) {
            throw ApiException.notFound("That reminder was not found.");
        }
    }

}
