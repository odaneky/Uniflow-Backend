package com.university.lms.admissions;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.admissions.domain.ApplicationStatus;
import com.university.lms.admissions.repository.ApplicationRepository;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class ApplicationRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ApplicationRepository applicationRepository;

    @Test
    void searchWithStatusesAndSort() {
        var page = applicationRepository.search(
                List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.IN_REVIEW),
                null,
                null,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt")));
        assertThat(page.getTotalElements()).isZero();
    }
}
