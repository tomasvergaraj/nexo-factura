import { useEffect, useState } from "react";
import { Check, Hash, Info } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import {
  Alert, Button, Card, Field, Input, LoadingState, PageHeader,
} from "../../components/ui";
import { actualizarEmpresa, erroresDeCampo, getEmpresa, mensajeError } from "../../lib/api";
import { empresaIdActual, obtenerUsuario } from "../../lib/auth";
import { MENSAJE_RUT_INVALIDO, validarRut } from "../../lib/format";
import type { Empresa, EmpresaRequest } from "../../lib/types";
import { CertificadoCard } from "./CertificadoCard";

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
  fchResol: string;
  nroResol: string;
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
    fchResol: e.fchResol ?? "",
    nroResol: e.nroResol?.toString() ?? "",
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
    // Resolución SII: fecha y número van juntos (ambos o ninguno).
    const tieneFch = !!form.fchResol.trim();
    const tieneNro = !!form.nroResol.trim();
    if (tieneNro && !/^\d+$/.test(form.nroResol.trim())) {
      nuevos.nroResol = "Debe ser un número entero (0 en certificación).";
    }
    if (tieneFch !== tieneNro) {
      const campo = tieneFch ? "nroResol" : "fchResol";
      nuevos[campo] = "Completa la fecha y el número de resolución, o deja ambos vacíos.";
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
      fchResol: tieneFch ? form.fchResol.trim() : null,
      nroResol: tieneNro ? Number(form.nroResol) : null,
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

              <div className="border-t border-line pt-5">
                <h2 className="font-display text-sm font-semibold text-ink">Resolución SII</h2>
                <p className="mt-1 text-xs leading-relaxed text-slate">
                  Número y fecha de la resolución que te autoriza como emisor electrónico. Van en la
                  carátula de los envíos y en la leyenda del timbre. En certificación el número es 0.
                  Déjalos vacíos para usar el valor por defecto del ambiente.
                </p>
                <div className="mt-4 grid gap-4 sm:grid-cols-2">
                  <Field label="Fecha de resolución" error={errores.fchResol}>
                    <Input
                      type="date"
                      value={form.fchResol}
                      disabled={!esAdmin}
                      onChange={(e) => set("fchResol", e.target.value)}
                    />
                  </Field>
                  <Field label="Número de resolución" error={errores.nroResol}>
                    <Input
                      inputMode="numeric"
                      value={form.nroResol}
                      disabled={!esAdmin}
                      placeholder="0"
                      onChange={(e) => set("nroResol", e.target.value)}
                    />
                  </Field>
                </div>
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

        <CertificadoCard esAdmin={esAdmin} />

        <Card className="flex items-start gap-4 p-6">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-cobalt-soft text-cobalt">
            <Hash size={22} strokeWidth={2} />
          </span>
          <div>
            <h2 className="font-display text-base font-semibold text-ink">Folios (CAF)</h2>
            <p className="mt-1.5 text-sm leading-relaxed text-slate">
              Los Códigos de Autorización de Folios numeran tus DTE y se administran
              en la sección <span className="font-medium text-ink-soft">Folios (CAF)</span>.
            </p>
          </div>
        </Card>
      </div>
    </AppShell>
  );
}
