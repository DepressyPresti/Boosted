package com.depressy.boosted.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DurationParser {

    // Supports mixed units like "1h30m15s", "2d4h", "90s", "10m", "1h", "2d"
    private static final Pattern PART = Pattern.compile("(\\d+)([smhdSMHD])");

    public static long parseToMillis(String input) {
        if (input == null || input.isEmpty()) return -1;
        long totalMs = 0;
        Matcher m = PART.matcher(input);
        int found = 0;
        while (m.find()) {
            found++;
            long n = Long.parseLong(m.group(1));
            char u = Character.toLowerCase(m.group(2).charAt(0));
            long mult;
            switch (u) {
                case 's': mult = 1000L; break;
                case 'm': mult = 60_000L; break;
                case 'h': mult = 3_600_000L; break;
                case 'd': mult = 86_400_000L; break;
                default: mult = 0L;
            }
            totalMs += n * mult;
        }
        if (found == 0) return -1;
        return totalMs;
    }
}
