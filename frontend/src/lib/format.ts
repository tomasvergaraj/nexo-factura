// Formateo y validación para Chile.

/**
 * Fecha local de HOY como YYYY-MM-DD. OJO: no usar toISOString() para esto —
 * es UTC y en Chile (UTC-3/-4) por la tarde-noche ya corresponde al día (o mes)
 * siguiente.
 */
export function hoyIso(): string {
  const d = new Date();
  const mes = String(d.getMonth() + 1).padStart(2, "0");
  const dia = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${mes}-${dia}`;
}

/** Mes local actual como YYYY-MM (período tributario en curso). */
export function mesActual(): string {
  return hoyIso().slice(0, 7);
}

export const MENSAJE_RUT_INVALIDO = "RUT inválido: dígito verificador incorrecto";

const CLP = new Intl.NumberFormat("es-CL", {
  style: "currency",
  currency: "CLP",
  maximumFractionDigits: 0,
});

export function formatCLP(monto: number): string {
  return CLP.format(monto ?? 0);
}

export function formatNumero(n: number): string {
  return new Intl.NumberFormat("es-CL").format(n ?? 0);
}

export function formatFecha(iso: string): string {
  if (!iso) return "";
  const [y, m, d] = iso.split("T")[0].split("-");
  return `${d}-${m}-${y}`;
}

/** Da formato con puntos y guion: 76543210-9 -> 76.543.210-9 */
export function formatRut(rut: string): string {
  const limpio = rut.replace(/[^0-9kK]/g, "").toUpperCase();
  if (limpio.length < 2) return rut;
  const cuerpo = limpio.slice(0, -1);
  const dv = limpio.slice(-1);
  return `${cuerpo.replace(/\B(?=(\d{3})+(?!\d))/g, ".")}-${dv}`;
}

/** Valida un RUT chileno con el algoritmo módulo 11. */
export function validarRut(rut: string): boolean {
  const limpio = rut.replace(/[^0-9kK]/g, "").toUpperCase();
  if (limpio.length < 2) return false;
  const cuerpo = limpio.slice(0, -1);
  const dv = limpio.slice(-1);
  if (!/^\d+$/.test(cuerpo)) return false;

  let suma = 0;
  let multiplo = 2;
  for (let i = cuerpo.length - 1; i >= 0; i--) {
    suma += parseInt(cuerpo[i], 10) * multiplo;
    multiplo = multiplo === 7 ? 2 : multiplo + 1;
  }
  const resto = 11 - (suma % 11);
  const dvEsperado = resto === 11 ? "0" : resto === 10 ? "K" : String(resto);
  return dv === dvEsperado;
}
