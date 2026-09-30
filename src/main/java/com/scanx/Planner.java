package com.scanx;

import com.scanx.DayAttendance.Stats;
import com.scanx.Models.Student;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * "I need X% by this date": how many more full days of attendance are needed?
 * Counts the real remaining working days (skipping weekends and holidays),
 * using the student's own course and year, so it matches DayAttendance exactly.
 */
public final class Planner {

    private Planner() {
    }

    public static final class Plan {
        public final Double currentPercent;
        public final int workingDaysSoFar;
        public final int futureWorkingDays;
        /** Full days still needed among the future working days. */
        public final double need;
        public final boolean reachable;
        /** Full days that can still be missed (or taken as half) and stay on target. */
        public final double canMiss;
        public final Double ifAllPercent;
        public final Double ifNonePercent;

        Plan(Double currentPercent, int workingDaysSoFar, int futureWorkingDays, double need,
             boolean reachable, double canMiss, Double ifAllPercent, Double ifNonePercent) {
            this.currentPercent = currentPercent;
            this.workingDaysSoFar = workingDaysSoFar;
            this.futureWorkingDays = futureWorkingDays;
            this.need = need;
            this.reachable = reachable;
            this.canMiss = canMiss;
            this.ifAllPercent = ifAllPercent;
            this.ifNonePercent = ifNonePercent;
        }
    }

    public static Plan plan(Connection c, Student student, int targetPercent, LocalDate byDate)
            throws SQLException {

        Stats soFar = DayAttendance.statsSoFar(c, student);

        LocalDate today = Times.today();
        LocalDate from = today.isAfter(byDate) ? byDate.plusDays(1) : today.plusDays(1);
        int future = 0;
        if (!from.isAfter(byDate)) {
            List<DayAttendance.DayResult> days =
                    DayAttendance.range(c, student, from, byDate);
            future = days.size();
        }

        double target = targetPercent / 100.0;
        double grandTotal = soFar.workingDays + future;
        double need = Math.max(0, Math.ceil((target * grandTotal - soFar.credit) * 100) / 100.0);
        boolean reachable = need <= future + 1e-9;
        double canMiss = reachable
                ? Math.max(0, Math.floor((soFar.credit + future - target * grandTotal) * 100) / 100.0)
                : 0;

        Double ifAll = grandTotal > 0 ? ((soFar.credit + future) / grandTotal) * 100 : null;
        Double ifNone = grandTotal > 0 ? (soFar.credit / grandTotal) * 100 : null;

        return new Plan(soFar.percent(), soFar.workingDays, future, need, reachable, canMiss, ifAll, ifNone);
    }

}
