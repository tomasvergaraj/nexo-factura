// Datos de demostración para previsualizar la UI sin backend.
// Cambie USE_MOCK a false en api.ts para consumir la API real.

import type {
  Caf, Cliente, DocumentoResponse, DocumentoResumen, Producto, RcofResponse, ResumenDashboard,
} from "./types";

export const clientesMock: Cliente[] = [
  { id: 1, rut: "77111222-3", razonSocial: "Comercial Las Palmas Ltda", comuna: "Viña del Mar", email: "pagos@laspalmas.cl" },
  { id: 2, rut: "78222333-4", razonSocial: "Constructora Andes SpA", comuna: "Quillota", email: "finanzas@andes.cl" },
  { id: 3, rut: "79333444-5", razonSocial: "Restaurant El Fogón EIRL", comuna: "La Calera", email: "admin@elfogon.cl" },
];

export const productosMock: Producto[] = [
  { id: 1, codigo: "SRV-001", nombre: "Desarrollo de landing page", precioNeto: 450000, unidad: "UN", afecto: true },
  { id: 2, codigo: "SRV-002", nombre: "Plan de soporte mensual", precioNeto: 120000, unidad: "MES", afecto: true },
  { id: 3, codigo: "SRV-003", nombre: "Hora de desarrollo", precioNeto: 25000, unidad: "HRA", afecto: true },
  { id: 4, codigo: "SRV-004", nombre: "Consultoría técnica (exenta)", precioNeto: 80000, unidad: "UN", afecto: false },
];

export const documentosMock: DocumentoResumen[] = [
  { id: 10, tipoDte: "FACTURA_AFECTA", codigoTipo: 33, folio: 142, estado: "ACEPTADO", fechaEmision: "2026-06-23", receptorRazonSocial: "Constructora Andes SpA", total: 1666500 },
  { id: 9, tipoDte: "FACTURA_AFECTA", codigoTipo: 33, folio: 141, estado: "ACEPTADO", fechaEmision: "2026-06-21", receptorRazonSocial: "Comercial Las Palmas Ltda", total: 535500 },
  { id: 8, tipoDte: "FACTURA_AFECTA", codigoTipo: 33, folio: 140, estado: "ENVIADO", fechaEmision: "2026-06-20", receptorRazonSocial: "Restaurant El Fogón EIRL", total: 285600 },
  { id: 7, tipoDte: "FACTURA_AFECTA", codigoTipo: 33, folio: null, estado: "BORRADOR", fechaEmision: "2026-06-20", receptorRazonSocial: "Comercial Las Palmas Ltda", total: 142800 },
  { id: 6, tipoDte: "NOTA_CREDITO", codigoTipo: 61, folio: 18, estado: "ACEPTADO", fechaEmision: "2026-06-18", receptorRazonSocial: "Constructora Andes SpA", total: 119000 },
  { id: 5, tipoDte: "FACTURA_AFECTA", codigoTipo: 33, folio: 139, estado: "RECHAZADO", fechaEmision: "2026-06-17", receptorRazonSocial: "Restaurant El Fogón EIRL", total: 333200 },
  { id: 4, tipoDte: "FACTURA_AFECTA", codigoTipo: 33, folio: 138, estado: "ACEPTADO", fechaEmision: "2026-06-16", receptorRazonSocial: "Comercial Las Palmas Ltda", total: 892500 },
  { id: 3, tipoDte: "FACTURA_AFECTA", codigoTipo: 33, folio: 137, estado: "ACEPTADO", fechaEmision: "2026-06-14", receptorRazonSocial: "Constructora Andes SpA", total: 1547000 },
];

export const dashboardMock: ResumenDashboard = {
  documentosMes: 38,
  montoEmitidoMes: 14580600,
  pendientesSii: 1,
  aceptados: 34,
  borradores: 3,
  recientes: documentosMock,
};

export const documentoDetalleMock: DocumentoResponse = {
  ...documentosMock[0],
  receptorRut: "78222333-4",
  neto: 1400000,
  exento: 0,
  tasaIva: 19,
  iva: 266000,
  trackId: "5829134",
  observacion: null,
  creadoEn: "2026-06-23T10:24:00Z",
  lineas: [
    { numeroLinea: 1, nombre: "Desarrollo de landing page", cantidad: 1, unidad: "UN", precioUnitario: 450000, descuentoMonto: 0, afecto: true, montoLinea: 450000 },
    { numeroLinea: 2, nombre: "Plan de soporte mensual", cantidad: 3, unidad: "MES", precioUnitario: 120000, descuentoMonto: 0, afecto: true, montoLinea: 360000 },
    { numeroLinea: 3, nombre: "Hora de desarrollo", cantidad: 23.6, unidad: "HRA", precioUnitario: 25000, descuentoMonto: 0, afecto: true, montoLinea: 590000 },
  ],
  referencias: [],
};

export const foliosMock: Caf[] = [
  { id: 1, tipoDte: "FACTURA_AFECTA", folioDesde: 1, folioHasta: 200, folioActual: 143, foliosDisponibles: 58, agotado: false, fechaVencimiento: "2026-12-31" },
  { id: 2, tipoDte: "NOTA_CREDITO", folioDesde: 1, folioHasta: 20, folioActual: 21, foliosDisponibles: 0, agotado: true, fechaVencimiento: "2026-12-31" },
  { id: 3, tipoDte: "BOLETA_AFECTA", folioDesde: 1, folioHasta: 500, folioActual: 87, foliosDisponibles: 414, agotado: false, fechaVencimiento: "2026-12-31" },
  { id: 4, tipoDte: "BOLETA_EXENTA", folioDesde: 1, folioHasta: 100, folioActual: 12, foliosDisponibles: 89, agotado: false, fechaVencimiento: "2026-12-31" },
];

/** RCOF de demostración para la fecha pedida (siempre con movimiento de boletas). */
export function rcofMock(fecha: string): RcofResponse {
  const afecta = {
    tipoDocumento: 39,
    foliosEmitidos: 14,
    foliosUtilizados: 13,
    folioInicial: 75,
    folioFinal: 87,
    foliosAnulados: 1,
    folioAnuladoInicial: 80,
    folioAnuladoFinal: 80,
    montoNeto: 546218,
    montoIva: 103782,
    montoExento: 0,
    montoTotal: 650000,
  };
  const exenta = {
    tipoDocumento: 41,
    foliosEmitidos: 3,
    foliosUtilizados: 3,
    folioInicial: 10,
    folioFinal: 12,
    foliosAnulados: 0,
    folioAnuladoInicial: null,
    folioAnuladoFinal: null,
    montoNeto: 0,
    montoIva: 0,
    montoExento: 84000,
    montoTotal: 84000,
  };
  return {
    fecha,
    secEnvio: 1,
    documentos: [afecta, exenta],
    totales: {
      foliosEmitidos: afecta.foliosEmitidos + exenta.foliosEmitidos,
      foliosUtilizados: afecta.foliosUtilizados + exenta.foliosUtilizados,
      foliosAnulados: afecta.foliosAnulados + exenta.foliosAnulados,
      montoNeto: afecta.montoNeto + exenta.montoNeto,
      montoIva: afecta.montoIva + exenta.montoIva,
      montoExento: afecta.montoExento + exenta.montoExento,
      montoTotal: afecta.montoTotal + exenta.montoTotal,
    },
    sinMovimiento: false,
  };
}

/** Serie de emisión de los últimos 7 días para el gráfico del dashboard. */
export const serieEmisionMock = [
  { dia: "Jue", valor: 1240000 },
  { dia: "Vie", valor: 2110000 },
  { dia: "Sáb", valor: 640000 },
  { dia: "Dom", valor: 180000 },
  { dia: "Lun", valor: 2480000 },
  { dia: "Mar", valor: 1980000 },
  { dia: "Mié", valor: 2666500 },
];
