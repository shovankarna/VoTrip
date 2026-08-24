package com.votrip.config;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Verifies the Authorization: Bearer &lt;firebase-id-token&gt; header on every request.
 *
 * <p>A missing or rejected token is not failed here - the request simply continues
 * unauthenticated and {@link RestAuthenticationEntryPoint} produces the 401. That keeps every
 * error response in this service flowing through one place (the {@code @ControllerAdvice}).
 *
 * <p>Deliberately not a Spring bean. Boot auto-registers every Filter bean into the servlet
 * chain, where it would run ahead of springSecurityFilterChain - the security chain then
 * installs its own SecurityContext and discards whatever this filter authenticated, while
 * OncePerRequestFilter skips the copy inside the chain as already-run. SecurityConfig
 * constructs it instead, so it exists only inside the security chain.
 */
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    /** Set when a token was present but rejected, so the 401 can say why. */
    public static final String AUTH_ERROR_ATTRIBUTE = "com.votrip.auth.error";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);

    private final FirebaseAuth firebaseAuth;

    public FirebaseAuthenticationFilter(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String idToken = header.substring(BEARER_PREFIX.length()).trim();
        try {
            FirebaseToken token = firebaseAuth.verifyIdToken(idToken);
            FirebaseUserPrincipal principal = FirebaseUserPrincipal.from(token);

            var authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Must be a fresh context, not SecurityContextHolder.getContext().setAuthentication(..).
            // Spring Security defers loading the current context, so mutating it in place does not
            // stick and the anonymous filter later overwrites it.
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            log.debug("Authenticated Firebase uid={}", principal.uid());
        } catch (FirebaseAuthException | IllegalArgumentException e) {
            // Expired, malformed, wrong-project, or revoked token.
            SecurityContextHolder.clearContext();
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getMessage());
            log.debug("Rejected Firebase ID token: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
