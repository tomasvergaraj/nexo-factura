import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { FileText, CircleDollarSign, Clock, CircleCheck, ArrowUpRight } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import { Kpi } from "../../components/app/Kpi";
import { Card, Spinner } from "../../components/ui";
import { StatusBadge } from "../../components/StatusBadge";
import { getDashboard } from "../../lib/api";
import { serieEmisionMock } from "../../lib/mock";
import { formatCLP, formatFecha } from "../../lib/format";
import { TIPO_DTE_LABEL, type ResumenDashboard } from "../../lib/types";

export function Dashboard() {
  const [data, setData] = useState<ResumenDashboard | null>(null);

  useEffect(() => {
    getDashboard(1).then(setData);
  }, []);

  return (
    <AppShell titulo="Resumen">
      {!data ? (
        <div className="grid h-64 place-items-center"><Spinner className="h-6 w-6" /></div>
      ) : (
        <div className="space-y-6">
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
                <h2 className="font-display text-base font-bold text-ink">Emisión últimos 7 días</h2>
                <span className="text-xs text-slate-soft">en pesos</span>
              </div>
              <BarChart />
            </Card>

            <Card className="flex flex-col justify-between p-6">
              <div>
                <h2 className="font-display text-base font-bold text-ink">Emite en segundos</h2>
                <p className="mt-2 text-sm leading-relaxed text-slate">
                  Crea una factura nueva, agrega el detalle y deja que el sistema
                  calcule el IVA, timbre y envíe al SII.
                </p>
              </div>
              <Link
                to="/app/nueva-factura"
                className="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-cobalt hover:gap-3 transition-all"
              >
                Nueva factura <ArrowUpRight size={16} />
              </Link>
            </Card>
          </div>

          {/* Recientes */}
          <Card className="overflow-hidden">
            <div className="flex items-center justify-between border-b border-line px-6 py-4">
              <h2 className="font-display text-base font-bold text-ink">Documentos recientes</h2>
              <Link to="/app/documentos" className="text-sm font-medium text-cobalt hover:underline">
                Ver todos
              </Link>
            </div>
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-slate-soft">
                  <th className="px-6 py-3 font-semibold">Folio</th>
                  <th className="px-6 py-3 font-semibold">Tipo</th>
                  <th className="px-6 py-3 font-semibold">Cliente</th>
                  <th className="px-6 py-3 font-semibold">Fecha</th>
                  <th className="px-6 py-3 font-semibold">Estado</th>
                  <th className="px-6 py-3 text-right font-semibold">Total</th>
                </tr>
              </thead>
              <tbody>
                {data.recientes.map((d) => (
                  <tr key={d.id} className="border-b border-line/70 last:border-0 hover:bg-mist/60">
                    <td className="px-6 py-3 font-medium text-ink tnum">{d.folio ?? "—"}</td>
                    <td className="px-6 py-3 text-slate">{TIPO_DTE_LABEL[d.tipoDte]}</td>
                    <td className="px-6 py-3 text-ink">{d.receptorRazonSocial}</td>
                    <td className="px-6 py-3 text-slate tnum">{formatFecha(d.fechaEmision)}</td>
                    <td className="px-6 py-3"><StatusBadge estado={d.estado} /></td>
                    <td className="px-6 py-3 text-right font-medium text-ink tnum">{formatCLP(d.total)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
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
        <div key={d.dia} className="flex h-full flex-1 flex-col items-center justify-end gap-2">
          <div
            className="w-full rounded-t-md bg-cobalt/85 transition-all hover:bg-cobalt"
            style={{ height: `${Math.max(6, (d.valor / max) * 88)}%` }}
            title={d.valor.toLocaleString("es-CL")}
          />
          <span className="text-xs text-slate-soft">{d.dia}</span>
        </div>
      ))}
    </div>
  );
}
