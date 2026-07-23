import { useEffect, useState } from "react";
import { Check, FileUp, KeyRound, ShieldCheck, ShieldAlert, Trash2 } from "lucide-react";
import { Alert, Badge, Button, Card, Field, Input, LoadingState, Modal } from "../../components/ui";
import { eliminarCertificado, getCertificado, mensajeError, subirCertificado } from "../../lib/api";
import { empresaIdActual } from "../../lib/auth";
import { formatFecha } from "../../lib/format";
import type { CertificadoResponse } from "../../lib/types";

/**
 * Certificado digital (PKCS#12) de firma electrónica de la empresa. Muestra el
 * estado del certificado activo (firmante, vigencia, aviso de vencimiento) y,
 * para ADMIN, permite subir uno nuevo o quitar el vigente. El archivo y su clave
 * nunca vuelven del backend: solo se ve metadata.
 */
export function CertificadoCard({ esAdmin }: { esAdmin: boolean }) {
  const [cert, setCert] = useState<CertificadoResponse | null>(null);
  const [cargando, setCargando] = useState(true);
  const [cargaError, setCargaError] = useState<string | null>(null);

  const [modal, setModal] = useState(false);
  const [archivo, setArchivo] = useState<File | null>(null);
  const [password, setPassword] = useState("");
  const [rutFirmante, setRutFirmante] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [exito, setExito] = useState(false);
  const [confirmarBaja, setConfirmarBaja] = useState(false);

  function cargar() {
    setCargando(true);
    getCertificado(empresaIdActual())
      .then((c) => { setCert(c); setCargaError(null); })
      .catch((e) => setCargaError(mensajeError(e, "No se pudo consultar el certificado.")))
      .finally(() => setCargando(false));
  }
  useEffect(cargar, []);

  function abrir() {
    setArchivo(null);
    setPassword("");
    setRutFirmante("");
    setError(null);
    setModal(true);
  }

  async function subir() {
    setError(null);
    if (!archivo) {
      setError("Selecciona el archivo del certificado (.p12 o .pfx).");
      return;
    }
    if (!password) {
      setError("Ingresa la clave del certificado.");
      return;
    }
    setGuardando(true);
    try {
      const nuevo = await subirCertificado(empresaIdActual(), archivo, password, rutFirmante || undefined);
      setCert(nuevo);
      setModal(false);
      setExito(true);
    } catch (e) {
      setError(mensajeError(e, "No se pudo cargar el certificado."));
    } finally {
      setGuardando(false);
    }
  }

  async function eliminar() {
    setGuardando(true);
    try {
      await eliminarCertificado(empresaIdActual());
      setCert(null);
      setConfirmarBaja(false);
      setExito(false);
    } catch (e) {
      setCargaError(mensajeError(e, "No se pudo quitar el certificado."));
    } finally {
      setGuardando(false);
    }
  }

  // Aviso de vencimiento próximo (< 30 días) o ya vencido.
  const porVencer = cert != null && cert.vigente && cert.diasParaVencer <= 30;
  const vencido = cert != null && !cert.vigente;

  return (
    <Card className="p-6 sm:p-8">
      <div className="flex items-start gap-4">
        <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full ${
          vencido ? "bg-danger/10 text-danger" : "bg-cobalt-soft text-cobalt"}`}>
          {vencido ? <ShieldAlert size={22} strokeWidth={2} /> : <ShieldCheck size={22} strokeWidth={2} />}
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="font-display text-base font-semibold text-ink">Certificado digital de firma</h2>
          <p className="mt-1.5 text-sm leading-relaxed text-slate">
            El PKCS#12 (.p12/.pfx) del representante legal con que se firman tus DTE.
            Se guarda cifrado; el archivo y su clave nunca salen del servidor.
          </p>

          <div className="mt-5">
            {cargando ? (
              <LoadingState mensaje="Consultando certificado…" />
            ) : cargaError ? (
              <Alert>{cargaError}</Alert>
            ) : cert ? (
              <div className="space-y-4">
                {exito && (
                  <Alert tone="success" icon={<Check size={16} />}>Certificado actualizado.</Alert>
                )}
                {vencido && <Alert>El certificado está vencido: sube uno vigente para poder emitir.</Alert>}
                {porVencer && (
                  <Alert tone="warn">
                    El certificado vence en {cert.diasParaVencer} día{cert.diasParaVencer === 1 ? "" : "s"}
                    {" "}({formatFecha(cert.validoHasta)}). Renuévalo pronto.
                  </Alert>
                )}
                <dl className="grid gap-4 rounded-lg bg-mist px-4 py-3 sm:grid-cols-2">
                  <Dato etiqueta="Firmante (RUN)" valor={cert.rutFirmante} />
                  <Dato etiqueta="Estado" valor={
                    <Badge tone={vencido ? "danger" : porVencer ? "warn" : "success"}>
                      {vencido ? "Vencido" : porVencer ? "Por vencer" : "Vigente"}
                    </Badge>
                  } />
                  <Dato etiqueta="Válido desde" valor={formatFecha(cert.validoDesde)} />
                  <Dato etiqueta="Válido hasta" valor={formatFecha(cert.validoHasta)} />
                  {cert.subject && <Dato etiqueta="Titular" valor={cert.subject} className="sm:col-span-2" />}
                  <Dato etiqueta="Archivo" valor={cert.nombreArchivo} className="sm:col-span-2" />
                </dl>
                {esAdmin && (
                  <div className="flex flex-wrap gap-3">
                    <Button variant="secondary" size="sm" onClick={abrir}>
                      <FileUp className="h-4 w-4" /> Reemplazar
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setConfirmarBaja(true)}>
                      <Trash2 className="h-4 w-4" /> Quitar
                    </Button>
                  </div>
                )}
              </div>
            ) : (
              <div className="rounded-lg border border-dashed border-line px-4 py-6 text-center">
                <KeyRound className="mx-auto h-6 w-6 text-slate-soft" />
                <p className="mt-2 text-sm text-slate">
                  {esAdmin
                    ? "Aún no hay certificado cargado. Súbelo para poder firmar y enviar tus DTE."
                    : "Aún no hay certificado cargado. Un administrador debe subirlo."}
                </p>
                {esAdmin && (
                  <Button className="mt-4" size="sm" onClick={abrir}>
                    <FileUp className="h-4 w-4" /> Cargar certificado
                  </Button>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      <Modal
        open={modal}
        onClose={() => setModal(false)}
        title={cert ? "Reemplazar certificado" : "Cargar certificado"}
        footer={
          <>
            <Button variant="secondary" onClick={() => setModal(false)} disabled={guardando}>Cancelar</Button>
            <Button onClick={subir} disabled={guardando}>{guardando ? "Cargando…" : "Cargar"}</Button>
          </>
        }
      >
        <div className="space-y-4">
          {error && <Alert>{error}</Alert>}
          <p className="text-sm text-slate">
            Sube el archivo PKCS#12 (.p12 o .pfx) del certificado digital y su clave.
            Se valida y cifra en el servidor; el archivo original no se almacena en claro.
          </p>
          <Field label="Archivo del certificado">
            <label className="flex cursor-pointer items-center gap-2 rounded-lg border border-dashed border-line px-4 py-3 text-sm text-slate hover:border-cobalt hover:text-cobalt">
              <FileUp className="h-4 w-4" />
              <span className="truncate">{archivo ? archivo.name : "Seleccionar archivo .p12 / .pfx…"}</span>
              <input
                type="file"
                accept=".p12,.pfx,application/x-pkcs12"
                className="hidden"
                onChange={(e) => { setArchivo(e.target.files?.[0] ?? null); setError(null); }}
              />
            </label>
          </Field>
          <Field label="Clave del certificado">
            <Input
              type="password"
              value={password}
              autoComplete="off"
              onChange={(e) => { setPassword(e.target.value); setError(null); }}
              placeholder="Clave del PKCS#12"
            />
          </Field>
          <Field
            label="RUN del firmante (opcional)"
            hint="Por defecto se extrae del certificado; complétalo solo si no lo trae."
          >
            <Input
              value={rutFirmante}
              onChange={(e) => setRutFirmante(e.target.value)}
              placeholder="11111111-1"
            />
          </Field>
        </div>
      </Modal>

      <Modal
        open={confirmarBaja}
        onClose={() => setConfirmarBaja(false)}
        title="Quitar certificado"
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmarBaja(false)} disabled={guardando}>Cancelar</Button>
            <Button variant="danger" onClick={eliminar} disabled={guardando}>
              {guardando ? "Quitando…" : "Quitar certificado"}
            </Button>
          </>
        }
      >
        <p className="text-sm text-slate">
          La empresa quedará sin certificado activo y no podrá firmar ni enviar DTE
          hasta cargar uno nuevo. ¿Continuar?
        </p>
      </Modal>
    </Card>
  );
}

function Dato({ etiqueta, valor, className = "" }: {
  etiqueta: string; valor: React.ReactNode; className?: string;
}) {
  return (
    <div className={className}>
      <dt className="text-xs text-slate-soft">{etiqueta}</dt>
      <dd className="mt-0.5 text-sm font-medium text-ink">{valor}</dd>
    </div>
  );
}
