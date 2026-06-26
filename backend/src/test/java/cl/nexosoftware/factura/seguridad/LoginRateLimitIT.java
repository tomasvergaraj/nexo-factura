package cl.nexosoftware.factura.seguridad;

import cl.nexosoftware.factura.AbstractIntegrationTest;
import cl.nexosoftware.factura.auth.RateLimiter;
import cl.nexosoftware.factura.auth.Rol;
import cl.nexosoftware.factura.auth.Usuario;
import cl.nexosoftware.factura.auth.UsuarioRepository;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integracion del rate limiting de autenticacion. Limites pequenos via
 * {@link DynamicPropertySource} (email 3, IP 10) para agotar rapido. El bean
 * RateLimiter es singleton: se reinicia en cada test.
 */
@AutoConfigureMockMvc
class LoginRateLimitIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RateLimiter rateLimiter;

    private static final String PASSWORD = "secreta123";

    private String email;

    @DynamicPropertySource
    static void limites(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.max-intentos-email", () -> 3);
        registry.add("app.security.rate-limit.max-intentos-ip", () -> 10);
        registry.add("app.security.rate-limit.ventana-segundos", () -> 60);
        registry.add("app.security.rate-limit.bloqueo-segundos", () -> 60);
    }

    @BeforeEach
    void preparar() {
        rateLimiter.reset();
        int sufijo = ThreadLocalRandom.current().nextInt(1000, 9999);
        Empresa empresa = empresaRepository.save(Empresa.builder()
                .rut("92" + sufijo + "000-1").razonSocial("Empresa RL").giro("Pruebas")
                .direccion("Calle 1").comuna("Quillota").build());
        email = "rl-" + sufijo + "@test.cl";
        usuarioRepository.save(Usuario.builder()
                .nombre("RL").email(email).passwordHash(passwordEncoder.encode(PASSWORD))
                .rol(Rol.ADMIN).activo(true).empresa(empresa).build());
    }

    @Test
    @DisplayName("tras 3 logins fallidos del mismo email, el 4to devuelve 429 con Retry-After")
    void superaLimiteEmail429() throws Exception {
        for (int i = 0; i < 3; i++) {
            loginMal(email).andExpect(status().isUnauthorized());
        }
        loginMal(email)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("el limite por email no afecta a otro email")
    void limitePorEmailNoAfectaOtro() throws Exception {
        for (int i = 0; i < 3; i++) loginMal(email).andExpect(status().isUnauthorized());
        loginMal(email).andExpect(status().isTooManyRequests());

        String otro = "otro-" + ThreadLocalRandom.current().nextInt(1000, 9999) + "@test.cl";
        loginMal(otro).andExpect(status().isUnauthorized()); // distinto email -> no bloqueado
    }

    @Test
    @DisplayName("un login correcto reinicia el presupuesto del email")
    void exitoReiniciaPresupuesto() throws Exception {
        loginMal(email).andExpect(status().isUnauthorized());
        loginMal(email).andExpect(status().isUnauthorized());
        loginOk(email).andExpect(status().isOk()); // reinicia el contador del email
        // Sin reset, el 3er fallo (total) ya estaria al limite; tras el reset hay budget nuevo.
        loginMal(email).andExpect(status().isUnauthorized());
        loginMal(email).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/registro esta limitado por IP")
    void registroLimitadoPorIp() throws Exception {
        // maxIp=10: las primeras 10 pasan el limite (201/409), la 11va -> 429.
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/registro")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registroBody("dup-" + email)));
        }
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registroBody("dup-" + email)))
                .andExpect(status().isTooManyRequests());
    }

    // ---- helpers ----

    private ResultActions loginMal(String correo) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + correo + "\",\"password\":\"password-incorrecta\"}"));
    }

    private ResultActions loginOk(String correo) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + correo + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private String registroBody(String correo) {
        return "{\"nombre\":\"Test\",\"email\":\"" + correo + "\",\"password\":\"" + PASSWORD + "\"}";
    }
}
