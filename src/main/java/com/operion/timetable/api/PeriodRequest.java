package com.operion.timetable.api;

import java.time.LocalTime;

public record PeriodRequest(Integer ordinal, String label, LocalTime startTime, LocalTime endTime, Boolean breakSlot) {
}
