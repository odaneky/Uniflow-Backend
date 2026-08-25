package com.university.lms.course.service;

import com.university.lms.administration.api.Auditable;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.course.domain.Building;
import com.university.lms.course.domain.CourseErrorCode;
import com.university.lms.course.domain.Room;
import com.university.lms.course.dto.BuildingResponse;
import com.university.lms.course.dto.CreateBuildingRequest;
import com.university.lms.course.dto.CreateRoomRequest;
import com.university.lms.course.dto.RoomResponse;
import com.university.lms.course.repository.BuildingRepository;
import com.university.lms.course.repository.RoomRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * G1: the facilities registry — buildings and the rooms in them, each with a seating capacity —
 * that {@link CourseService#replaceMeetings} consults to refuse scheduling a section into a room
 * too small for it. Additive: a meeting's {@code room} stays free text, matched against {@link
 * Room#normalizedCode} only when one has been registered.
 */
@Service
@Transactional(readOnly = true)
public class FacilitiesService {

    private final BuildingRepository buildingRepository;
    private final RoomRepository roomRepository;

    public FacilitiesService(BuildingRepository buildingRepository, RoomRepository roomRepository) {
        this.buildingRepository = buildingRepository;
        this.roomRepository = roomRepository;
    }

    public List<BuildingResponse> findBuildings() {
        return buildingRepository.findAll().stream().map(BuildingResponse::from).toList();
    }

    public List<RoomResponse> findRooms(UUID buildingId) {
        List<Room> rooms = buildingId == null ? roomRepository.findAll() : roomRepository.findByBuildingId(buildingId);
        return rooms.stream().map(RoomResponse::from).toList();
    }

    @Auditable(
            action = AuditTrail.Action.BUILDING_CREATED,
            entityType = AuditTrail.EntityType.BUILDING,
            entityId = "#result.id()",
            details = "#result.code()")
    @Transactional
    public BuildingResponse createBuilding(CreateBuildingRequest request) {
        String code = request.code().trim();
        if (buildingRepository.existsByCode(code)) {
            throw new ResourceAlreadyExistsException(
                    CourseErrorCode.BUILDING_CODE_ALREADY_EXISTS, "A building with code " + code + " already exists");
        }
        return BuildingResponse.from(buildingRepository.save(new Building(code, request.name().trim())));
    }

    @Auditable(
            action = AuditTrail.Action.ROOM_CREATED,
            entityType = AuditTrail.EntityType.ROOM,
            entityId = "#result.id()",
            details = "#result.code()")
    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        Building building = buildingRepository
                .findById(request.buildingId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CourseErrorCode.BUILDING_NOT_FOUND, "No building exists with id " + request.buildingId()));
        String normalized = Room.normalize(request.code());
        if (roomRepository.existsByNormalizedCode(normalized)) {
            throw new ResourceAlreadyExistsException(
                    CourseErrorCode.ROOM_CODE_ALREADY_EXISTS,
                    "A room matching \"" + request.code() + "\" already exists");
        }
        return RoomResponse.from(roomRepository.save(new Room(building, request.code().trim(), request.capacity())));
    }
}
