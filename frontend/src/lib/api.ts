// Cliente HTTP de la API. Con VITE_USE_MOCK="true" la app funciona de forma
// autónoma con datos de demostración (ideal para revisar la interfaz sin
// levantar el backend). Por defecto (false) consume la API real en /api.

import axios from "axios";
import {
  guardarSesion, guardarTokens, limpiarSesion, obtenerRefreshToken, obtenerToken, RUTA_LOGIN,
  type UsuarioSesion,
} from "./auth";
import { camposCaf } from "./caf";
import {
  clientesMock, comprasMock, dashboardMock, documentoDetalleMock, documentosMock, empresaMock,
  foliosMock, libroMock, productosMock, rcofMock,
} from "./mock";
import type {
  Caf, CafRequest, Cliente, ClienteRequest, Compra, CompraRequest, DocumentoResponse,
  DocumentoResumen, Empresa, EmpresaRequest, LibroResponse, Producto, ProductoRequest, RcofResponse,
  ReenvioMasivoResponse, ReferenciaRequest, ResumenDashboard, TipoDte, TipoOperacionLibro,
} from "./types";
import { TIPO_DTE_POR_CODIGO } from "./types";

// Modo demo público: el visitante recorre la app con datos de ejemplo, sin
// backend. Se activa en runtime vía localStorage; requiere recarga porque
// USE_MOCK se evalúa una sola vez al cargar el bundle.
const DEMO_KEY = "nf_demo";

/** true si la sesión actual es la demo pública (chequeo en runtime). */
export function estaEnDemo(): boolean {
  return localStorage.getItem(DEMO_KEY) === "1";
}

/** Entra a la demo: sesión ficticia + recarga hacia el panel. */
export function activarModoDemo() {
  localStorage.setItem(DEMO_KEY, "1");
  guardarSesion("demo-token", "demo-refresh", {
    id: 1, nombre: "Visita de demo", email: "demo@nexofactura.cl", rol: "ADMIN", empresaId: 1,
  });
  window.location.assign("/app");
}

// Opt-in a datos mock: build de demostración (VITE_USE_MOCK="true") o modo
// demo público activado en runtime.
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true" || estaEnDemo();

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";

const http = axios.create({ baseURL: BASE_URL });

http.interceptors.request.use((config) => {
  const token = obtenerToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Rutas de auth que NUNCA deben disparar un refresh (evita recursión).
const RUTAS_SIN_REFRESH = ["/auth/login", "/auth/registro", "/auth/refresh", "/auth/logout"];
function esRutaAuth(url?: string): boolean {
  return !!url && RUTAS_SIN_REFRESH.some((r) => url.includes(r));
}

// Single-flight: aunque varias peticiones reciban 401 a la vez, se hace UN solo
// /refresh y todas reutilizan su resultado.
let refreshEnVuelo: Promise<string> | null = null;

async function refrescarToken(): Promise<string> {
  const refreshToken = obtenerRefreshToken();
  if (!refreshToken) throw new Error("Sin refresh token");
  // axios "pelado" (sin estos interceptores) para que un 401 del refresh no recurse.
  const { data } = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken });
  guardarTokens(data.token, data.refreshToken);
  return data.token as string;
}

function forzarLogin() {
  limpiarSesion();
  if (window.location.pathname !== RUTA_LOGIN) {
    window.location.assign(RUTA_LOGIN);
  }
}

// 401: intentamos UN refresh y reintentamos la petición original. Si el refresh
// falla (o ya reintentamos, o es una ruta de auth), cerramos la sesión local y
// volvemos al login. Un 403 con token válido NO cierra sesión (lo maneja la página).
http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error?.response?.status;
    const original = error?.config;
    const puedeRefrescar =
      status === 401 &&
      original &&
      !original._retry &&
      !esRutaAuth(original.url) &&
      obtenerRefreshToken() != null;

    if (puedeRefrescar) {
      try {
        refreshEnVuelo = refreshEnVuelo ?? refrescarToken();
        const nuevoToken = await refreshEnVuelo;
        original._retry = true;
        original.headers = original.headers ?? {};
        original.headers.Authorization = `Bearer ${nuevoToken}`;
        return http(original);
      } catch {
        forzarLogin();
        return Promise.reject(error);
      } finally {
        refreshEnVuelo = null;
      }
    }

    if (status === 401) {
      forzarLogin();
    }
    return Promise.reject(error);
  },
);

/** Cierra sesión: revoca el refresh token en el servidor (best-effort) y limpia el estado local. */
export function cerrarSesion() {
  const enDemo = estaEnDemo();
  const refreshToken = obtenerRefreshToken();
  if (refreshToken && !enDemo) {
    void axios.post(`${BASE_URL}/auth/logout`, { refreshToken }).catch(() => {});
  }
  localStorage.removeItem(DEMO_KEY);
  limpiarSesion();
  // Salir de la demo exige recargar: USE_MOCK quedó fijado al cargar el bundle
  // y sin recarga un login real seguiría operando contra los mocks.
  if (enDemo) {
    window.location.assign("/");
  }
}

const demora = (ms = 250) => new Promise((r) => setTimeout(r, ms));

// Forma del cuerpo de error de la API (ApiError del backend).
export interface ApiErrorBody {
  estado: number;
  error: string;
  mensaje: string;
  ruta: string;
  detalles: { campo: string; mensaje: string }[] | null;
}

/** Mensaje legible de un error de la API (409 de negocio, etc.). */
export function mensajeError(error: unknown, fallback = "Ocurrió un error inesperado."): string {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as ApiErrorBody | undefined;
    if (body?.mensaje) return body.mensaje;
  }
  return fallback;
}

// ---- Estado del servicio ----

// El health de actuator vive fuera del prefijo /api (p. ej. /actuator/health).
const HEALTH_URL = `${BASE_URL.replace(/\/api\/?$/, "")}/actuator/health`;

/**
 * Comprueba la salud de la API (endpoint público, sin token ni interceptores:
 * un 401 aquí jamás debe redirigir al login). Devuelve true si responde UP.
 */
export async function comprobarSalud(): Promise<boolean> {
  if (USE_MOCK) {
    await demora();
    return true;
  }
  try {
    const { data } = await axios.get<{ status?: string }>(HEALTH_URL, { timeout: 8000 });
    return data?.status === "UP";
  } catch {
    return false;
  }
}

/** Errores de validación campo a campo (400). Devuelve un mapa campo→mensaje. */
export function erroresDeCampo(error: unknown): Record<string, string> {
  const mapa: Record<string, string> = {};
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as ApiErrorBody | undefined;
    for (const d of body?.detalles ?? []) {
      if (d.campo && !mapa[d.campo]) mapa[d.campo] = d.mensaje;
    }
  }
  return mapa;
}

// ---- Registro y sesión ----

export interface RegistroPayload {
  nombre: string;
  email: string;
  password: string;
}

/** Crea la cuenta (usuario ADMIN sin empresa) y guarda la sesión. */
export async function registrarCuenta(payload: RegistroPayload): Promise<UsuarioSesion> {
  if (USE_MOCK) {
    await demora(400);
    const usuario: UsuarioSesion = {
      id: 1, nombre: payload.nombre, email: payload.email, rol: "ADMIN", empresaId: null,
    };
    guardarSesion("demo-token", "demo-refresh", usuario);
    return usuario;
  }
  const { data } = await http.post("/auth/registro", payload);
  guardarSesion(data.token, data.refreshToken, data.usuario);
  return data.usuario as UsuarioSesion;
}

/**
 * Re-emite la sesión con /auth/refresh (rota el refresh token) para que el JWT
 * incorpore claims nuevos — p. ej. el empresaId tras crear la primera empresa.
 */
export async function refrescarSesion(): Promise<UsuarioSesion> {
  if (USE_MOCK) {
    await demora();
    const usuario: UsuarioSesion = {
      id: 1, nombre: "Administrador Demo", email: "admin@nexofactura.cl", rol: "ADMIN", empresaId: 1,
    };
    guardarSesion("demo-token", "demo-refresh", usuario);
    return usuario;
  }
  const refreshToken = obtenerRefreshToken();
  if (!refreshToken) throw new Error("Sin refresh token");
  const { data } = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken });
  guardarSesion(data.token, data.refreshToken, data.usuario);
  return data.usuario as UsuarioSesion;
}

// ---- Empresa (datos del emisor) ----

/**
 * Crea la empresa emisora. Si el usuario autenticado no tenía empresa (flujo
 * de registro), el backend lo asocia a ella; refrescarSesion() actualiza luego
 * el claim del JWT.
 */
export async function crearEmpresa(payload: EmpresaRequest): Promise<Empresa> {
  if (USE_MOCK) {
    await demora(400);
    return {
      ...empresaMock,
      ...payload,
      id: 1,
      actividadEconomica: payload.actividadEconomica ?? null,
      ciudad: payload.ciudad ?? null,
      telefono: payload.telefono ?? null,
      email: payload.email ?? null,
    };
  }
  const { data } = await http.post("/empresas", payload);
  return data;
}
export async function getEmpresa(empresaId: number): Promise<Empresa> {
  if (USE_MOCK) {
    await demora();
    return empresaMock;
  }
  const { data } = await http.get(`/empresas/${empresaId}`);
  return data;
}

/** Actualiza los datos del emisor. Requiere rol ADMIN (403 en caso contrario). */
export async function actualizarEmpresa(empresaId: number, payload: EmpresaRequest): Promise<Empresa> {
  if (USE_MOCK) {
    await demora(400);
    return {
      ...empresaMock,
      ...payload,
      id: empresaId,
      actividadEconomica: payload.actividadEconomica ?? null,
      ciudad: payload.ciudad ?? null,
      telefono: payload.telefono ?? null,
      email: payload.email ?? null,
    };
  }
  const { data } = await http.put(`/empresas/${empresaId}`, payload);
  return data;
}

// ---- Dashboard ----
export async function getDashboard(empresaId: number): Promise<ResumenDashboard> {
  if (USE_MOCK) {
    await demora();
    return dashboardMock;
  }
  const { data } = await http.get(`/empresas/${empresaId}/dashboard`);
  return data;
}

// ---- Documentos ----
export async function getDocumentos(empresaId: number): Promise<DocumentoResumen[]> {
  if (USE_MOCK) {
    await demora();
    return documentosMock;
  }
  const { data } = await http.get(`/empresas/${empresaId}/documentos`);
  return data.contenido;
}

export async function getDocumento(empresaId: number, id: number): Promise<DocumentoResponse> {
  if (USE_MOCK) {
    await demora();
    return documentoDetalleMock;
  }
  const { data } = await http.get(`/empresas/${empresaId}/documentos/${id}`);
  return data;
}

export interface NuevaLinea {
  productoId?: number;
  nombre: string;
  cantidad: number;
  precioUnitario: number;
  descuentoMonto: number;
  /** Descuento % de la línea (DescuentoPct); excluyente con descuentoMonto. */
  descuentoPct?: number;
  afecto: boolean;
  /** Código del otro impuesto (catálogo CATALOGO_IMPUESTOS); omitido = solo IVA. */
  codImpAdic?: number;
}

export async function crearDocumento(
  empresaId: number,
  payload: {
    tipoDte: string;
    clienteId: number | null;
    observacion?: string;
    lineas: NuevaLinea[];
    referencias?: ReferenciaRequest[];
    /** Descuento global % sobre las líneas afectas (DscRcgGlobal). */
    descuentoGlobalPct?: number;
  },
): Promise<DocumentoResponse> {
  if (USE_MOCK) {
    await demora(400);
    return payload.clienteId == null
      ? { ...documentoDetalleMock, receptorRut: "66666666-6", receptorRazonSocial: "Consumidor final" }
      : documentoDetalleMock;
  }
  const { data } = await http.post(`/empresas/${empresaId}/documentos`, payload);
  return data;
}

export async function emitirDocumento(empresaId: number, id: number): Promise<DocumentoResponse> {
  if (USE_MOCK) {
    await demora(400);
    return { ...documentoDetalleMock, estado: "FIRMADO", folio: documentoDetalleMock.folio ?? 144 };
  }
  const { data } = await http.post(`/empresas/${empresaId}/documentos/${id}/emitir`);
  return data;
}

export async function enviarDocumento(empresaId: number, id: number): Promise<DocumentoResponse> {
  if (USE_MOCK) {
    await demora(400);
    return { ...documentoDetalleMock, estado: "ENVIADO", trackId: documentoDetalleMock.trackId ?? "5829134" };
  }
  const { data } = await http.post(`/empresas/${empresaId}/documentos/${id}/enviar`);
  return data;
}

/** Reenvía al SII un documento EN_CONTINGENCIA o RECHAZADO (mismo XML firmado). */
export async function reenviarDocumento(empresaId: number, id: number): Promise<DocumentoResponse> {
  if (USE_MOCK) {
    await demora(400);
    return { ...documentoDetalleMock, estado: "ENVIADO", ultimoErrorEnvio: null };
  }
  const { data } = await http.post(`/empresas/${empresaId}/documentos/${id}/reenviar`);
  return data;
}

/** Reintenta el envío de todos los documentos EN_CONTINGENCIA de la empresa. */
export async function reenviarPendientes(empresaId: number): Promise<ReenvioMasivoResponse> {
  if (USE_MOCK) {
    await demora(400);
    return { procesados: 0, enviados: 0, enContingencia: 0, documentos: [] };
  }
  const { data } = await http.post(`/empresas/${empresaId}/documentos/reenviar-pendientes`);
  return data;
}

export async function consultarEstadoSii(empresaId: number, id: number): Promise<DocumentoResponse> {
  if (USE_MOCK) {
    await demora(400);
    return { ...documentoDetalleMock, estado: "ACEPTADO" };
  }
  const { data } = await http.post(`/empresas/${empresaId}/documentos/${id}/estado-sii`);
  return data;
}

export async function descargarPdf(empresaId: number, id: number): Promise<Blob> {
  if (USE_MOCK) {
    await demora();
    return new Blob(["%PDF-1.4 (mock)"], { type: "application/pdf" });
  }
  const { data } = await http.get(`/empresas/${empresaId}/documentos/${id}/pdf`, {
    responseType: "blob",
  });
  return data;
}

// ---- Clientes y productos ----
export async function getClientes(empresaId: number): Promise<Cliente[]> {
  if (USE_MOCK) {
    await demora();
    return clientesMock;
  }
  const { data } = await http.get(`/empresas/${empresaId}/clientes`);
  return data.contenido;
}

function clienteDeMock(id: number, payload: ClienteRequest): Cliente {
  return {
    id,
    rut: payload.rut,
    razonSocial: payload.razonSocial,
    giro: payload.giro,
    comuna: payload.comuna,
    email: payload.email,
  };
}

export async function crearCliente(empresaId: number, payload: ClienteRequest): Promise<Cliente> {
  if (USE_MOCK) {
    await demora(400);
    return clienteDeMock(Date.now(), payload);
  }
  const { data } = await http.post(`/empresas/${empresaId}/clientes`, payload);
  return data;
}

export async function editarCliente(
  empresaId: number, id: number, payload: ClienteRequest,
): Promise<Cliente> {
  if (USE_MOCK) {
    await demora(400);
    return clienteDeMock(id, payload);
  }
  const { data } = await http.put(`/empresas/${empresaId}/clientes/${id}`, payload);
  return data;
}

export async function getProductos(empresaId: number): Promise<Producto[]> {
  if (USE_MOCK) {
    await demora();
    return productosMock;
  }
  const { data } = await http.get(`/empresas/${empresaId}/productos`);
  return data.contenido;
}

function productoDeMock(id: number, payload: ProductoRequest): Producto {
  return {
    id,
    codigo: payload.codigo,
    nombre: payload.nombre,
    precioNeto: payload.precioNeto,
    unidad: payload.unidad ?? "UN",
    afecto: payload.afecto,
  };
}

export async function crearProducto(empresaId: number, payload: ProductoRequest): Promise<Producto> {
  if (USE_MOCK) {
    await demora(400);
    return productoDeMock(Date.now(), payload);
  }
  const { data } = await http.post(`/empresas/${empresaId}/productos`, payload);
  return data;
}

export async function editarProducto(
  empresaId: number, id: number, payload: ProductoRequest,
): Promise<Producto> {
  if (USE_MOCK) {
    await demora(400);
    return productoDeMock(id, payload);
  }
  const { data } = await http.put(`/empresas/${empresaId}/productos/${id}`, payload);
  return data;
}

// ---- Folios (CAF) ----
// GET devuelve una LISTA PLANA (no `data.contenido`).
export async function getFolios(empresaId: number): Promise<Caf[]> {
  if (USE_MOCK) {
    await demora();
    return foliosMock;
  }
  const { data } = await http.get(`/empresas/${empresaId}/folios`);
  return data;
}

export async function cargarCaf(empresaId: number, payload: CafRequest): Promise<Caf> {
  if (USE_MOCK) {
    await demora(400);
    const campos = camposCaf(payload.xmlCaf);
    const td = campos?.td ?? 33;
    const desde = campos?.desde ?? 1;
    const hasta = campos?.hasta ?? desde;
    const tipo: TipoDte = TIPO_DTE_POR_CODIGO[td] ?? "FACTURA_AFECTA";
    return {
      id: Date.now(),
      tipoDte: tipo,
      folioDesde: desde,
      folioHasta: hasta,
      folioActual: desde - 1,
      foliosDisponibles: hasta - desde + 1,
      agotado: false,
      fechaVencimiento: null,
    };
  }
  const { data } = await http.post(`/empresas/${empresaId}/folios`, payload);
  return data;
}

// ---- RCOF (Reporte de Consumo de Folios) ----
export async function getRcof(empresaId: number, fecha: string): Promise<RcofResponse> {
  if (USE_MOCK) {
    await demora();
    return rcofMock(fecha);
  }
  const { data } = await http.get(`/empresas/${empresaId}/rcof`, { params: { fecha } });
  return data;
}

// ---- Compras (documentos recibidos) ----
export async function getCompras(empresaId: number, periodo: string): Promise<Compra[]> {
  if (USE_MOCK) {
    await demora();
    return comprasMock(periodo);
  }
  const { data } = await http.get(`/empresas/${empresaId}/compras`, { params: { periodo } });
  return data;
}

export async function crearCompra(empresaId: number, payload: CompraRequest): Promise<Compra> {
  if (USE_MOCK) {
    await demora(400);
    return {
      id: Date.now(), ...payload, ivaRetenido: payload.ivaRetenido ?? 0,
      observacion: payload.observacion ?? null, creadoEn: new Date().toISOString(),
    };
  }
  const { data } = await http.post(`/empresas/${empresaId}/compras`, payload);
  return data;
}

export async function eliminarCompra(empresaId: number, id: number): Promise<void> {
  if (USE_MOCK) {
    await demora(300);
    return;
  }
  await http.delete(`/empresas/${empresaId}/compras/${id}`);
}

// ---- Libros de compra/venta (IECV) ----
export async function getLibro(
  empresaId: number, tipo: TipoOperacionLibro, periodo: string,
): Promise<LibroResponse> {
  if (USE_MOCK) {
    await demora();
    return libroMock(tipo, periodo);
  }
  const ruta = tipo === "VENTA" ? "ventas" : "compras";
  const { data } = await http.get(`/empresas/${empresaId}/libros/${ruta}`, { params: { periodo } });
  return data;
}

/**
 * XML LibroCompraVenta del período (sin firmar), para descarga. Se pide como
 * Blob para conservar los BYTES tal como los sirve el backend: el prólogo
 * declara ISO-8859-1, y decodificarlo a string y re-codificarlo a UTF-8 al
 * guardar produciría un archivo cuyo contenido contradice su declaración.
 */
export async function getLibroXml(
  empresaId: number, tipo: TipoOperacionLibro, periodo: string,
): Promise<Blob> {
  if (USE_MOCK) {
    await demora();
    return new Blob(
      [`<?xml version="1.0" encoding="ISO-8859-1"?>\n<LibroCompraVenta version="1.0"/>`],
      { type: "application/xml" },
    );
  }
  const ruta = tipo === "VENTA" ? "ventas" : "compras";
  const { data } = await http.get(`/empresas/${empresaId}/libros/${ruta}/xml`, {
    params: { periodo },
    responseType: "blob",
  });
  return data;
}

export default http;
