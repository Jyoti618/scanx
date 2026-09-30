package com.scanx;

import com.scanx.Models.Student;

import java.sql.SQLException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * Face matching. The browser turns a face into 128 numbers (a "template").
 * Here we only compare templates: a small distance means the same person.
 */
public final class Faces {

    private Faces() {
    }

    public static final int SIZE = 128;

    /** Teacher scanner: pick the closest enrolled face if it is at most this far away. */
    public static final double IDENTIFY_MAX = 0.5;

    /** A student checking their own face: a little more relaxed. */
    public static final double VERIFY_MAX = 0.55;

    public static final class Match {
        private final Student student;
        private final double distance;

        public Match(Student student, double distance) {
            this.student = student;
            this.distance = distance;
        }

        public Student student() {
            return student;
        }

        public double distance() {
            return distance;
        }
    }

    public static void validate(List<Double> descriptor) {
        if (descriptor == null || descriptor.size() != SIZE) {
            throw ApiException.badRequest("A face template must have " + SIZE + " numbers.");
        }
        for (Double value : descriptor) {
            if (value == null || value.isNaN() || value.isInfinite() || Math.abs(value) > 5) {
                throw ApiException.badRequest("The face template is not valid.");
            }
        }
    }

    /** Written as plain JSON text, for example [0.12,-0.03, ...]. */
    public static String toText(List<Double> descriptor) {
        return Json.write(descriptor);
    }

    /** Returns null if the saved text is missing or damaged. */
    public static double[] parse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            Object value = Json.parse(text);
            if (!(value instanceof List)) {
                return null;
            }
            List<?> list = (List<?>) value;
            if (list.size() != SIZE) {
                return null;
            }
            double[] numbers = new double[SIZE];
            for (int i = 0; i < SIZE; i++) {
                if (!(list.get(i) instanceof Number)) {
                    return null;
                }
                numbers[i] = ((Number) list.get(i)).doubleValue();
            }
            return numbers;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static double distance(double[] a, List<Double> b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b.get(i);
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /** Which enrolled student looks most like this face? Null if nobody is close enough. */
    public static Match identify(Connection c, List<Double> descriptor) throws SQLException {
        Match best = null;

        for (Student student : Students.all(c)) {
            double[] saved = parse(student.faceDescriptor());
            if (saved == null) {
                continue;
            }
            double distance = distance(saved, descriptor);
            if (best == null || distance < best.distance()) {
                best = new Match(student, distance);
            }
        }

        return best != null && best.distance() <= IDENTIFY_MAX ? best : null;
    }

    /** Is this face the same person as the student's saved face? */
    public static boolean verify(Student student, List<Double> descriptor) {
        double[] saved = parse(student.faceDescriptor());
        return saved != null && distance(saved, descriptor) <= VERIFY_MAX;
    }

    /** Helper for tests and demos: a list of numbers from an array. */
    public static List<Double> asList(double[] numbers) {
        List<Double> list = new ArrayList<>();
        for (double n : numbers) {
            list.add(n);
        }
        return list;
    }

}
