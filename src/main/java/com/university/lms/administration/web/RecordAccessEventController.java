package com.university.lms.administration.web;

import com.university.lms.administration.domain.RecordAccessEvent;
import com.university.lms.administration.dto.RecordAccessEventResponse;
import com.university.lms.administration.repository.RecordAccessEventRepository;
import com.university.lms.common.dto.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/record-access")
public class RecordAccessEventController {

    private final RecordAccessEventRepository repository;

    public RecordAccessEventController(RecordAccessEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/students/{studentId}")
    public PageResponse<RecordAccessEventResponse> forStudent(
            @PathVariable UUID studentId,
            @PageableDefault(size = 50, sort = "accessedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(
                repository.findByStudentIdOrderByAccessedAtDesc(studentId, pageable), RecordAccessEventResponse::from);
    }
}
