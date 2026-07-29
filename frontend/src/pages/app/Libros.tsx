import { useEffect, useState } from "react";
import { AlertTriangle, BookOpen, ClipboardCheck, FileDown, RefreshCw, Send } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import { Card, Input, Button, EmptyState, PageHeader, LoadingState, Alert, Th, Badge, Modal } from "../../components/ui";
import { getLibro, getLibroXml, enviarLibro, getEnviosLibro, estadoEnvioLibro, getLibrosPendientes, mensajeError } from "../../lib/api";
import { empresaIdActual } from "../../lib/auth";
import { formatCLP, formatFecha, formatFechaHora, formatNumero, formatRut, mesActual } from "../../lib/format";
import { nombreTipoDte, type EnvioLibro, type EstadoEnvioLibro, type LibroPendiente, type LibroResponse, type TipoOperacionLibro } from "../../lib/types";

const MES_ACTUAL = mesActual(); // YYYY-MM (mes local, no UTC)

const ESTADO_ENVIO: Record<EstadoEnvioLibro, { label: string; tone: "success" | "warn" | "danger" | "cobalt" }> = {
  RECIBIDO: { label: "Recibido", tone: "cobalt" },
  ACEPTADO: { label: "Aceptado", tone: "success" },
  ACEPTADO_CON_REPARO: { label: "Aceptado con reparo", tone: "warn" },
  RECHAZADO: { label: "Rechazado", tone: "danger" },
};

export function Libros() {
  const [tipo, setTipo] = useState<TipoOperacionLibro>("VENTA");
  const [periodo, setPeriodo] = useState(MES_ACTUAL);
  const [libro, setLibro] = useState<LibroResponse | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [descargando, setDescargando] = useState(false);
  const [envios, setEnvios] = useState<EnvioLibro[]>([]);
  const [pendientes, setPendientes] = useState<LibroPendiente[]>([]);
  const [confirmando, setConfirmando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [refrescando, setRefrescando] = useState<string | null>(null);
  // Override del factor de proporcionalidad para ESTE período. Se separa del
  // valor escrito para no recargar el libro en cada tecla: solo "Aplicar" (o
  // limpiar el campo) dispara el refetch.
  const [factorTexto, setFactorTexto] = useState("");
  const [factorAplicado, setFactorAplicado] = useState<number | undefined>(undefined);

  useEffect(() => {
    let vigente = true;
    setCargando(true);
    setError(null);
    setEnvios([]);
    Promise.all([
      getLibro(empresaIdActual(), tipo, periodo, factorAplicado),
      getEnviosLibro(empresaIdActual(), tipo, periodo),
    ])
      .then(([l, e]) => { if (vigente) { setLibro(l); setEnvios(e); } })
      .catch((e) => { if (vigente) setError(mensajeError(e, "No se pudo cargar el libro.")); })
      .finally(() => { if (vigente) setCargando(false); });
    return () => { vigente = false; };
  }, [tipo, periodo, factorAplicado]);

  // Cambiar de libro o de período descarta el override: era de ese período.
  useEffect(() => {
    setFactorTexto("");
    setFactorAplicado(undefined);
  }, [tipo, periodo]);

  // Avisos de la revisión automática (por empresa, no por período): se cargan una
  // vez y se refrescan tras enviar. Falla en silencio: es informativo, no crítico.
  useEffect(() => {
    let vigente = true;
    getLibrosPendientes(empresaIdActual())
      .then((p) => { if (vigente) setPendientes(p); })
      .catch(() => { /* aviso no crítico */ });
    return () => { vigente = false; };
  }, []);

  function irAPendiente(p: LibroPendiente) {
    setError(null);
    setTipo(p.tipoOperacion);
    setPeriodo(p.periodo);
    if (p.estado === "PREPARADO") setConfirmando(true);
  }

  async function confirmarEnvio() {
    setEnviando(true);
    setError(null);
    try {
      await enviarLibro(empresaIdActual(), tipo, periodo, factorAplicado);
      setConfirmando(false);
      // Releo la lista para mostrar el envío recién creado con su TrackID, y los
      // pendientes: el libro recién enviado deja de figurar como pendiente.
      const [nuevosEnvios, nuevosPendientes] = await Promise.all([
        getEnviosLibro(empresaIdActual(), tipo, periodo),
        getLibrosPendientes(empresaIdActual()),
      ]);
      setEnvios(nuevosEnvios);
      setPendientes(nuevosPendientes);
    } catch (e) {
      setError(mensajeError(e, "No se pudo enviar el libro al SII."));
    } finally {
      setEnviando(false);
    }
  }

  async function refrescarEstado(trackId: string) {
    setRefrescando(trackId);
    setError(null);
    try {
      const estado = await estadoEnvioLibro(empresaIdActual(), trackId);
      setEnvios((prev) => prev.map((e) => (e.trackId === trackId ? { ...e, estado } : e)));
    } catch (e) {
      setError(mensajeError(e, "No se pudo consultar el estado del envío."));
    } finally {
      setRefrescando(null);
    }
  }

  async function descargarXml() {
    setDescargando(true);
    setError(null);
    try {
      const xml = await getLibroXml(empresaIdActual(), tipo, periodo, factorAplicado);
      const nombre = `libro-${tipo === "VENTA" ? "ventas" : "compras"}-${periodo}.xml`;
      const url = URL.createObjectURL(xml);
      const a = document.createElement("a");
      a.href = url;
      a.download = nombre;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 10_000);
    } catch (e) {
      setError(mensajeError(e, "No se pudo descargar el XML."));
    } finally {
      setDescargando(false);
    }
  }

  const conMovimiento = libro && !libro.sinMovimiento;
  const ivaUsoComun = libro?.resumen.reduce((suma, r) => suma + r.ivaUsoComun, 0) ?? 0;
  const creditoUsoComun = libro?.resumen.reduce((suma, r) => suma + r.creditoIvaUsoComun, 0) ?? 0;
  const conUsoComun = !!conMovimiento && ivaUsoComun > 0;
  // Es una proporción: fuera de [0,1] el SII rechazaría el libro.
  const factorValido = /^\d*\.?\d+$/.test(factorTexto.trim().replace(",", "."))
    && Number(factorTexto.trim().replace(",", ".")) >= 0
    && Number(factorTexto.trim().replace(",", ".")) <= 1;

  function aplicarFactor() {
    if (factorValido) setFactorAplicado(Number(factorTexto.trim().replace(",", ".")));
  }

  return (
    <AppShell titulo="Libros de compra y venta">
      <div className="space-y-6">
        <PageHeader
          titulo="Libros de compra y venta (IECV)"
          descripcion="Resumen mensual por tipo de documento y detalle del período tributario."
          accion={
            <div className="flex items-center gap-2">
              <Input
                type="month"
                value={periodo}
                max={MES_ACTUAL}
                onChange={(e) => setPeriodo(e.target.value)}
                className="w-44"
                aria-label="Período tributario"
              />
              <Button variant="secondary" onClick={descargarXml} disabled={descargando || !conMovimiento}>
                {descargando ? "Generando…" : <><FileDown size={16} /> XML</>}
              </Button>
              <Button
                onClick={() => setConfirmando(true)}
                disabled={enviando || !conMovimiento}
                className="shrink-0 whitespace-nowrap"
              >
                <Send size={16} /> Enviar al SII
              </Button>
            </div>
          }
        />

        {/* Selector ventas/compras */}
        <div className="inline-flex rounded-full border border-line bg-white p-1">
          {(["VENTA", "COMPRA"] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTipo(t)}
              aria-pressed={tipo === t}
              className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
                tipo === t ? "bg-cobalt text-white" : "text-slate hover:text-ink"
              }`}
            >
              {t === "VENTA" ? "Ventas" : "Compras"}
            </button>
          ))}
        </div>

        {error && <Alert>{error}</Alert>}

        {/* Solo aparece cuando el período trae IVA de uso común, que es cuando el
            XML exige FctProp. Sin factor el libro no se puede firmar, así que el
            aviso dice dónde configurarlo en vez de dejar reventar el envío. */}
        {conUsoComun && (
          <Card className={`p-5 ${libro!.fctProp == null ? "border-danger/40" : ""}`}>
            <h2 className="font-display text-sm font-semibold text-ink">
              IVA de uso común en este período
            </h2>
            {libro!.fctProp == null ? (
              <p className="mt-1 text-xs leading-relaxed text-danger">
                Este libro tiene IVA de uso común y no hay factor de proporcionalidad, así que no
                se puede enviar. Configúralo en Configuración, o informa uno solo para este período.
              </p>
            ) : (
              <p className="mt-1 text-xs leading-relaxed text-slate">
                Se está aplicando el factor{" "}
                <span className="font-semibold text-ink tnum">{libro!.fctProp.toFixed(2)}</span>
                {factorAplicado == null && <> (el configurado en la empresa)</>}: crédito
                proporcional de {formatCLP(creditoUsoComun)} sobre {formatCLP(ivaUsoComun)} de IVA
                de uso común.
              </p>
            )}
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <Input
                inputMode="decimal"
                value={factorTexto}
                placeholder="0.60"
                className="w-28"
                aria-label="Factor de proporcionalidad para este período"
                onChange={(e) => setFactorTexto(e.target.value)}
              />
              <Button variant="secondary" onClick={aplicarFactor} disabled={!factorValido}>
                Aplicar a este período
              </Button>
              {factorAplicado != null && (
                <Button
                  variant="secondary"
                  onClick={() => { setFactorTexto(""); setFactorAplicado(undefined); }}
                >
                  Usar el de la empresa
                </Button>
              )}
            </div>
            <p className="mt-2 text-xs text-slate-soft">
              Solo para este período; no cambia el factor configurado en la empresa.
            </p>
          </Card>
        )}

        {pendientes.length > 0 && (
          <Card className="overflow-hidden border-cobalt/30">
            <div className="border-b border-line bg-cobalt/5 px-6 py-4">
              <h2 className="font-display text-base font-semibold text-ink">Libros pendientes de envío</h2>
              <p className="mt-0.5 text-xs text-slate-soft">
                Revisión automática del período anterior. Prepara el libro (lo firma y valida) pero no lo envía:
                revísalo y envíalo tú.
              </p>
            </div>
            <ul className="divide-y divide-line">
              {pendientes.map((p) => {
                const esError = p.estado === "ERROR";
                return (
                  <li key={p.id} className="flex items-center justify-between gap-3 px-6 py-4">
                    <div className="flex items-start gap-3">
                      <span className={esError ? "text-danger" : "text-cobalt"}>
                        {esError ? <AlertTriangle size={18} /> : <ClipboardCheck size={18} />}
                      </span>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-medium text-ink">
                            Libro de {p.tipoOperacion === "VENTA" ? "ventas" : "compras"}{" "}
                            <span className="tnum">{p.periodo}</span>
                          </span>
                          <Badge tone={esError ? "danger" : "cobalt"}>
                            {esError ? "No se pudo preparar" : "Listo para enviar"}
                          </Badge>
                        </div>
                        {esError && p.detalle && (
                          <p className="mt-1 text-xs text-slate-soft">{p.detalle}</p>
                        )}
                      </div>
                    </div>
                    <Button
                      variant={esError ? "secondary" : "primary"}
                      onClick={() => irAPendiente(p)}
                      className="shrink-0 whitespace-nowrap"
                    >
                      {esError ? "Revisar" : <><Send size={16} /> Revisar y enviar</>}
                    </Button>
                  </li>
                );
              })}
            </ul>
          </Card>
        )}

        {envios.length > 0 && (
          <Card className="overflow-hidden">
            <div className="flex items-center justify-between gap-2 border-b border-line px-6 py-4">
              <div>
                <h2 className="font-display text-base font-semibold text-ink">Envíos al SII</h2>
                <p className="mt-0.5 text-xs text-slate-soft">
                  Libro de {tipo === "VENTA" ? "ventas" : "compras"} del período {periodo}.
                </p>
              </div>
              <Badge tone="success">Ya enviado</Badge>
            </div>
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-line">
                  <Th>Enviado</Th>
                  <Th>TrackID</Th>
                  <Th>Estado</Th>
                  <Th align="right">Acción</Th>
                </tr>
              </thead>
              <tbody>
                {envios.map((e) => {
                  const est = e.estado ? ESTADO_ENVIO[e.estado] : null;
                  return (
                    <tr key={e.id} className="border-b border-line last:border-0">
                      <td className="px-4 py-3.5 text-slate tnum">{formatFechaHora(e.tmstEnvio)}</td>
                      <td className="px-4 py-3.5 font-medium text-ink tnum">{e.trackId}</td>
                      <td className="px-4 py-3.5">
                        {est
                          ? <Badge tone={est.tone}>{est.label}</Badge>
                          : <span className="text-xs text-slate-soft">Pendiente de consulta</span>}
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        <Button
                          variant="secondary"
                          onClick={() => refrescarEstado(e.trackId)}
                          disabled={refrescando === e.trackId}
                        >
                          <RefreshCw size={16} className={refrescando === e.trackId ? "animate-spin" : ""} />
                          {refrescando === e.trackId ? "Consultando…" : "Actualizar estado"}
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </Card>
        )}

        {cargando ? (
          <Card><LoadingState mensaje="Cargando libro…" /></Card>
        ) : !libro || libro.sinMovimiento ? (
          <Card>
            <EmptyState
              icon={<BookOpen size={22} />}
              titulo="Sin movimiento en el período"
              descripcion={tipo === "VENTA"
                ? "No hay documentos emitidos en este período. Elige otro mes."
                : "No hay compras registradas en este período. Regístralas en la sección Compras."}
            />
          </Card>
        ) : (
          <>
            {/* Resumen por tipo */}
            <Card className="overflow-hidden">
              <div className="border-b border-line px-6 py-4">
                <h2 className="font-display text-base font-semibold text-ink">Resumen del período {libro.periodo}</h2>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-line">
                    <Th>Tipo de documento</Th>
                    <Th align="right">Docs.</Th>
                    <Th align="right">Anulados</Th>
                    <Th align="right">Neto</Th>
                    <Th align="right">Exento</Th>
                    <Th align="right">IVA</Th>
                    <Th align="right">Otros imp.</Th>
                    <Th align="right">IVA retenido</Th>
                    <Th align="right">Total</Th>
                  </tr>
                </thead>
                <tbody>
                  {libro.resumen.map((r) => (
                    <tr key={r.tipoDocumento} className="border-b border-line last:border-0">
                      <td className="px-4 py-3.5 text-ink">
                        {nombreTipoDte(r.tipoDocumento)}
                        <span className="ml-1 text-slate-soft tnum">({r.tipoDocumento})</span>
                      </td>
                      <td className="px-4 py-3.5 text-right text-ink tnum">{formatNumero(r.documentos)}</td>
                      <td className="px-4 py-3.5 text-right text-slate tnum">{formatNumero(r.anulados)}</td>
                      <td className="px-4 py-3.5 text-right text-slate tnum">{formatCLP(r.neto)}</td>
                      <td className="px-4 py-3.5 text-right text-slate tnum">{formatCLP(r.exento)}</td>
                      <td className="px-4 py-3.5 text-right text-slate tnum">{formatCLP(r.iva)}</td>
                      <td className="px-4 py-3.5 text-right text-slate tnum">{r.otrosImpuestos > 0 ? formatCLP(r.otrosImpuestos) : "—"}</td>
                      <td className="px-4 py-3.5 text-right text-slate tnum">{r.ivaRetenido > 0 ? `-${formatCLP(r.ivaRetenido)}` : "—"}</td>
                      <td className="px-4 py-3.5 text-right font-semibold text-ink tnum">{formatCLP(r.total)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="border-t border-line bg-mist/40">
                    <td className="px-4 py-3.5 font-semibold text-ink">Total</td>
                    <td className="px-4 py-3.5 text-right font-semibold text-ink tnum">{formatNumero(libro.totales.documentos)}</td>
                    <td className="px-4 py-3.5 text-right font-semibold text-ink tnum">{formatNumero(libro.totales.anulados)}</td>
                    <td className="px-4 py-3.5 text-right font-semibold text-ink tnum">{formatCLP(libro.totales.neto)}</td>
                    <td className="px-4 py-3.5 text-right font-semibold text-ink tnum">{formatCLP(libro.totales.exento)}</td>
                    <td className="px-4 py-3.5 text-right font-semibold text-ink tnum">{formatCLP(libro.totales.iva)}</td>
                    <td className="px-4 py-3.5 text-right font-semibold text-ink tnum">{libro.totales.otrosImpuestos > 0 ? formatCLP(libro.totales.otrosImpuestos) : "—"}</td>
                    <td className="px-4 py-3.5 text-right font-semibold text-ink tnum">{libro.totales.ivaRetenido > 0 ? `-${formatCLP(libro.totales.ivaRetenido)}` : "—"}</td>
                    <td className="px-4 py-3.5 text-right font-semibold text-cobalt tnum">{formatCLP(libro.totales.total)}</td>
                  </tr>
                </tfoot>
              </table>
            </Card>

            {/* Detalle por documento */}
            <Card className="overflow-hidden">
              <div className="border-b border-line px-6 py-4">
                <h2 className="font-display text-base font-semibold text-ink">Detalle por documento</h2>
                {tipo === "VENTA" && (
                  <p className="mt-0.5 text-xs text-slate-soft">
                    Las boletas van solo resumidas (sin detalle), como en el IECV.
                  </p>
                )}
              </div>
              {libro.detalle.length === 0 ? (
                <EmptyState
                  icon={<BookOpen size={22} />}
                  titulo="Sin documentos detallados"
                  descripcion="El movimiento del período corresponde solo a boletas, que van resumidas."
                />
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-line">
                        <Th>Tipo</Th>
                        <Th align="right">Folio</Th>
                        <Th>Fecha</Th>
                        <Th>{tipo === "VENTA" ? "Receptor" : "Proveedor"}</Th>
                        <Th align="right">Neto</Th>
                        <Th align="right">Exento</Th>
                        <Th align="right">IVA</Th>
                        <Th align="right">Total</Th>
                      </tr>
                    </thead>
                    <tbody>
                      {libro.detalle.map((d) => (
                        <tr
                          // En compras, tipo+folio puede repetirse entre proveedores distintos.
                          key={`${d.tipoDocumento}-${d.folio}-${d.rutContraparte}`}
                          className={`border-b border-line last:border-0 ${d.anulado ? "opacity-60" : ""}`}
                        >
                          <td className="px-4 py-3.5 text-ink">
                            {nombreTipoDte(d.tipoDocumento)}
                            {d.anulado && <span className="ml-2"><Badge tone="neutral">Anulado</Badge></span>}
                          </td>
                          <td className="px-4 py-3.5 text-right font-medium text-ink tnum">{d.folio}</td>
                          <td className="px-4 py-3.5 text-slate tnum">{formatFecha(d.fecha)}</td>
                          <td className="px-4 py-3.5 text-ink">
                            {d.razonSocial}
                            <span className="ml-2 text-xs text-slate-soft tnum">{formatRut(d.rutContraparte)}</span>
                          </td>
                          <td className="px-4 py-3.5 text-right text-slate tnum">{formatCLP(d.neto)}</td>
                          <td className="px-4 py-3.5 text-right text-slate tnum">{formatCLP(d.exento)}</td>
                          <td className="px-4 py-3.5 text-right text-slate tnum">{formatCLP(d.iva)}</td>
                          <td className="px-4 py-3.5 text-right font-medium text-ink tnum">{formatCLP(d.total)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>
          </>
        )}
      </div>

      <Modal
        open={confirmando}
        onClose={() => { if (!enviando) setConfirmando(false); }}
        title="Enviar libro al SII"
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmando(false)} disabled={enviando}>Cancelar</Button>
            <Button onClick={confirmarEnvio} disabled={enviando}>
              {enviando ? "Enviando…" : <><Send size={16} /> Enviar</>}
            </Button>
          </>
        }
      >
        <div className="space-y-3 text-sm text-slate">
          <p>
            Se firmará y enviará al SII el libro de{" "}
            <span className="font-medium text-ink">{tipo === "VENTA" ? "ventas" : "compras"}</span>{" "}
            del período <span className="font-medium text-ink tnum">{periodo}</span>.
          </p>
          {envios.length > 0 && (
            <Alert>
              Este período ya tiene {envios.length === 1 ? "un envío" : `${envios.length} envíos`} registrado{envios.length === 1 ? "" : "s"}.
              Enviar de nuevo genera un TrackID adicional en el SII.
            </Alert>
          )}
          <p className="text-xs text-slate-soft">
            El SII responde con un TrackID; el estado del procesamiento se consulta después con “Actualizar estado”.
          </p>
        </div>
      </Modal>
    </AppShell>
  );
}
