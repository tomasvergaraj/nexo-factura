import { type ReactNode } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import {
  LayoutDashboard, FileText, Users, Package, Hash, ClipboardList, Settings, LogOut, Plus,
} from "lucide-react";
import { Logo } from "../Logo";
import { Button } from "../ui";
import { obtenerUsuario } from "../../lib/auth";
import { cerrarSesion } from "../../lib/api";

const NAV = [
  { to: "/app", label: "Resumen", icon: LayoutDashboard, exact: true },
  { to: "/app/documentos", label: "Documentos", icon: FileText },
  { to: "/app/clientes", label: "Clientes", icon: Users },
  { to: "/app/productos", label: "Productos", icon: Package },
  { to: "/app/folios", label: "Folios (CAF)", icon: Hash },
  { to: "/app/rcof", label: "Consumo de folios", icon: ClipboardList },
  { to: "/app/configuracion", label: "Configuración", icon: Settings },
];

export function AppShell({ children, titulo }: { children: ReactNode; titulo: string }) {
  const location = useLocation();
  const navigate = useNavigate();
  const usuario = obtenerUsuario();

  const activo = (to: string, exact?: boolean) =>
    exact ? location.pathname === to : location.pathname.startsWith(to);

  return (
    <div className="flex min-h-screen bg-canvas">
      {/* Sidebar */}
      <aside className="fixed inset-y-0 left-0 hidden w-60 flex-col border-r border-line bg-white lg:flex">
        <div className="flex h-16 items-center border-b border-line px-5">
          <Link to="/app" aria-label="Ir al resumen"><Logo size={28} /></Link>
        </div>
        <nav className="flex-1 space-y-0.5 p-3">
          {NAV.map(({ to, label, icon: Icon, exact }) => {
            const seleccionado = activo(to, exact);
            return (
              <Link
                key={to}
                to={to}
                aria-current={seleccionado ? "page" : undefined}
                className={`flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors duration-150 ${
                  seleccionado
                    ? "bg-cobalt-soft text-cobalt"
                    : "text-slate hover:bg-mist hover:text-ink"
                }`}
              >
                <Icon size={18} strokeWidth={2} className={seleccionado ? "text-cobalt" : "text-slate-soft"} />
                {label}
              </Link>
            );
          })}
        </nav>
        <div className="border-t border-line p-3">
          <button
            onClick={() => { cerrarSesion(); navigate("/ingresar"); }}
            className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-slate transition-colors duration-150 hover:bg-danger-soft hover:text-danger"
          >
            <LogOut size={18} className="text-slate-soft" /> Cerrar sesión
          </button>
        </div>
      </aside>

      {/* Contenido */}
      <div className="flex-1 lg:pl-60">
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-line bg-white/90 px-6 backdrop-blur">
          <h1 className="font-display text-lg font-semibold text-ink">{titulo}</h1>
          <div className="flex items-center gap-3 sm:gap-4">
            <Link to="/app/nueva-factura">
              <Button size="sm"><Plus size={16} /> Nueva factura</Button>
            </Link>
            <div className="hidden h-6 w-px bg-line sm:block" aria-hidden="true" />
            <div className="flex items-center gap-2.5">
              <div className="flex h-9 w-9 items-center justify-center rounded-full bg-cobalt text-sm font-semibold text-white">
                {usuario?.nombre?.[0] ?? "N"}
              </div>
              <div className="hidden text-sm leading-tight sm:block">
                <div className="font-medium text-ink">{usuario?.nombre ?? "Administrador"}</div>
                <div className="text-xs text-slate-soft">Nexo Software SpA</div>
              </div>
            </div>
          </div>
        </header>
        <main className="mx-auto max-w-6xl px-6 py-8">{children}</main>
      </div>
    </div>
  );
}
