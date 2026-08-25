package com.university.lms.course.service;

import com.university.lms.course.api.CourseCatalog;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.course.domain.Room;
import com.university.lms.course.repository.RoomRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Answers "is this room big enough" — a different question from {@link TeachingConflictChecker},
 * which only asks whether the room is already busy. A section could pass every clash check and
 * still be scheduled into a room smaller than its own enrollment cap; nothing checked that until
 * this existed.
 *
 * <p>Additive, like the room registry itself: a meeting whose {@code room} matches nothing in
 * {@link RoomRepository} is silently skipped, since not every room has necessarily been registered
 * yet.
 */
@Component
class RoomCapacityChecker {

    private final RoomRepository roomRepository;

    RoomCapacityChecker(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    /**
     * The first meeting whose registered room is too small for the section's capacity, described
     * for the caller's error message — or empty when every meeting's room (that is registered at
     * all) can seat the section.
     */
    Optional<String> capacityIssue(CourseSection section, List<CourseCatalog.Meeting> proposed) {
        for (CourseCatalog.Meeting meeting : proposed) {
            String normalized = Room.normalize(meeting.room());
            if (normalized == null) {
                continue;
            }
            Optional<Room> room = roomRepository.findByNormalizedCode(normalized);
            if (room.isEmpty()) {
                continue;
            }
            if (section.getCapacity() > room.get().getCapacity()) {
                return Optional.of(meeting.room() + " seats " + room.get().getCapacity()
                        + ", but the section's capacity is " + section.getCapacity());
            }
        }
        return Optional.empty();
    }
}
