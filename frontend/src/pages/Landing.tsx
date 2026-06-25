import { Link } from "react-router-dom";
import {
  FileText, Hash, FileCheck2, Building2, BarChart3, Plug,
  ArrowRight, Check, ShieldCheck, Plus,
} from "lucide-react";
import { SiteNav } from "../components/site/SiteNav";
import { SiteFooter } from "../components/site/SiteFooter";
import { FacturaPreview } from "../components/FacturaPreview";
import { Button, Card } from "../components/ui";

export function Landing() {
  return (
    <div className="bg-white">
      <SiteNav />
      <Hero />
      <TrustBar />
      <Features />
      <HowItWorks />
      <Pricing />
      <Faq />
      <CtaBand />
      <SiteFooter />
    </div>
  );
}

/* ------------------------------- Hero ------------------------------- */
function Hero() {
  return (
    <section className="relative overflow-hidden border-b border-line">
      <div className="mx-auto grid max-w-6xl items-center gap-12 px-5 py-20 lg:grid-cols-[1.05fr_0.95fr] lg:py-28">
        <div>
          <span className="inline-flex items-center gap-2 rounded-full border border-line bg-white px-3 py-1 text-xs font-medium text-slate shadow-xs">
            <ShieldCheck size={14} className="text-cobalt" /> Conforme al SII de Chile
          </span>
          <h1 className="mt-6 font-display text-4xl font-bold leading-[1.08] text-ink sm:text-5xl">
            La facturación electrónica de tu empresa,{" "}
            <span className="text-cobalt">al día con el SII</span>.
          </h1>
          <p className="mt-5 max-w-lg text-lg leading-relaxed text-slate">
            Emite facturas, boletas y notas de crédito válidas en segundos. Los
            folios, el timbre y el envío al SII son automáticos: tú solo revisas
            y emites.
          </p>
          <div className="mt-8 flex flex-wrap items-center gap-3">
            <Link to="/ingresar">
              <Button size="lg">Probar gratis <ArrowRight size={18} /></Button>
            </Link>
            <a href="#como-funciona">
              <Button variant="secondary" size="lg">Ver cómo funciona</Button>
            </a>
          </div>
          <p className="mt-4 text-sm text-slate-soft">14 días gratis · sin tarjeta · cancela cuando quieras</p>
        </div>

        <div className="relative">
          <div className="absolute -inset-6 -z-10 rounded-xl bg-mist" />
          <FacturaPreview className="shadow-lg" />
        </div>
      </div>
    </section>
  );
}

/* ----------------------------- TrustBar ----------------------------- */
function TrustBar() {
  const items = ["Integrado con el SII", "Webpay · Transbank", "Certificación y producción", "Respaldo en la nube"];
  return (
    <section className="border-b border-line bg-canvas">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-center gap-x-10 gap-y-3 px-5 py-6 text-sm font-medium text-slate">
        {items.map((i) => (
          <span key={i} className="inline-flex items-center gap-2">
            <Check size={15} className="text-cobalt" /> {i}
          </span>
        ))}
      </div>
    </section>
  );
}

/* ----------------------------- Features ----------------------------- */
const FEATURES = [
  { icon: FileText, titulo: "Emisión de DTE", desc: "Facturas afectas y exentas, boletas y notas de crédito o débito con el formato que exige el SII." },
  { icon: Hash, titulo: "Folios y CAF", desc: "Carga tus CAF y el sistema asigna el folio correcto en cada emisión, sin duplicados ni saltos." },
  { icon: FileCheck2, titulo: "Representación en PDF", desc: "Cada documento genera su PDF con timbre listo para enviar a tu cliente o imprimir." },
  { icon: Building2, titulo: "Multi-empresa", desc: "Administra varias razones sociales desde una sola cuenta, cada una con sus folios y clientes." },
  { icon: BarChart3, titulo: "Reportes claros", desc: "Sigue lo emitido del mes, lo pendiente en el SII y el detalle por cliente en un panel ordenado." },
  { icon: Plug, titulo: "API para integrar", desc: "Conecta tu sitio o sistema de gestión y emite documentos de forma programática." },
];

function Features() {
  return (
    <section id="producto" className="mx-auto max-w-6xl px-5 py-24">
      <div className="max-w-2xl">
        <h2 className="font-display text-3xl font-semibold text-ink">
          Todo lo que necesitas para facturar
        </h2>
        <p className="mt-4 text-lg leading-relaxed text-slate">
          Una herramienta enfocada: emitir bien, cumplir con el SII y mantener tu
          operación ordenada.
        </p>
      </div>
      <div className="mt-14 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {FEATURES.map(({ icon: Icon, titulo, desc }) => (
          <Card key={titulo} className="p-6 transition-shadow hover:shadow-md">
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-cobalt-soft text-cobalt">
              <Icon size={22} strokeWidth={2} />
            </span>
            <h3 className="mt-5 font-display text-base font-semibold text-ink">{titulo}</h3>
            <p className="mt-2 text-sm leading-relaxed text-slate">{desc}</p>
          </Card>
        ))}
      </div>
    </section>
  );
}

/* --------------------------- HowItWorks ----------------------------- */
const PASOS = [
  { n: "01", titulo: "Carga tu CAF", desc: "Sube el Código de Autorización de Folios que entrega el SII. Quedan listos para usarse." },
  { n: "02", titulo: "Emite el documento", desc: "Eliges el cliente, agregas el detalle y el sistema calcula el IVA, timbra y firma." },
  { n: "03", titulo: "El SII responde", desc: "Enviamos el DTE al SII y registramos su estado. Tu cliente recibe el PDF al instante." },
];

function HowItWorks() {
  return (
    <section id="como-funciona" className="border-y border-line bg-canvas">
      <div className="mx-auto max-w-6xl px-5 py-24">
        <h2 className="max-w-2xl font-display text-3xl font-semibold text-ink">
          De la emisión al SII en tres pasos
        </h2>
        <div className="mt-14 grid gap-6 md:grid-cols-3">
          {PASOS.map((p) => (
            <div key={p.n} className="relative">
              <span className="font-display text-5xl font-semibold text-cobalt/30 tnum">{p.n}</span>
              <h3 className="mt-3 font-display text-lg font-semibold text-ink">{p.titulo}</h3>
              <p className="mt-2 text-sm leading-relaxed text-slate">{p.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ------------------------------ Pricing ----------------------------- */
const PLANES = [
  {
    nombre: "Emprende", precio: "$0", periodo: "primeros 14 días",
    desc: "Para partir y probar la emisión.",
    features: ["1 razón social", "Hasta 20 documentos", "Emisión de facturas y boletas", "Soporte por correo"],
    destacado: false, cta: "Empezar gratis",
  },
  {
    nombre: "Pyme", precio: "$19.900", periodo: "por mes",
    desc: "Para operar todos los meses.",
    features: ["1 razón social", "Documentos ilimitados", "Notas de crédito y débito", "Reportes y PDF", "Soporte prioritario"],
    destacado: true, cta: "Probar 14 días",
  },
  {
    nombre: "Estudio", precio: "$39.900", periodo: "por mes",
    desc: "Para varias empresas o contadores.",
    features: ["Hasta 5 razones sociales", "Todo lo del plan Pyme", "Acceso a la API", "Usuarios por empresa"],
    destacado: false, cta: "Hablar con ventas",
  },
];

function Pricing() {
  return (
    <section id="precios" className="mx-auto max-w-6xl px-5 py-24">
      <div className="mx-auto max-w-2xl text-center">
        <h2 className="font-display text-3xl font-semibold text-ink">Precios simples y en pesos</h2>
        <p className="mt-4 text-lg leading-relaxed text-slate">Sin costos de instalación. Cambia o cancela tu plan cuando quieras.</p>
      </div>
      <div className="mt-14 grid items-start gap-6 lg:grid-cols-3">
        {PLANES.map((p) => (
          <Card
            key={p.nombre}
            className={`flex flex-col p-7 ${p.destacado ? "border-cobalt ring-1 ring-cobalt shadow-md" : ""}`}
          >
            {p.destacado && (
              <span className="mb-3 inline-flex w-fit rounded-full bg-cobalt-soft px-2.5 py-0.5 text-xs font-medium text-cobalt">
                Más elegido
              </span>
            )}
            <h3 className="font-display text-lg font-semibold text-ink">{p.nombre}</h3>
            <p className="mt-1 text-sm text-slate">{p.desc}</p>
            <div className="mt-6 flex items-baseline gap-1.5">
              <span className="font-display text-4xl font-bold text-ink tnum">{p.precio}</span>
              <span className="text-sm text-slate-soft">/ {p.periodo}</span>
            </div>
            <ul className="mt-7 flex-1 space-y-3">
              {p.features.map((f) => (
                <li key={f} className="flex items-start gap-2.5 text-sm text-ink-soft">
                  <Check size={17} className="mt-0.5 shrink-0 text-cobalt" /> {f}
                </li>
              ))}
            </ul>
            <Link to="/ingresar" className="mt-8">
              <Button variant={p.destacado ? "primary" : "secondary"} className="w-full">{p.cta}</Button>
            </Link>
          </Card>
        ))}
      </div>
    </section>
  );
}

/* -------------------------------- FAQ ------------------------------- */
const PREGUNTAS = [
  { q: "¿Necesito un certificado digital?", a: "Sí. El SII exige firmar los DTE con el certificado digital del representante legal. En Nexo Factura lo cargas una vez y queda asociado a tu empresa." },
  { q: "¿Sirve para boletas además de facturas?", a: "Sí. Puedes emitir facturas afectas y exentas, boletas y notas de crédito o débito, cada una con su tipo y folio." },
  { q: "¿Puedo traer mis folios actuales?", a: "Sí. Cargas el CAF que entrega el SII y el sistema continúa la numeración desde donde estabas." },
  { q: "¿Los documentos son válidos ante el SII?", a: "Sí. El documento se timbra, se firma y se envía al SII, que responde con la aceptación. Queda registrado su estado en tu panel." },
];

function Faq() {
  return (
    <section id="preguntas" className="border-t border-line bg-canvas">
      <div className="mx-auto max-w-3xl px-5 py-24">
        <h2 className="text-center font-display text-3xl font-semibold text-ink">Preguntas frecuentes</h2>
        <div className="mt-12 space-y-3">
          {PREGUNTAS.map((p) => (
            <details key={p.q} className="group rounded-lg border border-line bg-white p-5 shadow-xs">
              <summary className="flex cursor-pointer list-none items-center justify-between gap-4 font-display text-base font-semibold text-ink">
                {p.q}
                <Plus
                  size={18}
                  className="shrink-0 text-cobalt transition-transform duration-150 group-open:rotate-45"
                />
              </summary>
              <p className="mt-3 text-sm leading-relaxed text-slate">{p.a}</p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ------------------------------ CTA band ---------------------------- */
function CtaBand() {
  return (
    <section className="bg-ink">
      <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-6 px-5 py-16 text-center md:flex-row md:text-left">
        <div>
          <h2 className="font-display text-2xl font-semibold text-white sm:text-3xl">
            Empieza a emitir hoy
          </h2>
          <p className="mt-2 text-base text-white/70">
            Primera asesoría gratis. Te respondemos hoy por WhatsApp.
          </p>
        </div>
        <div className="flex flex-wrap items-center justify-center gap-3">
          <Link to="/ingresar">
            <Button size="lg">Probar gratis <ArrowRight size={18} /></Button>
          </Link>
          <a href="https://wa.me/56981964119" target="_blank" rel="noreferrer">
            <Button variant="secondary" size="lg" className="border-white/20 bg-white/10 text-white hover:border-white/30 hover:bg-white/15">
              Hablar por WhatsApp
            </Button>
          </a>
        </div>
      </div>
    </section>
  );
}
