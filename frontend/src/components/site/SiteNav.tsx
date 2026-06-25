import { Link } from "react-router-dom";
import { Logo } from "../Logo";
import { Button } from "../ui";

export function SiteNav() {
  return (
    <header className="sticky top-0 z-40 border-b border-line/80 bg-white/80 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5">
        <Link to="/"><Logo size={30} /></Link>
        <nav className="hidden items-center gap-8 text-sm font-medium text-slate md:flex">
          <a href="/#producto" className="hover:text-ink">Producto</a>
          <a href="/#como-funciona" className="hover:text-ink">Cómo funciona</a>
          <a href="/#precios" className="hover:text-ink">Precios</a>
          <a href="/#preguntas" className="hover:text-ink">Preguntas</a>
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
