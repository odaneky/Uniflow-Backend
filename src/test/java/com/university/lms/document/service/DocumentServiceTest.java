package com.university.lms.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.university.lms.document.api.DocumentStore.StoredFile;
import com.university.lms.document.config.StorageProperties;
import com.university.lms.document.domain.Document;
import com.university.lms.document.domain.StorageProvider;
import com.university.lms.document.repository.DocumentRepository;
import com.university.lms.document.storage.BlobStore;
import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.student.api.StudentDirectory;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code store()} used to hard-code {@code StorageProvider.LOCAL_FILESYSTEM} on every document
 * regardless of which {@link BlobStore} was actually wired in — harmless while local filesystem was
 * the only implementation, but a document would have recorded the wrong provider the moment a
 * second one existed. It now asks the injected store what it is.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final UUID OWNER_ID = UUID.randomUUID();

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private BlobStore blobStore;

    @Mock
    private StaffAppointments staffAppointments;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private AcademicStructure academicStructure;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(
                documentRepository,
                currentUserProvider,
                userDirectory,
                blobStore,
                new StorageProperties(null, 0, null, null, null, null, null),
                staffAppointments,
                studentDirectory,
                academicStructure);
        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void storedDocumentRecordsWhicheverProviderTheInjectedBlobStoreActuallyIs() {
        when(blobStore.provider()).thenReturn(StorageProvider.S3);

        StoredFile stored = service.store(OWNER_ID, "ASSESSMENT_SUBMISSION", "evidence.pdf", "application/pdf", "content".getBytes());

        assertThat(stored).isNotNull();
        org.mockito.ArgumentCaptor<Document> captor = org.mockito.ArgumentCaptor.forClass(Document.class);
        org.mockito.Mockito.verify(documentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStorageProvider()).isEqualTo(StorageProvider.S3);
    }

    @Test
    void aLocalBlobStoreRecordsLocalFilesystem() {
        when(blobStore.provider()).thenReturn(StorageProvider.LOCAL_FILESYSTEM);

        service.store(OWNER_ID, "ASSESSMENT_SUBMISSION", "evidence.pdf", "application/pdf", "content".getBytes());

        org.mockito.ArgumentCaptor<Document> captor = org.mockito.ArgumentCaptor.forClass(Document.class);
        org.mockito.Mockito.verify(documentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStorageProvider()).isEqualTo(StorageProvider.LOCAL_FILESYSTEM);
    }
}
