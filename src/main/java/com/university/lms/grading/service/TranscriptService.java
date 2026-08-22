package com.university.lms.grading.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.curriculum.repository.TransferCreditRepository;
import com.university.lms.grading.api.AcademicRecord;
import com.university.lms.grading.domain.Grade;
import com.university.lms.grading.repository.GradeRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TranscriptService {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 11);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final GradeRepository gradeRepository;
    private final CourseCatalog courseCatalog;
    private final AcademicStructure academicStructure;
    private final StudentDirectory studentDirectory;
    private final TransferCreditRepository transferCreditRepository;
    private final CurrentUserProvider currentUserProvider;
    private final GradeService gradeService;

    public TranscriptService(
            GradeRepository gradeRepository,
            CourseCatalog courseCatalog,
            AcademicStructure academicStructure,
            StudentDirectory studentDirectory,
            TransferCreditRepository transferCreditRepository,
            CurrentUserProvider currentUserProvider,
            GradeService gradeService) {
        this.gradeRepository = gradeRepository;
        this.courseCatalog = courseCatalog;
        this.academicStructure = academicStructure;
        this.studentDirectory = studentDirectory;
        this.transferCreditRepository = transferCreditRepository;
        this.currentUserProvider = currentUserProvider;
        this.gradeService = gradeService;
    }

    public byte[] officialTranscript(UUID studentId) {
        requireAccess(studentId);
        StudentDirectory.StudentSummary student = studentDirectory
                .findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND, "No student exists with id " + studentId));
        AcademicRecord.Summary summary = gradeService.summaryOf(studentId);
        List<Grade> overall = gradeRepository.findAllByStudentIdAndPublishedTrue(studentId).stream()
                .filter(grade -> grade.getAssessmentId() == null)
                .sorted(Comparator.comparing(Grade::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, buffer);
            document.open();
            document.add(new Paragraph("Official Academic Transcript", TITLE));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Student: " + student.studentNumber(), BODY));
            document.add(new Paragraph(
                    "Generated: " + DATE.format(Instant.now().atZone(ZoneOffset.UTC)), BODY));
            if (summary.gpa() != null) {
                document.add(new Paragraph("Cumulative GPA: " + summary.gpa(), BODY));
            }
            document.add(new Paragraph(
                    "Credits earned: " + summary.creditsEarned() + " / attempted " + summary.creditsAttempted(),
                    BODY));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Coursework", TITLE));
            for (Grade grade : overall) {
                courseCatalog.findSection(grade.getCourseSectionId()).ifPresent(section -> {
                    String termLabel = academicStructure
                            .findTerm(section.academicTermId(), Instant.now())
                            .map(term -> term.academicYearCode() + " " + term.name())
                            .orElse("");
                    try {
                        document.add(new Paragraph(
                                section.courseCode()
                                        + " · "
                                        + grade.getLetter()
                                        + " · "
                                        + termLabel,
                                BODY));
                    } catch (DocumentException ex) {
                        throw new IllegalStateException("Failed to render transcript line", ex);
                    }
                });
            }
            var transfers = transferCreditRepository.findByStudentIdOrderByAwardedAtDesc(studentId);
            if (!transfers.isEmpty()) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph("Transfer Credit", TITLE));
                for (var transfer : transfers) {
                    document.add(new Paragraph(
                            transfer.getExternalCourseCode()
                                    + " ("
                                    + transfer.getExternalInstitution()
                                    + ") · "
                                    + transfer.getCreditsAwarded()
                                    + " credits",
                            BODY));
                }
            }
            document.close();
            return buffer.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate transcript PDF", ex);
        }
    }

    public byte[] ownOfficialTranscript() {
        CurrentUser caller = currentUserProvider.require();
        UUID studentId = studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
        return officialTranscript(studentId);
    }

    private void requireAccess(UUID studentId) {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR)) {
            return;
        }
        UUID own = studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
        if (!own.equals(studentId)) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
    }
}
