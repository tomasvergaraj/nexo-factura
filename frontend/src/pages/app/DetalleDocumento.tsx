import { useEffect, useState, type ReactNode } from "react";
import { useParams, Link } from "react-router-dom";
import {
  ArrowLeft, Receipt, Send, RefreshCw, FileDown, FileWarning,
} from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import { Card, Button, Badge, EmptyState, LoadingState, Alert, Th } from "../../components/ui";
import { StatusBadge } from "../../components/StatusBadge";
import {
  getDocumento, emitirDocumento, enviarDocumento, consultarEstadoSii, descargarPdf, mensajeError,
} from "../../lib/api";
import { empresaIdActual, obtenerUsuario } from "../../lib/auth";
import { formatCLP, formatFecha, formatRut } from "../../lib/format";
import {
  ESTADO_LABEL, TIPO_DTE_LABEL, TIPO_REFERENCIA_LABEL,
  type DocumentoResponse,
} from "../../lib/types";

type Accion = "emitir" | "enviar" | "estado" | "pdf";

const ROLES_EMISION = ["ADMIN", "EMISOR"];

export function DetalleDocumento() {
  const { id } = useParams<{ id: string }>();
  const docId = Number(id);

  const [doc, setDoc] = useState<DocumentoResponse | null>(null);
  const [cargando, setCargando] = useState(true);
  const [noEncontrado, setNoEncontrado] = useState(false);
  const [ocupado, setOcupado] = useState<Accion | null>(null);
  const [error, setError] = useState<string | null>(null);

  const usuario = obtenerUsuario();
  const puedeEmitir = !!usuario && ROLES_EMISION.includes(usuario.rol);

  useEffect(() => {
    let activo = true;
    setCargando(true);
    setNoEncontrado(false);
    getDocumento(empresaIdActual(), docId)
      .then((d) => { if (activo) setDoc(d); })
      .catch(() => { if (activo) setNoEncontrado(true); })
      .finally(() => { if (activo) setCargando(false); });
    return () => { activo = false; };
  }, [docId]);

  async function ejecutar(accion: Accion, fn: () => Promise<DocumentoResponse>) {
    setOcupado(accion);
    setError(null);
    try {
      const actualizado = await fn();
      setDoc(actualizado);
    } catch (e) {
      setError(mensajeError(e, "No se pudo completar la acción."));
    } finally {
      setOcupado(null);
    }
  }

  async function verPdf() {
    setOcupado("pdf");
    setError(null);
    try {
      const blob = await descargarPdf(empresaIdActual(), docId);
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener");
      // Liberar la URL tras dar tiempo a que el navegador abra la pestaña.
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e) {
      setError(mensajeError(e, "No se pudo generar el PDF."));
    } finally {
      setOcupado(null);
    }
  }

  if (cargando) {
    return (
      <AppShell titulo="Documento">
        <LoadingState mensaje="Cargando documento…" />
      </AppShell>
    );
  }

  if (noEncontrado || !doc) {
    return (
      <AppShell titulo="Documento">
        <Card>
          <EmptyState
            icon={<FileWarning size={24} />}
            titulo="Documento no encontrado"
            descripcion="El documento no existe o no pertenece a tu empresa."
            accion={<Link to="/app/documentos"><Button variant="secondary"><ArrowLeft size={16} /> Volver</Button></Link>}
          />
        </Card>
      </AppShell>
    );
  }

  const acciones: ReactNode[] = [];
  if (puedeEmitir && doc.estado === "BORRADOR") {
    acciones.push(
      <Button key="emitir" onClick={() => ejecutar("emitir", () => emitirDocumento(empresaIdActual(), docId))} disabled={ocupado !== null}>
        {ocupado === "emitir" ? "Emitiendo…" : <><Receipt size={16} /> Emitir</>}
      </Button>,
    );
  }
  if (puedeEmitir && doc.estado === "FIRMADO") {
    acciones.push(
      <Button key="enviar" onClick={() => ejecutar("enviar", () => enviarDocumento(empresaIdActual(), docId))} disabled={ocupado !== null}>
        {ocupado === "enviar" ? "Enviando…" : <><Send size={16} /> Enviar al SII</>}
      </Button>,
    );
  }
  if (puedeEmitir && (doc.estado === "ENVIADO" || doc.estado === "REPARO")) {
    acciones.push(
      <Button key="estado" variant="secondary" onClick={() => ejecutar("estado", () => consultarEstadoSii(empresaIdActual(), docId))} disabled={ocupado !== null}>
        {ocupado === "estado" ? "Consultando…" : <><RefreshCw size={16} /> Actualizar estado SII</>}
      </Button>,
    );
  }
  if (doc.folio != null) {
    acciones.push(
      <Button key="pdf" variant="secondary" onClick={verPdf} disabled={ocupado !== null}>
        {ocupado === "pdf" ? "Generando…" : <><FileDown size={16} /> Ver PDF</>}
      </Button>,
    );
  }

  return (
    <AppShell titulo="Documento">
      <div className="space-y-6">
        <Link to="/app/documentos" className="inline-flex items-center gap-1.5 text-sm font-medium text-slate transition-colors hover:text-ink">
          <ArrowLeft size={16} /> Volver a documentos
        </Link>

        {error && <Alert>{error}</Alert>}

        {/* Cabecera */}
        <Card className="p-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <div className="flex flex-wrap items-center gap-2.5">
                <h2 className="font-display text-xl font-semibold text-ink">{TIPO_DTE_LABEL[doc.tipoDte]}</h2>
                <Badge>código {doc.codigoTipo}</Badge>
                <StatusBadge estado={doc.estado} />
              </div>
              <p className="mt-1.5 text-sm text-slate tnum">
                Folio {doc.folio ?? "sin asignar"} · Emisión {formatFecha(doc.fechaEmision)}
              </p>
              {doc.trackId && (
                <p className="mt-1 text-xs text-slate-soft tnum">Track ID SII: {doc.trackId}</p>
              )}
              {doc.sello && (
                <p className="mt-1 break-all text-xs text-slate-soft tnum" title="SHA-256 del XML firmado">
                  Sello: {doc.sello}
                </p>
              )}
            </div>
            {acciones.length > 0 && (
              <div className="flex flex-wrap gap-2">{acciones}</div>
            )}
          </div>

          <div className="mt-6 grid gap-5 border-t border-line pt-6 sm:grid-cols-2">
            <div>
              <h3 className="text-xs font-medium uppercase tracking-wide text-slate-soft">Receptor</h3>
              <p className="mt-1.5 font-medium text-ink">{doc.receptorRazonSocial}</p>
              <p className="text-sm text-slate tnum">{formatRut(doc.receptorRut)}</p>
            </div>
            {doc.observacion && (
              <div>
                <h3 className="text-xs font-medium uppercase tracking-wide text-slate-soft">Observación</h3>
                <p className="mt-1.5 text-sm text-slate">{doc.observacion}</p>
              </div>
            )}
          </div>
        </Card>

        {/* Referencias */}
        {doc.referencias.length > 0 && (
          <Card className="p-6">
            <h3 className="font-display text-base font-semibold text-ink">Referencias</h3>
            <div className="mt-4 space-y-2">
              {doc.referencias.map((r, i) => (
                <div key={i} className="flex flex-wrap items-center gap-2 rounded-md border border-line bg-mist/40 px-3 py-2 text-sm">
                  <Badge tone="cobalt">{TIPO_REFERENCIA_LABEL[r.tipoReferencia]} ({r.codigoReferencia})</Badge>
                  <span className="text-ink tnum">Doc. {r.tipoDocumentoRef} · Folio {r.folioRef}</span>
                  <span className="text-slate-soft tnum">{formatFecha(r.fechaRef)}</span>
                  {r.razon && <span className="text-slate">— {r.razon}</span>}
                </div>
              ))}
            </div>
          </Card>
        )}

        {/* Líneas */}
        <Card className="overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-line">
                <Th>Detalle</Th>
                <Th align="right">Cant.</Th>
                <Th align="right">Precio</Th>
                <Th align="right">Importe</Th>
              </tr>
            </thead>
            <tbody>
              {doc.lineas.map((l) => (
                <tr key={l.numeroLinea} className="border-b border-line last:border-0 transition-colors hover:bg-mist/60">
                  <td className="px-4 py-3.5 text-ink">
                    {l.nombre}
                    {!l.afecto && <span className="ml-2 text-xs text-slate-soft">(exento)</span>}
                    {l.codImpAdic != null && (
                      <span className="ml-2 text-xs text-slate-soft">· imp. {l.codImpAdic}</span>
                    )}
                  </td>
                  <td className="px-4 py-3.5 text-right text-slate tnum">{l.cantidad} {l.unidad}</td>
                  <td className="px-4 py-3.5 text-right text-slate tnum">{formatCLP(l.precioUnitario)}</td>
                  <td className="px-4 py-3.5 text-right font-medium text-ink tnum">{formatCLP(l.montoLinea)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="flex justify-end border-t border-line px-4 py-5">
            <div className="w-64 space-y-2 text-sm">
              <Total label="Neto" valor={formatCLP(doc.neto)} />
              {doc.exento > 0 && <Total label="Exento" valor={formatCLP(doc.exento)} />}
              <Total label={`IVA ${doc.tasaIva}%`} valor={formatCLP(doc.iva)} />
              {doc.impuestos.map((imp) =>
                imp.esRetencion ? (
                  <Total key={imp.codigo} label="IVA retenido" valor={`-${formatCLP(imp.monto)}`} />
                ) : (
                  <Total key={imp.codigo} label={`Imp. adicional ${imp.codigo} (${imp.tasa}%)`} valor={formatCLP(imp.monto)} />
                ),
              )}
              <div className="flex items-center justify-between border-t border-line pt-2.5">
                <span className="font-semibold text-ink">Total</span>
                <span className="font-display text-lg font-semibold text-cobalt tnum">{formatCLP(doc.total)}</span>
              </div>
            </div>
          </div>
        </Card>

        <p className="text-center text-xs text-slate-soft">
          Estado actual: {ESTADO_LABEL[doc.estado]}.
        </p>
      </div>
    </AppShell>
  );
}

function Total({ label, valor }: { label: string; valor: string }) {
  return (
    <div className="flex items-center justify-between text-slate">
      <span>{label}</span>
      <span className="text-ink tnum">{valor}</span>
    </div>
  );
}
