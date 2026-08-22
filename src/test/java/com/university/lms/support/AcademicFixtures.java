package com.university.lms.support;

import com.university.lms.academic.domain.AcademicTerm;
import com.university.lms.academic.domain.AcademicYear;
import com.university.lms.academic.domain.Department;
import com.university.lms.academic.domain.Faculty;
import com.university.lms.academic.domain.Programme;
import com.university.lms.academic.domain.TermType;
import com.university.lms.academic.repository.AcademicTermRepository;
import com.university.lms.academic.repository.AcademicYearRepository;
import com.university.lms.academic.repository.DepartmentRepository;
import com.university.lms.academic.repository.FacultyRepository;
import com.university.lms.academic.repository.ProgrammeRepository;
import com.university.lms.course.domain.Course;
import com.university.lms.course.domain.CourseComponent;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.course.repository.CourseRepository;
import com.university.lms.course.repository.CourseSectionRepository;
import com.university.lms.identity.domain.User;
import com.university.lms.identity.repository.UserRepository;
import com.university.lms.student.domain.Student;
import com.university.lms.student.repository.StudentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the chain of records an enrolment depends on — user, faculty, department, programme,
 * year, term, course, section — so that individual tests can state only what they actually care
 * about.
 */
@Component
public class AcademicFixtures {

    /**
     * Distinguishes one test run from the next.
     *
     * <p>The sequence alone is not enough: it restarts at zero every JVM, so a second run against
     * a database that persists between runs collides on the unique code columns. Testcontainers
     * masks that by starting empty each time, which is exactly why the fixtures must not depend on
     * it — the same tests are expected to pass against a supplied database too.
     */
    private static final String RUN = Long.toString(System.nanoTime(), 36).toUpperCase(java.util.Locale.ROOT);

    /**
     * Static, not per-instance. Spring caches a context per unique test configuration, so a suite
     * with (say) a plain {@code @SpringBootTest} and an {@code @AutoConfigureMockMvc} one gets two
     * {@code AcademicFixtures} beans — each with its own counter, each minting {@code F...1} and
     * colliding on the unique code index. Per-JVM is the scope that matches the database.
     */
    private static final AtomicInteger sequence = new AtomicInteger();

    /** Short, unique-per-run token that fits the 20-character code columns. */
    private String token(int n) {
        String suffix = RUN.substring(Math.max(0, RUN.length() - 7));
        return suffix + n;
    }

    private final UserRepository userRepository;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final ProgrammeRepository programmeRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicTermRepository academicTermRepository;
    private final CourseRepository courseRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final StudentRepository studentRepository;

    public AcademicFixtures(
            UserRepository userRepository,
            FacultyRepository facultyRepository,
            DepartmentRepository departmentRepository,
            ProgrammeRepository programmeRepository,
            AcademicYearRepository academicYearRepository,
            AcademicTermRepository academicTermRepository,
            CourseRepository courseRepository,
            CourseSectionRepository courseSectionRepository,
            StudentRepository studentRepository) {
        this.userRepository = userRepository;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.programmeRepository = programmeRepository;
        this.academicYearRepository = academicYearRepository;
        this.academicTermRepository = academicTermRepository;
        this.courseRepository = courseRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.studentRepository = studentRepository;
    }

    private int next() {
        return sequence.incrementAndGet();
    }

    @Transactional
    public User user() {
        int n = next();
        return userRepository.save(
                new User("user" + token(n), "user" + token(n) + "@university.test", "Test", "User" + n));
    }

    @Transactional
    public Programme programme() {
        int n = next();
        Faculty faculty = facultyRepository.save(new Faculty("F" + token(n), "Faculty " + n));
        Department department = departmentRepository.save(new Department(faculty, "D" + token(n), "Department " + n));
        return programmeRepository.save(new Programme(department, "P" + token(n), "Programme " + n, "BSc", 120, 3));
    }

    /** A term whose registration window is open right now. */
    @Transactional
    public AcademicTerm openTerm() {
        int n = next();
        AcademicYear year = academicYearRepository.save(
                new AcademicYear("Y" + token(n), LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31)));
        AcademicTerm term = new AcademicTerm(
                year, "Semester " + n, TermType.SEMESTER, n, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 20));
        term.openRegistration(
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(7, ChronoUnit.DAYS));
        return academicTermRepository.save(term);
    }

    @Transactional
    public Student student(Programme programme) {
        User user = user();
        return studentRepository.save(
                new Student(user.getId(), "S" + token(next()), programme.getId(), LocalDate.of(2026, 9, 1)));
    }

    /** An OPEN section with the requested capacity, ready to accept enrolments. */
    @Transactional
    public CourseSection openSection(AcademicTerm term, int capacity) {
        int n = next();
        Programme programme = programme();
        Course course = new Course(
                "C" + token(n),
                "Test Course " + n,
                3,
                1,
                programme.getDepartment().getId(),
                Set.of(CourseComponent.LECTURE));
        course.activate();
        courseRepository.save(course);

        CourseSection section = new CourseSection(course, term.getId(), "A", capacity);
        section.open();
        return courseSectionRepository.save(section);
    }

    public CourseSection reload(CourseSection section) {
        return courseSectionRepository.findById(section.getId()).orElseThrow();
    }
}
