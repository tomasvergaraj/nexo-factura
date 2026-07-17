import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { AlertTriangle, CheckCircle2, RefreshCw } from "lucide-react";
import { SitePage } from "../../components/site/SitePage";
import { Badge, Button, Card, Spinner } from "../../components/ui";
import { comprobarSalud } from "../../lib/api";

type EstadoApi = "verificando" | "operativo" | "incidente";

const INTERVALO_MS = 30_000;

export function Estado() {
  const [estado, setEstado] = useState<EstadoApi>("verificando");
  const [ultimaVerificacion, setUltimaVerificacion] = useState<Date | null>(null);
  const [comprobando, setComprobando] = useState(false);
  // Evita chequeos solapados (el tick no debe pisar el resultado del botón) y
  // descarta cualquier resultado que llegue después de desmontar la página.
  const enVuelo = useRef(false);
  const activo = useRef(true);

  const comprobar = useCallback(async () => {
    if (enVuelo.current) return;
    enVuelo.current = true;
    setComprobando(true);
    const ok = await comprobarSalud();
    enVuelo.current = false;
    if (!activo.current) return;
    setEstado(ok ? "operativo" : "incidente");
    setUltimaVerificacion(new Date());
    setComprobando(false);
  }, []);

  useEffect(() => {
    activo.current = true;
    void comprobar();
    const timer = setInterval(() => void comprobar(), INTERVALO_MS);
    return () => {
      activo.current = false;
      clearInterval(timer);
    };
  }, [comprobar]);

  return (
    <SitePage
      titulo="Estado del servicio"
      descripcion="Estado en vivo de la plataforma. Esta página consulta directamente nuestros servidores y se actualiza cada 30 segundos."
    >
      <BannerGeneral estado={estado} />

      <Card className="mt-6 overflow-hidden">
        <FilaComponente
          nombre="API y emisión de DTE"
          descripcion="Autenticación, emisión, folios, libros y reportes."
          estado={estado}
        />
        <FilaComponente
          nombre="Portal web"
          descripcion="Este sitio y el panel de la aplicación."
          estado="operativo"
        />
      </Card>

      <div className="mt-6 flex flex-wrap items-center gap-4">
        <Button variant="secondary" size="sm" onClick={() => void comprobar()} disabled={comprobando}>
          <RefreshCw size={15} className={comprobando ? "animate-spin" : ""} />
          Volver a comprobar
        </Button>
        {ultimaVerificacion && (
          <span className="text-sm text-slate-soft tnum">
            Última verificación: {ultimaVerificacion.toLocaleTimeString("es-CL")}
          </span>
        )}
      </div>

      <p className="mt-10 text-sm leading-relaxed text-slate">
        La emisión depende además de la disponibilidad del propio SII. Si el SII
        está en contingencia, tus documentos quedan en cola de contingencia y
        los reenvías con un clic desde tu panel cuando el SII se restablece.
        ¿Ves un problema que aquí aparece como resuelto?{" "}
        <Link to="/contacto" className="font-medium text-cobalt transition-colors hover:text-cobalt-dark">
          Avísanos
        </Link>.
      </p>
    </SitePage>
  );
}

function BannerGeneral({ estado }: { estado: EstadoApi }) {
  if (estado === "verificando") {
    return (
      <Card className="flex items-center gap-3 p-5">
        <Spinner />
        <span className="text-sm font-medium text-slate">Verificando el estado de la plataforma…</span>
      </Card>
    );
  }
  if (estado === "operativo") {
    return (
      <div className="flex items-center gap-3 rounded-lg border border-success/30 bg-success-soft p-5">
        <CheckCircle2 size={20} className="shrink-0 text-success" />
        <span className="text-sm font-semibold text-success">Todos los sistemas operativos</span>
      </div>
    );
  }
  return (
    <div className="flex items-start gap-3 rounded-lg border border-danger/30 bg-danger-soft p-5">
      <AlertTriangle size={20} className="mt-0.5 shrink-0 text-danger" />
      <div>
        <p className="text-sm font-semibold text-danger">No pudimos contactar la plataforma</p>
        <p className="mt-1 text-sm text-danger">
          Puede ser un problema de nuestros servidores o de tu conexión. Estamos
          atentos: si persiste, escríbenos por WhatsApp.
        </p>
      </div>
    </div>
  );
}

function FilaComponente({ nombre, descripcion, estado }: {
  nombre: string;
  descripcion: string;
  estado: EstadoApi;
}) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-line px-5 py-4 last:border-b-0">
      <div>
        <p className="text-sm font-medium text-ink">{nombre}</p>
        <p className="mt-0.5 text-sm text-slate-soft">{descripcion}</p>
      </div>
      {estado === "verificando" ? (
        <Badge tone="neutral">Verificando…</Badge>
      ) : estado === "operativo" ? (
        <Badge tone="success">Operativo</Badge>
      ) : (
        <Badge tone="danger">Con problemas</Badge>
      )}
    </div>
  );
}
