package cl.nexosoftware.factura.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoca todas las filas vigentes de un usuario (deteccion de reuso / logout total). */
    @Modifying
    @Query("update RefreshToken r set r.revocado = true where r.usuario.id = :usuarioId and r.revocado = false")
    int revocarTodosDeUsuario(@Param("usuarioId") Long usuarioId);

    /** Purga de filas expiradas (mantenimiento; evita crecimiento indefinido). */
    @Modifying
    @Query("delete from RefreshToken r where r.expiraEn < :limite")
    int eliminarExpiradosAntesDe(@Param("limite") OffsetDateTime limite);
}
