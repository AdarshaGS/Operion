package com.operion.timetable.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.timetable.Room;
import com.operion.timetable.RoomRepository;
import com.operion.timetable.TimetableService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequirePermission("TIMETABLE_VIEW")
public class RoomController {

	private final TimetableService timetableService;
	private final RoomRepository roomRepository;

	public RoomController(TimetableService timetableService, RoomRepository roomRepository) {
		this.timetableService = timetableService;
		this.roomRepository = roomRepository;
	}

	@GetMapping("/api/v1/timetable/rooms")
	public List<RoomResponse> list() {
		return timetableService.listRooms().stream().map(RoomResponse::from).toList();
	}

	@PostMapping("/api/v1/timetable/rooms")
	@RequirePermission("TIMETABLE_MANAGE")
	public RoomResponse create(@Valid @RequestBody RoomRequest request) {
		return RoomResponse.from(timetableService.createRoom(request.name(), request.roomType(), request.capacity()));
	}

	@PutMapping("/api/v1/timetable/rooms/{id}")
	@RequirePermission("TIMETABLE_MANAGE")
	public RoomResponse update(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
		Room room = find(id);
		return RoomResponse.from(timetableService.updateRoom(room, request.name(), request.roomType(), request.capacity(),
				request.active() == null ? room.isActive() : request.active()));
	}

	@DeleteMapping("/api/v1/timetable/rooms/{id}")
	@RequirePermission("TIMETABLE_MANAGE")
	public void delete(@PathVariable Long id) {
		timetableService.deleteRoom(find(id));
	}

	private Room find(Long id) {
		return roomRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No room with id " + id));
	}
}
