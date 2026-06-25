import { useEffect, useState } from "react";
import { Plus, Hash } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import {
  Card, Input, Button, Field, Select, Modal, Badge, EmptyState,
  PageHeader, LoadingState, Alert,
} from "../../components/ui";
import { cargarCaf, getFolios, mensajeError } from "../../lib/api";
import { empresaIdActual } from "../../lib/auth";
import { formatFecha, formatNumero } from "../../lib/format";
import { TIPO_DTE_LABEL, type Caf, type CafRequest, type TipoDte } from "../../lib/types";

const TIPOS: TipoDte[] = [
  "FACTURA_AFECTA", "FACTURA_EXENTA", "BOLETA_AFECTA", "BOLETA_EXENTA", "NOTA_DEBITO", "NOTA_CREDITO",
];

interface FormCaf {
  tipoDte: TipoDte;
  folioDesde: string;
  folioHasta: string;
  fechaAutorizacion: string;
  fechaVencimiento: string;
}

const VACIO: FormCaf = {
  tipoDte: "FACTURA_AFECTA", folioDesde: "", folioHasta: "", fechaAutorizacion: "", fechaVencimiento: "",
};

export function Folios() {
  const [folios, setFolios] = useState<Caf[] | null>(null);
  const [abierto, setAbierto] = useState(false);
  const [form, setForm] = useState<FormCaf>(VACIO);
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  function cargar() {
    getFolios(empresaIdActual()).then(setFolios);
  }
  useEffect(cargar, []);

  function abrirNuevo() {
    setForm(VACIO);
    setError(null);
    setAbierto(true);
  }

  async function guardar() {
    setError(null);
    const desde = Number(form.folioDesde);
    const hasta = Number(form.folioHasta);
    if (!desde || desde < 1) {
      setError("Indica un folio inicial válido.");
      return;
    }
    if (!hasta || hasta < desde) {
      setError("El folio final debe ser mayor o igual al inicial.");
      return;
    }

    const payload: CafRequest = {
      tipoDte: form.tipoDte,
      folioDesde: desde,
      folioHasta: hasta,
      ...(form.fechaAutorizacion ? { fechaAutorizacion: form.fechaAutorizacion } : {}),
      ...(form.fechaVencimiento ? { fechaVencimiento: form.fechaVencimiento } : {}),
    };

    setGuardando(true);
    try {
      await cargarCaf(empresaIdActual(), payload);
      setAbierto(false);
      cargar();
    } catch (e) {
      setError(mensajeError(e, "No se pudo cargar el CAF."));
    } finally {
      setGuardando(false);
    }
  }

  return (
    <AppShell titulo="Folios (CAF)">
      <div className="space-y-6">
        <PageHeader
          titulo="Folios (CAF)"
          descripcion="Códigos de Autorización de Folios entregados por el SII para cada tipo de documento."
          accion={<Button onClick={abrirNuevo}><Plus className="h-4 w-4" /> Cargar CAF</Button>}
        />

        {!folios ? (
          <Card>
            <LoadingState mensaje="Cargando folios…" />
          </Card>
        ) : folios.length === 0 ? (
          <Card>
            <EmptyState
              icon={<Hash className="h-6 w-6" />}
              titulo="Aún no hay folios cargados"
              descripcion="Carga un CAF del SII para poder asignar folios al emitir documentos."
              accion={<Button onClick={abrirNuevo}><Plus className="h-4 w-4" /> Cargar CAF</Button>}
            />
          </Card>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            {folios.map((caf) => {
              const rango = caf.folioHasta - caf.folioDesde;
              const progreso = rango > 0
                ? Math.min(1, Math.max(0, (caf.folioActual - caf.folioDesde) / rango))
                : caf.agotado ? 1 : 0;
              const pct = Math.round(progreso * 100);
              return (
                <Card key={caf.id} className="p-6">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <h3 className="truncate font-display text-base font-semibold text-ink">
                        {TIPO_DTE_LABEL[caf.tipoDte]}
                      </h3>
                      <p className="mt-1 text-xs text-slate-soft tnum">
                        Folios {formatNumero(caf.folioDesde)} – {formatNumero(caf.folioHasta)}
                      </p>
                    </div>
                    {caf.agotado
                      ? <Badge tone="danger">Agotado</Badge>
                      : <Badge tone="success">Disponible</Badge>}
                  </div>

                  <div className="mt-5">
                    <div className="mb-2 flex items-baseline justify-between">
                      <span className="text-xs font-medium uppercase tracking-wide text-slate-soft">
                        Consumo
                      </span>
                      <span className="text-xs font-medium text-slate tnum">{pct}%</span>
                    </div>
                    <div
                      className="h-2 w-full overflow-hidden rounded-full bg-mist"
                      role="progressbar"
                      aria-valuenow={pct}
                      aria-valuemin={0}
                      aria-valuemax={100}
                    >
                      <div
                        className={`h-full rounded-full transition-[width] duration-150 ${caf.agotado ? "bg-danger" : "bg-cobalt"}`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>

                  <dl className="mt-5 grid grid-cols-2 gap-4 border-t border-line pt-4">
                    <div>
                      <dt className="text-xs text-slate-soft">Folio actual</dt>
                      <dd className="mt-0.5 text-sm font-medium text-ink tnum">
                        {formatNumero(caf.folioActual)}
                      </dd>
                    </div>
                    <div className="text-right">
                      <dt className="text-xs text-slate-soft">Disponibles</dt>
                      <dd className={`mt-0.5 text-sm font-semibold tnum ${caf.agotado ? "text-danger" : "text-cobalt"}`}>
                        {formatNumero(caf.foliosDisponibles)}
                      </dd>
                    </div>
                  </dl>

                  {caf.fechaVencimiento && (
                    <p className="mt-4 text-xs text-slate-soft tnum">
                      Vence el {formatFecha(caf.fechaVencimiento)}
                    </p>
                  )}
                </Card>
              );
            })}
          </div>
        )}
      </div>

      <Modal
        open={abierto}
        onClose={() => setAbierto(false)}
        title="Cargar CAF"
        footer={
          <>
            <Button variant="secondary" onClick={() => setAbierto(false)} disabled={guardando}>Cancelar</Button>
            <Button onClick={guardar} disabled={guardando}>
              {guardando ? "Cargando…" : "Cargar"}
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {error && <Alert>{error}</Alert>}
          <Field label="Tipo de documento">
            <Select
              value={form.tipoDte}
              onChange={(e) => setForm((f) => ({ ...f, tipoDte: e.target.value as TipoDte }))}
            >
              {TIPOS.map((t) => (
                <option key={t} value={t}>{TIPO_DTE_LABEL[t]}</option>
              ))}
            </Select>
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Folio desde">
              <Input
                type="number" min={1} className="tnum"
                value={form.folioDesde}
                onChange={(e) => setForm((f) => ({ ...f, folioDesde: e.target.value }))}
              />
            </Field>
            <Field label="Folio hasta">
              <Input
                type="number" min={1} className="tnum"
                value={form.folioHasta}
                onChange={(e) => setForm((f) => ({ ...f, folioHasta: e.target.value }))}
              />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Fecha autorización" hint="Opcional">
              <Input
                type="date"
                value={form.fechaAutorizacion}
                onChange={(e) => setForm((f) => ({ ...f, fechaAutorizacion: e.target.value }))}
              />
            </Field>
            <Field label="Fecha vencimiento" hint="Opcional">
              <Input
                type="date"
                value={form.fechaVencimiento}
                onChange={(e) => setForm((f) => ({ ...f, fechaVencimiento: e.target.value }))}
              />
            </Field>
          </div>
        </div>
      </Modal>
    </AppShell>
  );
}
