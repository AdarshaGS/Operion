package com.operion.timetable.api;

import com.operion.timetable.Room;

public record RoomResponse(Long id, String name, String roomType, Integer capacity, boolean active) {

	static RoomResponse from(Room room) {
		return new RoomResponse(room.getId(), room.getName(), room.getRoomType(), room.getCapacity(), room.isActive());
	}
}
