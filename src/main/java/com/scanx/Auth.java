package com.scanx;

import com.scanx.Models.AppUser;
import com.scanx.Models.CurrentUser;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Passwords and sign-in.
 *  - passwords are stored as PBKDF2 hashes (never the password itself)
 *  - after sign-in the browser gets a random token; only a hash of it is stored in MySQL
 *
 * Roles: ADMIN (the college account, sees and edits everything),
 *        TEACHER (takes registers, creates events, cannot manage courses or other logins),
 *        STUDENT.
 */
public final class Auth {

    private Auth() {
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 120_000;
    public static final List<String> ROLES = Arrays.asList("ADMIN", "TEACHER", "STUDENT");

    public static final class LoginResult {
        private final String token;
        private final String role;
        private final String studentId;
        private final String name;

        public LoginResult(String token, String role, String studentId, String name) {
            this.token = token;
            this.role = role;
            this.studentId = studentId;
            this.name = name;
        }

        public String token() {
            return token;
        }

        public String role() {
            return role;
        }

        public String studentId() {
            return studentId;
        }

        public String name() {
            return name;
        }
    }

    // =====================================================
    //  Passwords
    // =====================================================

    public static String hashPassword(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$" + Util.hex(salt) + "$" + Util.hex(hash);
    }

    public static boolean verifyPassword(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals("pbkdf2")) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Util.unhex(parts[2]);
            byte[] expected = Util.unhex(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is not available", e);
        }
    }

    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Util.hex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // =====================================================
    //  Users
    // =====================================================

    private static final Db.RowMapper<AppUser> USER = rs -> new AppUser(
            rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
            rs.getString("role"), rs.getString("student_id"), rs.getString("display_name"));

    public static AppUser findUser(Connection c, String username) throws SQLException {
        return Db.queryOne(c, "SELECT * FROM app_users WHERE username = ?", USER, username);
    }

    public static AppUser findById(Connection c, long id) throws SQLException {
        return Db.queryOne(c, "SELECT * FROM app_users WHERE id = ?", USER, id);
    }

    public static AppUser findStudentUser(Connection c, String studentId) throws SQLException {
        return Db.queryOne(c, "SELECT * FROM app_users WHERE student_id = ?", USER, studentId);
    }

    public static List<AppUser> staff(Connection c) throws SQLException {
        return Db.query(c, "SELECT * FROM app_users WHERE role IN ('ADMIN','TEACHER') ORDER BY role, display_name",
                USER);
    }

    public static boolean adminExists(Connection c) throws SQLException {
        return Db.exists(c, "SELECT 1 FROM app_users WHERE role = 'ADMIN' LIMIT 1");
    }

    public static long createUser(Connection c, String username, String password, String role,
                                  String studentId, String displayName) throws SQLException {
        if (!ROLES.contains(role)) {
            throw ApiException.badRequest("That role is not valid.");
        }
        return Db.insert(c,
                "INSERT INTO app_users (username, password_hash, role, student_id, display_name) "
                        + "VALUES (?, ?, ?, ?, ?)",
                username, hashPassword(password), role, studentId, displayName);
    }

    /** Sets a new password and signs the user out everywhere. */
    public static void setPassword(Connection c, long userId, String newPassword) throws SQLException {
        Db.update(c, "UPDATE app_users SET password_hash = ? WHERE id = ?",
                hashPassword(newPassword), userId);
        Db.update(c, "DELETE FROM auth_tokens WHERE user_id = ?", userId);
    }

    // =====================================================
    //  Sign in / sign out
    // =====================================================

    public static LoginResult login(Connection c, String username, String password) throws SQLException {

        if (Util.isBlank(username) || password == null || password.isEmpty()) {
            throw ApiException.badRequest("Enter your username and password.");
        }

        String name = username.trim();

        /* Student IDs are stored in capitals; staff usernames keep their own case */
        AppUser user = findUser(c, name);
        if (user == null) {
            user = findUser(c, name.toUpperCase());
        }

        if (user == null || !verifyPassword(password, user.passwordHash())) {
            pauseAfterFailedLogin();
            throw ApiException.unauthorized("Invalid login details. Please try again.");
        }

        Instant now = Times.now();
        Db.update(c, "DELETE FROM auth_tokens WHERE expires_at < ?", now);

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Util.hex(raw);

        Db.update(c, "INSERT INTO auth_tokens (token_hash, user_id, expires_at) VALUES (?, ?, ?)",
                sha256Hex(token), user.id(), now.plusSeconds(Config.TOKEN_HOURS * 3600L));

        return new LoginResult(token, user.role(), user.studentId(), user.displayName());
    }

    public static void logout(Connection c, String token) throws SQLException {
        Db.update(c, "DELETE FROM auth_tokens WHERE token_hash = ?", sha256Hex(token));
    }

    /** Called for every request. Returns the user if the token is real and not expired, else null. */
    public static CurrentUser authenticate(Connection c, String token) throws SQLException {
        AppUser user = Db.queryOne(c,
                "SELECT u.* FROM auth_tokens t JOIN app_users u ON u.id = t.user_id "
                        + "WHERE t.token_hash = ? AND t.expires_at > ?",
                USER, sha256Hex(token), Times.now());

        return user == null
                ? null
                : new CurrentUser(user.id(), user.role(), user.studentId(), user.username());
    }

    /**
     * Changes your own password. Every OTHER device is signed out, but the one you are
     * using stays signed in (currentToken).
     */
    public static void changePassword(Connection c, CurrentUser current, String oldPassword,
                                      String newPassword, String currentToken) throws SQLException {

        AppUser user = findById(c, current.userId());

        if (user == null) {
            throw ApiException.unauthorized("Please sign in again.");
        }
        if (!verifyPassword(oldPassword, user.passwordHash())) {
            throw ApiException.badRequest("The current password is not correct.");
        }
        if (newPassword == null || newPassword.length() < 4) {
            throw ApiException.badRequest("The new password needs at least 4 characters.");
        }

        Db.update(c, "UPDATE app_users SET password_hash = ? WHERE id = ?", hashPassword(newPassword), user.id());
        Db.update(c, "DELETE FROM auth_tokens WHERE user_id = ? AND token_hash <> ?",
                user.id(), sha256Hex(currentToken == null ? "" : currentToken));
    }

    public static String displayName(Connection c, CurrentUser current) throws SQLException {
        AppUser user = findById(c, current.userId());
        return user == null ? current.username() : user.displayName();
    }

    private static void pauseAfterFailedLogin() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
