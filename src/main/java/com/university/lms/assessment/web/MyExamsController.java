package com.university.lms.assessment.web;

import com.university.lms.assessment.dto.ExamTimetableResponse;
import com.university.lms.assessment.service.MyExamsService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own exam timetable.
 *
 * <p>Takes no identifier, like the rest of the {@code /me} family. An exam timetable places a named
 * person in a known room at a known time, so there is deliberately no version of this that accepts
 * somebody else's student id.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MyExamsController {

    private final MyExamsService myExamsService;

    public MyExamsController(MyExamsService myExamsService) {
        this.myExamsService = myExamsService;
    }

    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/exams")
    public ExamTimetableResponse exams() {
        return myExamsService.ownTimetable();
    }
}
