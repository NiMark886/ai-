package com.offerpilot.interview.web.dto;

import java.util.List;
import java.util.Map;

public record StatsOverview(
        int totalInterviews,
        int completedInterviews,
        double averageScore,
        int bestScore,
        Map<String, Integer> dimensionAverages,
        List<ScorePoint> scoreTrend
) {
}
