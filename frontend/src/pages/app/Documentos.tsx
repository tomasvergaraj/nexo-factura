import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Plus, Search } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import { Card, Spinner, Input, Button } from "../../components/ui";
import { StatusBadge } from "../../components/StatusBadge";
import { getDocumentos } from "../../lib/api";
import { empresaIdActual } from "../../lib/auth";
import { formatCLP, formatFecha } from "../../lib/format";
import { ESTADO_LABEL, TIPO_DTE_LABEL, type DocumentoResumen, type EstadoDte } from "../../lib/types";

type Filtro = "TODOS" | EstadoDte;
const FILTROS: Filtro[] = ["TODOS", "ACEPTADO", "ENVIADO", "BORRADOR", "RECHAZADO"];

export function Documentos() {
  const [docs, setDocs] = useState<DocumentoResumen[] | null>(null);
  const [filtro, setFiltro] = useState<Filtro>("TODOS");
  const [busqueda, setBusqueda] = useState("");

  useEffect(() => {
    getDocumentos(empresaIdActual()).then(setDocs);
  }, []);

  const visibles = useMemo(() => {
    if (!docs) return [];
    return docs.filter((d) => {
      const okEstado = filtro === "TODOS" || d.estado === filtro;
      const okBusqueda = d.receptorRazonSocial.toLowerCase().includes(busqueda.toLowerCase())
        || String(d.folio ?? "").includes(busqueda);
      return okEstado && okBusqueda;
    });
  }, [docs, filtro, busqueda]);

  return (
    <AppShell titulo="Documentos">
      <div className="space-y-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-wrap gap-1.5">
            {FILTROS.map((f) => (
              <button
                key={f}
                onClick={() => setFiltro(f)}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                  filtro === f ? "bg-ink text-white" : "bg-white text-slate border border-line hover:border-slate-soft"
                }`}
              >
                {f === "TODOS" ? "Todos" : ESTADO_LABEL[f]}
              </button>
            ))}
          </div>
          <div className="relative w-full sm:w-64">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-soft" />
            <Input
              className="pl-9"
              placeholder="Buscar cliente o folio…"
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
            />
          </div>
        </div>

        <Card className="overflow-hidden">
          {!docs ? (
            <div className="grid h-64 place-items-center"><Spinner className="h-6 w-6" /></div>
          ) : (
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
                {visibles.map((d) => (
                  <tr key={d.id} className="border-b border-line/70 last:border-0 hover:bg-mist/60">
                    <td className="px-6 py-3.5 font-medium text-ink tnum">{d.folio ?? "—"}</td>
                    <td className="px-6 py-3.5 text-slate">{TIPO_DTE_LABEL[d.tipoDte]}</td>
                    <td className="px-6 py-3.5 text-ink">{d.receptorRazonSocial}</td>
                    <td className="px-6 py-3.5 text-slate tnum">{formatFecha(d.fechaEmision)}</td>
                    <td className="px-6 py-3.5"><StatusBadge estado={d.estado} /></td>
                    <td className="px-6 py-3.5 text-right font-medium text-ink tnum">{formatCLP(d.total)}</td>
                  </tr>
                ))}
                {visibles.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-6 py-12 text-center text-sm text-slate-soft">
                      No hay documentos que coincidan con el filtro.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </Card>

        <div className="flex justify-end">
          <Link to="/app/nueva-factura">
            <Button><Plus size={16} /> Nueva factura</Button>
          </Link>
        </div>
      </div>
    </AppShell>
  );
}
