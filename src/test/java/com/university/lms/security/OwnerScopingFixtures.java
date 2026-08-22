package com.university.lms.security;

import com.university.lms.academic.domain.AcademicTerm;
import com.university.lms.academic.domain.Programme;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.course.repository.CourseSectionRepository;
import com.university.lms.enrollment.domain.Enrollment;
import com.university.lms.enrollment.repository.EnrollmentRepository;
import com.university.lms.identity.domain.User;
import com.university.lms.identity.repository.UserRepository;
import com.university.lms.student.domain.Student;
import com.university.lms.student.repository.StudentRepository;
import com.university.lms.support.AcademicFixtures;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Builds people who exist on <em>both</em> sides of the identity boundary: a Keycloak subject and
 * the local rows it resolves to.
 *
 * <p>{@link AcademicFixtures} cannot do this — it predates the link and creates users with no
 * subject, which is exactly the state that cannot be logged in as.
 */
@Component
public class OwnerScopingFixtures {

    /** A person as the tests need them: the token subject, and everything it must resolve to. */
    public record Person(String subject, UUID userId, UUID studentId, String studentNumber) {}

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final AcademicFixtures academicFixtures;

    // Cached because they are shared scenery, not the thing under test. Each is created by its own
    // committed call, so a later rollback cannot leave this field pointing at a row that no longer
    // exists — which is precisely what happened when these methods were transactional.
    private volatile Programme programme;
    private volatile AcademicTerm term;

    public OwnerScopingFixtures(
            UserRepository userRepository,
            StudentRepository studentRepository,
            EnrollmentRepository enrollmentRepository,
            CourseSectionRepository courseSectionRepository,
            AcademicFixtures academicFixtures) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.academicFixtures = academicFixtures;
    }

    /** A student who can authenticate: a subject, a user row linked to it, and a student record. */
    public Person student() {
        int n = SEQUENCE.incrementAndGet();
        String subject = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(User.fromIdentityProvider(
                subject, "owner" + n + "-" + subject.substring(0, 8), subject.substring(0, 8) + "@university.test",
                "Owner", "Student" + n));

        String studentNumber = "9" + String.format("%09d", Math.abs(subject.hashCode() % 1_000_000_000));
        Student student = studentRepository.saveAndFlush(
                new Student(user.getId(), studentNumber, programme().getId(), LocalDate.of(2026, 9, 1)));

        return new Person(subject, user.getId(), student.getId(), student.getStudentNumber());
    }

    /** A lecturer who can authenticate: a subject and a user row, no student record. */
    public Person lecturer() {
        int n = SEQUENCE.incrementAndGet();
        String subject = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(User.fromIdentityProvider(
                subject,
                "lecturer" + n + "-" + subject.substring(0, 8),
                "lecturer" + n + "-" + subject.substring(0, 8) + "@university.test",
                "Lee",
                "Lecturer" + n));
        return new Person(subject, user.getId(), null, null);
    }

    /** A section with room in it. Capacity is generous so a test never fails for being full. */
    public UUID openSection() {
        return academicFixtures.openSection(term(), 50).getId();
    }

    public UUID openSectionTaughtBy(UUID lecturerUserId) {
        CourseSection section = academicFixtures.openSection(term(), 50);
        section.assignLecturer(lecturerUserId);
        return courseSectionRepository.saveAndFlush(section).getId();
    }

    public UUID enrol(UUID studentId, UUID sectionId) {
        return enrollmentRepository
                .saveAndFlush(new Enrollment(studentId, sectionId))
                .getId();
    }

    private Programme programme() {
        if (programme == null) {
            synchronized (this) {
                if (programme == null) {
                    programme = academicFixtures.programme();
                }
            }
        }
        return programme;
    }

    private AcademicTerm term() {
        if (term == null) {
            synchronized (this) {
                if (term == null) {
                    term = academicFixtures.openTerm();
                }
            }
        }
        return term;
    }
}
