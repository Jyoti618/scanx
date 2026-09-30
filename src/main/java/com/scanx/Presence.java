package com.scanx;

import com.scanx.Models.ActionResult;
import com.scanx.Models.PresenceRecord;
import com.scanx.Models.Student;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The gate scan: entry and exit.
 * The first scan of a visit is the entry, the next scan is the exit.
 * What a day counts as (full / half / absent) is worked out by DayAttendance,
 * this class only records the raw entry and exit times.
 */
public final class Presence {

    private Presence() {
    }

    /** The same student scanned again within this time is ignored (double-scan guard). */
    public static final long DUPLICATE_SCAN_MS = 30_000;

    static final Db.RowMapper<PresenceRecord> MAPPER = rs -> new PresenceRecord(
            rs.getLong("id"), rs.getString("student_id"), Db.date(rs, "visit_date"),
            Db.instant(rs, "entry_time"), Db.instant(rs, "exit_time"),
            Db.longOrNull(rs, "duration_ms"), rs.getBoolean("incomplete"));

    static PresenceRecord lastVisit(Connection c, String studentId) throws SQLException {
        return Db.queryOne(c,
                "SELECT * FROM presence_records WHERE student_id = ? ORDER BY id DESC LIMIT 1",
                MAPPER, studentId);
    }

    /** Is the student inside the college right now (entered today, not left yet)? */
    public static boolean isInside(Connection c, String studentId) throws SQLException {
        PresenceRecord last = lastVisit(c, studentId);

        return last != null
                && last.exit() == null
                && !last.incomplete()
                && last.visitDate().equals(Times.today());
    }

    public static ActionResult toggle(Connection c, String studentId, String method) throws SQLException {

        Student student = Students.find(c, studentId);
        if (student == null) {
            return ActionResult.fail("UNKNOWN_STUDENT", "That student is not registered.");
        }

        Instant now = Times.now();

        /* Ignore an accidental double scan */

        Instant lastScan = Db.queryOne(c,
                "SELECT logged_at FROM scan_log WHERE student_id = ? AND kind IN ('entry', 'exit') "
                        + "ORDER BY id DESC LIMIT 1",
                rs -> Db.instant(rs, "logged_at"), studentId);

        if (lastScan != null && Duration.between(lastScan, now).toMillis() < DUPLICATE_SCAN_MS) {
            return ActionResult.fail("TOO_SOON", student,
                    student.name() + " was just scanned. Wait a moment before scanning again.");
        }

        LocalDate today = Times.dateOf(now);
        PresenceRecord last = lastVisit(c, studentId);

        if (last != null && last.exit() == null && !last.incomplete()) {

            /* Leaving */

            if (last.visitDate().equals(today)) {
                long durationMs = Duration.between(last.entry(), now).toMillis();

                Db.update(c,
                        "UPDATE presence_records SET exit_time = ?, duration_ms = ?, exit_method = ? "
                                + "WHERE id = ?",
                        now, durationMs, method, last.id());

                writeLog(c, studentId, "exit", method, now);

                return ActionResult.success("EXIT", "EXIT",
                        student.name() + " left the college.", student, now, durationMs);
            }

            /* Forgot to scan out on an earlier day */

            Db.update(c, "UPDATE presence_records SET incomplete = 1, duration_ms = 0 WHERE id = ?", last.id());
        }

        /* Arriving */

        Db.update(c,
                "INSERT INTO presence_records (student_id, visit_date, entry_time, incomplete, entry_method) "
                        + "VALUES (?, ?, ?, 0, ?)",
                studentId, today, now, method);

        writeLog(c, studentId, "entry", method, now);

        return ActionResult.success("ENTRY", "ENTRY",
                student.name() + " entered the college.", student, now, null);
    }

    static void writeLog(Connection c, String studentId, String kind, String method, Instant now)
            throws SQLException {
        Db.update(c,
                "INSERT INTO scan_log (logged_at, student_id, kind, method) VALUES (?, ?, ?, ?)",
                now, studentId, kind, method);
    }

}
