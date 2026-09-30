package com.scanx;

import com.scanx.Models.AcademicEvent;
import com.scanx.Models.CalendarDay;
import com.scanx.Models.Course;
import com.scanx.Models.CurrentUser;
import com.scanx.Models.Semester;
import com.scanx.Models.Student;
import com.scanx.Models.Subject;
import com.scanx.Models.Timing;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The "bootstrap" data every page needs once it opens: who is signed in,
 * the course/semester/subject structure, settings, the calendar, and events.
 * Attendance numbers are fetched separately (they can be large), see StudentApi and AdminApi.
 */
public final class Snapshot {

    private Snapshot() {
    }

    public static Map<String, Object> build(Connection c, CurrentUser user) throws SQLException {

        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("version", 2);
        out.put("serverTime", Times.iso(Times.now()));
        out.put("timezone", Times.zoneName());
        out.put("role", user.role());
        out.put("username", user.username());
        out.put("studentId", user.studentId());
        out.put("displayName", Auth.displayName(c, user));

        List<Map<String, Object>> courses = new ArrayList<Map<String, Object>>();
        for (Course course : Courses.courses(c)) {
            courses.add(courseMap(course));
        }
        out.put("courses", courses);

        List<Map<String, Object>> semesters = new ArrayList<Map<String, Object>>();
        for (Semester semester : Courses.semesters(c)) {
            semesters.add(semesterMap(semester));
        }
        out.put("semesters", semesters);

        List<Map<String, Object>> subjects = new ArrayList<Map<String, Object>>();
        for (Subject subject : Courses.subjects(c)) {
            subjects.add(subjectMap(subject));
        }
        out.put("subjects", subjects);

        Map<String, Object> settings = new LinkedHashMap<String, Object>();
        settings.put("targetPercent", Settings.TARGET_PERCENT);
        List<Integer> workingDays = new ArrayList<Integer>(Settings.workingDays(c));
        java.util.Collections.sort(workingDays);
        settings.put("workingDays", workingDays);
        out.put("settings", settings);

        List<Map<String, Object>> timings = new ArrayList<Map<String, Object>>();
        for (Timing timing : Timings.allTimings(c)) {
            timings.add(timingMap(timing));
        }
        out.put("timings", timings);

        LocalDate from = Times.today().minusMonths(2);
        LocalDate to = Times.today().plusMonths(3);
        List<Map<String, Object>> calendarDays = new ArrayList<Map<String, Object>>();
        for (CalendarDay day : Timings.calendarBetween(c, from, to)) {
            calendarDays.add(calendarMap(day));
        }
        out.put("calendarDays", calendarDays);

        Student me = user.studentId() == null ? null : Students.find(c, user.studentId());
        List<Map<String, Object>> events = new ArrayList<Map<String, Object>>();
        for (AcademicEvent event : Events.list(c, me == null ? null : me.courseId(), me == null ? null : me.yearNo())) {
            events.add(eventMap(event));
        }
        out.put("events", events);

        if (me != null) {
            out.put("me", studentMap(me));
        }

        return out;
    }

    // =====================================================
    //  Small converters (also used by the API routes)
    // =====================================================

    public static Map<String, Object> courseMap(Course c) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", c.id());
        m.put("name", c.name());
        m.put("years", c.years());
        return m;
    }

    public static Map<String, Object> semesterMap(Semester s) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", s.id());
        m.put("courseId", s.courseId());
        m.put("name", s.name());
        m.put("yearNo", s.yearNo());
        return m;
    }

    public static Map<String, Object> subjectMap(Subject s) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", s.id());
        m.put("semesterId", s.semesterId());
        m.put("name", s.name());
        return m;
    }

    public static Map<String, Object> studentMap(Student s) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", s.id());
        m.put("name", s.name());
        m.put("courseId", s.courseId());
        m.put("courseName", s.courseName());
        m.put("semesterId", s.semesterId());
        m.put("semesterName", s.semesterName());
        m.put("yearNo", s.yearNo());
        m.put("hasFace", s.faceDescriptor() != null);
        m.put("faceEnrolledAt", Times.iso(s.faceEnrolledAt()));
        m.put("joinedDate", s.joinedDate().toString());
        return m;
    }

    public static Map<String, Object> eventMap(AcademicEvent e) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", e.id());
        m.put("title", e.title());
        m.put("type", e.type());
        m.put("date", e.date().toString());
        m.put("time", e.time() == null ? "" : e.time());
        m.put("location", e.location() == null ? "" : e.location());
        m.put("audienceCourseId", e.courseId());
        m.put("audienceYear", e.yearNo());
        return m;
    }

    public static Map<String, Object> timingMap(Timing t) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", t.id());
        m.put("effectiveFrom", t.effectiveFrom().toString());
        m.put("halfDayTime", t.halfDayTime());
        m.put("courseId", t.courseId());
        m.put("yearNo", t.yearNo());
        return m;
    }

    public static Map<String, Object> calendarMap(CalendarDay d) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", d.id());
        m.put("date", d.date().toString());
        m.put("kind", d.kind());
        m.put("endTime", d.endTime());
        m.put("note", d.note());
        m.put("courseId", d.courseId());
        m.put("yearNo", d.yearNo());
        return m;
    }

}
