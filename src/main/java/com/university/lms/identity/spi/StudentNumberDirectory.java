package com.university.lms.identity.spi;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves an institutional student number to the local user account it belongs to.
 *
 * <p>An <b>outbound</b> port: declared by {@code identity} because identity correlation needs it,
 * implemented by {@code student} because that module owns student numbers. Declaring it the other
 * way round — identity calling the student module directly — would be a cycle, since the student
 * module already depends on identity to answer "who is calling".
 *
 * <p>Optional at runtime. The identity module must continue to work in a deployment where no
 * student module is present, correlating on subject and email alone.
 */
public interface StudentNumberDirectory {

    /**
     * @param studentNumber exactly as the identity provider asserted it, trimmed
     * @return the user account behind that student record, or empty if the number is unknown here
     */
    Optional<UUID> findUserIdByStudentNumber(String studentNumber);
}
