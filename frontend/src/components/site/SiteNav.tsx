import { Link } from "react-router-dom";
import { Logo } from "../Logo";
import { Button } from "../ui";

const ENLACES = [
  { to: "/#producto", label: "Producto" },
  { to: "/#como-funciona", label: "Cómo funciona" },
  { to: "/#precios", label: "Precios" },
  { to: "/#preguntas", label: "Preguntas" },
];

export function SiteNav() {
  return (
    <header className="sticky top-0 z-40 border-b border-line bg-white/80 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5">
        <Link to="/" className="rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cobalt/40 focus-visible:ring-offset-2">
          <Logo size={30} />
        </Link>
        <nav className="hidden items-center gap-1 md:flex">
          {ENLACES.map((e) => (
            <Link
              key={e.to}
              to={e.to}
              className="rounded-full px-3.5 py-2 text-sm font-medium text-slate transition-colors hover:bg-mist hover:text-ink"
            >
              {e.label}
            </Link>
          ))}
        </nav>
        <div className="flex items-center gap-2">
          <Link to="/ingresar" className="hidden sm:block">
            <Button variant="ghost" size="sm">Ingresar</Button>
          </Link>
          <Link to="/ingresar">
            <Button size="sm">Probar gratis</Button>
          </Link>
        </div>
      </div>
    </header>
  );
}
