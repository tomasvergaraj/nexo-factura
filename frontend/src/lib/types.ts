// Tipos compartidos con la API (espejo de los DTOs del backend).

export type EstadoDte =
  | "BORRADOR"
  | "FIRMADO"
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
  montoLinea: number;
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
  trackId: string | null;
  observacion: string | null;
  lineas: LineaResponse[];
  creadoEn: string;
  referencias: ReferenciaResponse[];
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
  ENVIADO: "Enviado al SII",
  ACEPTADO: "Aceptado",
  RECHAZADO: "Rechazado",
  REPARO: "Con reparo",
  ANULADO: "Anulado",
};
