import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Plus, Trash2, Receipt } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import { Card, Field, Select, Input, Button, Spinner, Textarea } from "../../components/ui";
import {
  crearDocumento, getClientes, getDocumento, getDocumentos, getProductos, mensajeError,
  type NuevaLinea,
} from "../../lib/api";
import { empresaIdActual } from "../../lib/auth";
import { formatCLP, formatFecha, formatRut } from "../../lib/format";
import {
  CODIGO_TIPO_DTE, TIPO_DTE_LABEL, TIPO_REFERENCIA_LABEL,
  type Cliente, type DocumentoResumen, type Producto, type ReferenciaRequest,
  type TipoDte, type TipoReferencia,
} from "../../lib/types";

interface LineaEditable extends NuevaLinea {
  uid: number;
}

const TASA_IVA = 19;

// Tipos emisibles desde este formulario.
const TIPOS_EMISIBLES: TipoDte[] = [
  "FACTURA_AFECTA", "FACTURA_EXENTA", "NOTA_CREDITO", "NOTA_DEBITO",
];

const TIPOS_REFERENCIA: TipoReferencia[] = ["ANULA_DOCUMENTO", "CORRIGE_TEXTO", "CORRIGE_MONTO"];

function esNota(tipo: TipoDte) {
  return tipo === "NOTA_CREDITO" || tipo === "NOTA_DEBITO";
}

export function NuevaFactura() {
  const navigate = useNavigate();
  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [productos, setProductos] = useState<Producto[]>([]);
  const [emisibles, setEmisibles] = useState<DocumentoResumen[]>([]);
  const [tipoDte, setTipoDte] = useState<TipoDte>("FACTURA_AFECTA");
  const [clienteId, setClienteId] = useState<number | "">("");
  const [lineas, setLineas] = useState<LineaEditable[]>([]);
  const [emitiendo, setEmitiendo] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);

  // Referencia (solo notas de crédito/débito).
  const [refDocId, setRefDocId] = useState<number | "">("");
  const [folioRef, setFolioRef] = useState<number | null>(null);
  const [fechaRef, setFechaRef] = useState<string>("");
  const [tipoDocumentoRef, setTipoDocumentoRef] = useState<number | null>(null);
  const [tipoReferencia, setTipoReferencia] = useState<TipoReferencia>("ANULA_DOCUMENTO");
  const [razon, setRazon] = useState("");

  const afectoPorDefecto = tipoDte !== "FACTURA_EXENTA";

  useEffect(() => {
    const empresaId = empresaIdActual();
    Promise.all([getClientes(empresaId), getProductos(empresaId), getDocumentos(empresaId)])
      .then(([cs, ps, ds]) => {
        setClientes(cs);
        setProductos(ps);
        setEmisibles(ds.filter((d) => d.folio != null));
      })
      .finally(() => setCargando(false));
  }, []);

  const cliente = clientes.find((c) => c.id === clienteId);

  function cambiarTipo(nuevo: TipoDte) {
    setTipoDte(nuevo);
    setError(null);
    // Valor por defecto del tipo de referencia según la nota.
    setTipoReferencia(nuevo === "NOTA_DEBITO" ? "CORRIGE_MONTO" : "ANULA_DOCUMENTO");
    // Las líneas exentas no llevan IVA.
    if (nuevo === "FACTURA_EXENTA") {
      setLineas((prev) => prev.map((l) => ({ ...l, afecto: false })));
    }
    // Si dejamos de ser nota, limpiamos la referencia.
    if (!esNota(nuevo)) {
      setRefDocId("");
      setFolioRef(null);
      setFechaRef("");
      setTipoDocumentoRef(null);
      setRazon("");
    }
  }

  async function elegirDocumentoReferenciado(docId: number) {
    setRefDocId(docId);
    const resumen = emisibles.find((d) => d.id === docId);
    if (!resumen) return;
    setFolioRef(resumen.folio);
    setFechaRef(resumen.fechaEmision);
    setTipoDocumentoRef(CODIGO_TIPO_DTE[resumen.tipoDte]);
    // Autocompletar el cliente del documento original (vía su RUT receptor).
    try {
      const original = await getDocumento(empresaIdActual(), docId);
      const match = clientes.find((c) => c.rut === original.receptorRut);
      if (match) setClienteId(match.id);
    } catch {
      // Sin bloquear: el usuario puede elegir el cliente manualmente.
    }
  }

  function agregarLinea() {
    setLineas((prev) => [
      ...prev,
      { uid: Date.now(), nombre: "", cantidad: 1, precioUnitario: 0, descuentoMonto: 0, afecto: afectoPorDefecto },
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
          ? { ...l, productoId: p.id, nombre: p.nombre, precioUnitario: p.precioNeto, afecto: afectoPorDefecto && p.afecto }
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

  const requiereReferencia = esNota(tipoDte);
  const referenciaCompleta = !requiereReferencia
    || (folioRef != null && tipoDocumentoRef != null && fechaRef !== "" && razon.trim() !== "");

  const puedeEmitir = clienteId !== "" && lineas.length > 0 && totales.total > 0 && referenciaCompleta;

  async function emitir() {
    setError(null);
    if (clienteId === "" || lineas.length === 0 || totales.total <= 0) return;

    let referencias: ReferenciaRequest[] | undefined;
    if (requiereReferencia) {
      if (folioRef == null || tipoDocumentoRef == null || !fechaRef || !razon.trim()) {
        setError("Completa la referencia al documento original (folio, fecha y motivo).");
        return;
      }
      referencias = [{
        tipoDocumentoRef,
        folioRef,
        fechaRef,
        tipoReferencia,
        razon: razon.trim(),
      }];
    }

    setEmitiendo(true);
    try {
      const creado = await crearDocumento(empresaIdActual(), {
        tipoDte,
        clienteId: clienteId as number,
        lineas: lineas.map(({ uid: _uid, ...l }) => l),
        ...(referencias ? { referencias } : {}),
      });
      navigate(`/app/documentos/${creado.id}`);
    } catch (e) {
      setError(mensajeError(e, "No se pudo crear el documento."));
    } finally {
      setEmitiendo(false);
    }
  }

  if (cargando) {
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
            <h2 className="mb-4 font-display text-base font-bold text-ink">Documento</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Tipo de documento">
                <Select value={tipoDte} onChange={(e) => cambiarTipo(e.target.value as TipoDte)}>
                  {TIPOS_EMISIBLES.map((t) => (
                    <option key={t} value={t}>{TIPO_DTE_LABEL[t]}</option>
                  ))}
                </Select>
              </Field>
              <Field label="Cliente">
                <Select value={clienteId} onChange={(e) => setClienteId(Number(e.target.value) || "")}>
                  <option value="">Selecciona un cliente…</option>
                  {clientes.map((c) => (
                    <option key={c.id} value={c.id}>{c.razonSocial} · {formatRut(c.rut)}</option>
                  ))}
                </Select>
              </Field>
            </div>
            {cliente && (
              <p className="mt-3 text-xs text-slate">
                {formatRut(cliente.rut)} · {cliente.comuna ?? "—"} · {cliente.email ?? "sin correo"}
              </p>
            )}
          </Card>

          {requiereReferencia && (
            <Card className="p-6">
              <h2 className="mb-4 font-display text-base font-bold text-ink">
                Referencia al documento original
              </h2>
              <div className="space-y-4">
                <Field label="Documento de referencia" hint="Solo documentos con folio asignado.">
                  <Select
                    value={refDocId}
                    onChange={(e) => {
                      const v = Number(e.target.value);
                      if (v) elegirDocumentoReferenciado(v);
                      else { setRefDocId(""); setFolioRef(null); setFechaRef(""); setTipoDocumentoRef(null); }
                    }}
                  >
                    <option value="">Selecciona el documento original…</option>
                    {emisibles.map((d) => (
                      <option key={d.id} value={d.id}>
                        {TIPO_DTE_LABEL[d.tipoDte]} N° {d.folio} · {d.receptorRazonSocial} · {formatCLP(d.total)}
                      </option>
                    ))}
                  </Select>
                </Field>

                {folioRef != null && (
                  <p className="text-xs text-slate tnum">
                    Referencia: documento {tipoDocumentoRef} · folio {folioRef} · {formatFecha(fechaRef)}
                  </p>
                )}

                <Field label="Tipo de referencia">
                  <Select
                    value={tipoReferencia}
                    onChange={(e) => setTipoReferencia(e.target.value as TipoReferencia)}
                  >
                    {TIPOS_REFERENCIA.map((t) => (
                      <option key={t} value={t}>{TIPO_REFERENCIA_LABEL[t]}</option>
                    ))}
                  </Select>
                </Field>

                <Field label="Motivo (razón)">
                  <Textarea
                    rows={2}
                    value={razon}
                    placeholder="Ej: Anula factura por error en el monto."
                    onChange={(e) => setRazon(e.target.value)}
                  />
                </Field>
              </div>
            </Card>
          )}

          <Card className="p-6">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="font-display text-base font-bold text-ink">Detalle</h2>
              <Button variant="secondary" size="sm" onClick={agregarLinea}>
                <Plus size={15} /> Agregar línea
              </Button>
            </div>

            {lineas.length === 0 ? (
              <p className="rounded-lg border border-dashed border-line py-8 text-center text-sm text-slate-soft">
                Agrega productos o servicios al documento.
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
            <p className="mt-1 text-xs text-slate-soft">{TIPO_DTE_LABEL[tipoDte]}</p>
            <div className="mt-5 space-y-2.5 text-sm">
              <Linea label="Neto" valor={formatCLP(totales.neto)} />
              {totales.exento > 0 && <Linea label="Exento" valor={formatCLP(totales.exento)} />}
              <Linea label={`IVA ${TASA_IVA}%`} valor={formatCLP(totales.iva)} />
              <div className="flex items-center justify-between border-t border-line pt-3">
                <span className="font-semibold text-ink">Total</span>
                <span className="font-display text-xl font-bold text-cobalt tnum">{formatCLP(totales.total)}</span>
              </div>
            </div>

            {error && (
              <div className="mt-4 rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger">{error}</div>
            )}

            <Button className="mt-6 w-full" onClick={emitir} disabled={!puedeEmitir || emitiendo}>
              {emitiendo ? "Creando…" : <><Receipt size={17} /> Crear documento</>}
            </Button>
            <p className="mt-3 text-center text-xs text-slate-soft">
              Se crea en borrador; podrás emitirlo y enviarlo al SII desde el detalle.
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
