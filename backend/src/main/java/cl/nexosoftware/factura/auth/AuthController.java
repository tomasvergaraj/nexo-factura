package cl.nexosoftware.factura.auth;

import cl.nexosoftware.factura.auth.AuthDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticacion", description = "Registro e inicio de sesion")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/registro")
    @Operation(summary = "Registrar un nuevo usuario administrador")
    public ResponseEntity<AuthResponse> registrar(@Valid @RequestBody RegistroRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(req));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesion y obtener un token JWT")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar el access token usando un refresh token (rota el refresh)")
    public ResponseEntity<AuthResponse> refrescar(@Valid @RequestBody RefrescarRequest req) {
        return ResponseEntity.ok(authService.refrescar(req.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revocar el refresh token (cierre de sesion)")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
