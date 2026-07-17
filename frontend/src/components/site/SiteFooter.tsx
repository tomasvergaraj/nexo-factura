import { Link } from "react-router-dom";
import { Logo } from "../Logo";

type FooterLink = { label: string; to: string };

// Todo enlace se navega con <Link> (client-side). Las anclas ("/#seccion")
// también: react-router actualiza la ruta y el efecto de hash en la Landing
// hace el scroll, sin recargar la página desde las subpáginas del sitio.
const COLUMNAS: { titulo: string; links: FooterLink[] }[] = [
  {
    titulo: "Producto",
    links: [
      { label: "Emisión de DTE", to: "/#emision-de-dte" },
      { label: "Folios y CAF", to: "/#folios-y-caf" },
      { label: "Reportes", to: "/#reportes" },
      { label: "API", to: "/#api" },
      { label: "Precios", to: "/#precios" },
    ],
  },
  {
    titulo: "Empresa",
    links: [
      { label: "Sobre Nexo", to: "/sobre" },
      { label: "Contacto", to: "/contacto" },
      { label: "Preguntas frecuentes", to: "/#preguntas" },
    ],
  },
  {
    titulo: "Legal",
    links: [
      { label: "Términos", to: "/terminos" },
      { label: "Privacidad", to: "/privacidad" },
      { label: "Estado del servicio", to: "/estado" },
    ],
  },
];

export function SiteFooter() {
  return (
    <footer className="border-t border-line bg-canvas">
      <div className="mx-auto max-w-6xl px-5 py-16">
        <div className="grid gap-10 md:grid-cols-[1.5fr_1fr_1fr_1fr]">
          <div>
            <Logo size={30} />
            <p className="mt-4 max-w-xs text-sm leading-relaxed text-slate">
              Facturación electrónica conforme al SII, pensada para PYMEs y equipos
              que quieren emitir sin fricción.
            </p>
          </div>
          {COLUMNAS.map((c) => (
            <FooterCol key={c.titulo} titulo={c.titulo} links={c.links} />
          ))}
        </div>
        <div className="mt-12 flex flex-col items-start justify-between gap-3 border-t border-line pt-6 text-xs text-slate-soft sm:flex-row sm:items-center">
          <span>© {new Date().getFullYear()} Nexo Software SpA · Quillota, Chile</span>
          <span>Hecho en la V Región</span>
        </div>
      </div>
    </footer>
  );
}

function FooterCol({ titulo, links }: { titulo: string; links: FooterLink[] }) {
  return (
    <div>
      <h4 className="mb-3 text-xs font-medium uppercase tracking-wide text-slate-soft">{titulo}</h4>
      <ul className="space-y-2.5 text-sm text-slate">
        {links.map((l) => (
          <li key={l.label}>
            <Link to={l.to} className="transition-colors hover:text-cobalt">{l.label}</Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
