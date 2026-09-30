package com.scanx;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * MySQL access through plain JDBC.
 *  - a small pool of connections
 *  - tx(...) runs work in one transaction: saved together, or undone if anything fails
 *  - small helpers so the SQL in the other files stays short
 *
 * Times are stored in MySQL DATETIME(3) columns as UTC (see toUtc).
 */
public final class Db {

    private Db() {
    }

    /** Runs code that uses a connection. */
    public interface Work<T> {
        T run(Connection c) throws Exception;
    }

    /** Turns one row of a query result into a Java object. */
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private static BlockingQueue<Connection> pool;

    public static void init(int size) throws SQLException {
        loadDriver();
        pool = new ArrayBlockingQueue<Connection>(size);
        for (int i = 0; i < size; i++) {
            pool.add(open());
        }
    }

    /** Tomcat needs the driver loaded by name. Newer connector first, then the old one. */
    private static void loadDriver() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException newer) {
            try {
                Class.forName("com.mysql.jdbc.Driver");
            } catch (ClassNotFoundException older) {
                throw new SQLException("The MySQL connector .jar was not found. "
                        + "Put mysql-connector-j-x.x.x.jar in WEB-INF/lib.");
            }
        }
    }

    /** Closes every connection (when the web application stops). */
    public static void shutdown() {
        if (pool == null) {
            return;
        }
        Connection c;
        while ((c = pool.poll()) != null) {
            try {
                c.close();
            } catch (SQLException ignored) {
                // already closed
            }
        }
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(Config.DB_URL, Config.DB_USER, Config.DB_PASSWORD);
    }

    private static Connection borrow() throws Exception {
        Connection c = pool.poll(15, TimeUnit.SECONDS);
        if (c == null) {
            throw new ApiException(503, "BUSY", "The server is busy. Please try again.");
        }
        try {
            if (c.isValid(2)) {
                return c;
            }
        } catch (SQLException ignored) {
            // fall through and open a fresh connection
        }
        try {
            c.close();
        } catch (SQLException ignored) {
            // already broken
        }
        try {
            return open();
        } catch (SQLException e) {
            pool.add(c); // keep the pool size; the next request will try again
            throw e;
        }
    }

    /** Runs the work in a transaction. Commit if it finishes, roll back if it throws. */
    public static <T> T tx(Work<T> work) throws Exception {
        Connection c = borrow();
        try {
            c.setAutoCommit(false);
            T result = work.run(c);
            c.commit();
            return result;
        } catch (Exception e) {
            try {
                c.rollback();
            } catch (SQLException ignored) {
                // nothing more to do
            }
            throw e;
        } finally {
            pool.add(c);
        }
    }

    // =====================================================
    //  Small JDBC helpers
    // =====================================================

    /**
     * Times go to MySQL as plain UTC date-times (DATETIME columns) and come back the same way,
     * so the time zone of the computer or the connector version never changes them.
     * Dates (DATE columns) are passed as plain dates.
     */
    private static LocalDateTime toUtc(Instant instant) {
        LocalDateTime time = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        /* DATETIME(3) keeps milliseconds */
        return time.withNano((time.getNano() / 1_000_000) * 1_000_000);
    }

    private static void bind(PreparedStatement ps, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            int index = i + 1;
            if (a == null) {
                ps.setObject(index, null);
            } else if (a instanceof Instant) {
                ps.setObject(index, toUtc((Instant) a));
            } else if (a instanceof LocalDate) {
                ps.setObject(index, a);
            } else if (a instanceof Boolean) {
                ps.setBoolean(index, (Boolean) a);
            } else if (a instanceof Integer) {
                ps.setInt(index, (Integer) a);
            } else if (a instanceof Long) {
                ps.setLong(index, (Long) a);
            } else {
                ps.setString(index, a.toString());
            }
        }
    }

    public static <T> List<T> query(Connection c, String sql, RowMapper<T> mapper, Object... args)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> rows = new ArrayList<T>();
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
                return rows;
            }
        }
    }

    /** The first row, or null when there is none. */
    public static <T> T queryOne(Connection c, String sql, RowMapper<T> mapper, Object... args)
            throws SQLException {
        List<T> rows = query(c, sql, mapper, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static boolean exists(Connection c, String sql, Object... args) throws SQLException {
        return !query(c, sql, rs -> 1, args).isEmpty();
    }

    /** INSERT / UPDATE / DELETE. Returns how many rows changed. */
    public static int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            return ps.executeUpdate();
        }
    }

    /** INSERT into a table with AUTO_INCREMENT id. Returns the new id. */
    public static long insert(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, args);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("The database did not return the new id");
            }
        }
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime time = rs.getObject(column, LocalDateTime.class);
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }

    public static LocalDate date(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }

    /** A nullable BIGINT column. */
    public static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    /** A nullable INT column. */
    public static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    /** "?, ?, ?" for an IN (...) list. */
    public static String marks(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(i == 0 ? "?" : ", ?");
        }
        return sb.toString();
    }

}
