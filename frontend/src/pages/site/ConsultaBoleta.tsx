import { useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";
import { FileCheck2, Search } from "lucide-react";
import { SitePage } from "../../components/site/SitePage";
import { Alert, Button, Card, Field, Input, Select } from "../../components/ui";
import { consultarBoletaPublica } from "../../lib/api";

// Página PÚBLICA de verificación de boletas: el sitio que el SII exige señalar
// bajo el timbre de cada boleta («Verifique documento: …»). No requiere sesión.
// El cliente ingresa los datos tal como vienen impresos; si coinciden exactos,
// se abre la representación impresa (PDF). Disponible 3 meses desde la emisión.

type FormConsulta = {
  rutEmisor: string;
  tipo: string;
  folio: string;
  fecha: string;
  total: string;
};

export function ConsultaBoleta() {
  // El RUT del emisor puede venir en la URL (?rut=78397017-1) para que el
  // enlace impreso deje al cliente con solo los datos variables por llenar.
  const [params] = useSearchParams();
  const [form, setForm] = useState<FormConsulta>({
    rutEmisor: params.get("rut") ?? "",
    tipo: params.get("tipo") ?? "39",
    folio: "",
    fecha: "",
    total: "",
  });
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [buscando, setBuscando] = useState(false);
  const [encontrada, setEncontrada] = useState(false);

  function set<K extends keyof FormConsulta>(campo: K, valor: string) {
    setForm((prev) => ({ ...prev, [campo]: valor }));
    setErrores((prev) => (prev[campo] ? { ...prev, [campo]: "" } : prev));
    setErrorGeneral(null);
    setEncontrada(false);
  }

  async function consultar(e: FormEvent) {
    e.preventDefault();
    setErrorGeneral(null);
    setEncontrada(false);

    const nuevos: Record<string, string> = {};
    if (!form.rutEmisor.trim()) nuevos.rutEmisor = "El RUT del emisor es obligatorio.";
    if (!/^\d+$/.test(form.folio.trim())) nuevos.folio = "El folio es el número de la boleta.";
    if (!form.fecha) nuevos.fecha = "La fecha de emisión es obligatoria.";
    const total = form.total.trim().replace(/\./g, "").replace(/\$/g, "");
    if (!/^\d+$/.test(total)) nuevos.total = "El monto total, en pesos, sin decimales.";
    if (Object.keys(nuevos).length > 0) {
      setErrores(nuevos);
      return;
    }

    setBuscando(true);
    try {
      const pdf = await consultarBoletaPublica({
        rutEmisor: form.rutEmisor.trim(),
        tipo: Number(form.tipo),
        folio: Number(form.folio.trim()),
        fecha: form.fecha,
        total: Number(total),
      });
      setEncontrada(true);
      window.open(URL.createObjectURL(pdf), "_blank", "noopener");
    } catch (error) {
      setErrorGeneral(await mensajeDeBlob(error));
    } finally {
      setBuscando(false);
    }
  }

  return (
    <SitePage
      titulo="Verificar boleta electrónica"
      descripcion="Comprueba una boleta electrónica emitida con nexo-factura. Ingresa los datos tal como aparecen impresos en el documento."
    >
      <Card className="p-6 sm:p-8">
        <form onSubmit={consultar} className="space-y-5">
          {errorGeneral && <Alert>{errorGeneral}</Alert>}
          {encontrada && (
            <Alert tone="success" icon={<FileCheck2 size={16} />}>
              Boleta encontrada: se abrió el documento en una pestaña nueva.
            </Alert>
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="RUT del emisor" error={errores.rutEmisor} hint="El RUT de la empresa que emitió la boleta.">
              <Input
                value={form.rutEmisor}
                placeholder="78.397.017-1"
                onChange={(e) => set("rutEmisor", e.target.value)}
              />
            </Field>
            <Field label="Tipo de documento" error={errores.tipo}>
              <Select value={form.tipo} onChange={(e) => set("tipo", e.target.value)}>
                <option value="39">Boleta electrónica (39)</option>
                <option value="41">Boleta exenta electrónica (41)</option>
              </Select>
            </Field>
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="Folio (N°)" error={errores.folio}>
              <Input
                inputMode="numeric"
                value={form.folio}
                placeholder="156"
                onChange={(e) => set("folio", e.target.value)}
              />
            </Field>
            <Field label="Fecha de emisión" error={errores.fecha}>
              <Input
                type="date"
                value={form.fecha}
                onChange={(e) => set("fecha", e.target.value)}
              />
            </Field>
            <Field label="Monto total" error={errores.total}>
              <Input
                inputMode="numeric"
                value={form.total}
                placeholder="29.800"
                onChange={(e) => set("total", e.target.value)}
              />
            </Field>
          </div>

          <div className="flex justify-end">
            <Button type="submit" disabled={buscando}>
              <Search size={15} />
              {buscando ? "Consultando…" : "Consultar boleta"}
            </Button>
          </div>
        </form>
      </Card>

      <p className="mt-8 text-sm leading-relaxed text-slate">
        Las boletas están disponibles para consulta en línea durante los 3 meses
        siguientes a su emisión, según lo establecido por el Servicio de
        Impuestos Internos. Si los datos no coinciden con ningún documento,
        revisa que estén escritos exactamente como aparecen en tu boleta.
      </p>
    </SitePage>
  );
}

/**
 * El endpoint responde PDF, así que también los errores llegan como blob; el
 * cuerpo JSON del backend ({mensaje}) hay que leerlo del blob a mano.
 */
async function mensajeDeBlob(error: unknown): Promise<string> {
  const generico = "No se pudo realizar la consulta. Inténtalo de nuevo en unos minutos.";
  if (typeof error === "object" && error !== null && "response" in error) {
    const respuesta = (error as { response?: { data?: unknown } }).response;
    if (respuesta?.data instanceof Blob) {
      try {
        const cuerpo = JSON.parse(await respuesta.data.text()) as { mensaje?: string };
        if (cuerpo.mensaje) return cuerpo.mensaje;
      } catch {
        // Cuerpo no-JSON (proxy caído, etc.): cae al mensaje genérico.
      }
    }
  }
  return generico;
}
