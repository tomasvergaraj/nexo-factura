import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowLeft, AlertCircle, ShieldCheck } from "lucide-react";
import { Logo } from "../components/Logo";
import { Alert, Button, Field, Input } from "../components/ui";
import { FacturaPreview } from "../components/FacturaPreview";
import axios from "axios";
import http, { activarModoDemo, USE_MOCK } from "../lib/api";
import { guardarSesion } from "../lib/auth";

export function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("admin@nexofactura.cl");
  const [password, setPassword] = useState("nexo1234");
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function entrar() {
    setCargando(true);
    setError(null);
    try {
      if (USE_MOCK) {
        guardarSesion("demo-token", "demo-refresh", {
          id: 1, nombre: "Administrador Demo", email, rol: "ADMIN", empresaId: 1,
        });
        navigate("/app");
        return;
      }
      // Integración real: POST /api/auth/login -> { token, refreshToken, usuario }
      const { data } = await http.post("/auth/login", { email, password });
      guardarSesion(data.token, data.refreshToken, data.usuario);
      navigate("/app");
    } catch (e) {
      // El 401 de credenciales se maneja aquí (mensaje local) para que el
      // interceptor de respuesta no dispare la redirección/cierre de sesión.
      if (axios.isAxiosError(e) && e.response?.status === 401) {
        setError("Credenciales inválidas");
      } else if (axios.isAxiosError(e) && e.response?.status === 429) {
        setError("Demasiados intentos. Espera unos minutos e inténtalo de nuevo.");
      } else {
        setError(e instanceof Error ? e.message : "No se pudo iniciar sesión");
      }
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
            <h1 className="mt-8 font-display text-2xl font-semibold text-ink">
              Ingresa a tu cuenta
            </h1>
            <p className="mt-2 text-sm text-slate">
              Administra y emite tus documentos tributarios.
            </p>

            <div className="mt-8 space-y-5">
              <Field label="Correo">
                <Input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="tu@empresa.cl"
                />
              </Field>
              <Field label="Contraseña">
                <Input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && entrar()}
                />
              </Field>

              {error && (
                <Alert tone="danger" icon={<AlertCircle className="h-4 w-4" />}>
                  {error}
                </Alert>
              )}

              <Button size="lg" className="w-full" onClick={entrar} disabled={cargando}>
                {cargando ? "Entrando…" : "Entrar"}
              </Button>
            </div>

            <p className="mt-6 text-sm text-slate">
              ¿Aún no tienes cuenta?{" "}
              <Link to="/registro" className="font-medium text-cobalt transition-colors hover:text-cobalt-dark">
                Crear cuenta
              </Link>
            </p>

            <div className="mt-6 flex items-start gap-3 rounded-lg border border-line bg-mist px-4 py-3">
              <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-cobalt-soft text-cobalt">
                <ShieldCheck className="h-4 w-4" />
              </span>
              <div className="flex-1 text-xs text-slate">
                <p className="font-medium text-ink-soft">¿Solo quieres mirar?</p>
                <p className="mt-0.5">
                  Recorre el panel con datos de ejemplo, sin cuenta.{" "}
                  <button
                    onClick={activarModoDemo}
                    className="rounded-full font-medium text-cobalt transition-colors hover:text-cobalt-dark"
                  >
                    Entrar a la demo →
                  </button>
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Panel ilustrado */}
      <div className="relative hidden items-center justify-center overflow-hidden border-l border-line bg-mist p-12 lg:flex">
        <div className="relative w-full max-w-md">
          <FacturaPreview className="shadow-lg" />
          <p className="mt-7 text-center text-sm text-slate">
            Cada documento timbrado, firmado y enviado al SII desde un solo lugar.
          </p>
        </div>
      </div>
    </div>
  );
}
