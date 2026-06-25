package cl.nexosoftware.factura.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTOs del flujo de autenticacion. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record RegistroRequest(
            @NotBlank String nombre,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres") String password
    ) {}

    public record AuthResponse(
            String token,
            String tipo,
            long expiraEnMinutos,
            UsuarioResponse usuario
    ) {}

    public record UsuarioResponse(
            Long id,
            String nombre,
            String email,
            Rol rol,
            Long empresaId
    ) {
        public static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(
                    u.getId(), u.getNombre(), u.getEmail(), u.getRol(),
                    u.getEmpresa() != null ? u.getEmpresa().getId() : null);
        }
    }
}
