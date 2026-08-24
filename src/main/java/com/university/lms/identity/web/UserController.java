package com.university.lms.identity.web;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.identity.dto.ProvisionIdentityRequest;
import com.university.lms.identity.dto.UserResponse;
import com.university.lms.identity.dto.UserSummaryResponse;
import com.university.lms.identity.service.IdentityProvisioningService;
import com.university.lms.identity.service.UserService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative identity management.
 *
 * <p>Every write here reaches the identity provider first, because the provider is what decides
 * whether someone can authenticate. There is no operation that changes only the local row: an
 * administrator who disables an account must be able to trust that access is actually revoked.
 *
 * <p>This is not a registration API. Ordinary students are not created here — their identity
 * originates in the university's registration system and is provisioned in. There is deliberately
 * no public sign-up endpoint anywhere in this application.
 *
 * <p>Passwords appear nowhere on this controller, in either direction.
 */
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    private final UserService userService;
    private final IdentityProvisioningService provisioningService;

    public UserController(UserService userService, IdentityProvisioningService provisioningService) {
        this.userService = userService;
        this.provisioningService = provisioningService;
    }

    /** Privileged provisioning. The account is created requiring a credential reset. */
    @AccessClass(REGISTRY_ONLY)
    @PostMapping
    public ResponseEntity<UserResponse> provision(@Valid @RequestBody ProvisionIdentityRequest request) {
        UserResponse created = provisioningService.provision(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @AccessClass(REGISTRY_ONLY)
    @GetMapping
    public PageResponse<UserSummaryResponse> findAll(
            @PageableDefault(size = 20, sort = "username", direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.findAll(pageable);
    }

    @AccessClass(REGISTRY_ONLY)
    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable UUID id) {
        return userService.findById(id);
    }

    /**
     * Revokes authentication at the identity provider.
     *
     * <p>Named {@code disable} rather than {@code suspend} because that is what it does: the account
     * stops authenticating. An earlier version of this endpoint set a local status flag that nothing
     * consulted, so it reported success while leaving access entirely intact.
     */
    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/{id}/disable")
    public UserResponse disable(@PathVariable UUID id) {
        return provisioningService.disable(id);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/{id}/enable")
    public UserResponse enable(@PathVariable UUID id) {
        return provisioningService.enable(id);
    }

    /** Roles as the identity provider holds them — the only place a role grants anything. */
    @AccessClass(REGISTRY_ONLY)
    @GetMapping("/{id}/roles")
    public List<String> roles(@PathVariable UUID id) {
        return provisioningService.rolesOf(id);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/{id}/roles")
    public List<String> grantRole(@PathVariable UUID id, @RequestParam @NotBlank String role) {
        return provisioningService.grantRole(id, role);
    }

    @AccessClass(REGISTRY_ONLY)
    @DeleteMapping("/{id}/roles")
    public List<String> revokeRole(@PathVariable UUID id, @RequestParam @NotBlank String role) {
        return provisioningService.revokeRole(id, role);
    }
}
