import { useEffect } from "react";
import type { ReactNode } from "react";
import { SiteNav } from "./SiteNav";
import { SiteFooter } from "./SiteFooter";

/**
 * Layout de página pública (fuera de /app): nav + cabecera + contenido + footer.
 * Restaura el scroll al inicio (react-router conserva la posición al navegar
 * desde el footer) y fija el título del documento.
 */
export function SitePage({ titulo, descripcion, ancho = "narrow", children }: {
  titulo: string;
  descripcion?: string;
  ancho?: "narrow" | "wide";
  children: ReactNode;
}) {
  useEffect(() => {
    window.scrollTo(0, 0);
    document.title = `${titulo} · Nexo Factura`;
    return () => {
      document.title = "Nexo Factura";
    };
  }, [titulo]);

  return (
    <div className="bg-white">
      <SiteNav />
      <main className={`mx-auto px-5 py-16 lg:py-20 ${ancho === "narrow" ? "max-w-3xl" : "max-w-6xl"}`}>
        <header>
          <h1 className="font-display text-3xl font-semibold text-ink sm:text-4xl">{titulo}</h1>
          {descripcion && (
            <p className="mt-4 max-w-2xl text-lg leading-relaxed text-slate">{descripcion}</p>
          )}
        </header>
        <div className="mt-10">{children}</div>
      </main>
      <SiteFooter />
    </div>
  );
}

/** Sección de texto legal/informativo: título de bloque + párrafos. */
export function ProseSection({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="mt-10 first:mt-0">
      <h2 className="font-display text-xl font-semibold text-ink">{titulo}</h2>
      <div className="mt-3 space-y-3 text-base leading-relaxed text-slate">{children}</div>
    </section>
  );
}
