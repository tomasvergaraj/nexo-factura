import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "sm" | "md" | "lg";

const VARIANTS: Record<Variant, string> = {
  primary: "bg-cobalt text-white hover:bg-cobalt-dark shadow-sm",
  secondary: "bg-white text-ink border border-line hover:border-slate-soft",
  ghost: "bg-transparent text-slate hover:bg-mist",
  danger: "bg-danger text-white hover:opacity-90",
};

const SIZES: Record<Size, string> = {
  sm: "h-9 px-3 text-sm",
  md: "h-11 px-5 text-sm",
  lg: "h-12 px-6 text-base",
};

interface BtnProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

export function Button({ variant = "primary", size = "md", className = "", ...props }: BtnProps) {
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed ${VARIANTS[variant]} ${SIZES[size]} ${className}`}
      {...props}
    />
  );
}

export function Card({ className = "", children }: { className?: string; children: ReactNode }) {
  return (
    <div className={`rounded-[14px] border border-line bg-white ${className}`}>{children}</div>
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
        <span className="mt-1 block text-xs text-danger">{error}</span>
      ) : hint ? (
        <span className="mt-1 block text-xs text-slate-soft">{hint}</span>
      ) : null}
    </label>
  );
}

export function Input({ className = "", ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={`h-11 w-full rounded-lg border border-line bg-white px-3 text-sm text-ink placeholder:text-slate-soft focus:border-cobalt focus:ring-0 ${className}`}
      {...props}
    />
  );
}

export function Select({ className = "", children, ...props }: SelectHTMLAttributes<HTMLSelectElement> & { children: ReactNode }) {
  return (
    <select
      className={`h-11 w-full rounded-lg border border-line bg-white px-3 text-sm text-ink focus:border-cobalt focus:ring-0 ${className}`}
      {...props}
    >
      {children}
    </select>
  );
}

export function Badge({ children, tone = "neutral" }: {
  children: ReactNode; tone?: "neutral" | "cobalt" | "success" | "warn" | "danger";
}) {
  const tones = {
    neutral: "bg-mist text-slate",
    cobalt: "bg-[#e7f2fc] text-cobalt",
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
