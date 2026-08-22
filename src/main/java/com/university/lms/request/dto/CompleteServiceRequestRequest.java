package com.university.lms.request.dto;

import java.util.UUID;

public record CompleteServiceRequestRequest(String note, UUID deliverableDocumentId) {}
