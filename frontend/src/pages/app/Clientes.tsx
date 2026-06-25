import { useEffect, useMemo, useState } from "react";
import { Plus, Search, Users, Pencil } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import {
  Card, Input, Button, Field, Modal, EmptyState,
  PageHeader, LoadingState, Th, Alert, IconButton,
} from "../../components/ui";
import {
  crearCliente, editarCliente, erroresDeCampo, getClientes, mensajeError,
} from "../../lib/api";
import { empresaIdActual } from "../../lib/auth";
import { formatRut, validarRut } from "../../lib/format";
import type { Cliente, ClienteRequest } from "../../lib/types";

const VACIO: ClienteRequest = {
  rut: "", razonSocial: "", giro: "", direccion: "", comuna: "", ciudad: "", email: "",
};

export function Clientes() {
  const [clientes, setClientes] = useState<Cliente[] | null>(null);
  const [busqueda, setBusqueda] = useState("");
  const [abierto, setAbierto] = useState(false);
  const [editando, setEditando] = useState<Cliente | null>(null);
  const [form, setForm] = useState<ClienteRequest>(VACIO);
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  function cargar() {
    getClientes(empresaIdActual()).then(setClientes);
  }
  useEffect(cargar, []);

  const visibles = useMemo(() => {
    if (!clientes) return [];
    const q = busqueda.trim().toLowerCase();
    if (!q) return clientes;
    return clientes.filter(
      (c) => c.razonSocial.toLowerCase().includes(q) || c.rut.toLowerCase().includes(q),
    );
  }, [clientes, busqueda]);

  function abrirNuevo() {
    setEditando(null);
    setForm(VACIO);
    setErrores({});
    setErrorGeneral(null);
    setAbierto(true);
  }

  function abrirEdicion(c: Cliente) {
    setEditando(c);
    setForm({
      rut: c.rut,
      razonSocial: c.razonSocial,
      giro: c.giro ?? "",
      direccion: "",
      comuna: c.comuna ?? "",
      ciudad: "",
      email: c.email ?? "",
    });
    setErrores({});
    setErrorGeneral(null);
    setAbierto(true);
  }

  function set<K extends keyof ClienteRequest>(campo: K, valor: ClienteRequest[K]) {
    setForm((prev) => ({ ...prev, [campo]: valor }));
  }

  async function guardar() {
    setErrores({});
    setErrorGeneral(null);

    if (!form.rut.trim() || !validarRut(form.rut)) {
      setErrores({ rut: "RUT invalido: digito verificador incorrecto" });
      return;
    }
    if (!form.razonSocial.trim()) {
      setErrores({ razonSocial: "La razón social es obligatoria." });
      return;
    }

    setGuardando(true);
    try {
      const empresaId = empresaIdActual();
      if (editando) {
        await editarCliente(empresaId, editando.id, form);
      } else {
        await crearCliente(empresaId, form);
      }
      setAbierto(false);
      cargar();
    } catch (error) {
      const campos = erroresDeCampo(error);
      if (Object.keys(campos).length > 0) {
        setErrores(campos);
      } else {
        setErrorGeneral(mensajeError(error, "No se pudo guardar el cliente."));
      }
    } finally {
      setGuardando(false);
    }
  }

  return (
    <AppShell titulo="Clientes">
      <div className="space-y-6">
        <PageHeader
          titulo="Clientes"
          descripcion="Administra los receptores de tus documentos electrónicos."
          accion={<Button onClick={abrirNuevo}><Plus className="h-4 w-4" /> Nuevo cliente</Button>}
        />

        <div className="relative w-full sm:max-w-xs">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-soft" />
          <Input
            className="pl-9"
            placeholder="Buscar por nombre o RUT…"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            aria-label="Buscar clientes"
          />
        </div>

        <Card className="overflow-hidden">
          {!clientes ? (
            <LoadingState mensaje="Cargando clientes…" />
          ) : visibles.length === 0 ? (
            <EmptyState
              icon={<Users className="h-6 w-6" />}
              titulo={busqueda ? "Sin coincidencias" : "Aún no hay clientes"}
              descripcion={busqueda
                ? "Ningún cliente coincide con la búsqueda."
                : "Crea tu primer cliente para empezar a emitir documentos."}
              accion={!busqueda && <Button onClick={abrirNuevo}><Plus className="h-4 w-4" /> Nuevo cliente</Button>}
            />
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-line">
                  <Th>RUT</Th>
                  <Th>Razón social</Th>
                  <Th>Comuna</Th>
                  <Th>Email</Th>
                  <Th align="right"><span className="sr-only">Acciones</span></Th>
                </tr>
              </thead>
              <tbody>
                {visibles.map((c) => (
                  <tr
                    key={c.id}
                    className="border-b border-line last:border-0 transition-colors hover:bg-mist/60"
                  >
                    <td className="px-4 py-3.5 font-medium text-ink tnum">{formatRut(c.rut)}</td>
                    <td className="px-4 py-3.5 text-ink">{c.razonSocial}</td>
                    <td className="px-4 py-3.5 text-slate">{c.comuna ?? "—"}</td>
                    <td className="px-4 py-3.5 text-slate">{c.email ?? "—"}</td>
                    <td className="px-4 py-3.5 text-right">
                      <IconButton
                        onClick={() => abrirEdicion(c)}
                        className="border border-line hover:border-cobalt hover:bg-cobalt-soft hover:text-cobalt"
                        aria-label={`Editar ${c.razonSocial}`}
                      >
                        <Pencil className="h-4 w-4" />
                      </IconButton>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      </div>

      <Modal
        open={abierto}
        onClose={() => setAbierto(false)}
        title={editando ? "Editar cliente" : "Nuevo cliente"}
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
            <Field label="RUT" error={errores.rut} hint="Con dígito verificador.">
              <Input
                value={form.rut}
                placeholder="76.543.210-9"
                onChange={(e) => set("rut", e.target.value)}
              />
            </Field>
            <Field label="Email" error={errores.email}>
              <Input
                type="email"
                value={form.email}
                placeholder="contacto@empresa.cl"
                onChange={(e) => set("email", e.target.value)}
              />
            </Field>
          </div>
          <Field label="Razón social" error={errores.razonSocial}>
            <Input value={form.razonSocial} onChange={(e) => set("razonSocial", e.target.value)} />
          </Field>
          <Field label="Giro" error={errores.giro}>
            <Input value={form.giro} onChange={(e) => set("giro", e.target.value)} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Comuna" error={errores.comuna}>
              <Input value={form.comuna} onChange={(e) => set("comuna", e.target.value)} />
            </Field>
            <Field label="Ciudad" error={errores.ciudad}>
              <Input value={form.ciudad} onChange={(e) => set("ciudad", e.target.value)} />
            </Field>
          </div>
        </div>
      </Modal>
    </AppShell>
  );
}
