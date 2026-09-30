package com.scanx;

import com.scanx.Models.ActionResult;
import com.scanx.Models.Student;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/** The gate scanner: works out who was scanned, then records their entry or exit. */
public final class Scans {

    private Scans() {
    }

    private static final Set<String> METHODS =
            new java.util.HashSet<String>(java.util.Arrays.asList("qr", "id-card", "face", "manual"));

    /** Give ONE of: descriptor (face), code (QR / barcode text) or studentId (typed). */
    public static ActionResult scan(Connection c, String code, String studentId,
                                    List<Double> descriptor, String method) throws SQLException {

        Student student;
        String usedMethod = method == null ? "" : method.trim().toLowerCase();

        if (descriptor != null && !descriptor.isEmpty()) {

            Faces.validate(descriptor);

            Faces.Match match = Faces.identify(c, descriptor);
            if (match == null) {
                return ActionResult.fail("FACE_NOT_RECOGNISED",
                        "Face not recognised. Enroll this student's face, or use their QR / ID.");
            }
            student = match.student();
            usedMethod = "face";

        } else if (!Util.isBlank(code)) {

            student = Students.resolveFromCode(c, code);
            if (student == null) {
                return ActionResult.fail("CODE_NOT_RECOGNISED", "That code is not a registered student ID.");
            }

        } else if (!Util.isBlank(studentId)) {

            student = Students.find(c, studentId.trim().toUpperCase());
            if (student == null) {
                return ActionResult.fail("UNKNOWN_STUDENT", "That ID is not a registered student.");
            }
            if (usedMethod.isEmpty()) {
                usedMethod = "manual";
            }

        } else {
            throw ApiException.badRequest("Send a face template, a code or a student ID.");
        }

        if (!METHODS.contains(usedMethod)) {
            usedMethod = !Util.isBlank(code) ? "qr" : "manual";
        }

        return Presence.toggle(c, student.id(), usedMethod);
    }

}
