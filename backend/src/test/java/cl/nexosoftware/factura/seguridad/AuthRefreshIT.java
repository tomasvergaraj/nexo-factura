package cl.nexosoftware.factura.seguridad;

import cl.nexosoftware.factura.AbstractIntegrationTest;
import cl.nexosoftware.factura.auth.RateLimiter;
import cl.nexosoftware.factura.auth.RefreshToken;
import cl.nexosoftware.factura.auth.RefreshTokenRepository;
import cl.nexosoftware.factura.auth.Rol;
import cl.nexosoftware.factura.auth.Usuario;
import cl.nexosoftware.factura.auth.UsuarioRepository;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import cl.nexosoftware.factura.tributario.SelloDte;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integracion del flujo de refresh/revocacion: login entrega access + refresh;
 * /refresh rota el refresh y emite un nuevo access; un refresh inexistente,
 * expirado, revocado o reutilizado devuelve 401; el reuso de un token rotado
 * revoca toda la cadena del usuario; /logout revoca.
 *
 * Nota (gap documentado): el access JWT viejo sigue valido hasta su expiracion
 * (60 min) aun tras /logout, porque la revocacion dura es solo de refresh tokens
 * (tokenVersion quedo fuera de alcance de P2-3).
 */
@AutoConfigureMockMvc
class AuthRefreshIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RateLimiter rateLimiter;

    private static final String PASSWORD = "secreta123";

    private Long empresaId;
    private String email;

    @BeforeEach
    void preparar() {
        rateLimiter.reset();
        int sufijo = ThreadLocalRandom.current().nextInt(1000, 9999);
        Empresa empresa = empresaRepository.save(Empresa.builder()
                .rut("92" + sufijo + "000-1").razonSocial("Empresa Auth").giro("Pruebas")
                .direccion("Calle 1").comuna("Quillota").build());
        empresaId = empresa.getId();
        email = "user-" + sufijo + "@test.cl";
        usuarioRepository.save(Usuario.builder()
                .nombre("User").email(email).passwordHash(passwordEncoder.encode(PASSWORD))
                .rol(Rol.ADMIN).activo(true).empresa(empresa).build());
    }

    @Test
    @DisplayName("login entrega access y refresh distintos y no vacios")
    void loginDevuelveAccessYRefresh() throws Exception {
        JsonNode r = login();
        assertThat(token(r)).isNotBlank();
        assertThat(refresh(r)).isNotBlank();
        assertThat(token(r)).isNotEqualTo(refresh(r));
        assertThat(r.get("usuario").get("email").asText()).isEqualTo(email);
    }

    @Test
    @DisplayName("refresh con token valido devuelve nuevo access y rota el refresh; el access sirve")
    void refreshRotaYElAccessSirve() throws Exception {
        String r0 = refresh(login());
        JsonNode rot = refrescar(r0).andExpect(status().isOk()).andReturnJson();
        assertThat(refresh(rot)).isNotEqualTo(r0);
        // El nuevo access token autentica una ruta protegida.
        mockMvc.perform(get("/api/empresas/{id}/documentos", empresaId)
                        .header("Authorization", "Bearer " + token(rot)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("refresh con token inexistente devuelve 401")
    void refreshTokenBasura401() throws Exception {
        refrescar("no-existe").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh con un token ya rotado (revocado) devuelve 401")
    void refreshTokenRotado401() throws Exception {
        String r0 = refresh(login());
        refrescar(r0).andExpect(status().isOk()); // r0 -> r1 (r0 queda revocado)
        refrescar(r0).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("reuso de un token revocado revoca toda la cadena del usuario")
    void reusoRevocaCadena() throws Exception {
        String r0 = refresh(login());
        String r1 = refresh(refrescar(r0).andExpect(status().isOk()).andReturnJson());
        // Reuso de r0 (revocado) -> 401 y se revocan TODOS los tokens del usuario.
        refrescar(r0).andExpect(status().isUnauthorized());
        // r1, aunque era valido, ahora tambien esta revocado.
        refrescar(r1).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout revoca el refresh; un refresh posterior devuelve 401")
    void logoutRevoca() throws Exception {
        String r = refresh(login());
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + r + "\"}"))
                .andExpect(status().isNoContent());
        refrescar(r).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout con token inexistente es idempotente (204)")
    void logoutIdempotente() throws Exception {
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"basura\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("refresh con token expirado devuelve 401")
    void refreshExpirado401() throws Exception {
        String raw = "refresh-expirado-" + ThreadLocalRandom.current().nextInt();
        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        refreshTokenRepository.save(RefreshToken.builder()
                .usuario(usuario)
                .tokenHash(SelloDte.calcular(raw)) // mismo SHA-256 hex que usa el servicio
                .expiraEn(OffsetDateTime.now().minusDays(1))
                .revocado(false)
                .build());
        refrescar(raw).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private JsonNode login() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private ResultadoJson refrescar(String refreshToken) throws Exception {
        return new ResultadoJson(mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}")));
    }

    private String token(JsonNode r) { return r.get("token").asText(); }
    private String refresh(JsonNode r) { return r.get("refreshToken").asText(); }

    /** Pequeño wrapper para encadenar andExpect y leer el JSON del refresh. */
    private final class ResultadoJson {
        private final org.springframework.test.web.servlet.ResultActions actions;
        ResultadoJson(org.springframework.test.web.servlet.ResultActions actions) { this.actions = actions; }
        ResultadoJson andExpect(org.springframework.test.web.servlet.ResultMatcher m) throws Exception {
            actions.andExpect(m);
            return this;
        }
        JsonNode andReturnJson() throws Exception {
            return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
        }
    }
}
