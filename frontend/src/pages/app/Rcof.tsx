import { useEffect, useState } from "react";
import { ClipboardList } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import { Card, Input, EmptyState, PageHeader, LoadingState, Alert, Th } from "../../components/ui";
import { getRcof, mensajeError } from "../../lib/api";
import { empresaIdActual } from "../../lib/auth";
import { formatCLP, formatNumero } from "../../lib/format";
import { TIPO_DTE_LABEL, TIPO_DTE_POR_CODIGO, type RcofResponse } from "../../lib/types";

const HOY = new Date().toISOString().slice(0, 10);

function rango(desde: number | null, hasta: number | null): string {
  if (desde == null || hasta == null) return "—";
  return desde === hasta ? String(desde) : `${desde}–${hasta}`;
}

export function Rcof() {
  const [fecha, setFecha] = useState(HOY);
  const [rcof, setRcof] = useState<RcofResponse | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let vigente = true;
    setCargando(true);
    setError(null);
    getRcof(empresaIdActual(), fecha)
      .then((r) => { if (vigente) setRcof(r); })
      .catch((e) => { if (vigente) setError(mensajeError(e, "No se pudo cargar el reporte.")); })
      .finally(() => { if (vigente) setCargando(false); });
    return () => { vigente = false; };
  }, [fecha]);

  return (
    <AppShell titulo="Consumo de folios (RCOF)">
      <div className="space-y-6">
        <PageHeader
          titulo="Consumo de folios (RCOF)"
          descripcion="Resumen diario de folios de boletas (39/41) utilizados y anulados."
          accion={
            <Input
              type="date"
              value={fecha}
              max={HOY}
              onChange={(e) => setFecha(e.target.value)}
              className="w-44"
              aria-label="Fecha del reporte"
            />
          }
        />

        {error && <Alert>{error}</Alert>}

        <Card className="overflow-hidden">
          {cargando ? (
            <LoadingState mensaje="Cargando reporte…" />
          ) : !rcof || rcof.sinMovimiento ? (
            <EmptyState
              icon={<ClipboardList size={22} />}
              titulo="Sin emisiones en esta fecha"
              descripcion="No se emitieron boletas en la fecha seleccionada. Elige otro día para ver su consumo de folios."
            />
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-line">
                  <Th>Tipo</Th>
                  <Th align="right">Utilizados</Th>
                  <Th align="right">Anulados</Th>
                  <Th align="right">Rango</Th>
                  <Th align="right">Neto</Th>
                  <Th align="right">Exento</Th>
                  <Th align="right">IVA</Th>
                  <Th align="right">Total</Th>
                </tr>
              </thead>
              <tbody>
                {rcof.documentos.map((row) => (
                  <tr key={row.tipoDocumento} className="border-b border-line last:border-0">
                    <td className="px-4 py-3.5 text-ink">
                      {TIPO_DTE_LABEL[TIPO_DTE_POR_CODIGO[row.tipoDocumento]]}
                      <span className="ml-1 text-slate-soft tnum">({row.tipoDocumento})</span>
                    </td>
                    <td className="px-4 py-3.5 text-right text-ink tnum">{formatNumero(row.foliosUtilizados)}</td>
                    <td className="px-4 py-3.5 text-right text-slate tnum">{formatNumero(row.foliosAnulados)}</td>
                    <td className="px-4 py-3.5 text-right text-slate tnum">{rango(row.folioInicial, row.folioFinal)}</td>
                    <td className="px-4 py-3.5 text-right text-slate tnum">{formatCLP(row.montoNeto)}</td>
                    <td className="px-4 py-3.5 text-right text-slate tnum">{formatCLP(row.montoExento)}</td>
                    <td className="px-4 py-3.5 text-right text-slate tnum">{formatCLP(row.montoIva)}</td>
                    <td className="px-4 py-3.5 text-right font-semibold text-ink tnum">{formatCLP(row.montoTotal)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr className="border-t border-line bg-mist/40">
                  <td className="px-4 py-3.5 font-semibold text-ink">Total</td>
                  <td className="px-4 py-3.5 text-right font-semibold text-ink tnum">{formatNumero(rcof.totales.foliosUtilizados)}</td>
                  <td className="px-4 py-3.5 text-right font-semibold text-ink tnum">{formatNumero(rcof.totales.foliosAnulados)}</td>
                  <td className="px-4 py-3.5" />
                  <td className="px-4 py-3.5 text-right font-semibold text-ink tnum">{formatCLP(rcof.totales.montoNeto)}</td>
                  <td className="px-4 py-3.5 text-right font-semibold text-ink tnum">{formatCLP(rcof.totales.montoExento)}</td>
                  <td className="px-4 py-3.5 text-right font-semibold text-ink tnum">{formatCLP(rcof.totales.montoIva)}</td>
                  <td className="px-4 py-3.5 text-right font-semibold text-cobalt tnum">{formatCLP(rcof.totales.montoTotal)}</td>
                </tr>
              </tfoot>
            </table>
          )}
        </Card>
      </div>
    </AppShell>
  );
}
