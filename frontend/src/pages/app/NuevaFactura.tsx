import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Plus, Trash2, Receipt } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import { Card, Field, Select, Input, Button, Spinner } from "../../components/ui";
import { crearDocumento, getClientes, getProductos, type NuevaLinea } from "../../lib/api";
import { empresaIdActual } from "../../lib/auth";
import { formatCLP, formatRut } from "../../lib/format";
import type { Cliente, Producto } from "../../lib/types";

interface LineaEditable extends NuevaLinea {
  uid: number;
}

const TASA_IVA = 19;

export function NuevaFactura() {
  const navigate = useNavigate();
  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [productos, setProductos] = useState<Producto[]>([]);
  const [clienteId, setClienteId] = useState<number | "">("");
  const [lineas, setLineas] = useState<LineaEditable[]>([]);
  const [emitiendo, setEmitiendo] = useState(false);

  useEffect(() => {
    const empresaId = empresaIdActual();
    getClientes(empresaId).then(setClientes);
    getProductos(empresaId).then(setProductos);
  }, []);

  const cliente = clientes.find((c) => c.id === clienteId);

  function agregarLinea() {
    setLineas((prev) => [
      ...prev,
      { uid: Date.now(), nombre: "", cantidad: 1, precioUnitario: 0, descuentoMonto: 0, afecto: true },
    ]);
  }

  function quitarLinea(uid: number) {
    setLineas((prev) => prev.filter((l) => l.uid !== uid));
  }

  function elegirProducto(uid: number, productoId: number) {
    const p = productos.find((x) => x.id === productoId);
    if (!p) return;
    setLineas((prev) =>
      prev.map((l) =>
        l.uid === uid
          ? { ...l, productoId: p.id, nombre: p.nombre, precioUnitario: p.precioNeto, afecto: p.afecto }
          : l,
      ),
    );
  }

  function actualizar(uid: number, campo: keyof LineaEditable, valor: number) {
    setLineas((prev) => prev.map((l) => (l.uid === uid ? { ...l, [campo]: valor } : l)));
  }

  // Cálculo en vivo (espejo de CalculadoraImpuestos del backend)
  const totales = useMemo(() => {
    let neto = 0;
    let exento = 0;
    for (const l of lineas) {
      const monto = Math.max(0, Math.round(l.cantidad * l.precioUnitario) - (l.descuentoMonto || 0));
      if (l.afecto) neto += monto;
      else exento += monto;
    }
    const iva = Math.round((neto * TASA_IVA) / 100);
    return { neto, exento, iva, total: neto + iva + exento };
  }, [lineas]);

  const puedeEmitir = clienteId !== "" && lineas.length > 0 && totales.total > 0;

  async function emitir() {
    if (!puedeEmitir) return;
    setEmitiendo(true);
    try {
      await crearDocumento(empresaIdActual(), {
        tipoDte: "FACTURA_AFECTA",
        clienteId: clienteId as number,
        lineas: lineas.map(({ uid: _uid, ...l }) => l),
      });
      navigate("/app/documentos");
    } finally {
      setEmitiendo(false);
    }
  }

  if (clientes.length === 0) {
    return (
      <AppShell titulo="Nueva factura">
        <div className="grid h-64 place-items-center"><Spinner className="h-6 w-6" /></div>
      </AppShell>
    );
  }

  return (
    <AppShell titulo="Nueva factura">
      <div className="grid gap-6 lg:grid-cols-[1.7fr_1fr]">
        {/* Formulario */}
        <div className="space-y-5">
          <Card className="p-6">
            <h2 className="mb-4 font-display text-base font-bold text-ink">Receptor</h2>
            <Field label="Cliente">
              <Select value={clienteId} onChange={(e) => setClienteId(Number(e.target.value) || "")}>
                <option value="">Selecciona un cliente…</option>
                {clientes.map((c) => (
                  <option key={c.id} value={c.id}>{c.razonSocial} · {formatRut(c.rut)}</option>
                ))}
              </Select>
            </Field>
            {cliente && (
              <p className="mt-3 text-xs text-slate">
                {formatRut(cliente.rut)} · {cliente.comuna ?? "—"} · {cliente.email ?? "sin correo"}
              </p>
            )}
          </Card>

          <Card className="p-6">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="font-display text-base font-bold text-ink">Detalle</h2>
              <Button variant="secondary" size="sm" onClick={agregarLinea}>
                <Plus size={15} /> Agregar línea
              </Button>
            </div>

            {lineas.length === 0 ? (
              <p className="rounded-lg border border-dashed border-line py-8 text-center text-sm text-slate-soft">
                Agrega productos o servicios a la factura.
              </p>
            ) : (
              <div className="space-y-3">
                {lineas.map((l) => {
                  const monto = Math.max(0, Math.round(l.cantidad * l.precioUnitario) - (l.descuentoMonto || 0));
                  return (
                    <div key={l.uid} className="rounded-lg border border-line p-3">
                      <div className="grid grid-cols-[1fr_auto] gap-2">
                        <Select
                          value={l.productoId ?? ""}
                          onChange={(e) => elegirProducto(l.uid, Number(e.target.value))}
                          className="h-10"
                        >
                          <option value="">Producto o servicio…</option>
                          {productos.map((p) => (
                            <option key={p.id} value={p.id}>{p.nombre}</option>
                          ))}
                        </Select>
                        <button
                          onClick={() => quitarLinea(l.uid)}
                          className="grid h-10 w-10 place-items-center rounded-lg border border-line text-slate-soft hover:border-danger hover:text-danger"
                          aria-label="Quitar línea"
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>
                      <div className="mt-2 grid grid-cols-3 gap-2">
                        <label className="text-xs text-slate-soft">
                          Cantidad
                          <Input
                            type="number" min={0} step="any" value={l.cantidad} className="mt-1 h-9"
                            onChange={(e) => actualizar(l.uid, "cantidad", Number(e.target.value))}
                          />
                        </label>
                        <label className="text-xs text-slate-soft">
                          Precio neto
                          <Input
                            type="number" min={0} value={l.precioUnitario} className="mt-1 h-9 tnum"
                            onChange={(e) => actualizar(l.uid, "precioUnitario", Number(e.target.value))}
                          />
                        </label>
                        <div className="text-xs text-slate-soft">
                          Importe
                          <div className="mt-1 flex h-9 items-center justify-end rounded-lg bg-mist px-3 font-medium text-ink tnum">
                            {formatCLP(monto)}
                          </div>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </Card>
        </div>

        {/* Resumen */}
        <div>
          <Card className="sticky top-24 p-6">
            <h2 className="font-display text-base font-bold text-ink">Resumen</h2>
            <div className="mt-5 space-y-2.5 text-sm">
              <Linea label="Neto" valor={formatCLP(totales.neto)} />
              {totales.exento > 0 && <Linea label="Exento" valor={formatCLP(totales.exento)} />}
              <Linea label={`IVA ${TASA_IVA}%`} valor={formatCLP(totales.iva)} />
              <div className="flex items-center justify-between border-t border-line pt-3">
                <span className="font-semibold text-ink">Total</span>
                <span className="font-display text-xl font-bold text-cobalt tnum">{formatCLP(totales.total)}</span>
              </div>
            </div>

            <Button className="mt-6 w-full" onClick={emitir} disabled={!puedeEmitir || emitiendo}>
              {emitiendo ? "Emitiendo…" : <><Receipt size={17} /> Emitir factura</>}
            </Button>
            <p className="mt-3 text-center text-xs text-slate-soft">
              Se asignará folio, timbre y se enviará al SII.
            </p>
          </Card>
        </div>
      </div>
    </AppShell>
  );
}

function Linea({ label, valor }: { label: string; valor: string }) {
  return (
    <div className="flex items-center justify-between text-slate">
      <span>{label}</span>
      <span className="text-ink tnum">{valor}</span>
    </div>
  );
}
