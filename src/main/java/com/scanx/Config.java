package com.scanx;

/**
 * All settings in one place.
 * Change a value here, or set the matching environment variable
 * (for example SCANX_DB_PASSWORD) so the password is not written in the file.
 */
public final class Config {

    private Config() {
    }

    // ---------- MySQL ----------

    /** The database "scanx" is created automatically if it does not exist. */
    public static final String DB_URL = env("SCANX_DB_URL",
            "jdbc:mysql://localhost:3306/scanx"
                    + "?createDatabaseIfNotExist=true&sslMode=DISABLED"
                    + "&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"
                    + "&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");

    public static final String DB_USER = env("SCANX_DB_USER", "root");

    /** Put your MySQL password here, or set SCANX_DB_PASSWORD. */
    public static final String DB_PASSWORD = env("SCANX_DB_PASSWORD", "your_mysql_password");

    /**
     * Only needed if you open the site from another address, for example
     * VS Code Live Server. Separate several addresses with commas.
     */
    public static final String CORS_ORIGINS = env("SCANX_CORS_ORIGINS",
            "http://localhost:5500,http://127.0.0.1:5500");

    // ---------- SCANX ----------

    /** The college's time zone. "Today" and "this week" use it. */
    public static final String TIMEZONE = env("SCANX_TIMEZONE", "Asia/Kolkata");

    /** Teacher account created on first start. CHANGE THE PASSWORD. */
    public static final String ADMIN_USER = env("SCANX_ADMIN_USER", "admin");
    public static final String ADMIN_PASSWORD = env("SCANX_ADMIN_PASSWORD", "1234");

    /** Password given to a student when the teacher adds them. */
    public static final String DEFAULT_STUDENT_PASSWORD = env("SCANX_STUDENT_PASSWORD", "1234");

    /** true = on first start, fill the database with 6 sample students. Use false for real use. */
    public static final boolean DEMO_DATA = Boolean.parseBoolean(env("SCANX_DEMO_DATA", "true"));

    /** Students leaving before this time count as a HALF day (24-hour "HH:mm"). Can be changed in Settings. */
    public static final String DEFAULT_HALF_DAY_TIME = env("SCANX_HALF_DAY_TIME", "14:50");

    /** Working days: 1 = Monday ... 7 = Sunday. Can be changed in Settings. */
    public static final String DEFAULT_WORKING_DAYS = env("SCANX_WORKING_DAYS", "1,2,3,4,5,6");

    /** How long a login lasts (hours). */
    public static final int TOKEN_HOURS = Integer.parseInt(env("SCANX_TOKEN_HOURS", "12"));

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || Util.isBlank(value) ? fallback : value.trim();
    }

}
