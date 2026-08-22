package com.university.lms.administration.api;

import java.util.UUID;

/** FERPA record-of-access logging — who viewed which student record and when. */
public interface RecordAccessLog {

    final class RecordType {
        public static final String STUDENT = "Student";
        public static final String GRADES = "Grades";
        public static final String ENROLLMENT = "Enrollment";
        public static final String FINANCE = "Finance";
        public static final String DOCUMENT = "Document";

        private RecordType() {}
    }

    final class Action {
        public static final String VIEW = "VIEW";
        public static final String EXPORT = "EXPORT";
        public static final String MODIFY = "MODIFY";

        private Action() {}
    }

    void record(UUID actorUserId, String actorLabel, UUID studentId, String recordType, String action, String details);
}
