import type { ReactNode } from "react";

export default function Card({ title, children, className = "" }: { title?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`card ${className}`}>
      {title && <h3 className="card-title">{title}</h3>}
      {children}
    </section>
  );
}

export function PhaseBadge({ phase }: { phase: number }) {
  return <span className="badge">Kommt in Phase {phase}</span>;
}
