package com.operion.timetable.api;

import jakarta.validation.constraints.NotBlank;

/** active is optional on create (defaults to true). */
public record RoomRequest(@NotBlank String name, String roomType, Integer capacity, Boolean active) {
}
