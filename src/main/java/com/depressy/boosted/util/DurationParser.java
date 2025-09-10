package com.depressy.boosted.util;

public class DurationParser {
    public static long parseToMillis(String raw) {
        if (raw == null || raw.isEmpty()) return 0L;
        long total = 0L;
        long num = 0L;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isDigit(c)) {
                num = (num * 10) + (c - '0');
                continue;
            }
            if (num <= 0) continue;
            switch (Character.toLowerCase(c)) {
                case 'd': total += num * 24L * 60L * 60L * 1000L; num = 0; break;
                case 'h': total += num * 60L * 60L * 1000L; num = 0; break;
                case 'm': total += num * 60L * 1000L; num = 0; break;
                case 's': total += num * 1000L; num = 0; break;
                default: // ignore
            }
        }
        if (num > 0) total += num * 1000L; // trailing seconds
        return total;
    }
}
