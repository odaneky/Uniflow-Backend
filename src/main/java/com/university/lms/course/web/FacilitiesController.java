package com.university.lms.course.web;

import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import com.university.lms.course.dto.BuildingResponse;
import com.university.lms.course.dto.CreateBuildingRequest;
import com.university.lms.course.dto.CreateRoomRequest;
import com.university.lms.course.dto.RoomResponse;
import com.university.lms.course.service.FacilitiesService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** G1: buildings and their rooms, each with a seating capacity. */
@RestController
@RequestMapping("/api/v1")
public class FacilitiesController {

    private final FacilitiesService facilitiesService;

    public FacilitiesController(FacilitiesService facilitiesService) {
        this.facilitiesService = facilitiesService;
    }

    @AccessClass(AUTHENTICATED)
    @GetMapping("/buildings")
    public List<BuildingResponse> findBuildings() {
        return facilitiesService.findBuildings();
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping("/buildings")
    public BuildingResponse createBuilding(@Valid @RequestBody CreateBuildingRequest request) {
        return facilitiesService.createBuilding(request);
    }

    @AccessClass(AUTHENTICATED)
    @GetMapping("/rooms")
    public List<RoomResponse> findRooms(@RequestParam(required = false) UUID buildingId) {
        return facilitiesService.findRooms(buildingId);
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping("/rooms")
    public RoomResponse createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return facilitiesService.createRoom(request);
    }
}
