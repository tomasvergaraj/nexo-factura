package cl.nexosoftware.factura.auth;

import cl.nexosoftware.factura.auth.AuthDtos.*;
import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Registro, login, refresh y cierre de sesion de usuarios. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final RateLimiter rateLimiter;

    @Transactional
    public AuthResponse registrar(RegistroRequest req) {
        String ip = ipActual();
        rateLimiter.verificarIp(ip);
        rateLimiter.registrarFalloIp(ip); // cada intento de registro cuenta contra la IP

        if (usuarioRepository.existsByEmail(req.email())) {
            throw new ReglaNegocioException("El email ya esta registrado");
        }
        Usuario usuario = Usuario.builder()
                .nombre(req.nombre())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .rol(Rol.ADMIN)
                .activo(true)
                .build();
        usuarioRepository.save(usuario);
        return construirRespuesta(usuario);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        String ip = ipActual();
        String email = req.email();
        rateLimiter.verificar(email, ip);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, req.password()));
        } catch (BadCredentialsException e) {
            rateLimiter.registrarFallo(email, ip);
            throw e;
        }
        rateLimiter.registrarExito(email);
        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        return construirRespuesta(usuario);
    }

    @Transactional
    public AuthResponse refrescar(String refreshToken) {
        RefreshTokenService.RotacionResultado r = refreshTokenService.refrescar(refreshToken);
        return new AuthResponse(r.accessJwt(), "Bearer", jwtService.getExpirationMinutes(),
                r.rawRefreshToken(), r.refreshExpiraEn(), UsuarioResponse.de(r.usuario()));
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revocar(refreshToken);
    }

    private AuthResponse construirRespuesta(Usuario usuario) {
        String token = jwtService.generarToken(usuario);
        RefreshTokenService.TokenEmitido refresh = refreshTokenService.emitir(usuario);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMinutes(),
                refresh.rawToken(), refresh.expiraEn(), UsuarioResponse.de(usuario));
    }

    /** IP del request en curso (o "" si no hay request ligado al hilo). */
    private String ipActual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            return rateLimiter.clientIp(sra.getRequest());
        }
        return "";
    }
}
