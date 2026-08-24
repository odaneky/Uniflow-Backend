package com.university.lms.common.security;

import static com.university.lms.common.security.SecurityRoles.ACADEMIC_ADVISOR;
import static com.university.lms.common.security.SecurityRoles.FACULTY_ADMIN;
import static com.university.lms.common.security.SecurityRoles.LECTURER;
import static com.university.lms.common.security.SecurityRoles.REGISTRAR;
import static com.university.lms.common.security.SecurityRoles.STUDENT;
import static com.university.lms.common.security.SecurityRoles.SYSTEM_ADMIN;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authentication and authorization.
 *
 * <p>The application is a <b>resource server</b> and nothing more: it validates bearer tokens
 * issued by Keycloak and never authenticates anyone itself. There is no login endpoint, no session,
 * no password check, and — since V16 — no password anywhere in the schema. There is deliberately no
 * {@code PasswordEncoder} bean: an application with nothing to hash does not need one, and its
 * presence would invite exactly the local credential store this architecture forbids.
 *
 * <p>Authorization is layered deliberately. The rules below are <b>coarse and role-based</b>: they
 * answer "may this kind of user call this kind of endpoint at all", and being in one ordered list
 * makes the whole surface auditable at a glance. They are not sufficient on their own. Ownership
 * questions — may this student read <em>this</em> record, may this lecturer grade <em>this</em>
 * section — depend on the data being addressed, cannot be expressed by a URL pattern, and belong
 * in the service layer where the entity is in hand. Method security is enabled for exactly that.
 *
 * <p>Matcher order is significant: the first match wins. The narrower rules therefore come first,
 * and the broad {@code GET → authenticated} fallbacks last.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final SecurityErrorResponder securityErrorResponder;
    private final TokenClaimReader claimReader;

    public SecurityConfig(SecurityErrorResponder securityErrorResponder, TokenClaimReader claimReader) {
        this.securityErrorResponder = securityErrorResponder;
        this.claimReader = claimReader;
    }

    /**
     * Local scrape lives on the loopback management port, not on 8080. Prometheus has no JWT, so
     * this chain permits {@code /actuator/prometheus} only when the request arrived on that port.
     * The public connector still requires a token — see
     * {@code AuthorizationRulesIntegrationTest.prometheusIsNotPublicOnTheApiPort}.
     */
    @Bean
    @Order(0)
    @ConditionalOnProperty(name = "management.server.port")
    public SecurityFilterChain managementScrapeChain(
            HttpSecurity http, @Value("${management.server.port}") int managementPort) throws Exception {
        http.securityMatcher(request -> request.getLocalPort() == managementPort)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/actuator/metrics",
                                "/actuator/metrics/**")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(securityErrorResponder)
                        .accessDeniedHandler(securityErrorResponder));
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                // No session is created, so there is no session-riding to protect against and no
                // CSRF token flow to unwind. Bearer tokens are not sent ambiently by the browser.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // -------- public --------
                        .requestMatchers(
                                HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        // Welcome page themes before Keycloak; no secrets in the payload.
                        .requestMatchers(HttpMethod.GET, "/api/v1/branding")
                        .permitAll()
                        // Prometheus and /actuator/metrics are not listed. Local scrape binds to
                        // loopback :8082; production exports OTLP only. Never permitAll those paths.

                        // -------- self-service --------
                        // No identifier is accepted anywhere under /me, so the resource is fixed by
                        // the authenticated principal. Listed first, and above the administrative
                        // /users rules, so that reading yourself never requires an admin role.
                        .requestMatchers("/api/v1/me", "/api/v1/me/**")
                        .authenticated()

                        // -------- identity: user administration --------
                        // Creating users, granting roles and suspending accounts are the keys to
                        // the kingdom; nothing below SYSTEM_ADMIN touches them.
                        .requestMatchers("/api/v1/users/**", "/api/v1/users")
                        .hasRole(SYSTEM_ADMIN)

                        // -------- academic structure --------
                        .requestMatchers(
                                HttpMethod.POST, "/api/v1/faculties", "/api/v1/departments", "/api/v1/programmes")
                        .hasAnyRole(SYSTEM_ADMIN, FACULTY_ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/programmes/**")
                        .hasAnyRole(SYSTEM_ADMIN, FACULTY_ADMIN, REGISTRAR)

                        // -------- academic calendar --------
                        // The registration window decides when students may compete for seats, so
                        // it sits with the registry rather than with faculty administration.
                        .requestMatchers(HttpMethod.PUT, "/api/v1/academic-terms/*/registration-window")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        // Publishing exam dates is the registry's call, like registration. Without
                        // this it would fall through to the broad authenticated rule and any signed-in
                        // student could move the examination period.
                        .requestMatchers(HttpMethod.PUT, "/api/v1/academic-terms/*/exam-window")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        // The office's view of a section's exams includes UNPUBLISHED drafts. Students
                        // read their own timetable through /me/exams, which returns published rows
                        // only; without this rule the broad GET fallback would hand them the drafts.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/courses/sections/*/exams",
                                "/api/v1/courses/sections/exams",
                                "/api/v1/courses/sections")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN, LECTURER)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/academic-terms/*/add-drop-window")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/payment-plans/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/academic-policy")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/branding")
                        .hasRole(SYSTEM_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/branding")
                        .hasRole(SYSTEM_ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/programmes/*/credit-load")
                        .hasAnyRole(SYSTEM_ADMIN, FACULTY_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/tuition-schedule", "/api/v1/tuition-schedule/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/tuition-schedule/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.POST, "/api/v1/fee-catalog", "/api/v1/fee-catalog/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/fee-catalog/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/fee-catalog/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.POST, "/api/v1/academic-years", "/api/v1/academic-terms")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)

                        // -------- course catalog --------
                        // Covers section creation and open/close, which live under /courses/**.
                        .requestMatchers(HttpMethod.POST, "/api/v1/courses/**", "/api/v1/courses")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/courses/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/courses/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/courses/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN)

                        // -------- student records --------
                        .requestMatchers(HttpMethod.POST, "/api/v1/students")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/students/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)

                        // -------- enrolment --------
                        // Completing an enrolment is an academic judgement, not a student action,
                        // so it is listed before the general enrolment rule and excludes STUDENT.
                        // Lecturers who pass this rule are still scoped to sections they teach
                        // in EnrollmentService.complete — a URL pattern cannot see that.
                        .requestMatchers(HttpMethod.POST, "/api/v1/enrollments/*/complete")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, LECTURER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/enrollments/override")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.POST, "/api/v1/enrollments/*/approve")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, LECTURER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/enrollments/**", "/api/v1/enrollments")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, ACADEMIC_ADVISOR, STUDENT)

                        // -------- teaching: content, assessment, grades --------
                        // Listed before the catalog POST rule would not help here: these live
                        // under /learning, /assessments and /grades, not /courses. Lecturers may
                        // write their own section's material; students may not.
                        .requestMatchers(HttpMethod.POST, "/api/v1/sections/*/attendance")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN, LECTURER)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/learning/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN, LECTURER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/learning/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN, LECTURER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/assessments/**", "/api/v1/assessments")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN, LECTURER)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/assessments/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN, LECTURER)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/assessments/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN, LECTURER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/grades/**", "/api/v1/grades")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN, LECTURER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/announcements/**", "/api/v1/announcements")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN, LECTURER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/notifications/**", "/api/v1/notifications")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN, LECTURER)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/notifications/**", "/api/v1/notifications")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN, LECTURER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/documents/**", "/api/v1/documents")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN, LECTURER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/applications", "/api/v1/applications/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/applications/*")
                        .permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/applications/*")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/programmes/*/application-form")
                        .permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/programmes/*/application-form")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/programmes", "/api/v1/programmes/*")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/academic-years", "/api/v1/academic-years/*")
                        .permitAll()
                        // The entry terms an applicant chooses from. Listed explicitly rather than
                        // widening the rule above to /**, which would also publish every future
                        // sub-resource of an academic year without anyone deciding to.
                        .requestMatchers(HttpMethod.GET, "/api/v1/academic-years/*/terms")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admissions/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.POST, "/api/v1/enrollments/override")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.POST, "/api/v1/enrollments/*/approve")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, LECTURER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/enrollments/*/accept-waitlist-offer")
                        .hasAnyRole(STUDENT)
                        .requestMatchers(HttpMethod.POST, "/api/v1/enrollments/*/decline-waitlist-offer")
                        .hasAnyRole(STUDENT)
                        .requestMatchers(HttpMethod.POST, "/api/v1/courses/sections/*/attendance/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, FACULTY_ADMIN, LECTURER)
                        .requestMatchers(HttpMethod.POST, "/api/v1/conversations/**", "/api/v1/conversations")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts/**", "/api/v1/accounts")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.POST, "/api/v1/financial-aid/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.POST, "/api/v1/service-holds/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.POST, "/api/v1/requests/**", "/api/v1/requests")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR, ACADEMIC_ADVISOR)
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/programmes/*/requirement-blocks",
                                "/api/v1/programmes/*/requirement-blocks/**")
                        .hasAnyRole(SYSTEM_ADMIN, FACULTY_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/programmes/*/requirement-blocks/*")
                        .hasAnyRole(SYSTEM_ADMIN, FACULTY_ADMIN, REGISTRAR)

                        // -------- audit trail --------
                        // Listed before the catch-all authenticated GET. The trail is a history of
                        // privileged actions; students and lecturers must not enumerate it.
                        .requestMatchers(HttpMethod.GET, "/api/v1/conversations/*/compliance-export")
                        .hasRole(SYSTEM_ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/v1/audit-events", "/api/v1/audit-events/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/record-access/**")
                        .hasAnyRole(SYSTEM_ADMIN, REGISTRAR)

                        // -------- reads --------
                        // Any authenticated member of the university may read the catalog and the
                        // academic structure. Reads of *personal* data are only role-gated here;
                        // narrowing them to the subject's own records is service-layer work and is
                        // still outstanding — see docs/ROADMAP.md, P0.1.
                        .requestMatchers(HttpMethod.GET, "/api/v1/**")
                        .authenticated()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults())
                        // Required *in addition* to the global one below, and easy to omit. The
                        // resource-server configurer installs its own BearerTokenAuthenticationEntryPoint
                        // which takes precedence whenever the failure originates in the bearer-token
                        // filter — i.e. for every malformed, expired or wrong-audience token, which is
                        // the overwhelming majority of real authentication failures. Configuring only
                        // the global entry point covers just the "no credentials at all" case and
                        // leaves the common ones answering with an empty body.
                        .authenticationEntryPoint(securityErrorResponder)
                        .accessDeniedHandler(securityErrorResponder))
                // Covers failures raised outside the bearer-token filter — chiefly a request with no
                // Authorization header at all, and access denials from method security.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(securityErrorResponder)
                        .accessDeniedHandler(securityErrorResponder));
        return http.build();
    }

    /**
     * Built from the JWK set URI rather than by issuer discovery.
     *
     * <p>{@code JwtDecoders.fromIssuerLocation} performs an HTTP round trip while the bean is being
     * created, which makes the identity provider a hard start-up dependency of the application: if
     * Keycloak is briefly unavailable during a deploy, the application does not start at all.
     * Building from the JWK set URI defers the first fetch to the first token that needs verifying,
     * and the key set is cached thereafter. The issuer is still validated — that check just no
     * longer requires a network call to configure.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${lms.security.jwt.audience}") String audience) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                // Pin the signature algorithm. Left open, the decoder accepts whatever the JWK set
                // offers, which makes the set itself part of the trust decision; an attacker who
                // could influence it could downgrade to a weaker algorithm. RS256 is what this
                // realm issues, and anything else is a misconfiguration worth failing on.
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri), new AudienceValidator(audience)));
        return decoder;
    }

    /**
     * Wires {@link RealmRoleAuthoritiesConverter} in; see that class for why the default will not do.
     *
     * <p>The principal's name is set from the configured subject claim rather than left to default,
     * so {@code Authentication.getName()} — which is what auditing records — is always the immutable
     * external identity and never a renameable username.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(ClaimMappingProperties claims) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new RealmRoleAuthoritiesConverter(claimReader));
        converter.setPrincipalClaimName(claims.subject());
        return converter;
    }

}
