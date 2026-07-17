// Tipos compartidos con la API (espejo de los DTOs del backend).

import type { UsuarioSesion } from "./auth";

/** Espejo de AuthResponse del backend (login / refresh). */
export interface AuthResponse {
  token: string;           // access JWT (corto)
  tipo: string;            // "Bearer"
  expiraEnMinutos: number; // vigencia del access
  refreshToken: string;    // opaco, rotado en cada /refresh
  refreshExpiraEn: string; // ISO-8601 (OffsetDateTime serializado)
  usuario: UsuarioSesion;
}

export type EstadoDte =
  | "BORRADOR"
  | "FIRMADO"
  | "EN_CONTINGENCIA"
  | "ENVIADO"
  | "ACEPTADO"
  | "RECHAZADO"
  | "REPARO"
  | "ANULADO";

export type TipoDte =
  | "FACTURA_AFECTA"
  | "FACTURA_EXENTA"
  | "BOLETA_AFECTA"
  | "BOLETA_EXENTA"
  | "NOTA_DEBITO"
  | "NOTA_CREDITO";

export interface DocumentoResumen {
  id: number;
  tipoDte: TipoDte;
  codigoTipo: number;
  folio: number | null;
  estado: EstadoDte;
  fechaEmision: string;
  receptorRazonSocial: string;
  total: number;
}

export interface LineaResponse {
  numeroLinea: number;
  nombre: string;
  cantidad: number;
  unidad: string;
  precioUnitario: number;
  descuentoMonto: number;
  afecto: boolean;
  /** Código del otro impuesto de la línea (catálogo CATALOGO_IMPUESTOS); null = solo IVA. */
  codImpAdic: number | null;
  montoLinea: number;
}

/** Desglose de un otro-impuesto del documento (espejo de ImpuestoResponse del backend). */
export interface ImpuestoResponse {
  codigo: number;
  nombre: string;
  tasa: number;
  esRetencion: boolean;
  base: number;
  monto: number;
}

export type TipoReferencia = "ANULA_DOCUMENTO" | "CORRIGE_TEXTO" | "CORRIGE_MONTO";

export const TIPO_REFERENCIA_LABEL: Record<TipoReferencia, string> = {
  ANULA_DOCUMENTO: "Anula documento",
  CORRIGE_TEXTO: "Corrige texto",
  CORRIGE_MONTO: "Corrige monto",
};

export interface ReferenciaResponse {
  tipoDocumentoRef: number;
  folioRef: number;
  fechaRef: string;
  tipoReferencia: TipoReferencia;
  codigoReferencia: number;
  razon: string;
}

export interface ReferenciaRequest {
  tipoDocumentoRef: number;
  folioRef: number;
  fechaRef: string;
  tipoReferencia: TipoReferencia;
  razon: string;
}

export interface DocumentoResponse extends DocumentoResumen {
  receptorRut: string;
  neto: number;
  exento: number;
  tasaIva: number;
  iva: number;
  /** Suma de impuestos adicionales (suben el total). */
  impuestosAdicionales: number;
  /** IVA retenido por cambio de sujeto (resta del total). */
  ivaRetenido: number;
  trackId: string | null;
  observacion: string | null;
  lineas: LineaResponse[];
  creadoEn: string;
  referencias: ReferenciaResponse[];
  /** Desglose de otros impuestos (bloques ImptoReten del XML). */
  impuestos: ImpuestoResponse[];
  /** Sello de integridad (SHA-256 del XML firmado); null mientras es borrador. */
  sello: string | null;
  /** Intentos de envío al SII realizados (exitosos o en contingencia). */
  intentosEnvio: number;
  /** Momento del último intento de envío (ISO-8601); null si nunca se envió. */
  ultimoEnvioEn: string | null;
  /** Motivo del último fallo de envío; null si el último intento fue exitoso. */
  ultimoErrorEnvio: string | null;
}

/** Resultado del reenvío masivo de documentos EN_CONTINGENCIA. */
export interface ReenvioMasivoResponse {
  procesados: number;
  enviados: number;
  enContingencia: number;
  documentos: DocumentoResumen[];
}

export interface Cliente {
  id: number;
  rut: string;
  razonSocial: string;
  giro?: string;
  comuna?: string;
  email?: string;
}

export interface ClienteRequest {
  rut: string;
  razonSocial: string;
  giro?: string;
  direccion?: string;
  comuna?: string;
  ciudad?: string;
  email?: string;
}

export interface Producto {
  id: number;
  codigo?: string;
  nombre: string;
  precioNeto: number;
  unidad: string;
  afecto: boolean;
}

export interface ProductoRequest {
  codigo?: string;
  nombre: string;
  precioNeto: number;
  unidad?: string;
  afecto: boolean;
}

/** Código de Autorización de Folios (espejo de CafResponse del backend). */
export interface Caf {
  id: number;
  tipoDte: TipoDte;
  folioDesde: number;
  folioHasta: number;
  folioActual: number;
  foliosDisponibles: number;
  agotado: boolean;
  fechaVencimiento: string | null;
}

export interface CafRequest {
  tipoDte: TipoDte;
  folioDesde: number;
  folioHasta: number;
  xmlCaf?: string;
  fechaAutorizacion?: string;
  fechaVencimiento?: string;
}

export interface ResumenDashboard {
  documentosMes: number;
  montoEmitidoMes: number;
  pendientesSii: number;
  aceptados: number;
  borradores: number;
  /** Documentos cuyo envío al SII falló y esperan reintento. */
  enContingencia: number;
  recientes: DocumentoResumen[];
}

export const TIPO_DTE_LABEL: Record<TipoDte, string> = {
  FACTURA_AFECTA: "Factura afecta",
  FACTURA_EXENTA: "Factura exenta",
  BOLETA_AFECTA: "Boleta",
  BOLETA_EXENTA: "Boleta exenta",
  NOTA_DEBITO: "Nota de débito",
  NOTA_CREDITO: "Nota de crédito",
};

/** Código de tipo de DTE del SII (espejo EXACTO de TipoDte del backend). */
export const CODIGO_TIPO_DTE: Record<TipoDte, number> = {
  FACTURA_AFECTA: 33,
  FACTURA_EXENTA: 34,
  BOLETA_AFECTA: 39,
  BOLETA_EXENTA: 41,
  NOTA_DEBITO: 56,
  NOTA_CREDITO: 61,
};

export const ESTADO_LABEL: Record<EstadoDte, string> = {
  BORRADOR: "Borrador",
  FIRMADO: "Firmado",
  EN_CONTINGENCIA: "En contingencia",
  ENVIADO: "Enviado al SII",
  ACEPTADO: "Aceptado",
  RECHAZADO: "Rechazado",
  REPARO: "Con reparo",
  ANULADO: "Anulado",
};

/** Tipos que admiten receptor "Consumidor final" (clienteId opcional) y precio bruto IVA-incluido. */
export const ES_BOLETA: Record<TipoDte, boolean> = {
  FACTURA_AFECTA: false,
  FACTURA_EXENTA: false,
  BOLETA_AFECTA: true,
  BOLETA_EXENTA: true,
  NOTA_DEBITO: false,
  NOTA_CREDITO: false,
};

export const RUT_CONSUMIDOR_FINAL = "66666666-6";
export const RAZON_CONSUMIDOR_FINAL = "Consumidor final";

// ---- Otros impuestos (P1-6): adicionales y retención de IVA ----

export interface ImpuestoCatalogo {
  codigo: number;
  nombre: string;
  tasa: number;
  esRetencion: boolean;
}

/**
 * Catálogo REPRESENTATIVO de otros impuestos, espejo EXACTO del enum TipoImpuesto
 * del backend (mismos códigos/tasas). Fuente del selector y del cálculo en vivo.
 */
export const CATALOGO_IMPUESTOS: ImpuestoCatalogo[] = [
  { codigo: 23, nombre: "Impuesto adicional artículos suntuarios", tasa: 15, esRetencion: false },
  { codigo: 24, nombre: "ILA licores, piscos, whisky y destilados", tasa: 31.5, esRetencion: false },
  { codigo: 25, nombre: "ILA vinos, espumosos y sidras", tasa: 20.5, esRetencion: false },
  { codigo: 26, nombre: "ILA cervezas y otras bebidas alcohólicas", tasa: 20.5, esRetencion: false },
  { codigo: 27, nombre: "ILA bebidas analcohólicas y minerales", tasa: 10, esRetencion: false },
  { codigo: 271, nombre: "ILA bebidas analcohólicas azucaradas", tasa: 18, esRetencion: false },
  { codigo: 15, nombre: "IVA retenido total (cambio de sujeto)", tasa: 19, esRetencion: true },
];

export const IMPUESTO_POR_CODIGO: Record<number, ImpuestoCatalogo> = Object.fromEntries(
  CATALOGO_IMPUESTOS.map((i) => [i.codigo, i]),
);

/**
 * Tipos que admiten otros impuestos: solo precios netos y afectos (33/56/61),
 * espejo de `!preciosBrutos() && esAfecto()` del backend.
 */
export const PERMITE_IMPUESTOS: Record<TipoDte, boolean> = {
  FACTURA_AFECTA: true,
  FACTURA_EXENTA: false,
  BOLETA_AFECTA: false,
  BOLETA_EXENTA: false,
  NOTA_DEBITO: true,
  NOTA_CREDITO: true,
};

/** Reverse lookup: código SII → TipoDte (el RCOF entrega el tipo como código numérico). */
export const TIPO_DTE_POR_CODIGO: Record<number, TipoDte> = Object.fromEntries(
  Object.entries(CODIGO_TIPO_DTE).map(([k, v]) => [v, k as TipoDte]),
) as Record<number, TipoDte>;

// ---- Compras (documentos recibidos) y libros IECV — espejo del backend ----

/** Documento recibido registrado manualmente (espejo de CompraResponse). */
export interface Compra {
  id: number;
  tipoDte: number; // código SII: 33 | 34 | 46 | 56 | 61
  folio: number;
  rutProveedor: string;
  razonSocial: string;
  fechaEmision: string; // YYYY-MM-DD
  neto: number;
  exento: number;
  iva: number;
  /** IVA retenido por el comprador (cambio de sujeto, típico del 46); resta del total. */
  ivaRetenido: number;
  total: number;
  observacion: string | null;
  creadoEn: string;
}

export interface CompraRequest {
  tipoDte: number;
  folio: number;
  rutProveedor: string;
  razonSocial: string;
  fechaEmision: string;
  neto: number;
  exento: number;
  iva: number;
  ivaRetenido?: number;
  total: number;
  observacion?: string;
}

/** Tipos admitidos en el registro de compras (espejo de CompraService.TIPOS_PERMITIDOS). */
export const TIPOS_COMPRA: { codigo: number; label: string }[] =
  [33, 34, 46, 56, 61].map((codigo) => ({ codigo, label: `${nombreTipoDte(codigo)} (${codigo})` }));

export type TipoOperacionLibro = "VENTA" | "COMPRA";

export interface LibroResumenTipo {
  tipoDocumento: number;
  documentos: number;
  anulados: number;
  neto: number;
  exento: number;
  iva: number;
  otrosImpuestos: number;
  ivaRetenido: number;
  total: number;
}

export interface LibroDetalleDoc {
  tipoDocumento: number;
  folio: number;
  fecha: string;
  rutContraparte: string;
  razonSocial: string;
  neto: number;
  exento: number;
  iva: number;
  otrosImpuestos: number;
  ivaRetenido: number;
  total: number;
  anulado: boolean;
}

export interface LibroTotales {
  documentos: number;
  anulados: number;
  neto: number;
  exento: number;
  iva: number;
  otrosImpuestos: number;
  ivaRetenido: number;
  total: number;
}

export interface LibroResponse {
  periodo: string; // YYYY-MM
  tipoOperacion: TipoOperacionLibro;
  resumen: LibroResumenTipo[];
  detalle: LibroDetalleDoc[];
  totales: LibroTotales;
  sinMovimiento: boolean;
}

/** Nombre de un tipo de DTE a partir de su código SII (incluye recibidos como el 46). */
export function nombreTipoDte(codigo: number): string {
  const tipo = TIPO_DTE_POR_CODIGO[codigo];
  if (tipo) return TIPO_DTE_LABEL[tipo];
  return codigo === 46 ? "Factura de compra" : `Tipo ${codigo}`;
}

// ---- RCOF (Reporte de Consumo de Folios) — nombres canónicos del backend ----
export interface RcofPorTipo {
  tipoDocumento: number; // 39 | 41
  foliosEmitidos: number;
  foliosUtilizados: number;
  folioInicial: number | null;
  folioFinal: number | null;
  foliosAnulados: number;
  folioAnuladoInicial: number | null;
  folioAnuladoFinal: number | null;
  montoNeto: number;
  montoIva: number;
  montoExento: number;
  montoTotal: number;
}

export interface RcofTotales {
  foliosEmitidos: number;
  foliosUtilizados: number;
  foliosAnulados: number;
  montoNeto: number;
  montoIva: number;
  montoExento: number;
  montoTotal: number;
}

export interface RcofResponse {
  fecha: string; // YYYY-MM-DD
  secEnvio: number;
  documentos: RcofPorTipo[];
  totales: RcofTotales;
  sinMovimiento: boolean;
}
