package cl.nexosoftware.factura.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Lee el header Authorization, valida el JWT y establece el contexto de seguridad. */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioDetailsService usuarioDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length());
        try {
            String email = jwtService.extraerEmail(token);
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // loadUserByUsername solo valida existencia/activo del usuario.
                UserDetails userDetails = usuarioDetailsService.loadUserByUsername(email);
                if (jwtService.esValido(token, userDetails.getUsername())) {
                    Long empresaId = jwtService.extraerEmpresaId(token); // tolera null
                    Long uid = jwtService.extraerUid(token);
                    Rol rol = Rol.valueOf(jwtService.extraerRol(token));
                    UsuarioPrincipal principal = new UsuarioPrincipal(
                            uid, email, rol, empresaId, userDetails.getAuthorities());
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (Exception ignored) {
            // token invalido: se continua sin autenticar y la cadena rechazara la peticion
        }
        chain.doFilter(request, response);
    }
}
