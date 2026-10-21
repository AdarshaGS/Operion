package com.operion.timetable;

import com.operion.common.InvalidRequestException;
import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A place a timetable slot can be held in (classroom, lab, hall...). roomType is free text the
 * organisation chooses - nothing branches on it. An inactive room keeps its history but cannot be picked for new slots.
 */
@Getter
@Entity
@Table(name = "rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends TenantScopedEntity {

	@Column(nullable = false)
	private String name;

	@Column(name = "room_type")
	private String roomType;

	private Integer capacity;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	public Room(String name, String roomType, Integer capacity) {
		this.active = true;
		update(name, roomType, capacity, true);
	}

	public void update(String name, String roomType, Integer capacity, boolean active) {
		if (name == null || name.isBlank()) {
			throw new InvalidRequestException("Room name is required");
		}
		if (capacity != null && capacity < 1) {
			throw new InvalidRequestException("Room capacity must be at least 1");
		}
		this.name = name.trim();
		this.roomType = roomType == null || roomType.isBlank() ? null : roomType.trim();
		this.capacity = capacity;
		this.active = active;
	}
}
