package com.university.lms.course.repository;

import com.university.lms.course.domain.Room;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByNormalizedCode(String normalizedCode);

    boolean existsByNormalizedCode(String normalizedCode);

    List<Room> findByBuildingId(UUID buildingId);
}
