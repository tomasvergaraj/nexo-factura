package cl.nexosoftware.factura.seguridad;

import cl.nexosoftware.factura.AbstractIntegrationTest;
import cl.nexosoftware.factura.auth.Rol;
import cl.nexosoftware.factura.auth.Usuario;
import cl.nexosoftware.factura.auth.UsuarioRepository;
import cl.nexosoftware.factura.cliente.Cliente;
import cl.nexosoftware.factura.cliente.ClienteRepository;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Robustez (P2-4): un RUT duplicado (incluso con distinto formato) se traduce en
 * 409, no en 500; y el bloqueo optimista (@Version) evita el lost update sobre
 * datos maestros, lanzando OptimisticLockingFailureException ante una escritura
 * concurrente sobre una version obsoleta.
 */
@AutoConfigureMockMvc
class RobustezIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteRepository clienteRepository;

    private static final String PASSWORD = "secreta123";

    private Long empresaId;
    private String email;

    @BeforeEach
    void preparar() {
        int sufijo = ThreadLocalRandom.current().nextInt(1000, 9999);
        Empresa empresa = empresaRepository.save(Empresa.builder()
                .rut("92" + sufijo + "000-1")
                .razonSocial("Empresa Robustez")
                .giro("Pruebas")
                .direccion("Calle 1")
                .comuna("Quillota")
                .build());
        empresaId = empresa.getId();

        email = "admin-" + sufijo + "@test.cl";
        usuarioRepository.save(Usuario.builder()
                .nombre("Admin")
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .rol(Rol.ADMIN)
                .activo(true)
                .empresa(empresa)
                .build());
    }

    @Test
    @DisplayName("un cliente con RUT duplicado devuelve 409 (no 500), aun con otro formato")
    void clienteDuplicadoDevuelve409() throws Exception {
        String token = login();

        // Primera alta: 201.
        mockMvc.perform(post("/api/empresas/{id}/clientes", empresaId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clienteJson("76543210-3", "Cliente Uno")))
                .andExpect(status().isCreated());

        // Mismo RUT exacto: 409.
        mockMvc.perform(post("/api/empresas/{id}/clientes", empresaId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clienteJson("76543210-3", "Cliente Uno otra vez")))
                .andExpect(status().isConflict());

        // Mismo RUT con puntos: tras normalizar es el mismo -> 409 (dedup correcta).
        mockMvc.perform(post("/api/empresas/{id}/clientes", empresaId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clienteJson("76.543.210-3", "Cliente Uno con puntos")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("una escritura sobre una version obsoleta lanza OptimisticLockingFailureException")
    void versionObsoletaLanzaConflicto() {
        Cliente guardado = clienteRepository.save(Cliente.builder()
                .empresaId(empresaId)
                .rut("78222333-K")
                .razonSocial("Cliente Version")
                .build());
        Long id = guardado.getId();

        // Dos copias separadas, ambas en version 0 (lecturas no transaccionales).
        Cliente copiaA = clienteRepository.findById(id).orElseThrow();
        Cliente copiaB = clienteRepository.findById(id).orElseThrow();

        // La copia A gana: version -> 1.
        copiaA.setRazonSocial("Editado por A");
        clienteRepository.save(copiaA);

        // La copia B trabaja sobre la version 0 ya superada -> conflicto.
        copiaB.setRazonSocial("Editado por B");
        assertThatThrownBy(() -> clienteRepository.save(copiaB))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(clienteRepository.findById(id).orElseThrow().getRazonSocial())
                .isEqualTo("Editado por A");
    }

    private String login() throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        return "Bearer " + json.get("token").asText();
    }

    private String clienteJson(String rut, String razonSocial) {
        return """
                {"rut":"%s","razonSocial":"%s"}
                """.formatted(rut, razonSocial);
    }
}
