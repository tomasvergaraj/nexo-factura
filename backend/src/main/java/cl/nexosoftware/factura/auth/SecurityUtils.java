package cl.nexosoftware.factura.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Acceso al usuario autenticado a partir del {@link SecurityContextHolder}.
 * La fuente del {@code empresaId} es el principal ({@link UsuarioPrincipal})
 * derivado del claim del JWT, nunca la base de datos.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** Devuelve el principal autenticado o {@code null} si no hay sesion. */
    public static UsuarioPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return (principal instanceof UsuarioPrincipal up) ? up : null;
    }

    /** Empresa del usuario autenticado (claim del JWT) o {@code null}. */
    public static Long currentEmpresaId() {
        UsuarioPrincipal up = currentUser();
        return up != null ? up.getEmpresaId() : null;
    }

    /** Rol del usuario autenticado o {@code null}. */
    public static Rol currentRol() {
        UsuarioPrincipal up = currentUser();
        return up != null ? up.getRol() : null;
    }

    /** {@code true} si el usuario autenticado tiene rol ADMIN. */
    public static boolean esAdmin() {
        return currentRol() == Rol.ADMIN;
    }
}
