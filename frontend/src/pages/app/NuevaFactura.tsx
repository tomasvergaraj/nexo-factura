import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Plus, Trash2, Receipt } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import {
  Card, Field, Select, Input, Button, Spinner, Textarea,
  PageHeader, LoadingState, Alert, IconButton, Badge,
} from "../../components/ui";
import {
  crearDocumento, getClientes, getDocumento, getDocumentos, getProductos, mensajeError,
  type NuevaLinea,
} from "../../lib/api";
import { empresaIdActual } from "../../lib/auth";
import { formatCLP, formatFecha, formatRut } from "../../lib/format";
import {
  CATALOGO_IMPUESTOS, CODIGO_TIPO_DTE, ES_BOLETA, IMPUESTO_POR_CODIGO, PERMITE_IMPUESTOS,
  RAZON_CONSUMIDOR_FINAL, RUT_CONSUMIDOR_FINAL,
  TIPO_DTE_LABEL, TIPO_REFERENCIA_LABEL,
  type Cliente, type DocumentoResumen, type Producto, type ReferenciaRequest,
  type TipoDte, type TipoReferencia,
} from "../../lib/types";

interface LineaEditable extends NuevaLinea {
  uid: number;
}

const TASA_IVA = 19;

// Tipos emisibles desde este formulario.
const TIPOS_EMISIBLES: TipoDte[] = [
  "FACTURA_AFECTA", "FACTURA_EXENTA", "BOLETA_AFECTA", "BOLETA_EXENTA", "NOTA_CREDITO", "NOTA_DEBITO",
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

  const esBoleta = ES_BOLETA[tipoDte];
  const afectoPorDefecto = tipoDte !== "FACTURA_EXENTA" && tipoDte !== "BOLETA_EXENTA";
  const labelPrecio = esBoleta ? "Precio (IVA incl.)" : "Precio neto";
  // Otros impuestos (P1-6): solo en facturas/notas afectas (33/56/61).
  const permiteImpuestos = PERMITE_IMPUESTOS[tipoDte];

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
    if (nuevo === "FACTURA_EXENTA" || nuevo === "BOLETA_EXENTA") {
      setLineas((prev) => prev.map((l) => ({ ...l, afecto: false })));
    }
    // Si el nuevo tipo no admite otros impuestos (boletas/exentas), limpiamos el código.
    if (!PERMITE_IMPUESTOS[nuevo]) {
      setLineas((prev) => prev.map((l) => ({ ...l, codImpAdic: undefined })));
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
    const afecto = afectoPorDefecto && p.afecto;
    setLineas((prev) =>
      prev.map((l) =>
        l.uid === uid
          // Una línea que deja de ser afecta no puede llevar otro impuesto.
          ? { ...l, productoId: p.id, nombre: p.nombre, precioUnitario: p.precioNeto, afecto, codImpAdic: afecto ? l.codImpAdic : undefined }
          : l,
      ),
    );
  }

  function actualizar(uid: number, campo: keyof LineaEditable, valor: number) {
    setLineas((prev) => prev.map((l) => (l.uid === uid ? { ...l, [campo]: valor } : l)));
  }

  function cambiarImpuesto(uid: number, codigo: number | undefined) {
    setLineas((prev) => prev.map((l) => (l.uid === uid ? { ...l, codImpAdic: codigo } : l)));
  }

  // Cálculo en vivo (espejo exacto de CalculadoraImpuestos del backend). En boletas
  // los precios son brutos (IVA incluido): el neto se desglosa una sola vez del
  // subtotal afecto y el IVA es la diferencia, igual que en el backend.
  const totales = useMemo(() => {
    let afecto = 0;
    let exento = 0;
    // Base agregada por código de otro impuesto (solo líneas afectas).
    const basePorCodigo = new Map<number, number>();
    for (const l of lineas) {
      const monto = Math.max(0, Math.round(l.cantidad * l.precioUnitario) - (l.descuentoMonto || 0));
      if (l.afecto) {
        afecto += monto;
        if (permiteImpuestos && l.codImpAdic != null) {
          basePorCodigo.set(l.codImpAdic, (basePorCodigo.get(l.codImpAdic) ?? 0) + monto);
        }
      } else {
        exento += monto;
      }
    }
    let neto: number;
    let iva: number;
    if (esBoleta) {
      neto = Math.round(afecto / (1 + TASA_IVA / 100));
      iva = afecto - neto;
    } else {
      neto = afecto;
      // Mismo orden de operaciones que el backend (neto * (tasa/100)) para que el
      // redondeo sea idéntico bit a bit (IEEE-754) y el preview no se desvíe.
      iva = Math.round(neto * (TASA_IVA / 100));
    }
    // Otros impuestos: redondeo por código (una vez sobre la base agregada),
    // ordenado por código (espejo del TreeMap del backend).
    const desglose = [...basePorCodigo.entries()]
      .sort((a, b) => a[0] - b[0])
      .map(([codigo, base]) => {
        const imp = IMPUESTO_POR_CODIGO[codigo];
        // base * (tasa/100): mismo orden que CalculadoraImpuestos para redondear igual
        // que el backend (p.ej. tasa 20,5% no descalza el total del preview en $1).
        return { codigo, nombre: imp.nombre, tasa: imp.tasa, esRetencion: imp.esRetencion, monto: Math.round(base * (imp.tasa / 100)) };
      });
    let adicionales = 0;
    let retenido = 0;
    for (const d of desglose) {
      if (d.esRetencion) retenido += d.monto;
      else adicionales += d.monto;
    }
    return { neto, exento, iva, adicionales, retenido, desglose, total: neto + iva + exento + adicionales - retenido };
  }, [lineas, esBoleta, permiteImpuestos]);

  const requiereReferencia = esNota(tipoDte);
  const referenciaCompleta = !requiereReferencia
    || (folioRef != null && tipoDocumentoRef != null && fechaRef !== "" && razon.trim() !== "");

  // En boletas el cliente es opcional (se emite a Consumidor final).
  const clienteOk = esBoleta || clienteId !== "";
  const puedeEmitir = clienteOk && lineas.length > 0 && totales.total > 0 && referenciaCompleta;

  async function emitir() {
    setError(null);
    if ((!esBoleta && clienteId === "") || lineas.length === 0 || totales.total <= 0) return;

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
        clienteId: clienteId === "" ? null : (clienteId as number),
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
        <LoadingState mensaje="Cargando datos…" />
      </AppShell>
    );
  }

  return (
    <AppShell titulo="Nueva factura">
      <div className="space-y-6">
        <PageHeader
          titulo="Nueva factura"
          descripcion="Completa el documento y revisa el resumen antes de crearlo."
        />

        <div className="grid gap-6 lg:grid-cols-[1.7fr_1fr]">
          {/* Formulario */}
          <div className="space-y-6">
            <Card className="p-6">
              <h2 className="mb-5 font-display text-base font-semibold text-ink">Documento</h2>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Tipo de documento">
                  <Select value={tipoDte} onChange={(e) => cambiarTipo(e.target.value as TipoDte)}>
                    {TIPOS_EMISIBLES.map((t) => (
                      <option key={t} value={t}>{TIPO_DTE_LABEL[t]}</option>
                    ))}
                  </Select>
                </Field>
                <Field
                  label="Cliente"
                  hint={esBoleta ? "Opcional en boletas. Sin cliente se emite a Consumidor final." : undefined}
                >
                  <Select value={clienteId} onChange={(e) => setClienteId(Number(e.target.value) || "")}>
                    <option value="">
                      {esBoleta ? "Consumidor final (sin cliente)" : "Selecciona un cliente…"}
                    </option>
                    {clientes.map((c) => (
                      <option key={c.id} value={c.id}>{c.razonSocial} · {formatRut(c.rut)}</option>
                    ))}
                  </Select>
                </Field>
              </div>
              {cliente ? (
                <div className="mt-4 rounded-sm bg-mist px-3 py-2.5 text-xs text-slate">
                  <span className="tnum">{formatRut(cliente.rut)}</span> · {cliente.comuna ?? "—"} · {cliente.email ?? "sin correo"}
                </div>
              ) : esBoleta ? (
                <div className="mt-4 flex items-center gap-2 rounded-sm bg-mist px-3 py-2.5 text-xs text-slate">
                  <Badge tone="cobalt">{RAZON_CONSUMIDOR_FINAL}</Badge>
                  <span className="tnum">{formatRut(RUT_CONSUMIDOR_FINAL)}</span>
                </div>
              ) : null}
            </Card>

            {requiereReferencia && (
              <Card className="p-6">
                <h2 className="mb-5 font-display text-base font-semibold text-ink">
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
                    <div className="rounded-sm bg-mist px-3 py-2.5 text-xs text-slate tnum">
                      Referencia: documento {tipoDocumentoRef} · folio {folioRef} · {formatFecha(fechaRef)}
                    </div>
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
              <div className="mb-5 flex items-center justify-between">
                <h2 className="font-display text-base font-semibold text-ink">Detalle</h2>
                <Button variant="secondary" size="sm" onClick={agregarLinea}>
                  <Plus className="h-4 w-4" /> Agregar línea
                </Button>
              </div>

              {lineas.length === 0 ? (
                <div className="rounded-lg border border-dashed border-line px-6 py-10 text-center">
                  <p className="text-sm text-slate-soft">Agrega productos o servicios al documento.</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {lineas.map((l) => {
                    const monto = Math.max(0, Math.round(l.cantidad * l.precioUnitario) - (l.descuentoMonto || 0));
                    return (
                      <div key={l.uid} className="rounded-lg border border-line p-4">
                        <div className="grid grid-cols-[1fr_auto] gap-2">
                          <Select
                            value={l.productoId ?? ""}
                            onChange={(e) => elegirProducto(l.uid, Number(e.target.value))}
                          >
                            <option value="">Producto o servicio…</option>
                            {productos.map((p) => (
                              <option key={p.id} value={p.id}>{p.nombre}</option>
                            ))}
                          </Select>
                          <IconButton
                            onClick={() => quitarLinea(l.uid)}
                            className="h-10 w-10 border border-line shadow-xs hover:border-danger hover:bg-danger-soft hover:text-danger"
                            aria-label="Quitar línea"
                          >
                            <Trash2 className="h-4 w-4" />
                          </IconButton>
                        </div>
                        <div className="mt-3 grid grid-cols-3 gap-3">
                          <label className="block text-xs font-medium text-slate-soft">
                            Cantidad
                            <Input
                              type="number" min={0} step="any" value={l.cantidad} className="mt-1.5 h-9 tnum"
                              onChange={(e) => actualizar(l.uid, "cantidad", Number(e.target.value))}
                            />
                          </label>
                          <label className="block text-xs font-medium text-slate-soft">
                            {labelPrecio}
                            <Input
                              type="number" min={0} value={l.precioUnitario} className="mt-1.5 h-9 tnum"
                              onChange={(e) => actualizar(l.uid, "precioUnitario", Number(e.target.value))}
                            />
                          </label>
                          <div className="text-xs font-medium text-slate-soft">
                            Importe
                            <div className="mt-1.5 flex h-9 items-center justify-end rounded-sm bg-mist px-3 text-sm font-medium text-ink tnum">
                              {formatCLP(monto)}
                            </div>
                          </div>
                        </div>
                        {permiteImpuestos && l.afecto && (
                          <label className="mt-3 block text-xs font-medium text-slate-soft">
                            Otro impuesto (adicional / retención)
                            <Select
                              value={l.codImpAdic ?? ""}
                              className="mt-1.5 h-9"
                              onChange={(e) =>
                                cambiarImpuesto(l.uid, e.target.value ? Number(e.target.value) : undefined)
                              }
                            >
                              <option value="">Sin otro impuesto</option>
                              {CATALOGO_IMPUESTOS.map((imp) => (
                                <option key={imp.codigo} value={imp.codigo}>
                                  {imp.nombre} ({imp.tasa}%){imp.esRetencion ? " · retención" : ""}
                                </option>
                              ))}
                            </Select>
                          </label>
                        )}
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
              <h2 className="font-display text-base font-semibold text-ink">Resumen</h2>
              <p className="mt-1 text-xs text-slate-soft">{TIPO_DTE_LABEL[tipoDte]}</p>
              <div className="mt-5 space-y-2.5 text-sm">
                <Linea label="Neto" valor={formatCLP(totales.neto)} />
                {totales.exento > 0 && <Linea label="Exento" valor={formatCLP(totales.exento)} />}
                <Linea label={`IVA ${TASA_IVA}%`} valor={formatCLP(totales.iva)} />
                {totales.desglose.map((d) =>
                  d.esRetencion ? (
                    <Linea key={d.codigo} label="IVA retenido" valor={`-${formatCLP(d.monto)}`} />
                  ) : (
                    <Linea key={d.codigo} label={`Imp. adicional ${d.codigo} (${d.tasa}%)`} valor={formatCLP(d.monto)} />
                  ),
                )}
                <div className="flex items-center justify-between border-t border-line pt-3">
                  <span className="font-semibold text-ink">Total</span>
                  <span className="font-display text-xl font-semibold text-cobalt tnum">{formatCLP(totales.total)}</span>
                </div>
              </div>

              {error && <Alert className="mt-4">{error}</Alert>}

              <Button className="mt-6 w-full" onClick={emitir} disabled={!puedeEmitir || emitiendo}>
                {emitiendo ? <><Spinner className="border-white/40 border-t-white" /> Creando…</> : <><Receipt className="h-4 w-4" /> Crear documento</>}
              </Button>
              <p className="mt-3 text-center text-xs text-slate-soft">
                Se crea en borrador; podrás emitirlo y enviarlo al SII desde el detalle.
              </p>
            </Card>
          </div>
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
