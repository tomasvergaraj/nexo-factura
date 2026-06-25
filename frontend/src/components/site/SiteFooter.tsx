import { Logo } from "../Logo";

export function SiteFooter() {
  return (
    <footer className="border-t border-line bg-mist">
      <div className="mx-auto max-w-6xl px-5 py-14">
        <div className="grid gap-10 md:grid-cols-[1.5fr_1fr_1fr_1fr]">
          <div>
            <Logo size={30} />
            <p className="mt-4 max-w-xs text-sm leading-relaxed text-slate">
              Facturación electrónica conforme al SII, pensada para PYMEs y equipos
              que quieren emitir sin fricción.
            </p>
          </div>
          <FooterCol titulo="Producto" items={["Emisión de DTE", "Folios y CAF", "Reportes", "API"]} />
          <FooterCol titulo="Empresa" items={["Sobre Nexo", "Clientes", "Blog", "Contacto"]} />
          <FooterCol titulo="Legal" items={["Términos", "Privacidad", "Estado del servicio"]} />
        </div>
        <div className="mt-12 flex flex-col items-start justify-between gap-3 border-t border-line pt-6 text-xs text-slate-soft sm:flex-row sm:items-center">
          <span>© {new Date().getFullYear()} Nexo Software SpA · Quillota, Chile</span>
          <span>Hecho en la V Región</span>
        </div>
      </div>
    </footer>
  );
}

function FooterCol({ titulo, items }: { titulo: string; items: string[] }) {
  return (
    <div>
      <h4 className="mb-3 text-sm font-semibold text-ink">{titulo}</h4>
      <ul className="space-y-2 text-sm text-slate">
        {items.map((i) => (
          <li key={i}><a href="/#" className="hover:text-cobalt">{i}</a></li>
        ))}
      </ul>
    </div>
  );
}
