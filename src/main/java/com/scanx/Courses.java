package com.scanx;

import com.scanx.Models.Course;
import com.scanx.Models.Semester;
import com.scanx.Models.Subject;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Courses (BCA, BSc ...), their semesters and subjects. The teacher edits these in Settings. */
public final class Courses {

    private Courses() {
    }

    static final Db.RowMapper<Course> COURSE = rs ->
            new Course(rs.getLong("id"), rs.getString("name"), rs.getInt("years"));

    static final Db.RowMapper<Semester> SEMESTER = rs ->
            new Semester(rs.getLong("id"), rs.getLong("course_id"), rs.getString("name"), rs.getInt("year_no"));

    static final Db.RowMapper<Subject> SUBJECT = rs ->
            new Subject(rs.getLong("id"), rs.getLong("semester_id"), rs.getString("name"));

    public static List<Course> courses(Connection c) throws SQLException {
        return Db.query(c, "SELECT * FROM courses ORDER BY name", COURSE);
    }

    public static List<Semester> semesters(Connection c) throws SQLException {
        return Db.query(c, "SELECT * FROM semesters ORDER BY course_id, year_no, name", SEMESTER);
    }

    public static List<Subject> subjects(Connection c) throws SQLException {
        return Db.query(c, "SELECT * FROM subjects ORDER BY semester_id, name", SUBJECT);
    }

    public static Course course(Connection c, long id) throws SQLException {
        return Db.queryOne(c, "SELECT * FROM courses WHERE id = ?", COURSE, id);
    }

    public static Semester semester(Connection c, long id) throws SQLException {
        return Db.queryOne(c, "SELECT * FROM semesters WHERE id = ?", SEMESTER, id);
    }

    public static Subject subject(Connection c, long id) throws SQLException {
        return Db.queryOne(c, "SELECT * FROM subjects WHERE id = ?", SUBJECT, id);
    }

    private static String cleanName(String name, int max, String what) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > max) {
            throw ApiException.badRequest("Enter " + what + " (up to " + max + " characters).");
        }
        return clean;
    }

    // ---------- courses ----------

    public static long addCourse(Connection c, String name, int years) throws SQLException {
        String clean = cleanName(name, 60, "the course name");
        if (years < 1 || years > 6) {
            throw ApiException.badRequest("A course can have 1 to 6 years.");
        }
        if (Db.exists(c, "SELECT 1 FROM courses WHERE name = ?", clean)) {
            throw ApiException.conflict("DUPLICATE", "That course already exists.");
        }
        return Db.insert(c, "INSERT INTO courses (name, years) VALUES (?, ?)", clean, years);
    }

    public static void updateCourse(Connection c, long id, String name, int years) throws SQLException {
        String clean = cleanName(name, 60, "the course name");
        if (years < 1 || years > 6) {
            throw ApiException.badRequest("A course can have 1 to 6 years.");
        }
        if (course(c, id) == null) {
            throw ApiException.notFound("That course was not found.");
        }
        if (Db.exists(c, "SELECT 1 FROM courses WHERE name = ? AND id <> ?", clean, id)) {
            throw ApiException.conflict("DUPLICATE", "That course already exists.");
        }
        Db.update(c, "UPDATE courses SET name = ?, years = ? WHERE id = ?", clean, years, id);
    }

    public static void deleteCourse(Connection c, long id) throws SQLException {
        if (course(c, id) == null) {
            throw ApiException.notFound("That course was not found.");
        }
        if (Db.exists(c, "SELECT 1 FROM students WHERE course_id = ? LIMIT 1", id)) {
            throw ApiException.conflict("IN_USE", "Students belong to this course. Move or remove them first.");
        }
        Db.update(c, "DELETE FROM courses WHERE id = ?", id);
    }

    // ---------- semesters ----------

    public static long addSemester(Connection c, long courseId, String name, int yearNo) throws SQLException {
        String clean = cleanName(name, 40, "the semester name");
        Course course = course(c, courseId);
        if (course == null) {
            throw ApiException.notFound("That course was not found.");
        }
        if (yearNo < 1 || yearNo > course.years()) {
            throw ApiException.badRequest("The year must be between 1 and " + course.years() + ".");
        }
        if (Db.exists(c, "SELECT 1 FROM semesters WHERE course_id = ? AND name = ?", courseId, clean)) {
            throw ApiException.conflict("DUPLICATE", "That semester already exists in this course.");
        }
        return Db.insert(c, "INSERT INTO semesters (course_id, name, year_no) VALUES (?, ?, ?)",
                courseId, clean, yearNo);
    }

    public static void updateSemester(Connection c, long id, String name, int yearNo) throws SQLException {
        String clean = cleanName(name, 40, "the semester name");
        Semester semester = semester(c, id);
        if (semester == null) {
            throw ApiException.notFound("That semester was not found.");
        }
        Course course = course(c, semester.courseId());
        if (yearNo < 1 || yearNo > course.years()) {
            throw ApiException.badRequest("The year must be between 1 and " + course.years() + ".");
        }
        if (Db.exists(c, "SELECT 1 FROM semesters WHERE course_id = ? AND name = ? AND id <> ?",
                course.id(), clean, id)) {
            throw ApiException.conflict("DUPLICATE", "That semester already exists in this course.");
        }
        Db.update(c, "UPDATE semesters SET name = ?, year_no = ? WHERE id = ?", clean, yearNo, id);
    }

    public static void deleteSemester(Connection c, long id) throws SQLException {
        if (semester(c, id) == null) {
            throw ApiException.notFound("That semester was not found.");
        }
        if (Db.exists(c, "SELECT 1 FROM students WHERE semester_id = ? LIMIT 1", id)) {
            throw ApiException.conflict("IN_USE", "Students are in this semester. Move or remove them first.");
        }
        Db.update(c, "DELETE FROM semesters WHERE id = ?", id);
    }

    // ---------- subjects ----------

    public static long addSubject(Connection c, long semesterId, String name) throws SQLException {
        String clean = cleanName(name, 80, "the subject name");
        if (semester(c, semesterId) == null) {
            throw ApiException.notFound("That semester was not found.");
        }
        if (Db.exists(c, "SELECT 1 FROM subjects WHERE semester_id = ? AND name = ?", semesterId, clean)) {
            throw ApiException.conflict("DUPLICATE", "That subject already exists in this semester.");
        }
        return Db.insert(c, "INSERT INTO subjects (semester_id, name) VALUES (?, ?)", semesterId, clean);
    }

    public static void updateSubject(Connection c, long id, String name) throws SQLException {
        String clean = cleanName(name, 80, "the subject name");
        Subject subject = subject(c, id);
        if (subject == null) {
            throw ApiException.notFound("That subject was not found.");
        }
        if (Db.exists(c, "SELECT 1 FROM subjects WHERE semester_id = ? AND name = ? AND id <> ?",
                subject.semesterId(), clean, id)) {
            throw ApiException.conflict("DUPLICATE", "That subject already exists in this semester.");
        }
        Db.update(c, "UPDATE subjects SET name = ? WHERE id = ?", clean, id);
    }

    public static void deleteSubject(Connection c, long id) throws SQLException {
        if (subject(c, id) == null) {
            throw ApiException.notFound("That subject was not found.");
        }
        if (Db.exists(c, "SELECT 1 FROM class_sessions WHERE subject_id = ? LIMIT 1", id)) {
            throw ApiException.conflict("IN_USE", "Attendance was already taken for this subject, so it cannot be removed.");
        }
        Db.update(c, "DELETE FROM subjects WHERE id = ?", id);
    }

}
