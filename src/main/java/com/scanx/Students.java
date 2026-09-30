package com.scanx;

import com.scanx.Models.Semester;
import com.scanx.Models.Student;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

/** Students: add, edit, remove, face templates, and reading a scanned code. */
public final class Students {

    private Students() {
    }

    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Z0-9._-]{1,30}$");

    private static final String SELECT =
            "SELECT s.id, s.name, s.course_id, c.name AS course_name, s.semester_id, "
                    + "m.name AS semester_name, m.year_no, s.face_descriptor, s.face_enrolled_at, s.joined_date "
                    + "FROM students s JOIN courses c ON c.id = s.course_id "
                    + "JOIN semesters m ON m.id = s.semester_id ";

    static final Db.RowMapper<Student> MAPPER = rs -> new Student(
            rs.getString("id"), rs.getString("name"), rs.getLong("course_id"), rs.getString("course_name"),
            rs.getLong("semester_id"), rs.getString("semester_name"), rs.getInt("year_no"),
            rs.getString("face_descriptor"), Db.instant(rs, "face_enrolled_at"), Db.date(rs, "joined_date"));

    public static List<Student> all(Connection c) throws SQLException {
        return Db.query(c, SELECT + "ORDER BY s.id", MAPPER);
    }

    public static List<Student> inSemester(Connection c, long semesterId) throws SQLException {
        return Db.query(c, SELECT + "WHERE s.semester_id = ? ORDER BY s.id", MAPPER, semesterId);
    }

    public static Student find(Connection c, String id) throws SQLException {
        return id == null ? null : Db.queryOne(c, SELECT + "WHERE s.id = ?", MAPPER, id);
    }

    public static Student require(Connection c, String id) throws SQLException {
        Student student = find(c, id);
        if (student == null) {
            throw ApiException.notFound("That student is not registered.");
        }
        return student;
    }

    private static LocalDate parseDate(String text, LocalDate fallback) {
        if (Util.isBlank(text)) {
            return fallback;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("The date is not valid.");
        }
    }

    private static void checkClass(Connection c, long courseId, long semesterId) throws SQLException {
        Semester semester = Courses.semester(c, semesterId);
        if (semester == null || semester.courseId() != courseId) {
            throw ApiException.badRequest("Choose a course and one of its semesters.");
        }
    }

    private static String cleanName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > 80) {
            throw ApiException.badRequest("Enter the student's name (up to 80 characters).");
        }
        return name;
    }

    /** Adds the student and a login for them (username = student ID). */
    public static Student add(Connection c, String rawId, String rawName, long courseId, long semesterId,
                              String startDate, String password) throws SQLException {

        String id = rawId == null ? "" : rawId.trim().toUpperCase();
        String name = cleanName(rawName);

        if (!ID_PATTERN.matcher(id).matches()) {
            throw ApiException.badRequest(
                    "Student ID can use letters, numbers, . - and _ (up to 30 characters).");
        }
        checkClass(c, courseId, semesterId);
        LocalDate joined = parseDate(startDate, Times.today());

        if (find(c, id) != null || Auth.findUser(c, id) != null) {
            throw ApiException.conflict("DUPLICATE", id + " already exists.");
        }

        String initialPassword = Util.isBlank(password) ? Config.DEFAULT_STUDENT_PASSWORD : password;
        if (initialPassword.length() < 4) {
            throw ApiException.badRequest("The password needs at least 4 characters.");
        }

        Db.update(c,
                "INSERT INTO students (id, name, course_id, semester_id, joined_date) VALUES (?, ?, ?, ?, ?)",
                id, name, courseId, semesterId, joined);

        Auth.createUser(c, id, initialPassword, "STUDENT", id, name);

        return find(c, id);
    }

    public static Student update(Connection c, String id, String rawName, long courseId, long semesterId,
                                 String startDate) throws SQLException {
        Student existing = require(c, id);
        String name = cleanName(rawName);
        checkClass(c, courseId, semesterId);
        LocalDate joined = parseDate(startDate, existing.joinedDate());

        Db.update(c, "UPDATE students SET name = ?, course_id = ?, semester_id = ?, joined_date = ? WHERE id = ?",
                name, courseId, semesterId, joined, id);
        Db.update(c, "UPDATE app_users SET display_name = ? WHERE student_id = ?", name, id);
        return find(c, id);
    }

    /** Removes the student. MySQL removes their login, visits, marks and reminders too. */
    public static void remove(Connection c, String id) throws SQLException {
        require(c, id);
        Db.update(c, "DELETE FROM students WHERE id = ?", id);
    }

    /**
     * Saves a face template.
     * onlyIfMissing = true is used when a student enrolls themselves:
     * changing an existing face is left to the teacher.
     */
    public static void saveFace(Connection c, String id, List<Double> descriptor, boolean onlyIfMissing)
            throws SQLException {

        Faces.validate(descriptor);
        Student student = require(c, id);

        if (onlyIfMissing && student.faceDescriptor() != null) {
            throw ApiException.conflict("FACE_EXISTS",
                    "Your face is already enrolled. Ask your teacher to enroll it again.");
        }

        Db.update(c, "UPDATE students SET face_descriptor = ?, face_enrolled_at = ? WHERE id = ?",
                Faces.toText(descriptor), Times.now(), id);
    }

    public static void resetPassword(Connection c, String id, String newPassword) throws SQLException {
        require(c, id);

        if (newPassword == null || newPassword.length() < 4) {
            throw ApiException.badRequest("The password needs at least 4 characters.");
        }

        Models.AppUser user = Auth.findStudentUser(c, id);
        if (user == null) {
            throw ApiException.notFound("That student has no login.");
        }

        Auth.setPassword(c, user.id(), newPassword);
    }

    /**
     * QR or barcode text to student.
     * Accepts "SCANX:12345", "12345", or ID-card text that contains exactly one student ID.
     */
    public static Student resolveFromCode(Connection c, String raw) throws SQLException {
        if (raw == null) {
            return null;
        }

        String code = raw.trim();
        if (code.isEmpty() || code.length() > 300) {
            return null;
        }

        String stripped = code.replaceFirst("(?i)^SCANX[:|\\s-]*", "").trim().toUpperCase();

        Student exact = find(c, stripped);
        if (exact != null) {
            return exact;
        }

        String upper = code.toUpperCase();
        Student found = null;
        int hits = 0;

        for (Student student : all(c)) {
            Pattern pattern = Pattern.compile(
                    "(^|[^A-Z0-9])" + Pattern.quote(student.id().toUpperCase()) + "($|[^A-Z0-9])");
            if (pattern.matcher(upper).find()) {
                found = student;
                hits++;
            }
        }

        return hits == 1 ? found : null;
    }

}
