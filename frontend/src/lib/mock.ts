// Datos de demostración para previsualizar la UI sin backend.
// Cambie USE_MOCK a false en api.ts para consumir la API real.

import type {
  Cliente, DocumentoResponse, DocumentoResumen, Producto, ResumenDashboard,
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
};

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
