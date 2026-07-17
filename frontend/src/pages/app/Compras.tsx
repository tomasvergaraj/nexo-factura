import { useEffect, useState } from "react";
import { Plus, ShoppingCart, Trash2 } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import {
  Card, Input, Button, Field, Modal, Select, EmptyState,
  PageHeader, LoadingState, Th, Alert, IconButton,
} from "../../components/ui";
import {
  crearCompra, eliminarCompra, erroresDeCampo, getCompras, mensajeError,
} from "../../lib/api";
import { empresaIdActual } from "../../lib/auth";
import {
  formatCLP, formatFecha, formatRut, hoyIso, MENSAJE_RUT_INVALIDO, mesActual, validarRut,
} from "../../lib/format";
import { nombreTipoDte, TIPOS_COMPRA, type Compra } from "../../lib/types";

const MES_ACTUAL = mesActual(); // YYYY-MM (mes local, no UTC)

interface FormCompra {
  tipoDte: number;
  folio: string;
  rutProveedor: string;
  razonSocial: string;
  fechaEmision: string;
  neto: string;
  exento: string;
  iva: string;
  ivaRetenido: string;
  observacion: string;
}

const VACIO: FormCompra = {
  tipoDte: 33, folio: "", rutProveedor: "", razonSocial: "",
  fechaEmision: hoyIso(),
  neto: "", exento: "", iva: "", ivaRetenido: "", observacion: "",
};

const entero = (v: string) => (v.trim() === "" ? 0 : Number(v));

export function Compras() {
  const [periodo, setPeriodo] = useState(MES_ACTUAL);
  const [compras, setCompras] = useState<Compra[] | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [abierto, setAbierto] = useState(false);
  const [form, setForm] = useState<FormCompra>(VACIO);
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [eliminando, setEliminando] = useState(false);
  // Se incrementa para refrescar la lista tras crear/eliminar SIN vaciar la
  // tabla (el estado solo vuelve a "cargando" al cambiar el período).
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let vigente = true;
    setErrorCarga(null);
    getCompras(empresaIdActual(), periodo)
      .then((c) => { if (vigente) setCompras(c); })
      .catch((e) => { if (vigente) setErrorCarga(mensajeError(e, "No se pudieron cargar las compras.")); });
    return () => { vigente = false; };
  }, [periodo, version]);

  function cambiarPeriodo(mes: string) {
    setCompras(null);
    setPeriodo(mes);
  }

  const totalPeriodo = (compras ?? []).reduce((acc, c) => acc + c.total, 0);
  const totalForm = entero(form.neto) + entero(form.exento) + entero(form.iva) - entero(form.ivaRetenido);

  function abrirNuevo() {
    setForm({ ...VACIO, fechaEmision: periodo === MES_ACTUAL ? hoyIso() : `${periodo}-01` });
    setErrores({});
    setErrorGeneral(null);
    setAbierto(true);
  }

  function set<K extends keyof FormCompra>(campo: K, valor: FormCompra[K]) {
    setForm((prev) => ({ ...prev, [campo]: valor }));
  }

  async function guardar() {
    setErrores({});
    setErrorGeneral(null);

    if (!form.folio.trim() || entero(form.folio) <= 0) {
      setErrores({ folio: "Ingresa el folio del documento." });
      return;
    }
    if (!form.rutProveedor.trim() || !validarRut(form.rutProveedor)) {
      setErrores({ rutProveedor: MENSAJE_RUT_INVALIDO });
      return;
    }
    if (!form.razonSocial.trim()) {
      setErrores({ razonSocial: "La razón social es obligatoria." });
      return;
    }
    if (entero(form.ivaRetenido) > entero(form.iva)) {
      setErrores({ ivaRetenido: "La retención no puede exceder el IVA del documento." });
      return;
    }
    if (totalForm <= 0) {
      setErrores({ neto: "Ingresa los montos del documento." });
      return;
    }

    setGuardando(true);
    try {
      await crearCompra(empresaIdActual(), {
        tipoDte: form.tipoDte,
        folio: entero(form.folio),
        rutProveedor: form.rutProveedor,
        razonSocial: form.razonSocial,
        fechaEmision: form.fechaEmision,
        neto: entero(form.neto),
        exento: entero(form.exento),
        iva: entero(form.iva),
        ivaRetenido: entero(form.ivaRetenido),
        total: totalForm,
        observacion: form.observacion.trim() || undefined,
      });
      setAbierto(false);
      // Si la fecha registrada cae en otro período, cambia el selector para mostrarla.
      const mesDoc = form.fechaEmision.slice(0, 7);
      if (mesDoc !== periodo) cambiarPeriodo(mesDoc);
      else setVersion((v) => v + 1);
    } catch (error) {
      const campos = erroresDeCampo(error);
      if (Object.keys(campos).length > 0) {
        setErrores(campos);
      } else {
        setErrorGeneral(mensajeError(error, "No se pudo registrar la compra."));
      }
    } finally {
      setGuardando(false);
    }
  }

  async function eliminar(c: Compra) {
    if (!window.confirm(`¿Eliminar el documento ${nombreTipoDte(c.tipoDte)} folio ${c.folio} de ${c.razonSocial}?`)) {
      return;
    }
    setEliminando(true);
    setErrorCarga(null);
    try {
      await eliminarCompra(empresaIdActual(), c.id);
      setVersion((v) => v + 1);
    } catch (e) {
      setErrorCarga(mensajeError(e, "No se pudo eliminar la compra."));
    } finally {
      setEliminando(false);
    }
  }

  return (
    <AppShell titulo="Compras">
      <div className="space-y-6">
        <PageHeader
          titulo="Compras"
          descripcion="Registra los documentos recibidos de tus proveedores para el libro de compras."
          accion={<Button onClick={abrirNuevo}><Plus className="h-4 w-4" /> Registrar compra</Button>}
        />

        <Input
          type="month"
          value={periodo}
          max={MES_ACTUAL}
          onChange={(e) => cambiarPeriodo(e.target.value)}
          className="w-44"
          aria-label="Período de compras"
        />

        {errorCarga && <Alert>{errorCarga}</Alert>}

        <Card className="overflow-hidden">
          {!compras ? (
            <LoadingState mensaje="Cargando compras…" />
          ) : compras.length === 0 ? (
            <EmptyState
              icon={<ShoppingCart className="h-6 w-6" />}
              titulo="Sin compras registradas"
              descripcion="No hay documentos recibidos en este período. Registra la primera compra del mes."
              accion={<Button onClick={abrirNuevo}><Plus className="h-4 w-4" /> Registrar compra</Button>}
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-line">
                    <Th>Tipo</Th>
                    <Th align="right">Folio</Th>
                    <Th>Proveedor</Th>
                    <Th>Fecha</Th>
                    <Th align="right">Neto</Th>
                    <Th align="right">Exento</Th>
                    <Th align="right">IVA</Th>
                    <Th align="right">IVA ret.</Th>
                    <Th align="right">Total</Th>
                    <Th align="right"><span className="sr-only">Acciones</span></Th>
                  </tr>
                </thead>
                <tbody>
                  {compras.map((c) => (
                    <tr key={c.id} className="border-b border-line last:border-0 transition-colors hover:bg-mist/60">
                      <td className="px-4 py-3.5 text-ink">
                        {nombreTipoDte(c.tipoDte)}
                        <span className="ml-1 text-slate-soft tnum">({c.tipoDte})</span>
                      </td>
                      <td className="px-4 py-3.5 text-right font-medium text-ink tnum">{c.folio}</td>
                      <td className="px-4 py-3.5 text-ink">
                        {c.razonSocial}
                        <span className="ml-2 text-xs text-slate-soft tnum">{formatRut(c.rutProveedor)}</span>
                      </td>
                      <td className="px-4 py-3.5 text-slate tnum">{formatFecha(c.fechaEmision)}</td>
                      <td className="px-4 py-3.5 text-right text-slate tnum">{formatCLP(c.neto)}</td>
                      <td className="px-4 py-3.5 text-right text-slate tnum">{formatCLP(c.exento)}</td>
                      <td className="px-4 py-3.5 text-right text-slate tnum">{formatCLP(c.iva)}</td>
                      <td className="px-4 py-3.5 text-right text-slate tnum">{c.ivaRetenido > 0 ? `-${formatCLP(c.ivaRetenido)}` : "—"}</td>
                      <td className="px-4 py-3.5 text-right font-medium text-ink tnum">{formatCLP(c.total)}</td>
                      <td className="px-4 py-3.5 text-right">
                        <IconButton
                          onClick={() => eliminar(c)}
                          disabled={eliminando}
                          className="border border-line hover:border-danger hover:bg-danger-soft hover:text-danger"
                          aria-label={`Eliminar folio ${c.folio}`}
                        >
                          <Trash2 className="h-4 w-4" />
                        </IconButton>
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="border-t border-line bg-mist/40">
                    <td className="px-4 py-3.5 font-semibold text-ink" colSpan={8}>
                      Total del período ({compras.length} {compras.length === 1 ? "documento" : "documentos"})
                    </td>
                    <td className="px-4 py-3.5 text-right font-semibold text-cobalt tnum">{formatCLP(totalPeriodo)}</td>
                    <td />
                  </tr>
                </tfoot>
              </table>
            </div>
          )}
        </Card>
      </div>

      <Modal
        open={abierto}
        onClose={() => setAbierto(false)}
        title="Registrar compra"
        footer={
          <>
            <Button variant="secondary" onClick={() => setAbierto(false)} disabled={guardando}>Cancelar</Button>
            <Button onClick={guardar} disabled={guardando}>
              {guardando ? "Guardando…" : "Guardar"}
            </Button>
          </>
        }
      >
        <div className="space-y-5">
          {errorGeneral && <Alert>{errorGeneral}</Alert>}
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Tipo de documento" error={errores.tipoDte}>
              <Select value={form.tipoDte} onChange={(e) => set("tipoDte", Number(e.target.value))}>
                {TIPOS_COMPRA.map((t) => (
                  <option key={t.codigo} value={t.codigo}>{t.label}</option>
                ))}
              </Select>
            </Field>
            <Field label="Folio" error={errores.folio}>
              <Input
                type="number"
                min={1}
                value={form.folio}
                onChange={(e) => set("folio", e.target.value)}
              />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="RUT del proveedor" error={errores.rutProveedor} hint="Con dígito verificador.">
              <Input
                value={form.rutProveedor}
                placeholder="76.543.210-9"
                onChange={(e) => set("rutProveedor", e.target.value)}
              />
            </Field>
            <Field label="Fecha de emisión" error={errores.fechaEmision}>
              <Input
                type="date"
                value={form.fechaEmision}
                onChange={(e) => set("fechaEmision", e.target.value)}
              />
            </Field>
          </div>
          <Field label="Razón social del proveedor" error={errores.razonSocial}>
            <Input value={form.razonSocial} onChange={(e) => set("razonSocial", e.target.value)} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Neto" error={errores.neto}>
              <Input type="number" min={0} value={form.neto} placeholder="0" onChange={(e) => set("neto", e.target.value)} />
            </Field>
            <Field label="Exento" error={errores.exento}>
              <Input type="number" min={0} value={form.exento} placeholder="0" onChange={(e) => set("exento", e.target.value)} />
            </Field>
            <Field label="IVA" error={errores.iva}>
              <Input type="number" min={0} value={form.iva} placeholder="0" onChange={(e) => set("iva", e.target.value)} />
            </Field>
            <Field
              label="IVA retenido"
              error={errores.ivaRetenido}
              hint="Solo si retienes el IVA (cambio de sujeto, factura de compra 46)."
            >
              <Input type="number" min={0} value={form.ivaRetenido} placeholder="0" onChange={(e) => set("ivaRetenido", e.target.value)} />
            </Field>
          </div>
          <div className="flex items-center justify-between rounded-md border border-line bg-mist/40 px-4 py-3 text-sm">
            <span className="font-medium text-ink">Total del documento</span>
            <span className="font-display font-semibold text-cobalt tnum">{formatCLP(totalForm)}</span>
          </div>
          <Field label="Observación" error={errores.observacion}>
            <Input value={form.observacion} onChange={(e) => set("observacion", e.target.value)} />
          </Field>
        </div>
      </Modal>
    </AppShell>
  );
}
