package com.university.lms.course.dto;

import com.university.lms.course.domain.Room;
import java.util.UUID;

public record RoomResponse(UUID id, UUID buildingId, String buildingCode, String code, int capacity) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(), room.getBuilding().getId(), room.getBuilding().getCode(), room.getCode(), room.getCapacity());
    }
}
