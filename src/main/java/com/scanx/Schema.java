package com.scanx;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

/**
 * The MySQL tables. Created automatically when the server starts.
 * The same statements are in sql/schema.sql if you want to run them by hand.
 *
 * Deleting a student also deletes everything that belongs to them
 * (login, visits, reminders, register marks) because of ON DELETE CASCADE.
 */
public final class Schema {

    private Schema() {
    }

    private static final String END = ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    public static final List<String> TABLES = Arrays.asList(

            "CREATE TABLE IF NOT EXISTS courses ("
                    + " id BIGINT NOT NULL AUTO_INCREMENT,"
                    + " name VARCHAR(60) NOT NULL,"
                    + " years INT NOT NULL,"
                    + " PRIMARY KEY (id),"
                    + " UNIQUE KEY uq_course_name (name)" + END,

            "CREATE TABLE IF NOT EXISTS semesters ("
                    + " id BIGINT NOT NULL AUTO_INCREMENT,"
                    + " course_id BIGINT NOT NULL,"
                    + " name VARCHAR(40) NOT NULL,"
                    + " year_no INT NOT NULL,"
                    + " PRIMARY KEY (id),"
                    + " UNIQUE KEY uq_semester (course_id, name),"
                    + " CONSTRAINT fk_sem_course FOREIGN KEY (course_id)"
                    + "   REFERENCES courses (id) ON DELETE CASCADE" + END,

            "CREATE TABLE IF NOT EXISTS subjects ("
                    + " id BIGINT NOT NULL AUTO_INCREMENT,"
                    + " semester_id BIGINT NOT NULL,"
                    + " name VARCHAR(80) NOT NULL,"
                    + " PRIMARY KEY (id),"
                    + " UNIQUE KEY uq_subject (semester_id, name),"
                    + " CONSTRAINT fk_sub_sem FOREIGN KEY (semester_id)"
                    + "   REFERENCES semesters (id) ON DELETE CASCADE" + END,

            "CREATE TABLE IF NOT EXISTS students ("
                    + " id VARCHAR(30) NOT NULL,"
                    + " name VARCHAR(80) NOT NULL,"
                    + " course_id BIGINT NOT NULL,"
                    + " semester_id BIGINT NOT NULL,"
                    + " face_descriptor TEXT NULL,"
                    + " face_enrolled_at DATETIME(3) NULL,"
                    + " joined_date DATE NOT NULL,"
                    + " PRIMARY KEY (id),"
                    + " INDEX idx_students_sem (semester_id),"
                    + " CONSTRAINT fk_stu_course FOREIGN KEY (course_id) REFERENCES courses (id),"
                    + " CONSTRAINT fk_stu_sem FOREIGN KEY (semester_id) REFERENCES semesters (id)" + END,

            "CREATE TABLE IF NOT EXISTS app_users ("
                    + " id BIGINT NOT NULL AUTO_INCREMENT,"
                    + " username VARCHAR(40) NOT NULL,"
                    + " password_hash VARCHAR(200) NOT NULL,"
                    + " role VARCHAR(10) NOT NULL,"
                    + " student_id VARCHAR(30) NULL,"
                    + " display_name VARCHAR(80) NOT NULL,"
                    + " PRIMARY KEY (id),"
                    + " UNIQUE KEY uq_users_username (username),"
                    + " CONSTRAINT fk_users_student FOREIGN KEY (student_id)"
                    + "   REFERENCES students (id) ON DELETE CASCADE" + END,

            "CREATE TABLE IF NOT EXISTS auth_tokens ("
                    + " token_hash CHAR(64) NOT NULL,"
                    + " user_id BIGINT NOT NULL,"
                    + " expires_at DATETIME(3) NOT NULL,"
                    + " PRIMARY KEY (token_hash),"
                    + " CONSTRAINT fk_tokens_user FOREIGN KEY (user_id)"
                    + "   REFERENCES app_users (id) ON DELETE CASCADE" + END,

            "CREATE TABLE IF NOT EXISTS college_timings ("
                    + " id BIGINT NOT NULL AUTO_INCREMENT,"
                    + " effective_from DATE NOT NULL,"
                    + " half_day_time VARCHAR(5) NOT NULL,"
                    + " course_id BIGINT NULL,"
                    + " year_no INT NULL,"
                    + " PRIMARY KEY (id),"
                    + " CONSTRAINT fk_timing_course FOREIGN KEY (course_id)"
                    + "   REFERENCES courses (id) ON DELETE CASCADE" + END,

            "CREATE TABLE IF NOT EXISTS calendar_days ("
                    + " id BIGINT NOT NULL AUTO_INCREMENT,"
                    + " day_date DATE NOT NULL,"
                    + " kind VARCHAR(10) NOT NULL,"
                    + " end_time VARCHAR(5) NULL,"
                    + " note VARCHAR(100) NULL,"
                    + " course_id BIGINT NULL,"
                    + " year_no INT NULL,"
                    + " PRIMARY KEY (id),"
                    + " INDEX idx_cal_date (day_date),"
                    + " CONSTRAINT fk_cal_course FOREIGN KEY (course_id)"
                    + "   REFERENCES courses (id) ON DELETE CASCADE" + END,

            "CREATE TABLE IF NOT EXISTS day_overrides ("
                    + " id BIGINT NOT NULL AUTO_INCREMENT,"
                    + " student_id VARCHAR(30) NOT NULL,"
                    + " day_date DATE NOT NULL,"
                    + " status VARCHAR(10) NOT NULL,"
                    + " set_by VARCHAR(40) NULL,"
                    + " PRIMARY KEY (id),"
                    + " UNIQUE KEY uq_override (student_id, day_date),"
                    + " CONSTRAINT fk_ovr_student FOREIGN KEY (student_id)"
                    + "   REFERENCES students (id) ON DELETE CASCADE" + END,

            "CREATE TABLE IF NOT EXISTS presence_records ("
                    + " id BIGINT NOT NULL AUTO_INCREMENT,"
                    + " student_id VARCHAR(30) NOT NULL,"
                    + " visit_date DATE NOT NULL,"
                    + " entry_time DATETIME(3) NOT NULL,"
                    + " exit_time DATETIME(3) NULL,"
                    + " duration_ms BIGINT NULL,"
                    + " incomplete TINYINT(1) NOT NULL DEFAULT 0,"
                    + " entry_method VARCHAR(20) NULL,"
                    + " exit_method VARCHAR(20) NULL,"
                    + " PRIMARY KEY (id),"
                    + " INDEX idx_presence_student (student_id, id),"
                    + " INDEX idx_presence_date (visit_date),"
                    + " CONSTRAINT fk_presence_student FOREIGN KEY (student_id)"
                    + "   REFERENCES students (id) ON DELETE CASCADE" + END,

            "CREATE TABLE IF NOT EXISTS scan_log ("
                    + " id BIGINT NOT NULL AUTO_INCREMENT,"
                    + " logged_at DATETIME(3) NOT NULL,"
                    + " student_id VARCHAR(30) NOT NULL,"
                    + " kind VARCHAR(10) NOT NULL,"
                    + " method VARCHAR(20) NULL,"
                    + " PRIMARY KEY (id),"
                    + " INDEX idx_log_student (student_id, id),"
                    + " CONSTRAINT fk_log_student FOREIGN KEY (student_id)"
                    + "   REFERENCES students (id) ON DELETE CASCADE" + END,

            "CREATE TABLE IF NOT EXISTS class_sessions ("
                    + " id BIGINT NOT NULL AUTO_INCREMENT,"
                    + " semester_id BIGINT NOT NULL,"
                    + " subject_id BIGINT NOT NULL,"
                    + " session_date DATE NOT NULL,"
                    + " period_no INT NOT NULL,"
                    + " taken_by VARCHAR(80) NULL,"
                    + " taken_at DATETIME(3) NOT NULL,"
                    + " PRIMARY KEY (id),"
                    + " UNIQUE KEY uq_session (subject_id, session_date, period_no),"
                    + " INDEX idx_session_date (session_date),"
                    + " CONSTRAINT fk_sess_sem FOREIGN KEY (semester_id)"
                    + "   REFERENCES semesters (id) ON DELETE CASCADE,"
                    + " CONSTRAINT fk_sess_subject FOREIGN KEY (subject_id)"
                    + "   REFERENCES subjects (id) ON DELETE CASCADE" + END,

            "CREATE TABLE IF NOT EXISTS session_marks ("
                    + " id BIGINT NOT NULL AUTO_INCREMENT,"
                    + " session_id BIGINT NOT NULL,"
                    + " student_id VARCHAR(30) NOT NULL,"
                    + " PRIMARY KEY (id),"
                    + " UNIQUE KEY uq_mark (session_id, student_id),"
                    + " CONSTRAINT fk_marks_session FOREIGN KEY (session_id)"
                    + "   REFERENCES class_sessions (id) ON DELETE CASCADE,"
                    + " CONSTRAINT fk_marks_student FOREIGN KEY (student_id)"
                    + "   REFERENCES students (id) ON DELETE CASCADE" + END,

            "CREATE TABLE IF NOT EXISTS academic_events ("
                    + " id BIGINT NOT NULL AUTO_INCREMENT,"
                    + " title VARCHAR(120) NOT NULL,"
                    + " event_type VARCHAR(30) NOT NULL,"
                    + " event_date DATE NOT NULL,"
                    + " event_time VARCHAR(5) NULL,"
                    + " location VARCHAR(120) NULL,"
                    + " audience_course_id BIGINT NULL,"
                    + " audience_year INT NULL,"
                    + " created_at DATETIME(3) NOT NULL,"
                    + " PRIMARY KEY (id),"
                    + " INDEX idx_events_date (event_date),"
                    + " CONSTRAINT fk_event_course FOREIGN KEY (audience_course_id)"
                    + "   REFERENCES courses (id) ON DELETE CASCADE" + END,

            "CREATE TABLE IF NOT EXISTS reminders ("
                    + " id BIGINT NOT NULL AUTO_INCREMENT,"
                    + " student_id VARCHAR(30) NOT NULL,"
                    + " reminder_text VARCHAR(200) NOT NULL,"
                    + " remind_date DATE NULL,"
                    + " done TINYINT(1) NOT NULL DEFAULT 0,"
                    + " created_at DATETIME(3) NOT NULL,"
                    + " PRIMARY KEY (id),"
                    + " INDEX idx_reminders_student (student_id),"
                    + " CONSTRAINT fk_reminders_student FOREIGN KEY (student_id)"
                    + "   REFERENCES students (id) ON DELETE CASCADE" + END,

            "CREATE TABLE IF NOT EXISTS app_settings ("
                    + " setting_key VARCHAR(50) NOT NULL,"
                    + " setting_value VARCHAR(100) NULL,"
                    + " PRIMARY KEY (setting_key)" + END
    );

    /**
     * An older SCANX database (from the first version) has tables that do not fit any more.
     * Stop with a clear message instead of failing later in strange ways.
     */
    public static void checkOldVersion(Connection c) throws SQLException {
        boolean studentsExist;
        try {
            Db.exists(c, "SELECT 1 FROM students LIMIT 1");
            studentsExist = true;
        } catch (SQLException e) {
            studentsExist = false;
        }
        if (!studentsExist) {
            return;
        }
        try {
            Db.exists(c, "SELECT course_id FROM students LIMIT 1");
        } catch (SQLException e) {
            throw new SQLException("This MySQL database belongs to the first version of SCANX. "
                    + "In MySQL Workbench run:  DROP DATABASE scanx;  then start the server again.");
        }
    }

    /** Creates every table that does not exist yet. */
    public static void create(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            for (String sql : TABLES) {
                st.execute(sql);
            }
        }
    }

}
