package com.university.lms.request.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.common.security.SecurityRoles;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.request.repository.ServiceRequestRepository;
import com.university.lms.student.api.StudentDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pins the type-to-role mapping behind {@link com.university.lms.request.api.RequestDirectory},
 * kept out of {@code notification.dispatch.ServiceRequestOutboxHandler} so that module never
 * switches on {@link ServiceRequestType} directly — see the javadoc on
 * {@code RequestDirectory.additionalNotificationRole}.
 */
@ExtendWith(MockitoExtension.class)
class DefaultRequestDirectoryTest {

    @Mock
    private ServiceRequestRepository repository;

    @Mock
    private StudentDirectory studentDirectory;

    private DefaultRequestDirectory directory;

    @BeforeEach
    void setUp() {
        directory = new DefaultRequestDirectory(repository, studentDirectory);
    }

    @Test
    void sapAppealAlsoNotifiesFinancialAidOfficers() {
        assertThat(directory.additionalNotificationRole(ServiceRequestType.SAP_APPEAL))
                .contains(SecurityRoles.FINANCIAL_AID_OFFICER);
    }

    @ParameterizedTest
    @EnumSource(value = ServiceRequestType.class, names = "SAP_APPEAL", mode = EnumSource.Mode.EXCLUDE)
    void everyOtherTypeHasNoAdditionalNotificationRole(ServiceRequestType type) {
        assertThat(directory.additionalNotificationRole(type)).isEmpty();
    }
}
