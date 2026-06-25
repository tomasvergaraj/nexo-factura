package cl.nexosoftware.factura.seguridad;

import cl.nexosoftware.factura.AbstractIntegrationTest;
import cl.nexosoftware.factura.auth.Rol;
import cl.nexosoftware.factura.auth.Usuario;
import cl.nexosoftware.factura.auth.UsuarioRepository;
import cl.nexosoftware.factura.cliente.Cliente;
import cl.nexosoftware.factura.cliente.ClienteRepository;
import cl.nexosoftware.factura.documento.DocumentoTributario;
import cl.nexosoftware.factura.documento.DocumentoRepository;
import cl.nexosoftware.factura.documento.EstadoDte;
import cl.nexosoftware.factura.documento.TipoDte;
import cl.nexosoftware.factura.empresa.Empresa;
import cl.nexosoftware.factura.empresa.EmpresaRepository;
import cl.nexosoftware.factura.producto.Producto;
import cl.nexosoftware.factura.producto.ProductoRepository;
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

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifica el aislamiento multi-tenant de extremo a extremo sobre los 5
 * controllers con path {@code /api/empresas/{empresaId}/...}.
 *
 * <p>Escenario: la empresa A tiene un usuario ADMIN; la empresa B tiene recursos
 * propios. El usuario de A obtiene un token real via {@code POST /api/auth/login}
 * y se comprueba que:
 * <ul>
 *   <li>cross-tenant (path = B con token de A) -&gt; 403</li>
 *   <li>mismo-tenant (path = A con token de A) -&gt; 2xx</li>
 *   <li>sin token -&gt; 401</li>
 *   <li>fila ajena (path = A, id de un recurso de B) -&gt; 404</li>
 * </ul>
 */
@AutoConfigureMockMvc
class AislamientoMultiTenantIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private DocumentoRepository documentoRepository;

    private static final String PASSWORD = "secreta123";

    private Long empresaIdA;
    private Long empresaIdB;
    private String emailAdminA;

    // Recursos de B (para el caso "fila ajena": path A, id de B).
    private Long clienteIdB;
    private Long productoIdB;
    private Long documentoIdB;

    // Recurso de A (para el caso mismo-tenant 2xx sobre un {id} concreto).
    private Long clienteIdA;
    private Long documentoIdA;

    @BeforeEach
    void preparar() {
        int sufijo = ThreadLocalRandom.current().nextInt(1000, 9999);

        Empresa a = empresaRepository.save(empresa("92" + sufijo + "000-1", "Empresa A"));
        Empresa b = empresaRepository.save(empresa("93" + sufijo + "000-2", "Empresa B"));
        empresaIdA = a.getId();
        empresaIdB = b.getId();

        emailAdminA = "admin-a-" + sufijo + "@test.cl";
        usuarioRepository.save(Usuario.builder()
                .nombre("Admin A")
                .email(emailAdminA)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .rol(Rol.ADMIN)
                .activo(true)
                .empresa(a)
                .build());

        // Recursos de A
        clienteIdA = clienteRepository.save(cliente(empresaIdA, "77100100-1")).getId();
        documentoIdA = documentoRepository.save(borrador(empresaIdA, "77100100-1")).getId();

        // Recursos de B
        clienteIdB = clienteRepository.save(cliente(empresaIdB, "77200200-2")).getId();
        productoIdB = productoRepository.save(producto(empresaIdB)).getId();
        documentoIdB = documentoRepository.save(borrador(empresaIdB, "77200200-2")).getId();
    }

    // ------------------------------------------------------------------
    // Cross-tenant: token de A apuntando al path de B -> 403 en los 5 controllers
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cross-tenant: token de A sobre rutas de B devuelve 403 en los 5 controllers")
    void crossTenantDevuelve403() throws Exception {
        String token = loginA();

        // documentos
        mockMvc.perform(get("/api/empresas/{id}/documentos", empresaIdB).header("Authorization", token))
                .andExpect(status().isForbidden());
        // documentos: estado-sii por POST (verbo del contrato WS3)
        mockMvc.perform(post("/api/empresas/{id}/documentos/{doc}/estado-sii", empresaIdB, documentoIdB)
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
        // clientes
        mockMvc.perform(get("/api/empresas/{id}/clientes", empresaIdB).header("Authorization", token))
                .andExpect(status().isForbidden());
        // productos
        mockMvc.perform(get("/api/empresas/{id}/productos", empresaIdB).header("Authorization", token))
                .andExpect(status().isForbidden());
        // folios
        mockMvc.perform(get("/api/empresas/{id}/folios", empresaIdB).header("Authorization", token))
                .andExpect(status().isForbidden());
        // dashboard
        mockMvc.perform(get("/api/empresas/{id}/dashboard", empresaIdB).header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Mismo-tenant: token de A sobre su propio path -> 2xx
    // ------------------------------------------------------------------

    @Test
    @DisplayName("mismo-tenant: token de A sobre sus propias rutas devuelve 2xx")
    void mismoTenantDevuelve2xx() throws Exception {
        String token = loginA();

        mockMvc.perform(get("/api/empresas/{id}/documentos", empresaIdA).header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/empresas/{id}/documentos/{doc}", empresaIdA, documentoIdA)
                        .header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/empresas/{id}/clientes", empresaIdA).header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/empresas/{id}/productos", empresaIdA).header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/empresas/{id}/folios", empresaIdA).header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/empresas/{id}/dashboard", empresaIdA).header("Authorization", token))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Sin token -> 401
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sin token: las rutas protegidas devuelven 401")
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/empresas/{id}/documentos", empresaIdA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/empresas/{id}/clientes", empresaIdA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/empresas/{id}/productos", empresaIdA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/empresas/{id}/folios", empresaIdA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/empresas/{id}/dashboard", empresaIdA))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Fila ajena (IDOR): path = A, id de un recurso de B -> 404 (no filtrar existencia)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("fila ajena: id de B bajo el path de A devuelve 404")
    void filaAjenaDevuelve404() throws Exception {
        String token = loginA();

        // documento de B accedido por GET bajo empresa A
        mockMvc.perform(get("/api/empresas/{id}/documentos/{doc}", empresaIdA, documentoIdB)
                        .header("Authorization", token))
                .andExpect(status().isNotFound());

        // estado-sii (POST) sobre documento de B bajo empresa A
        mockMvc.perform(post("/api/empresas/{id}/documentos/{doc}/estado-sii", empresaIdA, documentoIdB)
                        .header("Authorization", token))
                .andExpect(status().isNotFound());

        // actualizar cliente de B bajo empresa A
        mockMvc.perform(put("/api/empresas/{id}/clientes/{cli}", empresaIdA, clienteIdB)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clienteRequestJson("77299299-8", "Cliente Ajeno")))
                .andExpect(status().isNotFound());

        // actualizar producto de B bajo empresa A
        mockMvc.perform(put("/api/empresas/{id}/productos/{prod}", empresaIdA, productoIdB)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productoRequestJson("Producto Ajeno")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Login real contra el endpoint y retorna el header Authorization listo. */
    private String loginA() throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(emailAdminA, PASSWORD);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        return "Bearer " + json.get("token").asText();
    }

    private Empresa empresa(String rut, String razonSocial) {
        return Empresa.builder()
                .rut(rut)
                .razonSocial(razonSocial)
                .giro("Pruebas")
                .direccion("Calle 1")
                .comuna("Quillota")
                .build();
    }

    private Cliente cliente(Long empresaId, String rut) {
        return Cliente.builder()
                .empresaId(empresaId)
                .rut(rut)
                .razonSocial("Cliente " + empresaId)
                .build();
    }

    private Producto producto(Long empresaId) {
        return Producto.builder()
                .empresaId(empresaId)
                .codigo("P-" + empresaId)
                .nombre("Producto " + empresaId)
                .precioNeto(10000L)
                .unidad("UN")
                .afecto(true)
                .build();
    }

    private DocumentoTributario borrador(Long empresaId, String receptorRut) {
        return DocumentoTributario.builder()
                .empresaId(empresaId)
                .tipoDte(TipoDte.FACTURA_AFECTA)
                .estado(EstadoDte.BORRADOR)
                .fechaEmision(LocalDate.now())
                .receptorRut(receptorRut)
                .receptorRazonSocial("Receptor")
                .neto(10000)
                .exento(0)
                .tasaIva(19.0)
                .iva(1900)
                .total(11900)
                .build();
    }

    private String clienteRequestJson(String rut, String razonSocial) {
        return """
                {"rut":"%s","razonSocial":"%s"}
                """.formatted(rut, razonSocial);
    }

    private String productoRequestJson(String nombre) {
        return """
                {"nombre":"%s","precioNeto":12345,"afecto":true}
                """.formatted(nombre);
    }
}
