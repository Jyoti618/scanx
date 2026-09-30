package com.scanx;

import com.scanx.Models.AcademicEvent;
import com.scanx.Models.CurrentUser;
import com.scanx.Models.Reminder;
import com.scanx.Models.Student;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The JSON API. It knows nothing about servlets: the servlet (ApiServlet)
 * hands over the method, the path (with any ?query), the login header and the body,
 * and gets back a status code and the text to send.
 */
public final class Api {

    private Api() {
    }

    private static final int MAX_BODY = 256 * 1024;

    public static final class Response {
        public final int status;
        public final String contentType;
        public final byte[] body;

        Response(int status, String contentType, byte[] body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }
    }

    /** PUBLIC = no login needed. ANY = any signed-in user. STAFF = admin or teacher. ADMIN = admin only. STUDENT = student only. */
    private enum Access { PUBLIC, ANY, STAFF, ADMIN, STUDENT }

    private interface Handler {
        Object handle(Req req, Connection c) throws Exception;
    }

    private static final class Route {
        final String method;
        final String[] parts;
        final Access access;
        final Handler handler;

        Route(String method, String[] parts, Access access, Handler handler) {
            this.method = method;
            this.parts = parts;
            this.access = access;
            this.handler = handler;
        }
    }

    /** What the browser sent. */
    private static final class Req {
        String method;
        String path;
        String token;
        CurrentUser user;
        final Map<String, String> params = new HashMap<String, String>();
        final Map<String, String> query = new HashMap<String, String>();
        Map<String, Object> body = new LinkedHashMap<String, Object>();

        String str(String key) {
            return Json.str(body, key);
        }

        long id(String name) {
            try {
                return Long.parseLong(params.get(name));
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("That id is not valid.");
            }
        }

        String q(String key) {
            return query.get(key);
        }

        Long qLong(String key) {
            String v = query.get(key);
            if (v == null || v.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(v);
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("'" + key + "' must be a number.");
            }
        }

        int qInt(String key, int fallback) {
            String v = query.get(key);
            if (v == null || v.isEmpty()) {
                return fallback;
            }
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("'" + key + "' must be a number.");
            }
        }
    }

    private static final List<Route> routes = new ArrayList<Route>();
    private static final Set<String> corsOrigins = new HashSet<String>();

    static {
        for (String origin : Config.CORS_ORIGINS.split(",")) {
            if (!origin.trim().isEmpty()) {
                corsOrigins.add(origin.trim());
            }
        }
        registerRoutes();
    }

    public static String allowedOrigin(String origin) {
        return origin != null && corsOrigins.contains(origin) ? origin : null;
    }

    // =====================================================
    //  Small helpers used by many routes
    // =====================================================

    private static void add(String method, String path, Access access, Handler handler) {
        routes.add(new Route(method, path.split("/"), access, handler));
    }

    private static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("ok", true);
        return m;
    }

    private static List<Double> descriptor(Req r) {
        List<Double> numbers = Json.doubles(r.body, "descriptor");
        if (numbers == null) {
            throw ApiException.badRequest("Send the face template as a list of numbers.");
        }
        return numbers;
    }

    private static Long bodyLong(Req r, String key) {
        Object v = r.body.get(key);
        if (v == null) {
            return null;
        }
        return v instanceof Number ? ((Number) v).longValue() : Long.valueOf(v.toString());
    }

    private static Integer bodyInt(Req r, String key) {
        Object v = r.body.get(key);
        if (v == null) {
            return null;
        }
        return v instanceof Number ? ((Number) v).intValue() : Integer.valueOf(v.toString());
    }

    private static long requireLong(Req r, String key) {
        Long v = bodyLong(r, key);
        if (v == null) {
            throw ApiException.badRequest("'" + key + "' is required.");
        }
        return v;
    }

    private static String staffName(Req r) {
        return r.user.isAdmin() ? "Admin" : r.user.username();
    }

    // =====================================================
    //  Routes
    // =====================================================

    private static void registerRoutes() {

        add("GET", "/api/health", Access.PUBLIC, (r, c) -> ok());

        // ---------- sign in ----------

        add("POST", "/api/auth/login", Access.PUBLIC, (r, c) -> {
            Auth.LoginResult login = Auth.login(c, r.str("username"), r.str("password"));
            Map<String, Object> m = ok();
            m.put("token", login.token());
            m.put("role", login.role());
            m.put("studentId", login.studentId());
            m.put("name", login.name());
            return m;
        });

        add("POST", "/api/auth/logout", Access.ANY, (r, c) -> {
            Auth.logout(c, r.token);
            return ok();
        });

        add("GET", "/api/auth/me", Access.ANY, (r, c) -> {
            Map<String, Object> m = ok();
            m.put("role", r.user.role());
            m.put("studentId", r.user.studentId());
            m.put("name", Auth.displayName(c, r.user));
            return m;
        });

        add("POST", "/api/auth/change-password", Access.ANY, (r, c) -> {
            Auth.changePassword(c, r.user, r.str("oldPassword"), r.str("newPassword"), r.token);
            return ok();
        });

        add("GET", "/api/snapshot", Access.ANY, (r, c) -> Snapshot.build(c, r.user));

        // ---------- staff: gate scanner ----------

        add("POST", "/api/staff/scan", Access.STAFF, (r, c) ->
                Scans.scan(c, r.str("code"), r.str("studentId"), Json.doubles(r.body, "descriptor"), r.str("method"))
                        .toMap());

        add("GET", "/api/staff/presence/today", Access.STAFF, (r, c) -> {
            LocalDate today = Times.today();
            List<Object> rows = Db.query(c,
                    "SELECT p.*, s.name AS student_name FROM presence_records p "
                            + "JOIN students s ON s.id = p.student_id WHERE p.visit_date = ? ORDER BY p.id",
                    rs -> {
                        Map<String, Object> m = new LinkedHashMap<String, Object>();
                        m.put("id", rs.getLong("id"));
                        m.put("studentId", rs.getString("student_id"));
                        m.put("studentName", rs.getString("student_name"));
                        m.put("entry", Times.iso(Db.instant(rs, "entry_time")));
                        m.put("exit", Times.iso(Db.instant(rs, "exit_time")));
                        m.put("durationMs", Db.longOrNull(rs, "duration_ms"));
                        m.put("incomplete", rs.getBoolean("incomplete"));
                        return m;
                    }, today);
            Map<String, Object> out = ok();
            out.put("visits", rows);
            return out;
        });

        // ---------- staff: students ----------

        add("GET", "/api/staff/students", Access.STAFF, (r, c) -> {
            Long semesterId = r.qLong("semesterId");
            List<Student> students = semesterId == null ? Students.all(c) : Students.inSemester(c, semesterId);
            List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
            for (Student s : students) {
                Map<String, Object> m = Snapshot.studentMap(s);
                DayAttendance.Stats stats = DayAttendance.statsSoFar(c, s);
                m.put("attendancePercent", stats.percent());
                m.put("inside", Presence.isInside(c, s.id()));
                out.add(m);
            }
            Map<String, Object> res = ok();
            res.put("students", out);
            return res;
        });

        add("PUT", "/api/staff/students/{id}/face", Access.STAFF, (r, c) -> {
            Students.saveFace(c, r.params.get("id").toUpperCase(), descriptor(r), false);
            return ok();
        });

        // ---------- staff: subject register ----------

        add("GET", "/api/staff/register", Access.STAFF, (r, c) -> {
            long subjectId = r.qLong("subjectId") == null
                    ? throwBadRequest("subjectId is required") : r.qLong("subjectId");
            String date = r.q("date");
            int period = r.qInt("period", 1);
            Register.Taken taken = Register.forDate(c, subjectId, date, period);
            return registerMap(taken);
        });

        add("POST", "/api/staff/register", Access.STAFF, (r, c) -> {
            long subjectId = requireLong(r, "subjectId");
            String date = r.str("date");
            int period = bodyInt(r, "period") == null ? 1 : bodyInt(r, "period");
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) r.body.get("presentIds");
            List<String> presentIds = new ArrayList<String>();
            if (raw != null) {
                for (Object o : raw) {
                    presentIds.add(String.valueOf(o));
                }
            }
            Register.Taken taken = Register.save(c, subjectId, date, period, presentIds, staffName(r));
            return registerMap(taken);
        });

        // ---------- staff: attendance and corrections ----------

        add("GET", "/api/staff/attendance/{id}", Access.STAFF, (r, c) -> {
            Student student = Students.require(c, r.params.get("id").toUpperCase());
            String from = r.q("from");
            String to = r.q("to");
            LocalDate fromDate = from == null ? student.joinedDate() : LocalDate.parse(from);
            LocalDate toDate = to == null ? Times.today() : LocalDate.parse(to);
            return attendanceMap(c, student, fromDate, toDate);
        });

        add("POST", "/api/staff/day-override", Access.STAFF, (r, c) -> {
            DayAttendance.setOverride(c, r.str("studentId"), r.str("date"), r.str("status"), staffName(r));
            return ok();
        });

        add("DELETE", "/api/staff/day-override", Access.STAFF, (r, c) -> {
            DayAttendance.clearOverride(c, r.q("studentId"), r.q("date"));
            return ok();
        });

        add("GET", "/api/staff/reports/low", Access.STAFF, (r, c) -> {
            List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
            Map<Student, DayAttendance.Stats> low =
                    DayAttendance.belowTarget(c, Students.all(c), Settings.TARGET_PERCENT);
            for (Map.Entry<Student, DayAttendance.Stats> e : low.entrySet()) {
                Map<String, Object> m = Snapshot.studentMap(e.getKey());
                m.put("percent", e.getValue().percent());
                m.put("fullDays", e.getValue().fullDays);
                m.put("halfDays", e.getValue().halfDays);
                m.put("absentDays", e.getValue().absentDays);
                out.add(m);
            }
            Map<String, Object> res = ok();
            res.put("students", out);
            return res;
        });

        // ---------- staff: events ----------

        add("POST", "/api/staff/events", Access.STAFF, (r, c) -> {
            AcademicEvent e = Events.create(c, r.str("title"), r.str("type"), r.str("date"), r.str("time"),
                    r.str("location"), bodyLong(r, "audienceCourseId"), bodyInt(r, "audienceYear"));
            Map<String, Object> m = ok();
            m.put("event", Snapshot.eventMap(e));
            return m;
        });

        add("PUT", "/api/staff/events/{id}", Access.STAFF, (r, c) -> {
            AcademicEvent e = Events.update(c, r.id("id"), r.str("title"), r.str("type"), r.str("date"),
                    r.str("time"), r.str("location"), bodyLong(r, "audienceCourseId"), bodyInt(r, "audienceYear"));
            Map<String, Object> m = ok();
            m.put("event", Snapshot.eventMap(e));
            return m;
        });

        add("DELETE", "/api/staff/events/{id}", Access.STAFF, (r, c) -> {
            Events.delete(c, r.id("id"));
            return ok();
        });

        // ---------- admin: students ----------

        add("POST", "/api/admin/students", Access.ADMIN, (r, c) -> {
            Student s = Students.add(c, r.str("id"), r.str("name"), requireLong(r, "courseId"),
                    requireLong(r, "semesterId"), r.str("startDate"), r.str("password"));
            Map<String, Object> m = ok();
            m.put("student", Snapshot.studentMap(s));
            return m;
        });

        add("PUT", "/api/admin/students/{id}", Access.ADMIN, (r, c) -> {
            Student s = Students.update(c, r.params.get("id").toUpperCase(), r.str("name"),
                    requireLong(r, "courseId"), requireLong(r, "semesterId"), r.str("startDate"));
            Map<String, Object> m = ok();
            m.put("student", Snapshot.studentMap(s));
            return m;
        });

        add("DELETE", "/api/admin/students/{id}", Access.ADMIN, (r, c) -> {
            Students.remove(c, r.params.get("id").toUpperCase());
            return ok();
        });

        add("PUT", "/api/admin/students/{id}/password", Access.ADMIN, (r, c) -> {
            Students.resetPassword(c, r.params.get("id").toUpperCase(), r.str("password"));
            return ok();
        });

        // ---------- admin: courses / semesters / subjects ----------

        add("POST", "/api/admin/courses", Access.ADMIN, (r, c) -> {
            long id = Courses.addCourse(c, r.str("name"), bodyInt(r, "years") == null ? 3 : bodyInt(r, "years"));
            Map<String, Object> m = ok();
            m.put("course", Snapshot.courseMap(Courses.course(c, id)));
            return m;
        });

        add("PUT", "/api/admin/courses/{id}", Access.ADMIN, (r, c) -> {
            Courses.updateCourse(c, r.id("id"), r.str("name"), bodyInt(r, "years") == null ? 3 : bodyInt(r, "years"));
            Map<String, Object> m = ok();
            m.put("course", Snapshot.courseMap(Courses.course(c, r.id("id"))));
            return m;
        });

        add("DELETE", "/api/admin/courses/{id}", Access.ADMIN, (r, c) -> {
            Courses.deleteCourse(c, r.id("id"));
            return ok();
        });

        add("POST", "/api/admin/semesters", Access.ADMIN, (r, c) -> {
            long id = Courses.addSemester(c, requireLong(r, "courseId"), r.str("name"),
                    bodyInt(r, "yearNo") == null ? 1 : bodyInt(r, "yearNo"));
            Map<String, Object> m = ok();
            m.put("semester", Snapshot.semesterMap(Courses.semester(c, id)));
            return m;
        });

        add("PUT", "/api/admin/semesters/{id}", Access.ADMIN, (r, c) -> {
            Courses.updateSemester(c, r.id("id"), r.str("name"), bodyInt(r, "yearNo") == null ? 1 : bodyInt(r, "yearNo"));
            Map<String, Object> m = ok();
            m.put("semester", Snapshot.semesterMap(Courses.semester(c, r.id("id"))));
            return m;
        });

        add("DELETE", "/api/admin/semesters/{id}", Access.ADMIN, (r, c) -> {
            Courses.deleteSemester(c, r.id("id"));
            return ok();
        });

        add("POST", "/api/admin/subjects", Access.ADMIN, (r, c) -> {
            long id = Courses.addSubject(c, requireLong(r, "semesterId"), r.str("name"));
            Map<String, Object> m = ok();
            m.put("subject", Snapshot.subjectMap(Courses.subject(c, id)));
            return m;
        });

        add("PUT", "/api/admin/subjects/{id}", Access.ADMIN, (r, c) -> {
            Courses.updateSubject(c, r.id("id"), r.str("name"));
            Map<String, Object> m = ok();
            m.put("subject", Snapshot.subjectMap(Courses.subject(c, r.id("id"))));
            return m;
        });

        add("DELETE", "/api/admin/subjects/{id}", Access.ADMIN, (r, c) -> {
            Courses.deleteSubject(c, r.id("id"));
            return ok();
        });

        // ---------- admin: timings and calendar ----------

        add("POST", "/api/admin/timings", Access.ADMIN, (r, c) -> {
            long id = Timings.addTiming(c, r.str("effectiveFrom"), r.str("halfDayTime"),
                    bodyLong(r, "courseId"), bodyInt(r, "yearNo"));
            Map<String, Object> m = ok();
            m.put("id", id);
            return m;
        });

        add("DELETE", "/api/admin/timings/{id}", Access.ADMIN, (r, c) -> {
            Timings.deleteTiming(c, r.id("id"));
            return ok();
        });

        add("POST", "/api/admin/calendar", Access.ADMIN, (r, c) -> {
            long id = Timings.addCalendarDay(c, r.str("date"), r.str("kind"), r.str("endTime"), r.str("note"),
                    bodyLong(r, "courseId"), bodyInt(r, "yearNo"));
            Map<String, Object> m = ok();
            m.put("id", id);
            return m;
        });

        add("DELETE", "/api/admin/calendar/{id}", Access.ADMIN, (r, c) -> {
            Timings.deleteCalendarDay(c, r.id("id"));
            return ok();
        });

        // ---------- admin: settings, staff logins, demo ----------

        add("PUT", "/api/admin/settings", Access.ADMIN, (r, c) -> {
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) r.body.get("workingDays");
            List<Integer> days = new ArrayList<Integer>();
            if (raw != null) {
                for (Object o : raw) {
                    days.add(((Number) o).intValue());
                }
            }
            Settings.setWorkingDays(c, days);
            return ok();
        });

        add("POST", "/api/admin/staff", Access.ADMIN, (r, c) -> {
            String username = r.str("username") == null ? "" : r.str("username").trim();
            String name = r.str("name") == null ? username : r.str("name").trim();
            String role = "ADMIN".equalsIgnoreCase(r.str("role")) ? "ADMIN" : "TEACHER";
            String password = r.str("password");
            if (username.isEmpty() || username.length() > 40) {
                throw ApiException.badRequest("Enter a username (up to 40 characters).");
            }
            if (password == null || password.length() < 4) {
                throw ApiException.badRequest("The password needs at least 4 characters.");
            }
            if (Auth.findUser(c, username) != null) {
                throw ApiException.conflict("DUPLICATE", "That username already exists.");
            }
            long id = Auth.createUser(c, username, password, role, null, name);
            Map<String, Object> m = ok();
            m.put("id", id);
            return m;
        });

        add("GET", "/api/admin/staff", Access.ADMIN, (r, c) -> {
            List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
            for (Models.AppUser u : Auth.staff(c)) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", u.id());
                m.put("username", u.username());
                m.put("role", u.role());
                m.put("displayName", u.displayName());
                out.add(m);
            }
            Map<String, Object> res = ok();
            res.put("staff", out);
            return res;
        });

        add("DELETE", "/api/admin/staff/{id}", Access.ADMIN, (r, c) -> {
            long id = r.id("id");
            if (id == r.user.userId()) {
                throw ApiException.badRequest("You cannot remove your own login.");
            }
            Models.AppUser user = Auth.findById(c, id);
            if (user == null || !"TEACHER".equals(user.role())) {
                throw ApiException.notFound("That teacher login was not found.");
            }
            Db.update(c, "DELETE FROM app_users WHERE id = ?", id);
            return ok();
        });

        add("POST", "/api/admin/reset-demo", Access.ADMIN, (r, c) -> {
            if (!Config.DEMO_DATA) {
                throw ApiException.forbidden("Demo data is switched off.");
            }
            DemoData.reset(c);
            return ok();
        });

        // ---------- student ----------

        add("POST", "/api/student/self-scan", Access.STUDENT, (r, c) -> {
            List<Double> desc = descriptor(r);
            Student student = Students.require(c, r.user.studentId());
            if (student.faceDescriptor() == null) {
                return Models.ActionResult.fail("NO_FACE", student,
                        "Enroll your face first, or ask a teacher to scan your QR code.").toMap();
            }
            if (!Faces.verify(student, desc)) {
                return Models.ActionResult.fail("FACE_MISMATCH", student,
                        "That doesn't look like " + student.name() + ". Try again in better light.").toMap();
            }
            return Presence.toggle(c, r.user.studentId(), "face").toMap();
        });

        add("PUT", "/api/student/face", Access.STUDENT, (r, c) -> {
            Students.saveFace(c, r.user.studentId(), descriptor(r), true);
            return ok();
        });

        add("GET", "/api/student/attendance", Access.STUDENT, (r, c) -> {
            Student student = Students.require(c, r.user.studentId());
            String from = r.q("from");
            String to = r.q("to");
            LocalDate fromDate = from == null ? student.joinedDate() : LocalDate.parse(from);
            LocalDate toDate = to == null ? Times.today() : LocalDate.parse(to);
            return attendanceMap(c, student, fromDate, toDate);
        });

        add("GET", "/api/student/plan", Access.STUDENT, (r, c) -> {
            Student student = Students.require(c, r.user.studentId());
            int target = r.qInt("target", Settings.TARGET_PERCENT);
            LocalDate byDate = LocalDate.parse(r.q("by"));
            Planner.Plan plan = Planner.plan(c, student, target, byDate);
            Map<String, Object> m = ok();
            m.put("currentPercent", plan.currentPercent);
            m.put("workingDaysSoFar", plan.workingDaysSoFar);
            m.put("futureWorkingDays", plan.futureWorkingDays);
            m.put("need", plan.need);
            m.put("reachable", plan.reachable);
            m.put("canMiss", plan.canMiss);
            m.put("ifAllPercent", plan.ifAllPercent);
            m.put("ifNonePercent", plan.ifNonePercent);
            return m;
        });

        add("GET", "/api/student/presence", Access.STUDENT, (r, c) -> {
            List<Object> rows = Db.query(c,
                    "SELECT * FROM presence_records WHERE student_id = ? ORDER BY id DESC LIMIT 30",
                    rs -> presenceMap(rs), r.user.studentId());
            Map<String, Object> res = ok();
            res.put("visits", rows);
            res.put("inside", Presence.isInside(c, r.user.studentId()));
            return res;
        });

        add("POST", "/api/student/reminders", Access.STUDENT, (r, c) -> {
            Reminder reminder = Reminders.add(c, r.user.studentId(), r.str("text"), r.str("date"));
            Map<String, Object> m = ok();
            m.put("id", reminder.id());
            return m;
        });

        add("GET", "/api/student/reminders", Access.STUDENT, (r, c) -> {
            List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
            for (Reminder rem : Reminders.forStudent(c, r.user.studentId())) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", rem.id());
                m.put("text", rem.text());
                m.put("date", rem.date() == null ? "" : rem.date().toString());
                m.put("done", rem.done());
                out.add(m);
            }
            Map<String, Object> res = ok();
            res.put("reminders", out);
            return res;
        });

        add("POST", "/api/student/reminders/{id}/toggle", Access.STUDENT, (r, c) -> {
            Reminders.toggle(c, r.user.studentId(), r.id("id"));
            return ok();
        });

        add("DELETE", "/api/student/reminders/{id}", Access.STUDENT, (r, c) -> {
            Reminders.delete(c, r.user.studentId(), r.id("id"));
            return ok();
        });
    }

    private static long throwBadRequest(String msg) {
        throw ApiException.badRequest(msg);
    }

    private static Map<String, Object> presenceMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", rs.getLong("id"));
        m.put("date", Db.date(rs, "visit_date").toString());
        m.put("entry", Times.iso(Db.instant(rs, "entry_time")));
        m.put("exit", Times.iso(Db.instant(rs, "exit_time")));
        m.put("durationMs", Db.longOrNull(rs, "duration_ms"));
        m.put("incomplete", rs.getBoolean("incomplete"));
        return m;
    }

    private static Map<String, Object> registerMap(Register.Taken taken) {
        Map<String, Object> m = ok();
        m.put("sessionId", taken.sessionId);
        m.put("subjectId", taken.subjectId);
        m.put("subjectName", taken.subjectName);
        m.put("date", taken.date.toString());
        m.put("period", taken.periodNo);
        m.put("takenBy", taken.takenBy);
        m.put("presentIds", taken.presentIds);
        return m;
    }

    private static Map<String, Object> attendanceMap(Connection c, Student student, LocalDate from, LocalDate to)
            throws java.sql.SQLException {

        List<DayAttendance.DayResult> days = DayAttendance.range(c, student, from, to);
        DayAttendance.Stats stats = DayAttendance.stats(c, student, from, to);

        List<Map<String, Object>> dayList = new ArrayList<Map<String, Object>>();
        for (DayAttendance.DayResult d : days) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("date", d.date.toString());
            m.put("status", d.status);
            m.put("overridden", d.overridden);
            dayList.add(m);
        }

        List<Map<String, Object>> subjectStats = new ArrayList<Map<String, Object>>();
        for (Register.SubjectStats s : Register.statsForStudent(c, student)) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("subjectId", s.subjectId);
            m.put("subjectName", s.subjectName);
            m.put("attended", s.attended);
            m.put("total", s.total);
            m.put("percent", s.percent());
            subjectStats.add(m);
        }

        Map<String, Object> out = ok();
        out.put("days", dayList);
        out.put("fullDays", stats.fullDays);
        out.put("halfDays", stats.halfDays);
        out.put("absentDays", stats.absentDays);
        out.put("workingDays", stats.workingDays);
        out.put("percent", stats.percent());
        out.put("subjects", subjectStats);
        return out;
    }

    // =====================================================
    //  Handling one request
    // =====================================================

    private static Map<String, Object> error(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("ok", false);
        m.put("code", code);
        m.put("message", message);
        return m;
    }

    private static Response json(int status, Object body) {
        return new Response(status, "application/json; charset=utf-8",
                Json.write(body).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> out = new HashMap<String, String>();
        if (query == null || query.isEmpty()) {
            return out;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            out.put(urlDecode(key), urlDecode(value));
        }
        return out;
    }

    private static String urlDecode(String text) {
        try {
            return java.net.URLDecoder.decode(text, "UTF-8");
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * method        GET, POST, PUT or DELETE
     * rawPath       for example /api/snapshot or /api/staff/register?subjectId=3&date=2026-09-20
     * authorization the "Authorization" header (Bearer token) or null
     * bodyStream    the request body (only read for POST and PUT)
     */
    public static Response handle(String method, String rawPath, String authorization, InputStream bodyStream) {

        int qIndex = rawPath.indexOf('?');
        String path = qIndex < 0 ? rawPath : rawPath.substring(0, qIndex);
        String queryString = qIndex < 0 ? "" : rawPath.substring(qIndex + 1);

        String[] segments = path.split("/");
        Route matched = null;
        Map<String, String> params = new HashMap<String, String>();
        boolean pathKnown = false;

        for (Route route : routes) {
            Map<String, String> found = match(route.parts, segments);
            if (found == null) {
                continue;
            }
            pathKnown = true;
            if (route.method.equals(method)) {
                matched = route;
                params = found;
                break;
            }
        }

        if (matched == null) {
            return pathKnown
                    ? json(405, error("METHOD_NOT_ALLOWED", "That method is not allowed here."))
                    : json(404, error("NOT_FOUND", "There is no such API address."));
        }

        final Route route = matched;
        final Req req = new Req();
        req.method = method;
        req.path = path;
        req.params.putAll(params);
        req.query.putAll(parseQuery(queryString));

        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            req.token = authorization.substring(7).trim();
        }

        try {

            if ((method.equals("POST") || method.equals("PUT")) && bodyStream != null) {
                req.body = readBody(bodyStream);
            }

            Object result = Db.tx(new Db.Work<Object>() {
                @Override
                public Object run(Connection c) throws Exception {

                    if (route.access != Access.PUBLIC) {
                        CurrentUser user = req.token == null || req.token.isEmpty()
                                ? null
                                : Auth.authenticate(c, req.token);

                        if (user == null) {
                            throw ApiException.unauthorized("Please sign in again.");
                        }
                        if (route.access == Access.ADMIN && !user.isAdmin()) {
                            throw ApiException.forbidden("Only the admin can do this.");
                        }
                        if (route.access == Access.STAFF && !user.isStaff()) {
                            throw ApiException.forbidden("Only staff can do this.");
                        }
                        if (route.access == Access.STUDENT && (user.isStaff() || user.studentId() == null)) {
                            throw ApiException.forbidden("Only students can do this.");
                        }
                        req.user = user;
                    }

                    return route.handler.handle(req, c);
                }
            });

            return json(200, result);

        } catch (ApiException e) {
            return json(e.status, error(e.code, e.getMessage()));
        } catch (Exception e) {
            System.err.println("SCANX error in " + method + " " + path + ": " + e);
            e.printStackTrace();
            return json(500, error("SERVER_ERROR", "Something went wrong on the server."));
        }
    }

    private static Map<String, String> match(String[] pattern, String[] actual) {
        if (pattern.length != actual.length) {
            return null;
        }
        Map<String, String> params = new HashMap<String, String>();
        for (int i = 0; i < pattern.length; i++) {
            String p = pattern[i];
            if (p.startsWith("{") && p.endsWith("}")) {
                if (actual[i].isEmpty()) {
                    return null;
                }
                params.put(p.substring(1, p.length() - 1), actual[i]);
            } else if (!p.equals(actual[i])) {
                return null;
            }
        }
        return params;
    }

    private static Map<String, Object> readBody(InputStream in) {
        byte[] bytes;
        try {
            bytes = Util.readLimited(in, MAX_BODY);
        } catch (IOException e) {
            throw ApiException.badRequest("The request could not be read.");
        }
        if (bytes.length > MAX_BODY) {
            throw new ApiException(413, "TOO_LARGE", "The request is too large.");
        }
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return Json.parseObject(text);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("The request could not be read.");
        }
    }

}
