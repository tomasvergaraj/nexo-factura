package cl.nexosoftware.factura.auth;

import cl.nexosoftware.factura.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/** Emite y valida tokens JWT firmados con HMAC-SHA256. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = props.jwt().expirationMinutes();
    }

    public String generarToken(Usuario usuario) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(expirationMinutes, ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claims(Map.of(
                        "uid", usuario.getId(),
                        "rol", usuario.getRol().name(),
                        "empresaId", usuario.getEmpresa() != null ? usuario.getEmpresa().getId() : null))
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expira))
                .signWith(key)
                .compact();
    }

    public String extraerEmail(String token) {
        return extraerClaim(token, Claims::getSubject);
    }

    /** Empresa del usuario (claim {@code empresaId}); puede ser null. */
    public Long extraerEmpresaId(String token) {
        Number empresaId = parse(token).get("empresaId", Number.class);
        return empresaId != null ? empresaId.longValue() : null;
    }

    /** Rol del usuario (claim {@code rol}); puede ser null. */
    public String extraerRol(String token) {
        return parse(token).get("rol", String.class);
    }

    /** Id del usuario (claim {@code uid}); puede ser null. */
    public Long extraerUid(String token) {
        Number uid = parse(token).get("uid", Number.class);
        return uid != null ? uid.longValue() : null;
    }

    public boolean esValido(String token, String email) {
        try {
            Claims claims = parse(token);
            return claims.getSubject().equals(email) && claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    private <T> T extraerClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parse(token));
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
