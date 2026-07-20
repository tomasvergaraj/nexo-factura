import { Link } from "react-router-dom";
import { ArrowRight, FileCheck2, MapPin, ShieldCheck } from "lucide-react";
import { SitePage } from "../../components/site/SitePage";
import { Button, Card } from "../../components/ui";

const PRINCIPIOS = [
  {
    icon: FileCheck2,
    titulo: "Emitir bien, sin fricción",
    desc: "La facturación electrónica no debería exigir un manual. Nexo Factura automatiza folios, timbre, firma y envío para que emitir tome segundos, no trámites.",
  },
  {
    icon: ShieldCheck,
    titulo: "Cumplimiento primero",
    desc: "Cada documento se construye con el formato que exige el SII, se valida antes de firmar y queda con su estado registrado. Lo conforme no es opcional.",
  },
  {
    icon: MapPin,
    titulo: "Cerca de las PYMEs",
    desc: "Somos un equipo chico de la V Región y atendemos directo: la misma persona que construye el producto te responde el WhatsApp.",
  },
];

export function Sobre() {
  return (
    <SitePage
      titulo="Sobre Nexo"
      descripcion="Nexo Software SpA es un estudio de software de Quillota, Chile. Construimos herramientas simples para que las PYMEs cumplan con el SII sin pelearse con la tecnología."
    >
      <div className="space-y-4 text-base leading-relaxed text-slate">
        <p>
          Nexo Factura nació de ver el mismo problema una y otra vez: empresas
          chicas pagando sistemas pensados para corporaciones, o peleándose con
          el portal del SII para sacar una factura. Creemos que emitir un DTE
          válido debería ser tan simple como enviar un correo.
        </p>
        <p>
          Por eso construimos una herramienta enfocada: cargar tus folios,
          emitir con el formato correcto, firmar, enviar al SII y saber en qué
          estado quedó cada documento. Nada más — y nada menos.
        </p>
      </div>

      <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {PRINCIPIOS.map(({ icon: Icon, titulo, desc }) => (
          <Card key={titulo} className="p-6">
            <span className="flex h-11 w-11 items-center justify-center rounded-full bg-cobalt-soft text-cobalt">
              <Icon size={22} strokeWidth={2} />
            </span>
            <h2 className="mt-5 font-display text-base font-semibold text-ink">{titulo}</h2>
            <p className="mt-2 text-sm leading-relaxed text-slate">{desc}</p>
          </Card>
        ))}
      </div>

      <div className="mt-14 flex flex-wrap items-center gap-3 rounded-xl border border-line bg-canvas p-8">
        <div className="mr-auto">
          <h2 className="font-display text-lg font-semibold text-ink">¿Conversamos?</h2>
          <p className="mt-1 text-sm text-slate">
            Cuéntanos cómo facturas hoy y te mostramos cómo se ve en Nexo.
          </p>
        </div>
        <Link to="/contacto">
          <Button variant="secondary">Contacto</Button>
        </Link>
        <Link to="/ingresar">
          <Button>Probar gratis <ArrowRight size={16} /></Button>
        </Link>
      </div>
    </SitePage>
  );
}
