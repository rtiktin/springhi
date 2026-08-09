package com.springhi.portfolio.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;

public class MarketCalendar {

    private MarketCalendar() {}

    public static boolean isTradingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false;
        return !usMarketHolidays(date.getYear()).contains(date);
    }

    public static LocalDate nextTradingDay(LocalDate from) {
        LocalDate candidate = from.plusDays(1);
        while (!isTradingDay(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    public static LocalDate nextOrSameTradingDay(LocalDate from) {
        LocalDate candidate = from;
        while (!isTradingDay(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private static Set<LocalDate> usMarketHolidays(int year) {
        Set<LocalDate> holidays = new HashSet<>();

        holidays.add(observedHoliday(LocalDate.of(year, Month.JANUARY, 1)));
        holidays.add(nthWeekdayOfMonth(year, Month.JANUARY, DayOfWeek.MONDAY, 3));
        holidays.add(nthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3));
        holidays.add(goodFriday(year));
        holidays.add(lastWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY));
        holidays.add(observedHoliday(LocalDate.of(year, Month.JUNE, 19)));
        holidays.add(observedHoliday(LocalDate.of(year, Month.JULY, 4)));
        holidays.add(nthWeekdayOfMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1));
        holidays.add(nthWeekdayOfMonth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4));
        holidays.add(observedHoliday(LocalDate.of(year, Month.DECEMBER, 25)));

        holidays.remove(null);
        return holidays;
    }

    private static LocalDate observedHoliday(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY) return date.minusDays(1);
        if (dow == DayOfWeek.SUNDAY)   return date.plusDays(1);
        return date;
    }

    private static LocalDate nthWeekdayOfMonth(int year, Month month, DayOfWeek dow, int n) {
        LocalDate first = LocalDate.of(year, month, 1).with(TemporalAdjusters.nextOrSame(dow));
        return first.plusWeeks(n - 1);
    }

    private static LocalDate lastWeekdayOfMonth(int year, Month month, DayOfWeek dow) {
        return LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.lastDayOfMonth())
                .with(TemporalAdjusters.previousOrSame(dow));
    }

    private static LocalDate goodFriday(int year) {
        LocalDate easter = computeEaster(year);
        return easter.minusDays(2);
    }

    private static LocalDate computeEaster(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day   = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
