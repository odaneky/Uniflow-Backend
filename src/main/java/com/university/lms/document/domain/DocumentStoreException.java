package com.university.lms.document.domain;

import com.university.lms.common.exception.ApplicationException;
import java.io.Serial;
import org.springframework.http.HttpStatus;

/** The object store rejected or failed a read/write. Not a client mistake. */
public class DocumentStoreException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DocumentStoreException(String message) {
        super(DocumentErrorCode.DOCUMENT_STORE_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public DocumentStoreException(String message, Throwable cause) {
        super(DocumentErrorCode.DOCUMENT_STORE_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}
