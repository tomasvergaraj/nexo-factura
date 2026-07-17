import { useEffect, useState } from "react";
import { Building2, Check, Info, ShieldCheck } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import {
  Alert, Button, Card, Field, Input, LoadingState, PageHeader,
} from "../../components/ui";
import { actualizarEmpresa, erroresDeCampo, getEmpresa, mensajeError } from "../../lib/api";
import { empresaIdActual, obtenerUsuario } from "../../lib/auth";
import { MENSAJE_RUT_INVALIDO, validarRut } from "../../lib/format";
import type { Empresa, EmpresaRequest } from "../../lib/types";

// Estado del formulario: todos los campos como texto (incluida la actividad
// económica, que es un código numérico); se convierte al construir el payload.
type FormEmpresa = {
  rut: string;
  razonSocial: string;
  giro: string;
  actividadEconomica: string;
  direccion: string;
  comuna: string;
  ciudad: string;
  telefono: string;
  email: string;
};

function aFormulario(e: Empresa): FormEmpresa {
  return {
    rut: e.rut,
    razonSocial: e.razonSocial,
    giro: e.giro,
    actividadEconomica: e.actividadEconomica?.toString() ?? "",
    direccion: e.direccion,
    comuna: e.comuna,
    ciudad: e.ciudad ?? "",
    telefono: e.telefono ?? "",
    email: e.email ?? "",
  };
}

export function Configuracion() {
  const esAdmin = obtenerUsuario()?.rol === "ADMIN";

  const [form, setForm] = useState<FormEmpresa | null>(null);
  const [cargaError, setCargaError] = useState<string | null>(null);
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [exito, setExito] = useState(false);

  useEffect(() => {
    let activo = true;
    getEmpresa(empresaIdActual())
      .then((e) => activo && setForm(aFormulario(e)))
      .catch((error) => activo && setCargaError(mensajeError(error, "No se pudieron cargar los datos de la empresa.")));
    return () => {
      activo = false;
    };
  }, []);

  function set<K extends keyof FormEmpresa>(campo: K, valor: FormEmpresa[K]) {
    setForm((prev) => (prev ? { ...prev, [campo]: valor } : prev));
    setErrores((prev) => (prev[campo] ? { ...prev, [campo]: "" } : prev));
    setExito(false);
    setErrorGeneral(null);
  }

  async function guardar() {
    if (!form) return;
    setErrores({});
    setErrorGeneral(null);
    setExito(false);

    const nuevos: Record<string, string> = {};
    if (!form.rut.trim() || !validarRut(form.rut)) nuevos.rut = MENSAJE_RUT_INVALIDO;
    if (!form.razonSocial.trim()) nuevos.razonSocial = "La razón social es obligatoria.";
    if (!form.giro.trim()) nuevos.giro = "El giro es obligatorio.";
    if (!form.direccion.trim()) nuevos.direccion = "La dirección es obligatoria.";
    if (!form.comuna.trim()) nuevos.comuna = "La comuna es obligatoria.";
    if (form.actividadEconomica.trim() && !/^\d+$/.test(form.actividadEconomica.trim())) {
      nuevos.actividadEconomica = "Debe ser el código numérico de actividad económica.";
    }
    if (Object.keys(nuevos).length > 0) {
      setErrores(nuevos);
      return;
    }

    const payload: EmpresaRequest = {
      rut: form.rut.trim(),
      razonSocial: form.razonSocial.trim(),
      giro: form.giro.trim(),
      actividadEconomica: form.actividadEconomica.trim() ? Number(form.actividadEconomica) : null,
      direccion: form.direccion.trim(),
      comuna: form.comuna.trim(),
      ciudad: form.ciudad.trim() || undefined,
      telefono: form.telefono.trim() || undefined,
      email: form.email.trim() || undefined,
    };

    setGuardando(true);
    try {
      const actualizada = await actualizarEmpresa(empresaIdActual(), payload);
      setForm(aFormulario(actualizada));
      setExito(true);
    } catch (error) {
      const campos = erroresDeCampo(error);
      if (Object.keys(campos).length > 0) {
        setErrores(campos);
      } else {
        setErrorGeneral(mensajeError(error, "No se pudieron guardar los cambios."));
      }
    } finally {
      setGuardando(false);
    }
  }

  return (
    <AppShell titulo="Configuración">
      <div className="space-y-6">
        <PageHeader
          titulo="Datos de la empresa"
          descripcion="Información del emisor que aparece en los documentos que emites."
        />

        {cargaError ? (
          <Alert>{cargaError}</Alert>
        ) : !form ? (
          <Card>
            <LoadingState mensaje="Cargando datos de la empresa…" />
          </Card>
        ) : (
          <Card className="p-6 sm:p-8">
            <div className="space-y-5">
              {!esAdmin && (
                <Alert tone="info" icon={<Info size={16} />}>
                  Solo un usuario administrador puede modificar estos datos. Los ves en modo lectura.
                </Alert>
              )}
              {errorGeneral && <Alert>{errorGeneral}</Alert>}
              {exito && (
                <Alert tone="success" icon={<Check size={16} />}>
                  Datos de la empresa actualizados.
                </Alert>
              )}

              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="RUT" error={errores.rut} hint="Con dígito verificador.">
                  <Input
                    value={form.rut}
                    placeholder="76.543.210-9"
                    disabled={!esAdmin}
                    onChange={(e) => set("rut", e.target.value)}
                  />
                </Field>
                <Field label="Actividad económica" error={errores.actividadEconomica} hint="Código del giro ante el SII.">
                  <Input
                    inputMode="numeric"
                    value={form.actividadEconomica}
                    placeholder="620200"
                    disabled={!esAdmin}
                    onChange={(e) => set("actividadEconomica", e.target.value)}
                  />
                </Field>
              </div>

              <Field label="Razón social" error={errores.razonSocial}>
                <Input value={form.razonSocial} disabled={!esAdmin} onChange={(e) => set("razonSocial", e.target.value)} />
              </Field>

              <Field label="Giro" error={errores.giro}>
                <Input value={form.giro} disabled={!esAdmin} onChange={(e) => set("giro", e.target.value)} />
              </Field>

              <Field label="Dirección" error={errores.direccion}>
                <Input value={form.direccion} disabled={!esAdmin} onChange={(e) => set("direccion", e.target.value)} />
              </Field>

              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Comuna" error={errores.comuna}>
                  <Input value={form.comuna} disabled={!esAdmin} onChange={(e) => set("comuna", e.target.value)} />
                </Field>
                <Field label="Ciudad" error={errores.ciudad}>
                  <Input value={form.ciudad} disabled={!esAdmin} onChange={(e) => set("ciudad", e.target.value)} />
                </Field>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Teléfono" error={errores.telefono}>
                  <Input value={form.telefono} disabled={!esAdmin} placeholder="+56 9 1234 5678" onChange={(e) => set("telefono", e.target.value)} />
                </Field>
                <Field label="Email" error={errores.email}>
                  <Input type="email" value={form.email} disabled={!esAdmin} placeholder="contacto@empresa.cl" onChange={(e) => set("email", e.target.value)} />
                </Field>
              </div>
            </div>

            {esAdmin && (
              <div className="mt-8 flex justify-end border-t border-line pt-6">
                <Button onClick={guardar} disabled={guardando}>
                  {guardando ? "Guardando…" : "Guardar cambios"}
                </Button>
              </div>
            )}
          </Card>
        )}

        <Card className="flex items-start gap-4 p-6">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-cobalt-soft text-cobalt">
            <ShieldCheck size={22} strokeWidth={2} />
          </span>
          <div>
            <h2 className="flex items-center gap-2 font-display text-base font-semibold text-ink">
              <Building2 size={16} className="text-slate-soft" /> Certificado digital y folios (CAF)
            </h2>
            <p className="mt-1.5 text-sm leading-relaxed text-slate">
              El certificado digital del representante legal y los Códigos de
              Autorización de Folios habilitan la firma y numeración de tus DTE.
              Los folios se administran en la sección <span className="font-medium text-ink-soft">Folios (CAF)</span>.
            </p>
          </div>
        </Card>
      </div>
    </AppShell>
  );
}
