import { useEffect } from "react";
import { Link, useLocation } from "react-router-dom";
import {
  ArrowRight, BadgeCheck, BarChart3, Building2, Check, FileCheck2, FileText,
  Hash, Plus, Send, ShieldCheck, Stamp, Terminal,
} from "lucide-react";
import { SiteNav } from "../components/site/SiteNav";
import { SiteFooter } from "../components/site/SiteFooter";
import { Reveal } from "../components/site/Reveal";
import { FacturaPreview } from "../components/FacturaPreview";
import { Badge, Button, Card } from "../components/ui";

export function Landing() {
  // Scroll a la sección del hash cuando se llega a la Landing con un ancla
  // (p. ej. /#precios desde el footer o el nav de otra página). El scroll-mt-*
  // de cada sección compensa el nav sticky; scroll-behavior:smooth es global.
  const { hash } = useLocation();
  useEffect(() => {
    if (!hash) return;
    const el = document.getElementById(hash.slice(1));
    if (el) requestAnimationFrame(() => el.scrollIntoView());
  }, [hash]);

  return (
    <div className="bg-white">
      <SiteNav />
      <Hero />
      <Marquee />
      <ViajeDte />
      <Bento />
      <NotaEquipo />
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
      <div aria-hidden="true" className="hero-grid absolute inset-0" />
      <div className="relative mx-auto grid max-w-6xl items-center gap-12 px-5 py-20 lg:grid-cols-[1.05fr_0.95fr] lg:py-28">
        <div>
          <Reveal>
            <span className="inline-flex items-center gap-2 rounded-full border border-line bg-white px-3 py-1 text-xs font-medium text-slate">
              <ShieldCheck size={14} className="text-cobalt" /> Conforme al SII de Chile
            </span>
          </Reveal>
          <Reveal delay={80}>
            <h1 className="mt-6 font-display text-4xl font-semibold leading-[1.08] text-ink sm:text-5xl">
              La facturación electrónica de tu empresa,{" "}
              <span className="text-cobalt">al día con el SII</span>.
            </h1>
          </Reveal>
          <Reveal delay={160}>
            <p className="mt-5 max-w-lg text-lg leading-relaxed text-slate">
              Emite facturas, boletas y notas de crédito válidas en segundos. Los
              folios, el timbre y el envío al SII son automáticos: tú solo revisas
              y emites.
            </p>
          </Reveal>
          <Reveal delay={240}>
            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Link to="/ingresar">
                <Button size="lg">Probar gratis <ArrowRight size={18} /></Button>
              </Link>
              <a href="#como-funciona">
                <Button variant="secondary" size="lg">Ver cómo funciona</Button>
              </a>
            </div>
          </Reveal>
          <Reveal delay={320}>
            <p className="mt-4 text-sm text-slate-soft">14 días gratis · sin tarjeta · cancela cuando quieras</p>
          </Reveal>
        </div>

        <Reveal delay={200} className="relative">
          <div className="absolute -inset-6 -z-10 rounded-xl bg-mist" />
          <div className="float-slow relative">
            <FacturaPreview className="shadow-lg" />
            {/* Timbre que se estampa al cargar */}
            <div className="stamp-in absolute -right-2 top-8 rounded-lg border-2 border-success bg-white/85 px-3 py-1.5 font-display text-sm font-semibold uppercase tracking-widest text-success backdrop-blur-sm sm:-right-4">
              Aceptado · SII
            </div>
          </div>
          {/* Chips flotantes del proceso real */}
          <div className="float-slower absolute -left-8 top-12 hidden items-center gap-2 rounded-full border border-line bg-white px-3.5 py-1.5 text-xs font-medium text-slate lg:inline-flex">
            <Hash size={13} className="text-cobalt" /> Folio 1042 asignado
          </div>
          <div className="float-slow absolute -left-5 bottom-14 hidden items-center gap-2 rounded-full border border-line bg-white px-3.5 py-1.5 text-xs font-medium text-slate lg:inline-flex">
            <FileCheck2 size={13} className="text-cobalt" /> XML firmado
          </div>
        </Reveal>
      </div>
    </section>
  );
}

/* ------------------- Marquesina de sellos de confianza ------------------- */
const SELLOS = [
  "Integrado con el SII",
  "Timbre electrónico PDF417",
  "Folios CAF sin saltos ni duplicados",
  "IVA 19% calculado línea a línea",
  "Libros de compra y venta (IECV)",
  "Contingencia ante caídas del SII",
  "Set de pruebas de certificación",
  "Respaldo en la nube",
];

function Marquee() {
  return (
    <section className="border-b border-line bg-canvas py-5" aria-label="Características del servicio">
      <div className="marquee overflow-hidden">
        <div className="marquee-track flex w-max items-center gap-10 pr-10">
          {[...SELLOS, ...SELLOS].map((s, i) => (
            <span
              key={i}
              aria-hidden={i >= SELLOS.length}
              className="inline-flex shrink-0 items-center gap-2 text-sm font-medium text-slate"
            >
              <Check size={15} className="text-cobalt" /> {s}
            </span>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ----------------------- El viaje de un DTE ----------------------- */
// Los pasos y estados son los reales del sistema (BORRADOR → FIRMADO →
// ENVIADO → ACEPTADO); las badges son las mismas que usa el panel.
const PASOS_DTE = [
  {
    icon: FileText,
    titulo: "Escribes el borrador",
    desc: "Eliges el cliente, agregas el detalle y el IVA se calcula línea a línea. Nada se envía todavía.",
    badge: <Badge tone="neutral">Borrador</Badge>,
    nota: "Neto, IVA y total siempre cuadrados",
  },
  {
    icon: Stamp,
    titulo: "Se timbra y firma",
    desc: "El folio sale de tu CAF, se genera el timbre electrónico (TED) y el XML se firma con tu certificado digital.",
    badge: <Badge tone="cobalt">Firmado</Badge>,
    nota: "Folio correlativo, sin saltos",
  },
  {
    icon: Send,
    titulo: "Viaja al SII",
    desc: "Enviamos el EnvioDTE al SII y guardamos el número de seguimiento. Si el SII está caído, entra en contingencia y se reenvía solo.",
    badge: <Badge tone="cobalt">Enviado</Badge>,
    nota: "Reintento automático",
  },
  {
    icon: BadgeCheck,
    titulo: "El SII responde",
    desc: "La aceptación (o el reparo) queda registrada en tu panel y tu cliente recibe el PDF con timbre al instante.",
    badge: <Badge tone="success">Aceptado</Badge>,
    nota: "Estado consultable cuando quieras",
  },
];

function ViajeDte() {
  return (
    <section id="como-funciona" className="scroll-mt-16 border-b border-line bg-canvas">
      <div className="mx-auto max-w-6xl px-5 py-24">
        <Reveal>
          <h2 className="max-w-2xl font-display text-3xl font-semibold text-ink">
            El viaje de un documento, de borrador a aceptado
          </h2>
          <p className="mt-4 max-w-2xl text-lg leading-relaxed text-slate">
            Esto es exactamente lo que pasa cada vez que emites. Sin pasos
            ocultos: los mismos estados que verás en tu panel.
          </p>
        </Reveal>

        <Reveal className="mt-16">
          <div className="relative">
            {/* Línea de progreso que se dibuja al llegar a la sección */}
            <div aria-hidden="true" className="absolute left-0 right-0 top-[22px] hidden h-px bg-line lg:block">
              <div className="grow-x h-px w-full bg-cobalt" />
            </div>
            <ol className="grid gap-10 lg:grid-cols-4 lg:gap-8">
              {PASOS_DTE.map(({ icon: Icon, titulo, desc, badge, nota }, i) => (
                <li key={titulo} className="relative">
                  <Reveal delay={200 + i * 180} className="flex gap-4 lg:block">
                    <span className="relative z-10 flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-line bg-white text-cobalt">
                      <Icon size={19} strokeWidth={2} />
                    </span>
                    <div className="lg:mt-5">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="font-display text-base font-semibold text-ink">{titulo}</h3>
                        {badge}
                      </div>
                      <p className="mt-2 text-sm leading-relaxed text-slate">{desc}</p>
                      <p className="mt-2 text-xs font-medium text-slate-soft">{nota}</p>
                    </div>
                  </Reveal>
                </li>
              ))}
            </ol>
          </div>
        </Reveal>
      </div>
    </section>
  );
}

/* -------------------- Bento: el producto en viñetas -------------------- */
// Cada card conserva el id al que apunta el footer (p. ej. /#folios-y-caf).
function Bento() {
  return (
    <section id="producto" className="mx-auto max-w-6xl scroll-mt-16 px-5 py-24">
      <Reveal>
        <h2 className="max-w-2xl font-display text-3xl font-semibold text-ink">
          Lo que ves en el panel, tal cual
        </h2>
        <p className="mt-4 max-w-2xl text-lg leading-relaxed text-slate">
          Sin maquetas infladas: estas viñetas usan los mismos componentes,
          estados y montos con los que trabajarás todos los días.
        </p>
      </Reveal>

      <div className="mt-14 grid gap-5 lg:grid-cols-3">
        {/* Documentos: mini-tabla con estados reales */}
        <Reveal delay={100} className="lg:col-span-2">
          <Card id="emision-de-dte" className="scroll-mt-24 p-6">
            <h3 className="font-display text-base font-semibold text-ink">Emite y sigue cada documento</h3>
            <p className="mt-1.5 text-sm text-slate">
              Facturas afectas y exentas, boletas y notas de crédito o débito,
              cada una con su folio y su estado ante el SII.
            </p>
            <div className="mt-5 overflow-hidden rounded-lg border border-line">
              <table className="w-full text-sm">
                <thead className="bg-canvas text-left">
                  <tr className="text-xs font-medium uppercase tracking-wide text-slate-soft">
                    <th className="px-4 py-2.5">Documento</th>
                    <th className="hidden px-4 py-2.5 sm:table-cell">Receptor</th>
                    <th className="px-4 py-2.5 text-right">Total</th>
                    <th className="px-4 py-2.5">Estado</th>
                  </tr>
                </thead>
                <tbody>
                  <tr className="border-t border-line">
                    <td className="px-4 py-3 font-medium text-ink tnum">Factura N° 1041</td>
                    <td className="hidden px-4 py-3 text-slate sm:table-cell">Comercial Andes Ltda.</td>
                    <td className="px-4 py-3 text-right text-ink tnum">$1.184.050</td>
                    <td className="px-4 py-3"><Badge tone="success">Aceptado</Badge></td>
                  </tr>
                  <tr className="border-t border-line">
                    <td className="px-4 py-3 font-medium text-ink tnum">Boleta N° 5310</td>
                    <td className="hidden px-4 py-3 text-slate sm:table-cell">Consumidor final</td>
                    <td className="px-4 py-3 text-right text-ink tnum">$24.990</td>
                    <td className="px-4 py-3">
                      <span className="inline-flex items-center gap-1.5">
                        <span className="relative flex h-2 w-2">
                          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-cobalt opacity-60" />
                          <span className="relative inline-flex h-2 w-2 rounded-full bg-cobalt" />
                        </span>
                        <Badge tone="cobalt">Enviado</Badge>
                      </span>
                    </td>
                  </tr>
                  <tr className="border-t border-line">
                    <td className="px-4 py-3 font-medium text-ink tnum">N. crédito N° 88</td>
                    <td className="hidden px-4 py-3 text-slate sm:table-cell">Comercial Andes Ltda.</td>
                    <td className="px-4 py-3 text-right text-ink tnum">$−92.000</td>
                    <td className="px-4 py-3"><Badge tone="cobalt">Firmado</Badge></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </Card>
        </Reveal>

        {/* Folios / CAF */}
        <Reveal delay={200}>
          <Card id="folios-y-caf" className="flex h-full scroll-mt-24 flex-col p-6">
            <span className="flex h-11 w-11 items-center justify-center rounded-full bg-cobalt-soft text-cobalt">
              <Hash size={20} strokeWidth={2} />
            </span>
            <h3 className="mt-5 font-display text-base font-semibold text-ink">Folios bajo control</h3>
            <p className="mt-1.5 flex-1 text-sm leading-relaxed text-slate">
              Cargas el CAF que entrega el SII y cada emisión toma el folio
              correcto. Te avisamos antes de que se acaben.
            </p>
            <div className="mt-5">
              <div className="flex items-baseline justify-between text-sm">
                <span className="font-display text-2xl font-semibold text-ink tnum">958</span>
                <span className="text-xs text-slate-soft tnum">de 1.000 folios disponibles</span>
              </div>
              <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-mist">
                <div className="grow-x h-full w-[96%] rounded-full bg-cobalt" />
              </div>
            </div>
          </Card>
        </Reveal>

        {/* Reportes */}
        <Reveal delay={100}>
          <Card id="reportes" className="flex h-full scroll-mt-24 flex-col p-6">
            <span className="flex h-11 w-11 items-center justify-center rounded-full bg-cobalt-soft text-cobalt">
              <BarChart3 size={20} strokeWidth={2} />
            </span>
            <h3 className="mt-5 font-display text-base font-semibold text-ink">Reportes claros</h3>
            <p className="mt-1.5 flex-1 text-sm leading-relaxed text-slate">
              Lo emitido del mes, lo pendiente en el SII y el detalle por
              cliente, sin planillas aparte.
            </p>
            <div aria-hidden="true" className="mt-5 flex h-16 items-end gap-1.5">
              {[35, 55, 40, 70, 52, 85, 62].map((h, i) => (
                <div
                  key={i}
                  className="grow-y flex-1 rounded-t-[3px] bg-cobalt/25 last:bg-cobalt"
                  style={{ height: `${h}%`, transitionDelay: `${350 + i * 70}ms` }}
                />
              ))}
            </div>
          </Card>
        </Reveal>

        {/* PDF con timbre */}
        <Reveal delay={200}>
          <Card id="pdf" className="flex h-full scroll-mt-24 flex-col p-6">
            <span className="flex h-11 w-11 items-center justify-center rounded-full bg-cobalt-soft text-cobalt">
              <FileCheck2 size={20} strokeWidth={2} />
            </span>
            <h3 className="mt-5 font-display text-base font-semibold text-ink">PDF con timbre</h3>
            <p className="mt-1.5 flex-1 text-sm leading-relaxed text-slate">
              Cada documento genera su representación impresa con el timbre
              electrónico PDF417 que exige el SII.
            </p>
            <div aria-hidden="true" className="mt-5 rounded-sm border border-dashed border-line bg-mist/60 px-4 py-3">
              <div className="space-y-1">
                {[92, 78, 96, 64, 88].map((w, i) => (
                  <div key={i} className="h-[3px] rounded-full bg-ink/60" style={{ width: `${w}%` }} />
                ))}
              </div>
              <p className="mt-2 text-center text-[10px] font-medium uppercase tracking-wide text-slate-soft">
                Timbre electrónico SII
              </p>
            </div>
          </Card>
        </Reveal>

        {/* Multi-empresa */}
        <Reveal delay={300}>
          <Card id="multi-empresa" className="flex h-full scroll-mt-24 flex-col p-6">
            <span className="flex h-11 w-11 items-center justify-center rounded-full bg-cobalt-soft text-cobalt">
              <Building2 size={20} strokeWidth={2} />
            </span>
            <h3 className="mt-5 font-display text-base font-semibold text-ink">Multi-empresa</h3>
            <p className="mt-1.5 flex-1 text-sm leading-relaxed text-slate">
              Varias razones sociales en una cuenta, cada una con su
              certificado, sus folios y sus clientes.
            </p>
            <div className="mt-5 flex flex-wrap gap-2">
              <span className="rounded-full border border-cobalt-soft bg-cobalt-soft px-3 py-1 text-xs font-medium text-cobalt">Nexo Software SpA</span>
              <span className="rounded-full border border-line bg-white px-3 py-1 text-xs font-medium text-slate">Comercial Andes Ltda.</span>
              <span className="rounded-full border border-line bg-white px-3 py-1 text-xs font-medium text-slate">+ agregar</span>
            </div>
          </Card>
        </Reveal>

        {/* API: terminal a lo ancho */}
        <Reveal delay={150} className="lg:col-span-3">
          <Card id="api" className="scroll-mt-24 overflow-hidden">
            <div className="grid lg:grid-cols-[0.85fr_1.15fr]">
              <div className="p-6 lg:p-8">
                <span className="flex h-11 w-11 items-center justify-center rounded-full bg-cobalt-soft text-cobalt">
                  <Terminal size={20} strokeWidth={2} />
                </span>
                <h3 className="mt-5 font-display text-base font-semibold text-ink">Una API sin sorpresas</h3>
                <p className="mt-1.5 text-sm leading-relaxed text-slate">
                  Los mismos endpoints que usa este panel. Creas el documento,
                  lo emites y lo envías: tres llamadas y el SII tiene tu DTE.
                </p>
                <p className="mt-4 text-xs font-medium text-slate-soft">
                  Disponible en el plan Estudio.
                </p>
              </div>
              <div className="border-t border-line bg-ink p-6 font-mono text-xs leading-relaxed text-white/80 lg:border-l lg:border-t-0 lg:p-8">
                <p><span className="text-azure">POST</span> /api/empresas/1/documentos</p>
                <p className="text-white/45">{"{ \"tipoDte\": 33, \"receptorRut\": \"76543210-K\", … }"}</p>
                <p className="mt-3"><span className="text-white/45">→ 201 ·</span> <span className="text-white">folio 1042 asignado</span></p>
                <p className="mt-3"><span className="text-azure">POST</span> /api/empresas/1/documentos/1042/emitir</p>
                <p><span className="text-azure">POST</span> /api/empresas/1/documentos/1042/enviar</p>
                <p className="mt-3"><span className="text-white/45">→ estado:</span> <span className="text-success">"ACEPTADO"</span><span className="blink-caret text-white/80">▍</span></p>
              </div>
            </div>
          </Card>
        </Reveal>
      </div>
    </section>
  );
}

/* ------------------------- Nota del equipo ------------------------- */
function NotaEquipo() {
  return (
    <section className="border-t border-line bg-canvas">
      <div className="mx-auto max-w-3xl px-5 py-24">
        <Reveal>
          <span aria-hidden="true" className="font-display text-6xl font-semibold leading-none text-cobalt/20">“</span>
          <p className="mt-2 font-display text-xl font-medium leading-relaxed text-ink sm:text-2xl">
            Armamos Nexo Factura porque las pymes con las que trabajamos pagaban
            de más por sistemas enormes que igual las hacían pelear con el SII.
            Preferimos lo contrario: una herramienta chica y enfocada — emitir
            bien, cumplir y volver a trabajar.
          </p>
          <div className="mt-8 flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-cobalt text-sm font-semibold text-white">N</span>
            <div className="text-sm">
              <p className="font-medium text-ink">El equipo de Nexo Software</p>
              <p className="text-slate-soft">Quillota, Región de Valparaíso</p>
            </div>
          </div>
        </Reveal>
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
    <section id="precios" className="mx-auto max-w-6xl scroll-mt-16 px-5 py-24">
      <Reveal>
        <div className="mx-auto max-w-2xl text-center">
          <h2 className="font-display text-3xl font-semibold text-ink">Precios simples y en pesos</h2>
          <p className="mt-4 text-lg leading-relaxed text-slate">Sin costos de instalación. Cambia o cancela tu plan cuando quieras.</p>
        </div>
      </Reveal>
      <div className="mt-14 grid items-start gap-6 lg:grid-cols-3">
        {PLANES.map((p, i) => (
          <Reveal key={p.nombre} delay={i * 120}>
            <Card className={`flex flex-col p-7 ${p.destacado ? "border-cobalt ring-1 ring-cobalt" : ""}`}>
              {p.destacado && (
                <span className="mb-3 inline-flex w-fit rounded-full bg-cobalt-soft px-2.5 py-0.5 text-xs font-medium text-cobalt">
                  Más elegido
                </span>
              )}
              <h3 className="font-display text-lg font-semibold text-ink">{p.nombre}</h3>
              <p className="mt-1 text-sm text-slate">{p.desc}</p>
              <div className="mt-6 flex items-baseline gap-1.5">
                <span className="font-display text-4xl font-semibold text-ink tnum">{p.precio}</span>
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
          </Reveal>
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
  { q: "¿Qué pasa si el SII está caído?", a: "El documento queda en contingencia con su folio ya asignado y se reenvía automáticamente cuando el SII vuelve. No pierdes la venta ni el folio." },
];

function Faq() {
  return (
    <section id="preguntas" className="scroll-mt-16 border-t border-line bg-canvas">
      <div className="mx-auto max-w-3xl px-5 py-24">
        <Reveal>
          <h2 className="text-center font-display text-3xl font-semibold text-ink">Preguntas frecuentes</h2>
        </Reveal>
        <div className="mt-12 space-y-3">
          {PREGUNTAS.map((p, i) => (
            <Reveal key={p.q} delay={i * 70}>
              <details className="group rounded-lg border border-line bg-white p-5">
                <summary className="flex cursor-pointer list-none items-center justify-between gap-4 font-display text-base font-semibold text-ink">
                  {p.q}
                  <Plus
                    size={18}
                    className="shrink-0 text-cobalt transition-transform duration-150 group-open:rotate-45"
                  />
                </summary>
                <p className="mt-3 text-sm leading-relaxed text-slate">{p.a}</p>
              </details>
            </Reveal>
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
        <Reveal>
          <h2 className="font-display text-2xl font-semibold text-white sm:text-3xl">
            Empieza a emitir hoy
          </h2>
          <p className="mt-2 text-base text-white/70">
            Primera asesoría gratis. Te respondemos hoy por WhatsApp.
          </p>
        </Reveal>
        <Reveal delay={120}>
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
        </Reveal>
      </div>
    </section>
  );
}
