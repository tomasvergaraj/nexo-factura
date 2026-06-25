import type { ReactNode } from "react";
import { Card } from "../ui";

export function Kpi({ label, valor, sub, icono }: {
  label: string; valor: string; sub?: ReactNode; icono: ReactNode;
}) {
  return (
    <Card className="p-5">
      <div className="flex items-start justify-between">
        <span className="text-sm font-medium text-slate">{label}</span>
        <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-mist text-cobalt">
          {icono}
        </span>
      </div>
      <div className="mt-3 font-display text-2xl font-bold tracking-tight text-ink tnum">{valor}</div>
      {sub && <div className="mt-1 text-xs text-slate-soft">{sub}</div>}
    </Card>
  );
}
