import { useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { AlertCircle, ArrowLeft, ArrowRight, Building2, Check, UserRound } from "lucide-react";
import { Logo } from "../components/Logo";
import { Alert, Button, Field, Input } from "../components/ui";
import { FacturaPreview } from "../components/FacturaPreview";
import { crearEmpresa, erroresDeCampo, mensajeError, refrescarSesion, registrarCuenta } from "../lib/api";
import { obtenerUsuario } from "../lib/auth";
import { validarRut } from "../lib/format";

type Paso = "cuenta" | "empresa";

/**
 * Alta de una cuenta nueva en dos pasos: usuario administrador y luego la
 * empresa emisora. Si el usuario recarga con la cuenta ya creada (sesión sin
 * empresa asociada), se retoma directo en el paso de la empresa.
 */
export function Registro() {
  const navigate = useNavigate();
  const usuario = obtenerUsuario();

  const [paso, setPaso] = useState<Paso>(
    usuario && usuario.empresaId == null ? "empresa" : "cuenta",
  );
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [errores, setErrores] = useState<Record<string, string>>({});

  // Paso 1 — cuenta
  const [nombre, setNombre] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  // Paso 2 — empresa emisora
  const [rut, setRut] = useState("");
  const [razonSocial, setRazonSocial] = useState("");
  const [giro, setGiro] = useState("");
  const [direccion, setDireccion] = useState("");
  const [comuna, setComuna] = useState("");

  // Sesión completa: no hay nada que registrar aquí.
  if (usuario && usuario.empresaId != null) {
    return <Navigate to="/app" replace />;
  }

  async function crearCuenta() {
    setCargando(true);
    setError(null);
    setErrores({});
    try {
      await registrarCuenta({ nombre: nombre.trim(), email: email.trim(), password });
      setPaso("empresa");
    } catch (e) {
      setErrores(erroresDeCampo(e));
      setError(mensajeError(e, "No se pudo crear la cuenta."));
    } finally {
      setCargando(false);
    }
  }

  async function crearEmisor() {
    if (!validarRut(rut)) {
      setErrores({ rut: "El RUT no es válido." });
      return;
    }
    setCargando(true);
    setError(null);
    setErrores({});
    try {
      await crearEmpresa({
        rut: rut.trim(),
        razonSocial: razonSocial.trim(),
        giro: giro.trim(),
        direccion: direccion.trim(),
        comuna: comuna.trim(),
      });
      // El JWT aún trae empresaId=null: se re-emite para tomar el claim nuevo.
      await refrescarSesion();
      navigate("/app");
    } catch (e) {
      setErrores(erroresDeCampo(e));
      setError(mensajeError(e, "No se pudo registrar la empresa."));
    } finally {
      setCargando(false);
    }
  }

  return (
    <div className="grid min-h-screen bg-canvas lg:grid-cols-2">
      {/* Panel del formulario */}
      <div className="flex flex-col px-6 py-8 sm:px-12">
        <Link
          to="/"
          className="inline-flex items-center gap-1.5 text-sm font-medium text-slate transition-colors hover:text-ink"
        >
          <ArrowLeft className="h-4 w-4" /> Volver al inicio
        </Link>

        <div className="flex flex-1 items-center justify-center py-10">
          <div className="w-full max-w-sm">
            <Logo size={34} />

            {/* Indicador de pasos */}
            <div className="mt-8 flex items-center gap-2 text-xs font-medium">
              <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 ${
                paso === "cuenta" ? "bg-cobalt-soft text-cobalt" : "bg-success-soft text-success"
              }`}>
                {paso === "cuenta" ? <UserRound size={13} /> : <Check size={13} />} 1 · Tu cuenta
              </span>
              <span className="h-px w-4 bg-line" aria-hidden="true" />
              <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 ${
                paso === "empresa" ? "bg-cobalt-soft text-cobalt" : "bg-mist text-slate-soft"
              }`}>
                <Building2 size={13} /> 2 · Tu empresa
              </span>
            </div>

            {paso === "cuenta" ? (
              <>
                <h1 className="mt-6 font-display text-2xl font-semibold text-ink">Crea tu cuenta</h1>
                <p className="mt-2 text-sm text-slate">
                  14 días gratis, sin tarjeta. Primero tus datos, luego los de tu empresa.
                </p>
                <div className="mt-8 space-y-5">
                  <Field label="Nombre" error={errores.nombre}>
                    <Input
                      value={nombre}
                      onChange={(e) => setNombre(e.target.value)}
                      placeholder="Nombre y apellido"
                      autoComplete="name"
                    />
                  </Field>
                  <Field label="Correo" error={errores.email}>
                    <Input
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="tu@empresa.cl"
                      autoComplete="email"
                    />
                  </Field>
                  <Field label="Contraseña" hint="Mínimo 8 caracteres." error={errores.password}>
                    <Input
                      type="password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      onKeyDown={(e) => e.key === "Enter" && crearCuenta()}
                      autoComplete="new-password"
                    />
                  </Field>

                  {error && (
                    <Alert tone="danger" icon={<AlertCircle className="h-4 w-4" />}>{error}</Alert>
                  )}

                  <Button size="lg" className="w-full" onClick={crearCuenta} disabled={cargando}>
                    {cargando ? "Creando cuenta…" : <>Continuar <ArrowRight size={17} /></>}
                  </Button>
                </div>
                <p className="mt-6 text-sm text-slate">
                  ¿Ya tienes cuenta?{" "}
                  <Link to="/ingresar" className="font-medium text-cobalt transition-colors hover:text-cobalt-dark">
                    Ingresar
                  </Link>
                </p>
              </>
            ) : (
              <>
                <h1 className="mt-6 font-display text-2xl font-semibold text-ink">Datos de tu empresa</h1>
                <p className="mt-2 text-sm text-slate">
                  Los datos del emisor tal como figuran en el SII. Podrás
                  completar el resto en Configuración.
                </p>
                <div className="mt-8 space-y-5">
                  <Field label="RUT de la empresa" error={errores.rut}>
                    <Input
                      value={rut}
                      onChange={(e) => setRut(e.target.value)}
                      placeholder="76.543.210-K"
                      className="tnum"
                    />
                  </Field>
                  <Field label="Razón social" error={errores.razonSocial}>
                    <Input
                      value={razonSocial}
                      onChange={(e) => setRazonSocial(e.target.value)}
                      placeholder="Mi Empresa SpA"
                    />
                  </Field>
                  <Field label="Giro" error={errores.giro}>
                    <Input
                      value={giro}
                      onChange={(e) => setGiro(e.target.value)}
                      placeholder="Ej: Servicios informáticos"
                    />
                  </Field>
                  <div className="grid grid-cols-[1.2fr_1fr] gap-3">
                    <Field label="Dirección" error={errores.direccion}>
                      <Input
                        value={direccion}
                        onChange={(e) => setDireccion(e.target.value)}
                        placeholder="Calle y número"
                      />
                    </Field>
                    <Field label="Comuna" error={errores.comuna}>
                      <Input
                        value={comuna}
                        onChange={(e) => setComuna(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && crearEmisor()}
                        placeholder="Ej: Quillota"
                      />
                    </Field>
                  </div>

                  {error && (
                    <Alert tone="danger" icon={<AlertCircle className="h-4 w-4" />}>{error}</Alert>
                  )}

                  <Button size="lg" className="w-full" onClick={crearEmisor} disabled={cargando}>
                    {cargando ? "Creando empresa…" : "Entrar al panel"}
                  </Button>
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Panel ilustrado */}
      <div className="relative hidden items-center justify-center overflow-hidden border-l border-line bg-mist p-12 lg:flex">
        <div className="relative w-full max-w-md">
          <FacturaPreview className="shadow-lg" />
          <p className="mt-7 text-center text-sm text-slate">
            En unos minutos estarás emitiendo documentos con timbre y folio.
          </p>
        </div>
      </div>
    </div>
  );
}
