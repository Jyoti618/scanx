package com.scanx;

import com.scanx.Models.PresenceRecord;
import com.scanx.Models.Student;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns raw gate scans into one status per calendar day: FULL, HALF or ABSENT.
 * Non-working days (weekends, holidays) are skipped, not counted as absent.
 *
 *  - no entry scan at all           -> ABSENT
 *  - entered, never scanned out     -> FULL (benefit of the doubt; can be corrected below)
 *  - scanned out before the cut-off -> HALF
 *  - scanned out at/after cut-off   -> FULL
 *  - a teacher can override any single day for a student (sick leave, forgotten scan, and so on)
 */
public final class DayAttendance {

    private DayAttendance() {
    }

    public static final List<String> STATUSES = java.util.Arrays.asList("FULL", "HALF", "ABSENT");

    public static final class DayResult {
        public final LocalDate date;
        public final String status;
        public final boolean overridden;
        public final boolean working;

        DayResult(LocalDate date, String status, boolean overridden, boolean working) {
            this.date = date;
            this.status = status;
            this.overridden = overridden;
            this.working = working;
        }
    }

    /** The status of one day for one student, honouring any teacher override. */
    public static DayResult dayStatus(Connection c, Student student, LocalDate date) throws SQLException {

        if (!Timings.isWorkingDay(c, date, student.courseId(), student.yearNo())) {
            return new DayResult(date, null, false, false);
        }

        String override = Db.queryOne(c,
                "SELECT status FROM day_overrides WHERE student_id = ? AND day_date = ?",
                rs -> rs.getString(1), student.id(), date);
        if (override != null) {
            return new DayResult(date, override, true, true);
        }

        List<PresenceRecord> visits = Db.query(c,
                "SELECT * FROM presence_records WHERE student_id = ? AND visit_date = ? ORDER BY id",
                Presence.MAPPER, student.id(), date);

        if (visits.isEmpty()) {
            return new DayResult(date, "ABSENT", false, true);
        }

        LocalTime cutoff = Timings.endOfDay(c, date, student.courseId(), student.yearNo());
        if (cutoff == null) {
            cutoff = Timings.halfDayCutoff(c, student.courseId(), student.yearNo(), date);
        }

        LocalTime latestExit = null;
        boolean anyOpenOrIncomplete = false;

        for (PresenceRecord visit : visits) {
            if (visit.exit() != null) {
                LocalTime exitTime = Times.timeOf(visit.exit());
                if (latestExit == null || exitTime.isAfter(latestExit)) {
                    latestExit = exitTime;
                }
            } else {
                anyOpenOrIncomplete = true;
            }
        }

        /* Still inside today, or forgot to scan out: benefit of the doubt */
        if (anyOpenOrIncomplete || latestExit == null) {
            return new DayResult(date, "FULL", false, true);
        }

        return new DayResult(date, latestExit.isBefore(cutoff) ? "HALF" : "FULL", false, true);
    }

    /** Every working day between from and to (inclusive), with its status for this student. */
    public static List<DayResult> range(Connection c, Student student, LocalDate from, LocalDate to)
            throws SQLException {
        List<DayResult> results = new ArrayList<DayResult>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            DayResult result = dayStatus(c, student, date);
            if (result.working) {
                results.add(result);
            }
        }
        return results;
    }

    public static final class Stats {
        public final int fullDays;
        public final int halfDays;
        public final int absentDays;
        public final int workingDays;
        /** Full = 1 credit, half = 0.5 credit. */
        public final double credit;

        Stats(int fullDays, int halfDays, int absentDays) {
            this.fullDays = fullDays;
            this.halfDays = halfDays;
            this.absentDays = absentDays;
            this.workingDays = fullDays + halfDays + absentDays;
            this.credit = fullDays + halfDays * 0.5;
        }

        public Double percent() {
            return workingDays == 0 ? null : (credit / workingDays) * 100;
        }
    }

    public static Stats stats(Connection c, Student student, LocalDate from, LocalDate to) throws SQLException {
        int full = 0;
        int half = 0;
        int absent = 0;
        for (DayResult r : range(c, student, from, to)) {
            if ("FULL".equals(r.status)) {
                full++;
            } else if ("HALF".equals(r.status)) {
                half++;
            } else {
                absent++;
            }
        }
        return new Stats(full, half, absent);
    }

    /** Attendance from the day the student joined, up to today. */
    public static Stats statsSoFar(Connection c, Student student) throws SQLException {
        LocalDate from = student.joinedDate().isAfter(Times.today()) ? Times.today() : student.joinedDate();
        return stats(c, student, from, Times.today());
    }

    // =====================================================
    //  Teacher overrides (corrections)
    // =====================================================

    public static void setOverride(Connection c, String studentId, String dateText, String status,
                                   String setBy) throws SQLException {
        Student student = Students.require(c, studentId);

        LocalDate date;
        try {
            date = LocalDate.parse(dateText == null ? "" : dateText.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Enter a valid date.");
        }
        if (date.isAfter(Times.today())) {
            throw ApiException.badRequest("You cannot set attendance for a future date.");
        }
        if (!STATUSES.contains(status)) {
            throw ApiException.badRequest("Status must be FULL, HALF or ABSENT.");
        }
        if (!Timings.isWorkingDay(c, date, student.courseId(), student.yearNo())) {
            throw ApiException.badRequest("That date is not a working day.");
        }

        Db.update(c,
                "INSERT INTO day_overrides (student_id, day_date, status, set_by) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE status = VALUES(status), set_by = VALUES(set_by)",
                studentId, date, status, setBy);
    }

    public static void clearOverride(Connection c, String studentId, String dateText) throws SQLException {
        LocalDate date;
        try {
            date = LocalDate.parse(dateText == null ? "" : dateText.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Enter a valid date.");
        }
        Db.update(c, "DELETE FROM day_overrides WHERE student_id = ? AND day_date = ?", studentId, date);
    }

    /** Every student below the target, for the low-attendance report. */
    public static Map<Student, Stats> belowTarget(Connection c, List<Student> students, int targetPercent)
            throws SQLException {
        Map<Student, Stats> result = new LinkedHashMap<Student, Stats>();
        for (Student student : students) {
            Stats s = statsSoFar(c, student);
            Double percent = s.percent();
            if (percent != null && percent < targetPercent) {
                result.put(student, s);
            }
        }
        return result;
    }

}
