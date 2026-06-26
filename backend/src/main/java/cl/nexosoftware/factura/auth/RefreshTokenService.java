package cl.nexosoftware.factura.auth;

import cl.nexosoftware.factura.common.exception.TokenInvalidoException;
import cl.nexosoftware.factura.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * Emision, rotacion y revocacion de refresh tokens.
 *
 * El token crudo es opaco (256 bits aleatorios, base64url sin padding) y solo se
 * entrega una vez; en la BD vive unicamente su hash SHA-256. En cada
 * {@link #refrescar} se rota (se revoca el presentado y se emite uno nuevo). Si
 * se presenta un token cuyo hash coincide con una fila YA revocada se asume robo
 * y se revocan todas las sesiones del usuario.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository repository;
    private final JwtService jwtService;
    private final AppProperties props;

    // Auto-referencia (proxy) para invocar revocarCadena en una transaccion nueva.
    @Autowired @Lazy
    private RefreshTokenService self;

    /** Token crudo entregado al cliente + su vencimiento. */
    public record TokenEmitido(String rawToken, OffsetDateTime expiraEn) {}

    /** Resultado de una rotacion: nuevo access JWT + nuevo refresh. */
    public record RotacionResultado(Usuario usuario, String accessJwt,
                                    String rawRefreshToken, OffsetDateTime refreshExpiraEn) {}

    @Transactional
    public TokenEmitido emitir(Usuario usuario) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = B64.encodeToString(bytes);
        OffsetDateTime expiraEn = OffsetDateTime.now().plusDays(props.jwt().refreshExpirationDays());

        repository.save(RefreshToken.builder()
                .usuario(usuario)
                .tokenHash(hash(raw))
                .expiraEn(expiraEn)
                .revocado(false)
                .build());
        return new TokenEmitido(raw, expiraEn);
    }

    @Transactional
    public RotacionResultado refrescar(String rawToken) {
        RefreshToken actual = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new TokenInvalidoException("Refresh token inexistente"));

        // Reuso de un token ya revocado -> posible robo -> revoca toda la cadena.
        // La revocacion va en una transaccion NUEVA (REQUIRES_NEW): debe persistir
        // aunque la transaccion actual se aborte al lanzar la excepcion.
        if (actual.isRevocado()) {
            self.revocarCadena(actual.getUsuario().getId());
            throw new TokenInvalidoException("Reuso de refresh token revocado");
        }
        if (actual.getExpiraEn().isBefore(OffsetDateTime.now())) {
            throw new TokenInvalidoException("Refresh token expirado");
        }
        Usuario usuario = actual.getUsuario();
        if (!usuario.isActivo()) {
            throw new TokenInvalidoException("Usuario inactivo");
        }

        // Rotacion: revoca el presentado y emite uno nuevo.
        actual.setRevocado(true);
        repository.save(actual);

        TokenEmitido nuevo = emitir(usuario);
        return new RotacionResultado(
                usuario, jwtService.generarToken(usuario), nuevo.rawToken(), nuevo.expiraEn());
    }

    /**
     * Revoca TODA la cadena de refresh tokens de un usuario en una transaccion
     * independiente (REQUIRES_NEW): se invoca ante deteccion de reuso, justo antes
     * de abortar la transaccion principal, por lo que debe commitear por su cuenta.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revocarCadena(Long usuarioId) {
        repository.revocarTodosDeUsuario(usuarioId);
    }

    /** Revoca el refresh token presentado. Idempotente: token desconocido no falla. */
    @Transactional
    public void revocar(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(t -> {
            t.setRevocado(true);
            repository.save(t);
        });
    }

    private static String hash(String raw) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(h.length * 2);
            for (byte b : h) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
    }
}
