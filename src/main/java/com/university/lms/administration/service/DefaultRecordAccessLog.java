package com.university.lms.administration.service;

import com.university.lms.administration.api.RecordAccessLog;
import com.university.lms.administration.domain.RecordAccessEvent;
import com.university.lms.administration.repository.RecordAccessEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultRecordAccessLog implements RecordAccessLog {

    private static final Logger log = LoggerFactory.getLogger(DefaultRecordAccessLog.class);
    private static final int MAX_DETAILS = 500;

    private final RecordAccessEventRepository repository;

    public DefaultRecordAccessLog(RecordAccessEventRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID actorUserId, String actorLabel, UUID studentId, String recordType, String action, String details) {
        try {
            repository.save(new RecordAccessEvent(
                    actorUserId,
                    truncate(actorLabel, 200),
                    studentId,
                    recordType,
                    action,
                    truncate(details, MAX_DETAILS),
                    Instant.now()));
        } catch (RuntimeException ex) {
            log.error("Failed to write record access log for student {}", studentId, ex);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
