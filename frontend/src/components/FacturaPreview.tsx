import { formatCLP, formatRut } from "../lib/format";

/**
 * Representación fiel de un DTE chileno: recuadro rojo con RUT/folio, receptor,
 * detalle, IVA 19% y espacio de timbre. Es el "héroe" del landing porque muestra
 * el artefacto real que produce el producto, no un dashboard genérico.
 */
export function FacturaPreview({ className = "" }: { className?: string }) {
  const lineas = [
    { n: "Desarrollo de landing page", c: 1, p: 450000 },
    { n: "Plan de soporte mensual", c: 3, p: 360000 },
    { n: "Hora de desarrollo", c: 23.6, p: 590000 },
  ];
  const neto = 1400000;
  const iva = 266000;
  const total = 1666000;

  return (
    <div
      className={`overflow-hidden rounded-xl border border-line bg-white shadow-lg ${className}`}
    >
      {/* pliegue de papel */}
      <div className="h-1.5 bg-gradient-to-r from-cobalt via-azure to-cobalt opacity-90" />
      <div className="p-6">
        {/* cabecera */}
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="font-display text-base font-semibold text-ink">Nexo Software SpA</div>
            <div className="mt-0.5 text-xs text-slate">Desarrollo de software · Quillota</div>
            <div className="text-xs text-slate-soft tnum">{formatRut("765432109")}</div>
          </div>
          <div className="rounded-sm border-2 border-danger px-3 py-2 text-center">
            <div className="text-[10px] font-semibold uppercase tracking-wide text-danger tnum">R.U.T. 76.543.210-9</div>
            <div className="text-[11px] font-semibold text-danger">Factura Electrónica</div>
            <div className="font-display text-sm font-semibold text-danger tnum">N° 142</div>
          </div>
        </div>

        {/* receptor */}
        <div className="mt-4 rounded-sm bg-mist px-3 py-2 text-xs">
          <span className="text-slate-soft">Señor(es): </span>
          <span className="font-medium text-ink">Constructora Andes SpA</span>
          <span className="text-slate-soft tnum"> · {formatRut("782223334")}</span>
        </div>

        {/* detalle */}
        <div className="mt-4">
          <div className="grid grid-cols-[1fr_auto] gap-2 border-b border-line pb-1.5 text-xs font-medium uppercase tracking-wide text-slate-soft">
            <span>Detalle</span>
            <span className="text-right">Importe</span>
          </div>
          {lineas.map((l) => (
            <div key={l.n} className="grid grid-cols-[1fr_auto] gap-2 border-b border-line py-2 text-xs last:border-0">
              <span className="text-ink">
                {l.n} <span className="text-slate-soft tnum">×{l.c}</span>
              </span>
              <span className="text-right text-ink tnum">{formatCLP(l.p)}</span>
            </div>
          ))}
        </div>

        {/* totales + timbre */}
        <div className="mt-4 flex items-end justify-between gap-6">
          <div className="flex-1">
            <div className="grid h-16 place-items-center rounded-sm border border-dashed border-line bg-mist/60 text-center">
              <div className="text-[9px] leading-tight text-slate-soft">
                <div className="font-semibold">Timbre Electrónico SII</div>
                <div>PDF417 · verifique en sii.cl</div>
              </div>
            </div>
          </div>
          <div className="w-40 space-y-1 text-xs">
            <Row label="Neto" valor={formatCLP(neto)} />
            <Row label="IVA 19%" valor={formatCLP(iva)} />
            <div className="mt-1 flex items-center justify-between border-t border-line pt-1.5">
              <span className="font-semibold text-ink">Total</span>
              <span className="font-display text-sm font-semibold text-cobalt tnum">{formatCLP(total)}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Row({ label, valor }: { label: string; valor: string }) {
  return (
    <div className="flex items-center justify-between text-slate">
      <span>{label}</span>
      <span className="text-ink tnum">{valor}</span>
    </div>
  );
}
