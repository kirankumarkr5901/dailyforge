package com.dailyforge.common.security;

import com.dailyforge.common.error.ApiError;
import com.dailyforge.common.error.ErrorCode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
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

    @Bean
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
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiError.of(ErrorCode.AUTH_REQUIRED, "Sign in to do that."));
    }
}
