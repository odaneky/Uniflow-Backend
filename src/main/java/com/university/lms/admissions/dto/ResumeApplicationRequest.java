package com.university.lms.admissions.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * "Continue my application" — reference number plus the email it was started with.
 *
 * <p>Two factors on purpose. The reference appears on confirmation screens and in email, so it is
 * not really secret; pairing it with the address the application belongs to means possession of a
 * reference alone gets nobody in. The resulting link is emailed to that address rather than
 * returned in the response, so a caller who guesses a pair still learns nothing.
 */
public record ResumeApplicationRequest(
        @NotBlank(message = "is required") @Size(max = 20) String reference,
        @NotBlank(message = "is required") @Email(message = "must be a valid email address") @Size(max = 255)
                String applicantEmail) {}
