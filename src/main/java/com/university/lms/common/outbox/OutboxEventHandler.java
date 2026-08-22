package com.university.lms.common.outbox;

/** Processes one outbox event type — registered beans are invoked by {@link OutboxDispatcher}. */
public interface OutboxEventHandler {

    /** Event type this handler supports, e.g. {@code MessageSent}. */
    String eventType();

    void handle(DomainOutbox row) throws Exception;
}
