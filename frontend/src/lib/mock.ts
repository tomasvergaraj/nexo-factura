// Datos de demostración para previsualizar la UI sin backend.
// Cambie USE_MOCK a false en api.ts para consumir la API real.

import type {
  Caf, Cliente, Compra, DocumentoResponse, DocumentoResumen, Empresa, LibroResponse, Producto,
  RcofResponse, ResumenDashboard, TipoOperacionLibro,
} from "./types";

export const empresaMock: Empresa = {
  id: 1,
  rut: "76543210-9",
  razonSocial: "Nexo Software SpA",
  giro: "Desarrollo y mantención de software",
  actividadEconomica: 620200,
  direccion: "Calle O'Higgins 1234, Of. 302",
  comuna: "Quillota",
  ciudad: "Quillota",
  telefono: "+56 9 8196 4119",
  email: "contacto@nexosoftware.cl",
};

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
  enContingencia: 0,
  recientes: documentosMock,
};

export const documentoDetalleMock: DocumentoResponse = {
  ...documentosMock[0],
  receptorRut: "78222333-4",
  neto: 1400000,
  exento: 0,
  tasaIva: 19,
  iva: 266000,
  impuestosAdicionales: 0,
  ivaRetenido: 0,
  trackId: "5829134",
  observacion: null,
  creadoEn: "2026-06-23T10:24:00Z",
  lineas: [
    { numeroLinea: 1, nombre: "Desarrollo de landing page", cantidad: 1, unidad: "UN", precioUnitario: 450000, descuentoMonto: 0, afecto: true, codImpAdic: null, montoLinea: 450000 },
    { numeroLinea: 2, nombre: "Plan de soporte mensual", cantidad: 3, unidad: "MES", precioUnitario: 120000, descuentoMonto: 0, afecto: true, codImpAdic: null, montoLinea: 360000 },
    { numeroLinea: 3, nombre: "Hora de desarrollo", cantidad: 23.6, unidad: "HRA", precioUnitario: 25000, descuentoMonto: 0, afecto: true, codImpAdic: null, montoLinea: 590000 },
  ],
  referencias: [],
  impuestos: [],
  sello: "3a7bd3e2360a3d5f1f2e4b8c9d0e1a2b3c4d5e6f70819a2b3c4d5e6f7a8b9c0d1",
  intentosEnvio: 1,
  ultimoEnvioEn: "2026-06-23T10:25:00Z",
  ultimoErrorEnvio: null,
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

/** Compras registradas de demostración para el período pedido. */
export function comprasMock(periodo: string): Compra[] {
  return [
    { id: 1, tipoDte: 33, folio: 4581, rutProveedor: "96511460-1", razonSocial: "Distribuidora Central SpA", fechaEmision: `${periodo}-05`, neto: 380000, exento: 0, iva: 72200, ivaRetenido: 0, total: 452200, observacion: null, creadoEn: `${periodo}-05T09:00:00Z` },
    { id: 2, tipoDte: 33, folio: 890, rutProveedor: "77888999-0", razonSocial: "Papelería Insumos Ltda", fechaEmision: `${periodo}-12`, neto: 45000, exento: 0, iva: 8550, ivaRetenido: 0, total: 53550, observacion: null, creadoEn: `${periodo}-12T15:30:00Z` },
    { id: 3, tipoDte: 34, folio: 152, rutProveedor: "65432100-9", razonSocial: "Capacitación Norte EIRL", fechaEmision: `${periodo}-20`, neto: 0, exento: 150000, iva: 0, ivaRetenido: 0, total: 150000, observacion: null, creadoEn: `${periodo}-20T11:10:00Z` },
  ];
}

/** Libro IECV de demostración para el período y tipo de operación pedidos. */
export function libroMock(tipo: TipoOperacionLibro, periodo: string): LibroResponse {
  if (tipo === "COMPRA") {
    // Derivado de comprasMock (misma agregación que el backend) para que la
    // página de Compras y el libro nunca muestren cifras contradictorias.
    const compras = comprasMock(periodo);
    const porTipo = new Map<number, Compra[]>();
    for (const c of compras) {
      porTipo.set(c.tipoDte, [...(porTipo.get(c.tipoDte) ?? []), c]);
    }
    const suma = (cs: Compra[], f: (c: Compra) => number) => cs.reduce((acc, c) => acc + f(c), 0);
    const resumen = [...porTipo.entries()]
      .sort(([a], [b]) => a - b)
      .map(([tipoDocumento, cs]) => ({
        tipoDocumento, documentos: cs.length, anulados: 0,
        neto: suma(cs, (c) => c.neto), exento: suma(cs, (c) => c.exento), iva: suma(cs, (c) => c.iva),
        otrosImpuestos: 0, ivaRetenido: suma(cs, (c) => c.ivaRetenido), total: suma(cs, (c) => c.total),
      }));
    return {
      periodo,
      tipoOperacion: "COMPRA",
      resumen,
      detalle: compras.map((c) => ({
        tipoDocumento: c.tipoDte, folio: c.folio, fecha: c.fechaEmision,
        rutContraparte: c.rutProveedor, razonSocial: c.razonSocial,
        neto: c.neto, exento: c.exento, iva: c.iva, otrosImpuestos: 0, ivaRetenido: c.ivaRetenido,
        total: c.total, anulado: false,
      })),
      totales: {
        documentos: compras.length, anulados: 0,
        neto: suma(compras, (c) => c.neto), exento: suma(compras, (c) => c.exento),
        iva: suma(compras, (c) => c.iva), otrosImpuestos: 0,
        ivaRetenido: suma(compras, (c) => c.ivaRetenido), total: suma(compras, (c) => c.total),
      },
      sinMovimiento: false,
    };
  }
  return {
    periodo,
    tipoOperacion: "VENTA",
    resumen: [
      { tipoDocumento: 33, documentos: 6, anulados: 1, neto: 4934200, exento: 0, iva: 937498, otrosImpuestos: 0, ivaRetenido: 0, total: 5871698 },
      { tipoDocumento: 39, documentos: 16, anulados: 1, neto: 546218, exento: 84000, iva: 103782, otrosImpuestos: 0, ivaRetenido: 0, total: 734000 },
      { tipoDocumento: 61, documentos: 1, anulados: 0, neto: 100000, exento: 0, iva: 19000, otrosImpuestos: 0, ivaRetenido: 0, total: 119000 },
    ],
    detalle: [
      { tipoDocumento: 33, folio: 142, fecha: `${periodo}-23`, rutContraparte: "78222333-4", razonSocial: "Constructora Andes SpA", neto: 1400000, exento: 0, iva: 266000, otrosImpuestos: 0, ivaRetenido: 0, total: 1666000, anulado: false },
      { tipoDocumento: 33, folio: 141, fecha: `${periodo}-21`, rutContraparte: "77111222-3", razonSocial: "Comercial Las Palmas Ltda", neto: 450000, exento: 0, iva: 85500, otrosImpuestos: 0, ivaRetenido: 0, total: 535500, anulado: false },
      { tipoDocumento: 33, folio: 138, fecha: `${periodo}-16`, rutContraparte: "77111222-3", razonSocial: "Comercial Las Palmas Ltda", neto: 0, exento: 0, iva: 0, otrosImpuestos: 0, ivaRetenido: 0, total: 0, anulado: true },
      { tipoDocumento: 61, folio: 18, fecha: `${periodo}-18`, rutContraparte: "78222333-4", razonSocial: "Constructora Andes SpA", neto: 100000, exento: 0, iva: 19000, otrosImpuestos: 0, ivaRetenido: 0, total: 119000, anulado: false },
    ],
    totales: { documentos: 23, anulados: 2, neto: 5580418, exento: 84000, iva: 1060280, otrosImpuestos: 0, ivaRetenido: 0, total: 6724698 },
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
