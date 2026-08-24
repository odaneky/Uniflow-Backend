package com.university.lms.admissions.web;

import com.university.lms.admissions.dto.ApplicationAccessResponse;
import com.university.lms.admissions.dto.ApplicationResponse;
import com.university.lms.admissions.dto.AttachApplicationDocumentRequest;
import com.university.lms.admissions.dto.CreateApplicationRequest;
import com.university.lms.admissions.dto.ResumeApplicationRequest;
import com.university.lms.admissions.dto.UpdateApplicationRequest;
import com.university.lms.admissions.service.AdmissionsService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Applicant-facing endpoints, reachable without a university identity.
 *
 * <p>An applicant has no account, and should not have one: they are not a student yet, and UniFlow
 * must not become a place where non-students get identities. So access to a single application is
 * granted by a <b>capability token</b> issued when the application is created and presented in
 * {@code X-Application-Token} on every subsequent call.
 *
 * <p>The token travels in a header rather than the URL on purpose. A path parameter is treated as
 * non-secret by the entire stack — browser history, {@code Referer} headers, access and CDN logs,
 * and anything the applicant pastes into a support ticket. Before this existed the application id
 * alone was sufficient to read, rewrite and submit somebody's application.
 *
 * <p>Signed-in admissions staff bypass the token; they work these records through the queue.
 */
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    /** Header rather than query parameter, so the secret stays out of logs and history. */
    private static final String TOKEN_HEADER = "X-Application-Token";

    private final AdmissionsService admissionsService;

    public ApplicationController(AdmissionsService admissionsService) {
        this.admissionsService = admissionsService;
    }

    /**
     * Starts an application and returns its capability token.
     *
     * <p>The only response that ever carries the raw token: the database keeps only a hash, so it
     * cannot be shown again. An applicant who loses it uses {@code /resume}.
     */
    @AccessClass(PUBLIC)
    @PostMapping
    public ResponseEntity<ApplicationAccessResponse> create(@Valid @RequestBody CreateApplicationRequest request) {
        ApplicationAccessResponse created = admissionsService.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/applications/" + created.application().id()))
                .body(created);
    }

    /**
     * Emails a fresh link to the address the application belongs to.
     *
     * <p>Always {@code 202}, matched or not. Any other answer would let a caller test whether a
     * given person applied to a given programme, which is disclosure in itself.
     */
    @AccessClass(PUBLIC)
    @PostMapping("/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resume(@Valid @RequestBody ResumeApplicationRequest request) {
        admissionsService.resume(request);
    }

    @AccessClass(PUBLIC)
    @GetMapping("/{id}")
    public ApplicationResponse byId(
            @PathVariable UUID id, @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return admissionsService.findByIdForApplicant(id, token);
    }

    @AccessClass(PUBLIC)
    @PatchMapping("/{id}")
    public ApplicationResponse update(
            @PathVariable UUID id,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody UpdateApplicationRequest request) {
        return admissionsService.update(id, token, request);
    }

    @AccessClass(PUBLIC)
    @PostMapping("/{id}/submit")
    public ApplicationResponse submit(
            @PathVariable UUID id, @RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return admissionsService.submit(id, token);
    }

    @AccessClass(PUBLIC)
    @PostMapping("/{id}/documents")
    public ApplicationResponse attachDocument(
            @PathVariable UUID id,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody AttachApplicationDocumentRequest request) {
        return admissionsService.attachDocument(id, token, request);
    }
}
