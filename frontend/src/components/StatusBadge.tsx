import { Badge } from "./ui";
import { ESTADO_LABEL, type EstadoDte } from "../lib/types";

const TONO: Record<EstadoDte, "neutral" | "cobalt" | "success" | "warn" | "danger"> = {
  BORRADOR: "neutral",
  FIRMADO: "cobalt",
  ENVIADO: "cobalt",
  ACEPTADO: "success",
  RECHAZADO: "danger",
  REPARO: "warn",
  ANULADO: "neutral",
};

export function StatusBadge({ estado }: { estado: EstadoDte }) {
  return <Badge tone={TONO[estado]}>{ESTADO_LABEL[estado]}</Badge>;
}
