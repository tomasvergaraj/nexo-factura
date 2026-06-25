export function Logo({ size = 32, withWordmark = true, light = false }: {
  size?: number; withWordmark?: boolean; light?: boolean;
}) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <img src="/logo.png" alt="Nexo Factura" width={size} height={size} className="shrink-0" />
      {withWordmark && (
        <span
          className={`font-display font-semibold leading-none ${light ? "text-white" : "text-ink"}`}
          style={{ fontSize: size * 0.62 }}
        >
          Nexo<span className="text-cobalt">Factura</span>
        </span>
      )}
    </span>
  );
}
