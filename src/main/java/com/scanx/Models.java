package com.scanx;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The plain data objects passed around the server.
 * Each field is read with a method of the same name, for example student.name().
 */
public final class Models {

    private Models() {
    }

    public static final class Course {
        private final long id;
        private final String name;
        private final int years;

        public Course(long id, String name, int years) {
            this.id = id;
            this.name = name;
            this.years = years;
        }

        public long id() {
            return id;
        }

        public String name() {
            return name;
        }

        public int years() {
            return years;
        }
    }

    public static final class Semester {
        private final long id;
        private final long courseId;
        private final String name;
        private final int yearNo;

        public Semester(long id, long courseId, String name, int yearNo) {
            this.id = id;
            this.courseId = courseId;
            this.name = name;
            this.yearNo = yearNo;
        }

        public long id() {
            return id;
        }

        public long courseId() {
            return courseId;
        }

        public String name() {
            return name;
        }

        public int yearNo() {
            return yearNo;
        }
    }

    public static final class Subject {
        private final long id;
        private final long semesterId;
        private final String name;

        public Subject(long id, long semesterId, String name) {
            this.id = id;
            this.semesterId = semesterId;
            this.name = name;
        }

        public long id() {
            return id;
        }

        public long semesterId() {
            return semesterId;
        }

        public String name() {
            return name;
        }
    }

    public static final class Student {
        private final String id;
        private final String name;
        private final long courseId;
        private final String courseName;
        private final long semesterId;
        private final String semesterName;
        private final int yearNo;
        private final String faceDescriptor;
        private final Instant faceEnrolledAt;
        private final LocalDate joinedDate;

        public Student(String id, String name, long courseId, String courseName, long semesterId, String semesterName, int yearNo, String faceDescriptor, Instant faceEnrolledAt, LocalDate joinedDate) {
            this.id = id;
            this.name = name;
            this.courseId = courseId;
            this.courseName = courseName;
            this.semesterId = semesterId;
            this.semesterName = semesterName;
            this.yearNo = yearNo;
            this.faceDescriptor = faceDescriptor;
            this.faceEnrolledAt = faceEnrolledAt;
            this.joinedDate = joinedDate;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public long courseId() {
            return courseId;
        }

        public String courseName() {
            return courseName;
        }

        public long semesterId() {
            return semesterId;
        }

        public String semesterName() {
            return semesterName;
        }

        public int yearNo() {
            return yearNo;
        }

        public String faceDescriptor() {
            return faceDescriptor;
        }

        public Instant faceEnrolledAt() {
            return faceEnrolledAt;
        }

        public LocalDate joinedDate() {
            return joinedDate;
        }
    }

    public static final class AppUser {
        private final long id;
        private final String username;
        private final String passwordHash;
        private final String role;
        private final String studentId;
        private final String displayName;

        public AppUser(long id, String username, String passwordHash, String role, String studentId, String displayName) {
            this.id = id;
            this.username = username;
            this.passwordHash = passwordHash;
            this.role = role;
            this.studentId = studentId;
            this.displayName = displayName;
        }

        public long id() {
            return id;
        }

        public String username() {
            return username;
        }

        public String passwordHash() {
            return passwordHash;
        }

        public String role() {
            return role;
        }

        public String studentId() {
            return studentId;
        }

        public String displayName() {
            return displayName;
        }
    }

    public static final class PresenceRecord {
        private final long id;
        private final String studentId;
        private final LocalDate visitDate;
        private final Instant entry;
        private final Instant exit;
        private final Long durationMs;
        private final boolean incomplete;

        public PresenceRecord(long id, String studentId, LocalDate visitDate, Instant entry, Instant exit, Long durationMs, boolean incomplete) {
            this.id = id;
            this.studentId = studentId;
            this.visitDate = visitDate;
            this.entry = entry;
            this.exit = exit;
            this.durationMs = durationMs;
            this.incomplete = incomplete;
        }

        public long id() {
            return id;
        }

        public String studentId() {
            return studentId;
        }

        public LocalDate visitDate() {
            return visitDate;
        }

        public Instant entry() {
            return entry;
        }

        public Instant exit() {
            return exit;
        }

        public Long durationMs() {
            return durationMs;
        }

        public boolean incomplete() {
            return incomplete;
        }
    }

    public static final class AcademicEvent {
        private final long id;
        private final String title;
        private final String type;
        private final LocalDate date;
        private final String time;
        private final String location;
        private final Long courseId;
        private final Integer yearNo;

        public AcademicEvent(long id, String title, String type, LocalDate date, String time, String location, Long courseId, Integer yearNo) {
            this.id = id;
            this.title = title;
            this.type = type;
            this.date = date;
            this.time = time;
            this.location = location;
            this.courseId = courseId;
            this.yearNo = yearNo;
        }

        public long id() {
            return id;
        }

        public String title() {
            return title;
        }

        public String type() {
            return type;
        }

        public LocalDate date() {
            return date;
        }

        public String time() {
            return time;
        }

        public String location() {
            return location;
        }

        public Long courseId() {
            return courseId;
        }

        public Integer yearNo() {
            return yearNo;
        }
    }

    public static final class Reminder {
        private final long id;
        private final String studentId;
        private final String text;
        private final LocalDate date;
        private final boolean done;

        public Reminder(long id, String studentId, String text, LocalDate date, boolean done) {
            this.id = id;
            this.studentId = studentId;
            this.text = text;
            this.date = date;
            this.done = done;
        }

        public long id() {
            return id;
        }

        public String studentId() {
            return studentId;
        }

        public String text() {
            return text;
        }

        public LocalDate date() {
            return date;
        }

        public boolean done() {
            return done;
        }
    }

    public static final class ScanLogEntry {
        private final long id;
        private final Instant time;
        private final String studentId;
        private final String kind;
        private final String method;

        public ScanLogEntry(long id, Instant time, String studentId, String kind, String method) {
            this.id = id;
            this.time = time;
            this.studentId = studentId;
            this.kind = kind;
            this.method = method;
        }

        public long id() {
            return id;
        }

        public Instant time() {
            return time;
        }

        public String studentId() {
            return studentId;
        }

        public String kind() {
            return kind;
        }

        public String method() {
            return method;
        }
    }

    public static final class Timing {
        private final long id;
        private final LocalDate effectiveFrom;
        private final String halfDayTime;
        private final Long courseId;
        private final Integer yearNo;

        public Timing(long id, LocalDate effectiveFrom, String halfDayTime, Long courseId, Integer yearNo) {
            this.id = id;
            this.effectiveFrom = effectiveFrom;
            this.halfDayTime = halfDayTime;
            this.courseId = courseId;
            this.yearNo = yearNo;
        }

        public long id() {
            return id;
        }

        public LocalDate effectiveFrom() {
            return effectiveFrom;
        }

        public String halfDayTime() {
            return halfDayTime;
        }

        public Long courseId() {
            return courseId;
        }

        public Integer yearNo() {
            return yearNo;
        }
    }

    public static final class CalendarDay {
        private final long id;
        private final LocalDate date;
        private final String kind;
        private final String endTime;
        private final String note;
        private final Long courseId;
        private final Integer yearNo;

        public CalendarDay(long id, LocalDate date, String kind, String endTime, String note, Long courseId, Integer yearNo) {
            this.id = id;
            this.date = date;
            this.kind = kind;
            this.endTime = endTime;
            this.note = note;
            this.courseId = courseId;
            this.yearNo = yearNo;
        }

        public long id() {
            return id;
        }

        public LocalDate date() {
            return date;
        }

        public String kind() {
            return kind;
        }

        public String endTime() {
            return endTime;
        }

        public String note() {
            return note;
        }

        public Long courseId() {
            return courseId;
        }

        public Integer yearNo() {
            return yearNo;
        }
    }

    public static final class CurrentUser {
        private final long userId;
        private final String role;
        private final String studentId;
        private final String username;

        public CurrentUser(long userId, String role, String studentId, String username) {
            this.userId = userId;
            this.role = role;
            this.studentId = studentId;
            this.username = username;
        }

        public long userId() {
            return userId;
        }

        public String role() {
            return role;
        }

        public String studentId() {
            return studentId;
        }

        public String username() {
            return username;
        }

        public boolean isAdmin() {
            return "ADMIN".equals(role);
        }

        /** Teachers and admins. */
        public boolean isStaff() {
            return "ADMIN".equals(role) || "TEACHER".equals(role);
        }
    }

    public static final class ActionResult {
        private final boolean ok;
        private final String code;
        private final String message;
        private final String action;
        private final String studentId;
        private final String studentName;
        private final String time;
        private final Long durationMs;

        public ActionResult(boolean ok, String code, String message, String action, String studentId, String studentName, String time, Long durationMs) {
            this.ok = ok;
            this.code = code;
            this.message = message;
            this.action = action;
            this.studentId = studentId;
            this.studentName = studentName;
            this.time = time;
            this.durationMs = durationMs;
        }

        public boolean ok() {
            return ok;
        }

        public String code() {
            return code;
        }

        public String message() {
            return message;
        }

        public String action() {
            return action;
        }

        public String studentId() {
            return studentId;
        }

        public String studentName() {
            return studentName;
        }

        public String time() {
            return time;
        }

        public Long durationMs() {
            return durationMs;
        }

        public static ActionResult fail(String code, String message) {
            return new ActionResult(false, code, message, null, null, null, null, null);
        }

        public static ActionResult fail(String code, Student student, String message) {
            return new ActionResult(false, code, message, null,
                    student == null ? null : student.id(),
                    student == null ? null : student.name(), null, null);
        }

        public static ActionResult success(String code, String action, String message, Student student,
                                           Instant time, Long durationMs) {
            return new ActionResult(true, code, message, action, student.id(), student.name(),
                    time == null ? null : time.toString(), durationMs);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("ok", ok);
            map.put("code", code);
            map.put("message", message);
            map.put("action", action);
            map.put("studentId", studentId);
            map.put("studentName", studentName);
            map.put("time", time);
            map.put("durationMs", durationMs);
            return map;
        }
    }

}
