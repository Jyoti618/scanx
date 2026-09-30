package com.scanx;

import com.scanx.Models.Semester;
import com.scanx.Models.Student;
import com.scanx.Models.Subject;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The subject-wise class register.
 * A teacher opens a subject and date, sees the class list, and ticks who was present.
 * This is separate from the day (gate) attendance: a student can be scanned in at the gate
 * for the day, but a teacher's register is the record of who was actually in a given class.
 */
public final class Register {

    private Register() {
    }

    public static final class Taken {
        public final long sessionId;
        public final long subjectId;
        public final String subjectName;
        public final LocalDate date;
        public final int periodNo;
        public final String takenBy;
        public final List<String> presentIds;

        Taken(long sessionId, long subjectId, String subjectName, LocalDate date, int periodNo,
              String takenBy, List<String> presentIds) {
            this.sessionId = sessionId;
            this.subjectId = subjectId;
            this.subjectName = subjectName;
            this.date = date;
            this.periodNo = periodNo;
            this.takenBy = takenBy;
            this.presentIds = presentIds;
        }
    }

    private static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Enter a valid date.");
        }
    }

    /**
     * Saves who was present for one subject, one date, one period.
     * Re-saving the same subject/date/period replaces the previous list (the teacher can correct mistakes).
     */
    public static Taken save(Connection c, long subjectId, String dateText, int periodNo,
                             List<String> presentIds, String takenBy) throws SQLException {

        Subject subject = Courses.subject(c, subjectId);
        if (subject == null) {
            throw ApiException.notFound("That subject was not found.");
        }
        LocalDate date = parseDate(dateText);
        if (date.isAfter(Times.today())) {
            throw ApiException.badRequest("You cannot take a register for a future date.");
        }
        if (periodNo < 1 || periodNo > 12) {
            throw ApiException.badRequest("The period number must be between 1 and 12.");
        }

        Semester semester = Courses.semester(c, subject.semesterId());

        Long existingId = Db.queryOne(c,
                "SELECT id FROM class_sessions WHERE subject_id = ? AND session_date = ? AND period_no = ?",
                rs -> rs.getLong(1), subjectId, date, periodNo);

        long sessionId;
        if (existingId != null) {
            sessionId = existingId;
            Db.update(c, "UPDATE class_sessions SET taken_by = ?, taken_at = ? WHERE id = ?",
                    takenBy, Times.now(), sessionId);
            Db.update(c, "DELETE FROM session_marks WHERE session_id = ?", sessionId);
        } else {
            sessionId = Db.insert(c,
                    "INSERT INTO class_sessions (semester_id, subject_id, session_date, period_no, taken_by, taken_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    semester.id(), subjectId, date, periodNo, takenBy, Times.now());
        }

        List<String> clean = new ArrayList<String>();
        for (String id : presentIds) {
            if (Util.isBlank(id)) {
                continue;
            }
            String upper = id.trim().toUpperCase();
            if (Students.find(c, upper) == null) {
                throw ApiException.badRequest(upper + " is not a registered student.");
            }
            if (!clean.contains(upper)) {
                clean.add(upper);
            }
        }
        for (String id : clean) {
            Db.update(c, "INSERT INTO session_marks (session_id, student_id) VALUES (?, ?)", sessionId, id);
        }

        return new Taken(sessionId, subjectId, subject.name(), date, periodNo, takenBy, clean);
    }

    public static Taken forDate(Connection c, long subjectId, String dateText, int periodNo) throws SQLException {
        Subject subject = Courses.subject(c, subjectId);
        if (subject == null) {
            throw ApiException.notFound("That subject was not found.");
        }
        LocalDate date = parseDate(dateText);

        Long sessionId = Db.queryOne(c,
                "SELECT id FROM class_sessions WHERE subject_id = ? AND session_date = ? AND period_no = ?",
                rs -> rs.getLong(1), subjectId, date, periodNo);

        if (sessionId == null) {
            return new Taken(0, subjectId, subject.name(), date, periodNo, null, new ArrayList<String>());
        }

        String takenBy = Db.queryOne(c, "SELECT taken_by FROM class_sessions WHERE id = ?",
                rs -> rs.getString(1), sessionId);
        List<String> present = Db.query(c, "SELECT student_id FROM session_marks WHERE session_id = ?",
                rs -> rs.getString(1), sessionId);

        return new Taken(sessionId, subjectId, subject.name(), date, periodNo, takenBy, present);
    }

    /** attended / total classes taken so far, for one student in one subject. */
    public static final class SubjectStats {
        public final long subjectId;
        public final String subjectName;
        public final int attended;
        public final int total;

        SubjectStats(long subjectId, String subjectName, int attended, int total) {
            this.subjectId = subjectId;
            this.subjectName = subjectName;
            this.attended = attended;
            this.total = total;
        }

        public Double percent() {
            return total == 0 ? null : (attended * 100.0) / total;
        }
    }

    public static List<SubjectStats> statsForStudent(Connection c, Student student) throws SQLException {
        List<Subject> subjects = Db.query(c, "SELECT * FROM subjects WHERE semester_id = ? ORDER BY name",
                Courses.SUBJECT, student.semesterId());

        List<SubjectStats> out = new ArrayList<SubjectStats>();
        for (Subject subject : subjects) {
            int total = Db.queryOne(c,
                    "SELECT COUNT(*) FROM class_sessions WHERE subject_id = ? AND session_date >= ?",
                    rs -> rs.getInt(1), subject.id(), student.joinedDate());
            int attended = Db.queryOne(c,
                    "SELECT COUNT(*) FROM session_marks m JOIN class_sessions s ON s.id = m.session_id "
                            + "WHERE s.subject_id = ? AND m.student_id = ? AND s.session_date >= ?",
                    rs -> rs.getInt(1), subject.id(), student.id(), student.joinedDate());
            out.add(new SubjectStats(subject.id(), subject.name(), attended, total));
        }
        return out;
    }

    public static Map<Long, List<Object[]>> recentSessionsBySubject(Connection c, long semesterId, int limit)
            throws SQLException {
        List<Object[]> rows = Db.query(c,
                "SELECT subject_id, session_date, period_no, id FROM class_sessions "
                        + "WHERE semester_id = ? ORDER BY session_date DESC, period_no DESC LIMIT ?",
                rs -> new Object[]{rs.getLong(1), rs.getDate(2).toString(), rs.getInt(3), rs.getLong(4)},
                semesterId, limit);
        Map<Long, List<Object[]>> bySubject = new LinkedHashMap<Long, List<Object[]>>();
        for (Object[] row : rows) {
            bySubject.computeIfAbsent((Long) row[0], k -> new ArrayList<Object[]>()).add(row);
        }
        return bySubject;
    }

}
