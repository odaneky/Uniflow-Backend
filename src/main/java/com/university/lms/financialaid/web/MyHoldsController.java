package com.university.lms.financialaid.web;

import com.university.lms.financialaid.dto.ServiceHoldResponse;
import com.university.lms.financialaid.service.ServiceHoldService;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/holds")
public class MyHoldsController {

    private final ServiceHoldService serviceHoldService;
    private final StudentDirectory studentDirectory;
    private final CurrentUserProvider currentUserProvider;

    public MyHoldsController(
            ServiceHoldService serviceHoldService,
            StudentDirectory studentDirectory,
            CurrentUserProvider currentUserProvider) {
        this.serviceHoldService = serviceHoldService;
        this.studentDirectory = studentDirectory;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<ServiceHoldResponse> ownActiveHolds() {
        UUID studentId = studentDirectory
                .studentIdOfUser(currentUserProvider.require().userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
        return serviceHoldService.activeHoldsFor(studentId);
    }
}
