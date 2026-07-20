import { useEffect, useState } from "react";
import { BookOpen, FileDown } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import { Card, Input, Button, EmptyState, PageHeader, LoadingState, Alert, Th, Badge } from "../../components/ui";
import { getLibro, getLibroXml, mensajeError } from "../../lib/api";
import { empresaIdActual } from "../../lib/auth";
import { formatCLP, formatFecha, formatNumero, formatRut, mesActual } from "../../lib/format";
import { nombreTipoDte, type LibroResponse, type TipoOperacionLibro } from "../../lib/types";

const MES_ACTUAL = mesActual(); // YYYY-MM (mes local, no UTC)

export function Libros() {
  const [tipo, setTipo] = useState<TipoOperacionLibro>("VENTA");
  const [periodo, setPeriodo] = useState(MES_ACTUAL);
  const [libro, setLibro] = useState<LibroResponse | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [descargando, setDescargando] = useState(false);

  useEffect(() => {
    let vigente = true;
    setCargando(true);
    setError(null);
    getLibro(empresaIdActual(), tipo, periodo)
      .then((l) => { if (vigente) setLibro(l); })
      .catch((e) => { if (vigente) setError(mensajeError(e, "No se pudo cargar el libro.")); })
      .finally(() => { if (vigente) setCargando(false); });
    return () => { vigente = false; };
  }, [tipo, periodo]);

  async function descargarXml() {
    setDescargando(true);
    setError(null);
    try {
      const xml = await getLibroXml(empresaIdActual(), tipo, periodo);
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
    </AppShell>
  );
}
