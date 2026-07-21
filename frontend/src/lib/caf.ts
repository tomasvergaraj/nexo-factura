/**
 * Extracción ligera de los campos del XML de un CAF del SII (espejo del parseo
 * real del backend), compartida por la vista previa de Folios y el mock de la
 * API. La validación real (claves, RUT del emisor, duplicados) la hace el
 * backend; aquí basta con leer TD/D/H/RE/FA para mostrar o simular.
 */
export function camposCaf(xml: string) {
  if (!xml.includes("<AUTORIZACION") || !xml.includes("<CAF")) return null;
  const numero = (re: RegExp) => {
    const v = re.exec(xml)?.[1];
    return v ? Number(v) : undefined;
  };
  return {
    td: numero(/<TD>(\d+)<\/TD>/),
    desde: numero(/<D>(\d+)<\/D>/),
    hasta: numero(/<H>(\d+)<\/H>/),
    re: /<RE>([^<]+)<\/RE>/.exec(xml)?.[1],
    fa: /<FA>([^<]+)<\/FA>/.exec(xml)?.[1],
  };
}
