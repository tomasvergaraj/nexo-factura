import { Hammer } from "lucide-react";
import { AppShell } from "../../components/app/AppShell";
import { Card } from "../../components/ui";

export function Placeholder({ titulo }: { titulo: string }) {
  return (
    <AppShell titulo={titulo}>
      <Card className="grid place-items-center px-6 py-20 text-center">
        <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-mist text-cobalt">
          <Hammer size={24} />
        </span>
        <h2 className="mt-4 font-display text-lg font-bold text-ink">{titulo} en construcción</h2>
        <p className="mt-2 max-w-sm text-sm text-slate">
          Este módulo es parte del alcance del producto. La estructura, navegación
          y modelo de datos ya están definidos en el backend.
        </p>
      </Card>
    </AppShell>
  );
}
