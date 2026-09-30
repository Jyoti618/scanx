package com.scanx;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;

/**
 * Starts and stops SCANX when Tomcat loads or unloads the web application.
 * (AppListener calls start() and stop().)
 */
public final class App {

    private App() {
    }

    public static void start() {

        System.out.println("SCANX starting ...");

        try {
            Db.init(8);
        } catch (Exception e) {
            System.err.println();
            System.err.println("SCANX could not connect to MySQL: " + e.getMessage());
            System.err.println("Check that:");
            System.err.println("  1. MySQL is running");
            System.err.println("  2. the user and password in Config.java are right");
            System.err.println("  3. the MySQL connector .jar is in WEB-INF/lib");
            throw new IllegalStateException("SCANX could not connect to MySQL", e);
        }

        try {
            Db.tx(new Db.Work<Object>() {
                @Override
                public Object run(java.sql.Connection c) throws Exception {
                    Schema.checkOldVersion(c);
                    Schema.create(c);
                    DemoData.ensureAdmin(c);
                    if (Config.DEMO_DATA) {
                        DemoData.seedIfEmpty(c);
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("SCANX could not prepare the database: " + e.getMessage(), e);
        }

        System.out.println("SCANX is ready (time zone " + Times.zoneName() + ").");
    }

    public static void stop() {
        Db.shutdown();

        /* Let go of the MySQL driver so Tomcat can unload the application cleanly */
        ClassLoader mine = App.class.getClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == mine) {
                try {
                    DriverManager.deregisterDriver(driver);
                } catch (SQLException ignored) {
                    // nothing more to do
                }
            }
        }
        try {
            Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread")
                    .getMethod("uncheckedShutdown").invoke(null);
        } catch (Throwable ignored) {
            // older or newer connector: not needed
        }
    }

}
