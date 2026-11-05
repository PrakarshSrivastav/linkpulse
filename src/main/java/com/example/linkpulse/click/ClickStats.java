package com.example.linkpulse.click;

import java.time.LocalDate;
import java.util.List;

public record ClickStats(String code, long total, List<DayCount> perDay, List<ReferrerCount> topReferrers) {

    public record DayCount(LocalDate day, long clicks) {}

    public record ReferrerCount(String referrer, long clicks) {}
}