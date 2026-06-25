package cl.nexosoftware.factura.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * Principal autenticado construido desde los claims del JWT. Implementa
 * {@link UserDetails} (con {@code getUsername()} = email) para mantener
 * compatibilidad con el resto de Spring Security.
 */
public class UsuarioPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final Rol rol;
    private final Long empresaId;
    private final Collection<? extends GrantedAuthority> authorities;

    public UsuarioPrincipal(Long id, String email, Rol rol, Long empresaId,
                            Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.rol = rol;
        this.empresaId = empresaId;
        this.authorities = authorities;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Rol getRol() {
        return rol;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    /** Compatibilidad: el username es el email del usuario. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
