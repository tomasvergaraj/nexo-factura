import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { FileText, CircleDollarSign, Clock, CircleCheck, ArrowUpRight, CloudOff } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import { Kpi } from "../../components/app/Kpi";
import { Alert, Button, Card, LoadingState, Th } from "../../components/ui";
import { StatusBadge } from "../../components/StatusBadge";
import { getDashboard, mensajeError, reenviarPendientes } from "../../lib/api";
import { empresaIdActual } from "../../lib/auth";
import { serieEmisionMock } from "../../lib/mock";
import { formatCLP, formatFecha } from "../../lib/format";
import { TIPO_DTE_LABEL, type ResumenDashboard } from "../../lib/types";

export function Dashboard() {
  const [data, setData] = useState<ResumenDashboard | null>(null);
  const [reintentando, setReintentando] = useState(false);
  const [avisoReenvio, setAvisoReenvio] = useState<string | null>(null);

  useEffect(() => {
    getDashboard(empresaIdActual()).then(setData);
  }, []);

  async function reintentarEnvios() {
    setReintentando(true);
    setAvisoReenvio(null);
    try {
      const resumen = await reenviarPendientes(empresaIdActual());
      setAvisoReenvio(
        resumen.enContingencia === 0
          ? `Se reenviaron ${resumen.enviados} de ${resumen.procesados} documentos.`
          : `Se reenviaron ${resumen.enviados} de ${resumen.procesados}; ${resumen.enContingencia} siguen en contingencia.`,
      );
      // La respuesta ya trae los contadores: se actualiza el estado local sin
      // pagar una recarga completa del dashboard.
      setData((d) => d && {
        ...d,
        enContingencia: resumen.enContingencia,
        pendientesSii: d.pendientesSii + resumen.enviados,
      });
    } catch (e) {
      setAvisoReenvio(mensajeError(e, "No se pudieron reenviar los documentos."));
    } finally {
      setReintentando(false);
    }
  }

  return (
    <AppShell titulo="Resumen">
      {!data ? (
        <LoadingState mensaje="Cargando resumen…" />
      ) : (
        <div className="space-y-6">
          {data.enContingencia > 0 && (
            <Alert tone="warn" icon={<CloudOff size={16} />}>
              <span className="flex flex-wrap items-center gap-3">
                {data.enContingencia === 1
                  ? "Hay 1 documento en contingencia (el SII no estaba disponible al enviarlo)."
                  : `Hay ${data.enContingencia} documentos en contingencia (el SII no estaba disponible al enviarlos).`}
                <Button size="sm" variant="secondary" onClick={reintentarEnvios} disabled={reintentando}>
                  {reintentando ? "Reintentando…" : "Reintentar envíos"}
                </Button>
              </span>
            </Alert>
          )}
          {avisoReenvio && <Alert tone="info">{avisoReenvio}</Alert>}

          {/* KPIs */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Kpi label="Documentos del mes" valor={String(data.documentosMes)}
              sub="emitidos en junio" icono={<FileText size={18} />} />
            <Kpi label="Monto emitido" valor={formatCLP(data.montoEmitidoMes)}
              sub="neto + IVA del mes" icono={<CircleDollarSign size={18} />} />
            <Kpi label="Pendientes en SII" valor={String(data.pendientesSii)}
              sub="esperando respuesta" icono={<Clock size={18} />} />
            <Kpi label="Aceptados" valor={String(data.aceptados)}
              sub={`${data.borradores} en borrador`} icono={<CircleCheck size={18} />} />
          </div>

          {/* Gráfico + accesos */}
          <div className="grid gap-5 lg:grid-cols-[1.6fr_1fr]">
            <Card className="p-6">
              <div className="flex items-center justify-between">
                <h2 className="font-display text-base font-semibold text-ink">Emisión últimos 7 días</h2>
                <span className="text-xs text-slate-soft">en pesos</span>
              </div>
              <BarChart />
            </Card>

            <Card className="flex flex-col justify-between p-6">
              <div>
                <h2 className="font-display text-base font-semibold text-ink">Emite en segundos</h2>
                <p className="mt-2 text-sm leading-relaxed text-slate">
                  Crea una factura nueva, agrega el detalle y deja que el sistema
                  calcule el IVA, timbre y envíe al SII.
                </p>
              </div>
              <Link
                to="/app/nueva-factura"
                className="group mt-4 inline-flex items-center gap-2 text-sm font-medium text-cobalt transition-colors hover:text-cobalt-dark"
              >
                Nueva factura
                <ArrowUpRight size={16} className="transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
              </Link>
            </Card>
          </div>

          {/* Recientes */}
          <Card className="overflow-hidden">
            <div className="flex items-center justify-between border-b border-line px-6 py-4">
              <h2 className="font-display text-base font-semibold text-ink">Documentos recientes</h2>
              <Link to="/app/documentos" className="text-sm font-medium text-cobalt transition-colors hover:text-cobalt-dark">
                Ver todos
              </Link>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-line">
                    <Th>Folio</Th>
                    <Th>Tipo</Th>
                    <Th>Cliente</Th>
                    <Th>Fecha</Th>
                    <Th>Estado</Th>
                    <Th align="right">Total</Th>
                  </tr>
                </thead>
                <tbody>
                  {data.recientes.map((d) => (
                    <tr key={d.id} className="border-b border-line/70 last:border-0 transition-colors hover:bg-mist/60">
                      <td className="px-6 py-3.5 font-medium text-ink tnum">{d.folio ?? "—"}</td>
                      <td className="px-6 py-3.5 text-slate">{TIPO_DTE_LABEL[d.tipoDte]}</td>
                      <td className="px-6 py-3.5 text-ink">{d.receptorRazonSocial}</td>
                      <td className="px-6 py-3.5 text-slate tnum">{formatFecha(d.fechaEmision)}</td>
                      <td className="px-6 py-3.5"><StatusBadge estado={d.estado} /></td>
                      <td className="px-6 py-3.5 text-right font-medium text-ink tnum">{formatCLP(d.total)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      )}
    </AppShell>
  );
}

function BarChart() {
  const max = Math.max(...serieEmisionMock.map((d) => d.valor));
  return (
    <div className="mt-6 flex h-44 items-end gap-3">
      {serieEmisionMock.map((d) => (
        <div key={d.dia} className="group flex h-full flex-1 flex-col items-center justify-end gap-2">
          <div className="flex h-full w-full flex-col justify-end overflow-hidden rounded-t-sm bg-mist">
            <div
              className="w-full rounded-t-sm bg-cobalt transition-colors group-hover:bg-cobalt-dark"
              style={{ height: `${Math.max(6, (d.valor / max) * 100)}%` }}
              title={d.valor.toLocaleString("es-CL")}
            />
          </div>
          <span className="text-xs text-slate-soft tnum">{d.dia}</span>
        </div>
      ))}
    </div>
  );
}
