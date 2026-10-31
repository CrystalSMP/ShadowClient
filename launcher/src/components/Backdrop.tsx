import { useMemo } from "react";

/** Dezenter animierter Hintergrund: Farbschleier und aufsteigende Pixel. */
export default function Backdrop() {
  const pixels = useMemo(
    () =>
      Array.from({ length: 28 }, (_, i) => ({
        left: Math.random() * 100,
        size: 2 + Math.round(Math.random() * 4),
        delay: -Math.random() * 18,
        duration: 14 + Math.random() * 14,
        opacity: 0.15 + Math.random() * 0.35,
        key: i,
      })),
    [],
  );
  return (
    <div className="backdrop" aria-hidden>
      <div className="backdrop-aurora" />
      {pixels.map((p) => (
        <span
          key={p.key}
          className="backdrop-pixel"
          style={{ left: `${p.left}%`, width: p.size, height: p.size, opacity: p.opacity, animationDelay: `${p.delay}s`, animationDuration: `${p.duration}s` }}
        />
      ))}
    </div>
  );
}
