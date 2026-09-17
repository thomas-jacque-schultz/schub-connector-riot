package schultz.thomas.schub.connector.riot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authentifie les appels venant des autres services Schub par un secret partagé.
 *
 * <p>Tous les services du maillage portent le même filtre : aucun n'est joignable
 * sans l'en-tête, y compris depuis l'overlay.</p>
 */
@Slf4j
@Component
public class InternalSecretFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Secret";

    @Value("${schub.internal-secret}")
    private String internalSecret;

    /**
     * La sonde de santé reste joignable sans secret.
     *
     * <p>Sans cette exemption, le filtre rejette {@code /actuator/health} avant que le
     * {@code permitAll} de la configuration de sécurité ne s'applique : Docker ne peut alors
     * jamais déclarer le service sain, et tout ce qui l'attend reste à quai. La sonde n'expose
     * que le statut — le détail est masqué par défaut.</p>
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (matches(request.getHeader(HEADER))) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "schub-internal", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Requête rejetée depuis {} — secret interne absent ou invalide", request.getRemoteAddr());
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    /** Comparaison à temps constant : une comparaison naïve fuit la longueur du préfixe correct. */
    private boolean matches(String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                internalSecret.getBytes(StandardCharsets.UTF_8));
    }
}
