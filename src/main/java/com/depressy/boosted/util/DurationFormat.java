package com.depressy.boosted.util;

public class DurationFormat {
    // Returns a compact human readable string like "2d 3h 10m 5s"
    public static String format(long millis) {
        if (millis <= 0) return "0s";
        long seconds = millis / 1000;
        long d = seconds / 86_400; seconds %= 86_400;
        long h = seconds / 3_600; seconds %= 3_600;
        long m = seconds / 60; seconds %= 60;
        long s = seconds;

        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
