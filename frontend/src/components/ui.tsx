import { useEffect } from "react";
import type {
  ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes,
} from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "sm" | "md" | "lg";

const VARIANTS: Record<Variant, string> = {
  primary: "bg-cobalt text-white hover:bg-cobalt-dark active:bg-cobalt-dark",
  secondary: "bg-white text-ink border border-line hover:border-slate-soft hover:bg-mist/40",
  ghost: "bg-transparent text-slate hover:bg-mist hover:text-ink",
  danger: "bg-danger text-white hover:opacity-90",
};

const SIZES: Record<Size, string> = {
  sm: "h-9 px-4 text-sm",
  md: "h-10 px-5 text-sm",
  lg: "h-11 px-6 text-sm",
};

interface BtnProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

export function Button({ variant = "primary", size = "md", className = "", ...props }: BtnProps) {
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-full font-medium transition duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cobalt/40 focus-visible:ring-offset-1 disabled:opacity-50 disabled:cursor-not-allowed ${VARIANTS[variant]} ${SIZES[size]} ${className}`}
      {...props}
    />
  );
}

export function Card({ className = "", id, children }: { className?: string; id?: string; children: ReactNode }) {
  return (
    <div id={id} className={`rounded-lg border border-line bg-white ${className}`}>{children}</div>
  );
}

export function Field({ label, hint, error, children }: {
  label: string; hint?: string; error?: string; children: ReactNode;
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium text-ink-soft">{label}</span>
      {children}
      {error ? (
        <span className="mt-1.5 block text-xs text-danger">{error}</span>
      ) : hint ? (
        <span className="mt-1.5 block text-xs text-slate-soft">{hint}</span>
      ) : null}
    </label>
  );
}

export function Input({ className = "", ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={`h-10 w-full rounded-full border border-line bg-white px-4 text-sm text-ink transition-[border-color,box-shadow] placeholder:text-slate-soft focus:border-cobalt focus:outline-none focus:ring-2 focus:ring-cobalt/25 disabled:cursor-not-allowed disabled:bg-mist disabled:text-slate ${className}`}
      {...props}
    />
  );
}

export function Select({ className = "", children, ...props }: SelectHTMLAttributes<HTMLSelectElement> & { children: ReactNode }) {
  return (
    <select
      className={`h-10 w-full rounded-full border border-line bg-white px-4 text-sm text-ink transition-[border-color,box-shadow] focus:border-cobalt focus:outline-none focus:ring-2 focus:ring-cobalt/25 disabled:cursor-not-allowed disabled:bg-mist disabled:text-slate ${className}`}
      {...props}
    >
      {children}
    </select>
  );
}

export function Textarea({ className = "", ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      className={`w-full rounded-lg border border-line bg-white px-4 py-2.5 text-sm leading-relaxed text-ink transition-[border-color,box-shadow] placeholder:text-slate-soft focus:border-cobalt focus:outline-none focus:ring-2 focus:ring-cobalt/25 disabled:cursor-not-allowed disabled:bg-mist disabled:text-slate ${className}`}
      {...props}
    />
  );
}

export function Checkbox({ label, className = "", ...props }: InputHTMLAttributes<HTMLInputElement> & { label?: ReactNode }) {
  return (
    <label className={`inline-flex cursor-pointer items-center gap-2 text-sm text-ink ${className}`}>
      <input
        type="checkbox"
        className="h-4 w-4 rounded-[4px] border-line text-cobalt focus-visible:ring-2 focus-visible:ring-cobalt/40 focus-visible:ring-offset-1"
        {...props}
      />
      {label}
    </label>
  );
}

export function Modal({ open, onClose, title, children, footer }: {
  open: boolean;
  onClose: () => void;
  title?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-ink/30 p-4 backdrop-blur-sm"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        className="w-full max-w-lg rounded-xl border border-line bg-white shadow-xl"
      >
        {title && (
          <div className="border-b border-line px-6 py-4">
            <h2 className="font-display text-base font-semibold text-ink">{title}</h2>
          </div>
        )}
        <div className="px-6 py-5">{children}</div>
        {footer && (
          <div className="flex items-center justify-end gap-2 border-t border-line px-6 py-4">{footer}</div>
        )}
      </div>
    </div>
  );
}

export function EmptyState({ icon, titulo, descripcion, accion }: {
  icon?: ReactNode;
  titulo: string;
  descripcion?: string;
  accion?: ReactNode;
}) {
  return (
    <div className="grid place-items-center px-6 py-16 text-center">
      {icon && (
        <span className="flex h-12 w-12 items-center justify-center rounded-full bg-cobalt-soft text-cobalt">
          {icon}
        </span>
      )}
      <h3 className="mt-4 font-display text-base font-semibold text-ink">{titulo}</h3>
      {descripcion && <p className="mt-2 max-w-sm text-sm text-slate">{descripcion}</p>}
      {accion && <div className="mt-6">{accion}</div>}
    </div>
  );
}

export function Badge({ children, tone = "neutral" }: {
  children: ReactNode; tone?: "neutral" | "cobalt" | "success" | "warn" | "danger";
}) {
  const tones = {
    neutral: "bg-mist text-slate",
    cobalt: "bg-cobalt-soft text-cobalt",
    success: "bg-success-soft text-success",
    warn: "bg-warn-soft text-warn",
    danger: "bg-danger-soft text-danger",
  };
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ${tones[tone]}`}>
      {children}
    </span>
  );
}

export function Spinner({ className = "" }: { className?: string }) {
  return (
    <span className={`inline-block h-4 w-4 animate-spin rounded-full border-2 border-line border-t-cobalt ${className}`} />
  );
}

/** Estado de carga centrado (spinner + texto), para el cuerpo de páginas y cards. */
export function LoadingState({ mensaje, className = "" }: { mensaje?: string; className?: string }) {
  return (
    <div className={`grid place-items-center py-16 ${className}`}>
      <Spinner className="h-6 w-6" />
      {mensaje && <p className="mt-3 text-sm text-slate">{mensaje}</p>}
    </div>
  );
}

const ALERT_TONES = {
  danger: "border-danger/30 bg-danger-soft text-danger",
  warn: "border-warn/30 bg-warn-soft text-warn",
  success: "border-success/30 bg-success-soft text-success",
  info: "border-cobalt/30 bg-cobalt-soft text-cobalt",
} as const;

/** Aviso inline tonal (errores, confirmaciones). Icono opcional a la izquierda. */
export function Alert({ tone = "danger", icon, children, className = "" }: {
  tone?: keyof typeof ALERT_TONES;
  icon?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={`flex items-start gap-2 rounded-lg border px-4 py-2.5 text-sm ${ALERT_TONES[tone]} ${className}`}>
      {icon && <span className="mt-0.5 shrink-0">{icon}</span>}
      <span>{children}</span>
    </div>
  );
}

const TH_ALIGN = { left: "text-left", right: "text-right" } as const;

/** Celda de cabecera de tabla con el estilo canónico (micro-mayúsculas). */
export function Th({ align = "left", className = "", children }: {
  align?: keyof typeof TH_ALIGN; className?: string; children?: ReactNode;
}) {
  return (
    <th className={`px-4 py-3 text-xs font-medium uppercase tracking-wide text-slate-soft ${TH_ALIGN[align]} ${className}`}>
      {children}
    </th>
  );
}

/** Botón cuadrado solo-icono (acciones de fila). El hover/borde se pasa por className. */
export function IconButton({ className = "", ...props }: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      className={`inline-flex h-9 w-9 items-center justify-center rounded-full text-slate-soft transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cobalt/40 focus-visible:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50 ${className}`}
      {...props}
    />
  );
}

/** Cabecera de página: título + descripción opcional + acción primaria opcional. */
export function PageHeader({ titulo, descripcion, accion }: {
  titulo: string; descripcion?: string; accion?: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h2 className="font-display text-lg font-semibold text-ink">{titulo}</h2>
        {descripcion && <p className="text-sm text-slate">{descripcion}</p>}
      </div>
      {accion && <div className="shrink-0">{accion}</div>}
    </div>
  );
}
