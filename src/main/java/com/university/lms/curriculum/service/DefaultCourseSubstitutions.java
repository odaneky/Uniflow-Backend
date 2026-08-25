package com.university.lms.curriculum.service;

import com.university.lms.administration.api.Auditable;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.curriculum.api.CourseSubstitutions;
import com.university.lms.curriculum.domain.CourseSubstitution;
import com.university.lms.curriculum.repository.CourseSubstitutionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultCourseSubstitutions implements CourseSubstitutions {

    private final CourseSubstitutionRepository repository;

    public DefaultCourseSubstitutions(CourseSubstitutionRepository repository) {
        this.repository = repository;
    }

    // Scoped to the student, not to the substitution row itself: record() is void and the row's
    // own id is never surfaced to the caller, but the student is a real, queryable entity and
    // this is exactly the kind of correction a registrar reviewing a student's record would want
    // to find.
    @Auditable(
            action = AuditTrail.Action.COURSE_SUBSTITUTION_RECORDED,
            entityType = AuditTrail.EntityType.STUDENT,
            entityId = "#studentId",
            details = "'Substitutes for required course ' + #requiredCourseId")
    @Override
    @Transactional
    public void record(
            UUID studentId, UUID requiredCourseId, UUID substituteCourseId, UUID serviceRequestId, UUID approvedBy) {
        // Replace, not update: the entity carries no mutator, so approving the same (student,
        // required course) pair again — a registrar correcting an earlier substitution — is a
        // delete-and-reinsert, never an edit of the row that was there.
        //
        // The delete must physically flush before the insert: Hibernate's default action-queue
        // order runs insertions before deletions, so without this the new row would reach the
        // database first and collide with uk_course_substitutions_student_required against the row
        // still there.
        repository.findByStudentIdAndRequiredCourseId(studentId, requiredCourseId).ifPresent(existing -> {
            repository.delete(existing);
            repository.flush();
        });
        repository.save(
                new CourseSubstitution(studentId, requiredCourseId, substituteCourseId, serviceRequestId, approvedBy));
    }
}
