import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { FileText, Plus, Search } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import { Card, Input, Button, EmptyState, PageHeader, LoadingState, Th } from "../../components/ui";
import { StatusBadge } from "../../components/StatusBadge";
import { getDocumentos } from "../../lib/api";
import { empresaIdActual } from "../../lib/auth";
import { formatCLP, formatFecha } from "../../lib/format";
import { ESTADO_LABEL, TIPO_DTE_LABEL, type DocumentoResumen, type EstadoDte } from "../../lib/types";

type Filtro = "TODOS" | EstadoDte;
const FILTROS: Filtro[] = ["TODOS", "ACEPTADO", "ENVIADO", "EN_CONTINGENCIA", "BORRADOR", "RECHAZADO"];

export function Documentos() {
  const navigate = useNavigate();
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
      <div className="space-y-6">
        <PageHeader
          titulo="Documentos"
          descripcion="Revisa y gestiona tus DTE emitidos."
          accion={
            <Link to="/app/nueva-factura">
              <Button><Plus size={16} /> Nueva factura</Button>
            </Link>
          }
        />

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-wrap gap-2">
            {FILTROS.map((f) => (
              <button
                key={f}
                onClick={() => setFiltro(f)}
                className={`rounded-full border px-3.5 py-1.5 text-sm font-medium transition-colors ${
                  filtro === f
                    ? "border-cobalt-soft bg-cobalt-soft text-cobalt"
                    : "border-line bg-white text-slate hover:bg-mist hover:text-ink"
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
            <LoadingState mensaje="Cargando documentos…" />
          ) : visibles.length === 0 ? (
            <EmptyState
              icon={<FileText size={22} />}
              titulo="Sin documentos"
              descripcion="No hay documentos que coincidan con el filtro. Ajusta la búsqueda o emite tu primer DTE."
              accion={
                <Link to="/app/nueva-factura">
                  <Button><Plus size={16} /> Nueva factura</Button>
                </Link>
              }
            />
          ) : (
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
                {visibles.map((d) => (
                  <tr
                    key={d.id}
                    onClick={() => navigate(`/app/documentos/${d.id}`)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        navigate(`/app/documentos/${d.id}`);
                      }
                    }}
                    tabIndex={0}
                    role="button"
                    aria-label={`Ver documento ${d.folio ?? "borrador"} de ${d.receptorRazonSocial}`}
                    className="cursor-pointer border-b border-line transition-colors last:border-0 hover:bg-mist/60 focus:bg-mist/60 focus:outline-none"
                  >
                    <td className="px-4 py-3.5 font-medium text-ink tnum">{d.folio ?? "—"}</td>
                    <td className="px-4 py-3.5 text-slate">{TIPO_DTE_LABEL[d.tipoDte]}</td>
                    <td className="px-4 py-3.5 text-ink">{d.receptorRazonSocial}</td>
                    <td className="px-4 py-3.5 text-slate tnum">{formatFecha(d.fechaEmision)}</td>
                    <td className="px-4 py-3.5"><StatusBadge estado={d.estado} /></td>
                    <td className="px-4 py-3.5 text-right font-semibold text-ink tnum">{formatCLP(d.total)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      </div>
    </AppShell>
  );
}
