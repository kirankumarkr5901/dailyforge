package com.dailyforge.common.security;

import com.dailyforge.common.error.ApiError;
import com.dailyforge.common.error.ErrorCode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The filter chain.
 *
 * The shape follows spec §4.1: an anonymous visitor may browse everything, and any
 * request that would *write* is refused with {@code AUTH_REQUIRED} so the frontend can
 * open the login sheet and replay the action afterwards. That is why the rules are
 * expressed by HTTP method rather than by path — a read is a read wherever it lives, and
 * a new feature is safe by default.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthProperties properties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(AuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Signing in must work even while a dead token is still in the client's storage.
     *
     * These endpoints were already {@code permitAll}, which was not enough: the resource
     * server still validated any token that happened to be presented and refused the
     * request before the handler ran. So a user whose access token had expired could not
     * sign in again — the request carrying their stale token was rejected for carrying
     * it, and clearing site data was the only way out. Google sign-in failed the same
     * way, for the same reason.
     *
     * Giving these paths their own chain with no token validation at all means an
     * irrelevant Authorization header is simply ignored, which is the only sane
     * treatment of a credential these endpoints exist to replace.
     *
     * Ordered first so it wins for its paths; everything else falls through to the main
     * chain below.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authEndpointsChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/auth/**")
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // No cookies are used for auth, so there is no CSRF surface to protect;
                // the token travels in the Authorization header, which a cross-site form
                // post cannot set.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth
                                        // Signing in cannot require being signed in.
                                        .requestMatchers("/api/v1/auth/**")
                                        .permitAll()
                                        .requestMatchers("/actuator/health/**", "/actuator/health")
                                        .permitAll()
                                        .requestMatchers("/h2-console/**")
                                        .permitAll()
                                        // Anonymous browsing: reads are open, and the
                                        // repository layer still scopes every query by
                                        // user, so an anonymous read sees only public or
                                        // demo data.
                                        .requestMatchers(HttpMethod.GET, "/api/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        // Everything that writes.
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2
                                        .jwt(jwt -> jwt.decoder(accessTokenDecoder()))
                                        .authenticationEntryPoint(this::writeAuthRequired)
                                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler()))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(this::writeAuthRequired))
                // The H2 console renders in a frame; only reachable on the local profile.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    /**
     * Decodes our own access tokens only. Google's tokens are verified separately, at
     * the sign-in endpoint, and are never accepted as a session credential.
     */
    @Bean
    public JwtDecoder accessTokenDecoder() {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withSecretKey(AccessTokenService.secretKey(properties.jwtSecret()))
                        .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(AccessTokenService.ISSUER));
        return decoder;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(properties.bcryptStrength());
    }

    /**
     * The one place a 401 is written. The body is the same error contract every other
     * failure uses, so the frontend has a single shape to map (spec §4.7).
     */
    private void writeAuthRequired(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            org.springframework.security.core.AuthenticationException exception)
            throws java.io.IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // Two very different failures used to answer with the same code, and the
        // difference decides what the client does next.
        //
        // No Authorization header means a genuinely anonymous visitor tried to write:
        // AUTH_REQUIRED, and the frontend opens the sign-in sheet.
        //
        // A header that is present but rejected means a signed-in user whose access
        // token has simply aged out — fifteen minutes is its whole life. That is
        // AUTH_TOKEN_EXPIRED, and the frontend answers it by silently refreshing.
        // Answering it with AUTH_REQUIRED told the client to give up and ask the user
        // to sign in, so the refresh path never ran once: every session ended after
        // fifteen minutes no matter how healthy its refresh token was.
        boolean tokenWasPresent = request.getHeader("Authorization") != null;
        objectMapper.writeValue(
                response.getOutputStream(),
                tokenWasPresent
                        ? ApiError.of(ErrorCode.AUTH_TOKEN_EXPIRED, "That session needs refreshing.")
                        : ApiError.of(ErrorCode.AUTH_REQUIRED, "Sign in to do that."));
    }
}
