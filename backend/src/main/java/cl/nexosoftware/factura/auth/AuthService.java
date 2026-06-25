package cl.nexosoftware.factura.auth;

import cl.nexosoftware.factura.auth.AuthDtos.*;
import cl.nexosoftware.factura.common.exception.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registro y login de usuarios. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse registrar(RegistroRequest req) {
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

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        Usuario usuario = usuarioRepository.findByEmail(req.email()).orElseThrow();
        return construirRespuesta(usuario);
    }

    private AuthResponse construirRespuesta(Usuario usuario) {
        String token = jwtService.generarToken(usuario);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMinutes(),
                UsuarioResponse.de(usuario));
    }
}
