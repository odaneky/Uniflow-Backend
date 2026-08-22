package com.university.lms.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a test that needs a real container runtime.
 *
 * <p>Integration tests run against genuine PostgreSQL rather than an in-memory substitute, because
 * the behaviour under test — unique-index enforcement, guarded UPDATE semantics, transaction
 * isolation — is exactly the behaviour an emulated database gets wrong. The consequence is that
 * they cannot run where Docker is absent, so they skip cleanly instead of failing the build and
 * training people to ignore red.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DockerAvailableCondition.class)
public @interface RequiresDocker {}
