// Cliente HTTP de la API. Con USE_MOCK = true la app funciona de forma autónoma
// con datos de demostración (ideal para revisar la interfaz sin levantar el
// backend). Cámbielo a false para consumir la API real en /api.

import axios from "axios";
import { obtenerToken } from "./auth";
import {
  clientesMock, dashboardMock, documentoDetalleMock, documentosMock, productosMock,
} from "./mock";
import type {
  Cliente, DocumentoResponse, DocumentoResumen, Producto, ResumenDashboard,
} from "./types";

export const USE_MOCK = true;

const http = axios.create({ baseURL: "/api" });

http.interceptors.request.use((config) => {
  const token = obtenerToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

const demora = (ms = 250) => new Promise((r) => setTimeout(r, ms));

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
  afecto: boolean;
}

export async function crearDocumento(
  empresaId: number,
  payload: { tipoDte: string; clienteId: number; observacion?: string; lineas: NuevaLinea[] },
): Promise<DocumentoResponse> {
  if (USE_MOCK) {
    await demora(400);
    return documentoDetalleMock;
  }
  const { data } = await http.post(`/empresas/${empresaId}/documentos`, payload);
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

export async function getProductos(empresaId: number): Promise<Producto[]> {
  if (USE_MOCK) {
    await demora();
    return productosMock;
  }
  const { data } = await http.get(`/empresas/${empresaId}/productos`);
  return data.contenido;
}

export default http;
