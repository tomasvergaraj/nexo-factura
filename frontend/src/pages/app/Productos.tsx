import { useEffect, useMemo, useState } from "react";
import { Plus, Search, Package, Pencil } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import {
  Card, Input, Button, Field, Modal, Checkbox, Badge, EmptyState,
  PageHeader, LoadingState, Alert, Th, IconButton,
} from "../../components/ui";
import {
  crearProducto, editarProducto, erroresDeCampo, getProductos, mensajeError,
} from "../../lib/api";
import { empresaIdActual } from "../../lib/auth";
import { formatCLP } from "../../lib/format";
import type { Producto, ProductoRequest } from "../../lib/types";

interface FormProducto {
  codigo: string;
  nombre: string;
  precioNeto: string;
  unidad: string;
  afecto: boolean;
}

const VACIO: FormProducto = { codigo: "", nombre: "", precioNeto: "", unidad: "UN", afecto: true };

export function Productos() {
  const [productos, setProductos] = useState<Producto[] | null>(null);
  const [busqueda, setBusqueda] = useState("");
  const [abierto, setAbierto] = useState(false);
  const [editando, setEditando] = useState<Producto | null>(null);
  const [form, setForm] = useState<FormProducto>(VACIO);
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  function cargar() {
    getProductos(empresaIdActual()).then(setProductos);
  }
  useEffect(cargar, []);

  const visibles = useMemo(() => {
    if (!productos) return [];
    const q = busqueda.trim().toLowerCase();
    if (!q) return productos;
    return productos.filter(
      (p) => p.nombre.toLowerCase().includes(q) || (p.codigo ?? "").toLowerCase().includes(q),
    );
  }, [productos, busqueda]);

  function abrirNuevo() {
    setEditando(null);
    setForm(VACIO);
    setErrores({});
    setErrorGeneral(null);
    setAbierto(true);
  }

  function abrirEdicion(p: Producto) {
    setEditando(p);
    setForm({
      codigo: p.codigo ?? "",
      nombre: p.nombre,
      precioNeto: String(p.precioNeto),
      unidad: p.unidad ?? "UN",
      afecto: p.afecto,
    });
    setErrores({});
    setErrorGeneral(null);
    setAbierto(true);
  }

  async function guardar() {
    setErrores({});
    setErrorGeneral(null);

    if (!form.nombre.trim()) {
      setErrores({ nombre: "El nombre es obligatorio." });
      return;
    }

    const payload: ProductoRequest = {
      codigo: form.codigo.trim() || undefined,
      nombre: form.nombre.trim(),
      precioNeto: Math.round(Number(form.precioNeto) || 0),
      unidad: form.unidad.trim() || undefined,
      afecto: form.afecto,
    };

    setGuardando(true);
    try {
      const empresaId = empresaIdActual();
      if (editando) {
        await editarProducto(empresaId, editando.id, payload);
      } else {
        await crearProducto(empresaId, payload);
      }
      setAbierto(false);
      cargar();
    } catch (error) {
      const campos = erroresDeCampo(error);
      if (Object.keys(campos).length > 0) {
        setErrores(campos);
      } else {
        setErrorGeneral(mensajeError(error, "No se pudo guardar el producto."));
      }
    } finally {
      setGuardando(false);
    }
  }

  return (
    <AppShell titulo="Productos">
      <div className="space-y-6">
        <PageHeader
          titulo="Productos"
          descripcion="Catálogo de productos y servicios para agilizar la emisión."
          accion={<Button onClick={abrirNuevo}><Plus className="h-4 w-4" /> Nuevo producto</Button>}
        />

        <div className="relative w-full sm:max-w-sm">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-soft" />
          <Input
            className="pl-9"
            placeholder="Buscar por nombre o código…"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
          />
        </div>

        <Card className="overflow-hidden">
          {!productos ? (
            <LoadingState mensaje="Cargando productos…" />
          ) : visibles.length === 0 ? (
            <EmptyState
              icon={<Package className="h-6 w-6" />}
              titulo={busqueda ? "Sin coincidencias" : "Aún no hay productos"}
              descripcion={busqueda
                ? "Ningún producto coincide con la búsqueda."
                : "Crea productos o servicios para agilizar la emisión."}
              accion={!busqueda && <Button onClick={abrirNuevo}><Plus className="h-4 w-4" /> Nuevo producto</Button>}
            />
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-line">
                  <Th>Código</Th>
                  <Th>Nombre</Th>
                  <Th>Unidad</Th>
                  <Th>Afecto</Th>
                  <Th align="right">Precio neto</Th>
                  <Th><span className="sr-only">Acciones</span></Th>
                </tr>
              </thead>
              <tbody>
                {visibles.map((p) => (
                  <tr key={p.id} className="border-b border-line last:border-0 transition-colors hover:bg-mist/60">
                    <td className="px-4 py-3.5 text-slate tnum">{p.codigo ?? "—"}</td>
                    <td className="px-4 py-3.5 font-medium text-ink">{p.nombre}</td>
                    <td className="px-4 py-3.5 text-slate">{p.unidad}</td>
                    <td className="px-4 py-3.5">
                      {p.afecto ? <Badge tone="cobalt">Afecto</Badge> : <Badge>Exento</Badge>}
                    </td>
                    <td className="px-4 py-3.5 text-right font-medium text-ink tnum">{formatCLP(p.precioNeto)}</td>
                    <td className="px-4 py-3.5 text-right">
                      <IconButton
                        onClick={() => abrirEdicion(p)}
                        className="hover:bg-mist hover:text-cobalt"
                        aria-label={`Editar ${p.nombre}`}
                      >
                        <Pencil className="h-4 w-4" />
                      </IconButton>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      </div>

      <Modal
        open={abierto}
        onClose={() => setAbierto(false)}
        title={editando ? "Editar producto" : "Nuevo producto"}
        footer={
          <>
            <Button variant="secondary" onClick={() => setAbierto(false)} disabled={guardando}>Cancelar</Button>
            <Button onClick={guardar} disabled={guardando}>
              {guardando ? "Guardando…" : "Guardar"}
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {errorGeneral && <Alert>{errorGeneral}</Alert>}
          <Field label="Nombre" error={errores.nombre}>
            <Input value={form.nombre} onChange={(e) => setForm((f) => ({ ...f, nombre: e.target.value }))} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Código" error={errores.codigo}>
              <Input value={form.codigo} onChange={(e) => setForm((f) => ({ ...f, codigo: e.target.value }))} />
            </Field>
            <Field label="Unidad" error={errores.unidad}>
              <Input value={form.unidad} onChange={(e) => setForm((f) => ({ ...f, unidad: e.target.value }))} />
            </Field>
          </div>
          <Field label="Precio neto" hint="Valor entero, sin decimales." error={errores.precioNeto}>
            <Input
              type="number"
              min={0}
              className="tnum"
              value={form.precioNeto}
              onChange={(e) => setForm((f) => ({ ...f, precioNeto: e.target.value }))}
            />
          </Field>
          <Checkbox
            label="Producto afecto a IVA"
            checked={form.afecto}
            onChange={(e) => setForm((f) => ({ ...f, afecto: e.target.checked }))}
          />
        </div>
      </Modal>
    </AppShell>
  );
}
