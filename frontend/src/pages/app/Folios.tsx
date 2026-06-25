import { useEffect, useState } from "react";
import { Plus, Hash } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import {
  Card, Spinner, Input, Button, Field, Select, Modal, Badge, EmptyState,
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
      <div className="space-y-5">
        <div className="flex items-center justify-between">
          <p className="text-sm text-slate">
            Códigos de Autorización de Folios entregados por el SII para cada tipo de documento.
          </p>
          <Button onClick={abrirNuevo}><Plus size={16} /> Cargar CAF</Button>
        </div>

        {!folios ? (
          <Card><div className="grid h-64 place-items-center"><Spinner className="h-6 w-6" /></div></Card>
        ) : folios.length === 0 ? (
          <Card>
            <EmptyState
              icon={<Hash size={24} />}
              titulo="Aún no hay folios cargados"
              descripcion="Carga un CAF del SII para poder asignar folios al emitir documentos."
              accion={<Button onClick={abrirNuevo}><Plus size={16} /> Cargar CAF</Button>}
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
                <Card key={caf.id} className="p-5">
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="font-display text-base font-bold text-ink">
                        {TIPO_DTE_LABEL[caf.tipoDte]}
                      </h3>
                      <p className="mt-0.5 text-xs text-slate-soft tnum">
                        Folios {formatNumero(caf.folioDesde)} – {formatNumero(caf.folioHasta)}
                      </p>
                    </div>
                    {caf.agotado
                      ? <Badge tone="danger">Agotado</Badge>
                      : <Badge tone="success">Disponible</Badge>}
                  </div>

                  <div className="mt-4">
                    <div className="h-2 w-full overflow-hidden rounded-full bg-mist">
                      <div
                        className={`h-full rounded-full ${caf.agotado ? "bg-danger" : "bg-cobalt"}`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                    <div className="mt-2 flex items-center justify-between text-xs text-slate">
                      <span className="tnum">Folio actual: {formatNumero(caf.folioActual)}</span>
                      <span className="tnum">{formatNumero(caf.foliosDisponibles)} disponibles</span>
                    </div>
                  </div>

                  {caf.fechaVencimiento && (
                    <p className="mt-3 text-xs text-slate-soft">
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
          {error && (
            <div className="rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger">{error}</div>
          )}
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
