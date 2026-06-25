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
}

export interface Cliente {
  id: number;
  rut: string;
  razonSocial: string;
  giro?: string;
  comuna?: string;
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

export const ESTADO_LABEL: Record<EstadoDte, string> = {
  BORRADOR: "Borrador",
  FIRMADO: "Firmado",
  ENVIADO: "Enviado al SII",
  ACEPTADO: "Aceptado",
  RECHAZADO: "Rechazado",
  REPARO: "Con reparo",
  ANULADO: "Anulado",
};
