package com.scanx;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Teacher settings kept in the app_settings table. */
public final class Settings {

    private Settings() {
    }

    private static final String WORKING_DAYS = "workingDays";

    /** Students need at least this attendance (percent). */
    public static final int TARGET_PERCENT = 75;

    /** Working days as ISO numbers: 1 = Monday ... 7 = Sunday. */
    public static Set<Integer> workingDays(Connection c) throws SQLException {
        String value = Db.queryOne(c, "SELECT setting_value FROM app_settings WHERE setting_key = ?",
                rs -> rs.getString(1), WORKING_DAYS);
        Set<Integer> days = new LinkedHashSet<Integer>();
        for (String part : (value == null ? Config.DEFAULT_WORKING_DAYS : value).split(",")) {
            try {
                int day = Integer.parseInt(part.trim());
                if (day >= 1 && day <= 7) {
                    days.add(day);
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return days;
    }

    public static void setWorkingDays(Connection c, List<Integer> days) throws SQLException {
        Set<Integer> clean = new LinkedHashSet<Integer>();
        for (Integer day : days) {
            if (day != null && day >= 1 && day <= 7) {
                clean.add(day);
            }
        }
        if (clean.isEmpty()) {
            throw ApiException.badRequest("Choose at least one working day.");
        }
        StringBuilder text = new StringBuilder();
        for (Integer day : new ArrayList<Integer>(clean)) {
            text.append(text.length() == 0 ? "" : ",").append(day);
        }
        Db.update(c,
                "INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)",
                WORKING_DAYS, text.toString());
    }

}
