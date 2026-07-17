import { Link } from "react-router-dom";
import { Mail, MapPin, MessageCircle } from "lucide-react";
import { SitePage } from "../../components/site/SitePage";
import { Button, Card } from "../../components/ui";

const WHATSAPP_URL = "https://wa.me/56981964119";
const CORREO = "contacto@nexosoftware.cl";

export function Contacto() {
  return (
    <SitePage
      titulo="Contacto"
      descripcion="¿Dudas sobre la emisión, los planes o cómo migrar tus folios? Escríbenos por el canal que te acomode: respondemos dentro del día hábil."
      ancho="wide"
    >
      <div className="grid gap-5 md:grid-cols-3">
        <Card className="flex flex-col p-6">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-cobalt-soft text-cobalt">
            <MessageCircle size={22} strokeWidth={2} />
          </span>
          <h2 className="mt-5 font-display text-base font-semibold text-ink">WhatsApp</h2>
          <p className="mt-2 flex-1 text-sm leading-relaxed text-slate">
            El canal más rápido. Ideal para dudas puntuales o coordinar una
            demostración.
          </p>
          <a href={WHATSAPP_URL} target="_blank" rel="noreferrer" className="mt-5">
            <Button className="w-full">Escribir al +56 9 8196 4119</Button>
          </a>
        </Card>

        <Card className="flex flex-col p-6">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-cobalt-soft text-cobalt">
            <Mail size={22} strokeWidth={2} />
          </span>
          <h2 className="mt-5 font-display text-base font-semibold text-ink">Correo</h2>
          <p className="mt-2 flex-1 text-sm leading-relaxed text-slate">
            Para consultas comerciales, soporte o temas administrativos con más
            detalle.
          </p>
          <a href={`mailto:${CORREO}`} className="mt-5">
            <Button variant="secondary" className="w-full">{CORREO}</Button>
          </a>
        </Card>

        <Card className="flex flex-col p-6">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-cobalt-soft text-cobalt">
            <MapPin size={22} strokeWidth={2} />
          </span>
          <h2 className="mt-5 font-display text-base font-semibold text-ink">Dónde estamos</h2>
          <p className="mt-2 flex-1 text-sm leading-relaxed text-slate">
            Quillota, Región de Valparaíso, Chile. Trabajamos de forma remota
            con clientes de todo el país.
          </p>
          <p className="mt-5 text-sm font-medium text-ink">Nexo Software SpA</p>
        </Card>
      </div>

      <p className="mt-10 text-sm text-slate-soft">
        Si ya eres cliente y detectas un problema con la emisión, revisa primero
        el <Link to="/estado" className="font-medium text-cobalt transition-colors hover:text-cobalt-dark">estado del servicio</Link>.
      </p>
    </SitePage>
  );
}
