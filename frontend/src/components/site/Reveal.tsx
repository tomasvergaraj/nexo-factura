import { useEffect, useRef } from "react";
import type { CSSProperties, ReactNode } from "react";

/**
 * Revela su contenido al entrar al viewport (IntersectionObserver + la
 * utilidad CSS .reveal). `delay` escalona la entrada de hermanos, en ms.
 * Con prefers-reduced-motion el CSS muestra todo de inmediato.
 */
export function Reveal({ children, delay = 0, className = "" }: {
  children: ReactNode;
  delay?: number;
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (e.isIntersecting) {
            el.classList.add("is-visible");
            io.disconnect();
          }
        }
      },
      { threshold: 0.15, rootMargin: "0px 0px -48px 0px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, []);

  return (
    <div
      ref={ref}
      className={`reveal ${className}`}
      style={{ "--reveal-delay": `${delay}ms` } as CSSProperties}
    >
      {children}
    </div>
  );
}
