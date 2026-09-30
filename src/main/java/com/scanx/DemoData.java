package com.scanx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * The admin account, and (in demo mode) one course with sample students and staff,
 * so the site is not empty the first time you open it.
 */
public final class DemoData {

    private DemoData() {
    }

    private static final String[][] STUDENTS = {
            {"ST001", "Student 01", "Semester 5"},
            {"ST002", "Student 02", "Semester 5"},
            {"ST003", "Student 03", "Semester 5"},
            {"ST004", "Student 04", "Semester 3"},
            {"ST005", "Student 05", "Semester 4"},
            {"ST006", "Student 06", "Semester 1"}
    };

    private static final String[][] SEMESTERS = {
            {"Semester 1", "1"}, {"Semester 2", "1"},
            {"Semester 3", "2"}, {"Semester 4", "2"},
            {"Semester 5", "3"}, {"Semester 6", "3"}
    };

    private static final String[][] SUBJECTS = {
            {"Semester 5", "AI"}, {"Semester 5", "Cyber Security & Internet Security"},
            {"Semester 5", "Data Analytics"}, {"Semester 5", "Computer Network"},
            {"Semester 5", "Software Testing"},
            {"Semester 3", "OOPs Using Java"}, {"Semester 3", "Software Engineering"},
            {"Semester 4", "Advanced Java Technology"},
            {"Semester 1", "Soft Skills"}, {"Semester 1", "Programming with C"}
    };

    /** Creates the admin login the first time the server starts. */
    public static void ensureAdmin(Connection c) throws SQLException {
        if (!Auth.adminExists(c)) {
            Auth.createUser(c, Config.ADMIN_USER, Config.ADMIN_PASSWORD, "ADMIN", null, "Admin");
            System.out.println("Admin login created: " + Config.ADMIN_USER
                    + "  (change the password in Config.java before real use)");
        }
    }

    public static void seedIfEmpty(Connection c) throws SQLException {
        if (Db.exists(c, "SELECT 1 FROM courses LIMIT 1")) {
            return;
        }
        seed(c);
        System.out.println("Demo data added: BCA course, 6 students (ST001 to ST006, password "
                + Config.DEFAULT_STUDENT_PASSWORD + "), a teacher login (teacher / " + Config.DEFAULT_STUDENT_PASSWORD + ").");
    }

    /** Removes everything and adds the sample data again. */
    public static void reset(Connection c) throws SQLException {
        /* Students first: this cascades away their logins, visits, register marks,
           reminders, overrides and scan history. Courses do NOT cascade-delete
           students (see Courses.deleteCourse), so this order matters. */
        Db.update(c, "DELETE FROM students");
        Db.update(c, "DELETE FROM app_users WHERE role <> 'ADMIN'");
        Db.update(c, "DELETE FROM courses"); // cascades semesters, subjects, class_sessions, session_marks
        Db.update(c, "DELETE FROM academic_events");
        Db.update(c, "DELETE FROM app_settings");
        Db.update(c, "DELETE FROM college_timings");
        Db.update(c, "DELETE FROM calendar_days");
        seed(c);
    }

    private static void seed(Connection c) throws SQLException {

        long courseId = Courses.addCourse(c, "BCA", 3);

        java.util.Map<String, Long> semesterIds = new java.util.HashMap<String, Long>();
        for (String[] row : SEMESTERS) {
            long id = Courses.addSemester(c, courseId, row[0], Integer.parseInt(row[1]));
            semesterIds.put(row[0], id);
        }

        for (String[] row : SUBJECTS) {
            Courses.addSubject(c, semesterIds.get(row[0]), row[1]);
        }

        LocalDate joined = Times.today().minusMonths(3);
        for (String[] row : STUDENTS) {
            Students.add(c, row[0], row[1], courseId, semesterIds.get(row[2]), joined.toString(),
                    Config.DEFAULT_STUDENT_PASSWORD);
        }

        Auth.createUser(c, "teacher", Config.DEFAULT_STUDENT_PASSWORD, "TEACHER", null, "Class Teacher");

        LocalDate today = Times.today();
        Events.create(c, "Computer Network Test", "Test", today.plusDays(2).toString(), "10:00",
                "Room 204", courseId, 3);
        Events.create(c, "Software Testing Assignment", "Assignment", today.plusDays(5).toString(), "23:59",
                "Submission deadline", null, null);
        Events.create(c, "AI Workshop", "Workshop", today.plusDays(9).toString(), "14:00",
                "Seminar Hall", null, null);

        Reminders.add(c, "ST001", "Revise OSI model", today.toString());
        Reminders.add(c, "ST001", "Prepare Computer Network assignment", today.plusDays(1).toString());

        /* A few days of gate scans so attendance is not empty */
        for (int back = 6; back >= 1; back--) {
            LocalDate day = today.minusDays(back);
            if (!Timings.isWorkingDay(c, day, courseId, 3)) {
                continue;
            }
            java.time.Instant morning = Times.at(day, java.time.LocalTime.of(8, 30 + back));
            java.time.Instant evening = Times.at(day, back == 3 ? java.time.LocalTime.of(12, 0)
                    : java.time.LocalTime.of(15, 30));
            for (String[] row : new String[][]{STUDENTS[0], STUDENTS[1]}) {
                Db.update(c,
                        "INSERT INTO presence_records (student_id, visit_date, entry_time, exit_time, "
                                + "duration_ms, incomplete, entry_method, exit_method) VALUES (?, ?, ?, ?, ?, 0, 'qr', 'qr')",
                        row[0], day, morning,
                        evening, evening.toEpochMilli() - morning.toEpochMilli());
            }
        }
    }

}
