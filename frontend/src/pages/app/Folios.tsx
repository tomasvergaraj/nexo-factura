import { useEffect, useState } from "react";
import { Plus, Hash, FileUp } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import {
  Card, Button, Field, Modal, Badge, EmptyState,
  PageHeader, LoadingState, Alert,
} from "../../components/ui";
import { cargarCaf, getFolios, mensajeError } from "../../lib/api";
import { empresaIdActual } from "../../lib/auth";
import { camposCaf } from "../../lib/caf";
import { formatFecha, formatNumero } from "../../lib/format";
import { TIPO_DTE_LABEL, TIPO_DTE_POR_CODIGO, type Caf } from "../../lib/types";

/**
 * Vista previa derivada del XML del CAF: suficiente para que el usuario
 * confirme que subió el archivo correcto (el parseo vive en lib/caf).
 */
function previewCaf(xml: string) {
  const campos = camposCaf(xml);
  if (!campos?.td || !campos.desde || !campos.hasta) return null;
  return {
    tipo: TIPO_DTE_POR_CODIGO[campos.td]
      ? TIPO_DTE_LABEL[TIPO_DTE_POR_CODIGO[campos.td]]
      : `Tipo ${campos.td}`,
    rango: `${campos.desde} – ${campos.hasta}`,
    re: campos.re ?? "—",
    fa: campos.fa ?? "—",
  };
}

export function Folios() {
  const [folios, setFolios] = useState<Caf[] | null>(null);
  const [abierto, setAbierto] = useState(false);
  const [xmlCaf, setXmlCaf] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  function cargar() {
    getFolios(empresaIdActual()).then(setFolios);
  }
  useEffect(cargar, []);

  function abrirNuevo() {
    setXmlCaf("");
    setError(null);
    setAbierto(true);
  }

  async function leerArchivo(file: File | undefined) {
    if (!file) return;
    // Los CAF del SII vienen en ISO-8859-1; TextDecoder lo maneja explícito.
    const buffer = await file.arrayBuffer();
    setXmlCaf(new TextDecoder("iso-8859-1").decode(buffer));
    setError(null);
  }

  const preview = xmlCaf.trim() ? previewCaf(xmlCaf) : null;

  async function guardar() {
    setError(null);
    if (!xmlCaf.trim()) {
      setError("Sube o pega el XML del CAF entregado por el SII.");
      return;
    }
    setGuardando(true);
    try {
      await cargarCaf(empresaIdActual(), { xmlCaf });
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
          <p className="text-sm text-slate">
            Sube el archivo XML del CAF tal como lo entrega el SII (Timbraje
            electrónico). El tipo de documento, el rango de folios y las fechas
            se leen del propio archivo.
          </p>
          <Field label="Archivo del CAF">
            <label className="flex cursor-pointer items-center gap-2 rounded-lg border border-dashed border-line px-4 py-3 text-sm text-slate hover:border-cobalt hover:text-cobalt">
              <FileUp className="h-4 w-4" />
              <span>Seleccionar archivo XML…</span>
              <input
                type="file"
                accept=".xml,text/xml"
                className="hidden"
                onChange={(e) => leerArchivo(e.target.files?.[0])}
              />
            </label>
          </Field>
          <Field label="O pega el XML" hint="Contenido completo del archivo AUTORIZACION">
            <textarea
              className="h-32 w-full rounded-lg border border-line bg-white px-3 py-2 font-mono text-xs text-ink outline-none focus:border-cobalt"
              value={xmlCaf}
              onChange={(e) => { setXmlCaf(e.target.value); setError(null); }}
              placeholder="<AUTORIZACION><CAF version=&quot;1.0&quot;>…"
              spellCheck={false}
            />
          </Field>
          {preview && (
            <div className="rounded-lg bg-mist px-4 py-3 text-sm">
              <p className="font-medium text-ink">{preview.tipo}</p>
              <p className="mt-1 text-xs text-slate tnum">
                Folios {preview.rango} · Emisor {preview.re} · Autorizado el {preview.fa}
              </p>
            </div>
          )}
          {xmlCaf.trim() && !preview && (
            <p className="text-xs text-warn">
              El contenido no parece un CAF del SII (falta AUTORIZACION/CAF); el
              backend lo validará al cargar.
            </p>
          )}
        </div>
      </Modal>
    </AppShell>
  );
}
