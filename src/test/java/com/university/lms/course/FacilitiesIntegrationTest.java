package com.university.lms.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.course.dto.BuildingResponse;
import com.university.lms.course.dto.CreateBuildingRequest;
import com.university.lms.course.dto.CreateRoomRequest;
import com.university.lms.course.dto.RoomResponse;
import com.university.lms.course.service.FacilitiesService;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.RunAs;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * G1: the facilities registry — buildings and rooms with a seating capacity, matched by normalized
 * code against a meeting's free-text room string.
 */
class FacilitiesIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private FacilitiesService facilitiesService;

    @Test
    @DisplayName("a room is registered under its building and listed with a normalized-matchable code")
    void registeringARoomPersistsItUnderItsBuilding() throws Exception {
        String buildingCode = "SCI-" + UUID.randomUUID().toString().substring(0, 6);
        String roomCode = "Lab " + UUID.randomUUID().toString().substring(0, 6);
        BuildingResponse building = RunAs.staff(
                () -> facilitiesService.createBuilding(new CreateBuildingRequest(buildingCode, "Science Block")));

        RoomResponse room = RunAs.staff(
                () -> facilitiesService.createRoom(new CreateRoomRequest(building.id(), roomCode, 24)));

        assertThat(room.buildingId()).isEqualTo(building.id());
        assertThat(room.capacity()).isEqualTo(24);

        List<RoomResponse> rooms = facilitiesService.findRooms(building.id());
        assertThat(rooms).extracting(RoomResponse::id).contains(room.id());
    }

    @Test
    @DisplayName("a duplicate building code is refused")
    void duplicateBuildingCodeRefused() throws Exception {
        String buildingCode = "DUP-" + UUID.randomUUID().toString().substring(0, 6);
        RunAs.staff(() -> facilitiesService.createBuilding(new CreateBuildingRequest(buildingCode, "First")));

        assertThatThrownBy(() -> RunAs.staff(
                        () -> facilitiesService.createBuilding(new CreateBuildingRequest(buildingCode, "Second"))))
                .isInstanceOf(ResourceAlreadyExistsException.class);
    }

    @Test
    @DisplayName("a room code that normalizes the same as an existing one is refused")
    void duplicateNormalizedRoomCodeRefused() throws Exception {
        String buildingCode = "ENG-" + UUID.randomUUID().toString().substring(0, 6);
        String tag = UUID.randomUUID().toString().substring(0, 6);
        BuildingResponse building =
                RunAs.staff(() -> facilitiesService.createBuilding(new CreateBuildingRequest(buildingCode, "Engineering")));
        RunAs.staff(() -> facilitiesService.createRoom(new CreateRoomRequest(building.id(), "Lab " + tag, 24)));

        assertThatThrownBy(() -> RunAs.staff(
                        () -> facilitiesService.createRoom(new CreateRoomRequest(building.id(), "Lab-" + tag, 30))))
                .isInstanceOf(ResourceAlreadyExistsException.class);
    }

    @Test
    @DisplayName("a room under a nonexistent building is refused")
    void roomUnderMissingBuildingRefused() {
        assertThatThrownBy(() -> RunAs.staff(
                        () -> facilitiesService.createRoom(new CreateRoomRequest(UUID.randomUUID(), "Ghost Room", 20))))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
