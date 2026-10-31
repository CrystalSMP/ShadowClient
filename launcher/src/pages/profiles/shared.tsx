import { Box, Flame, Gem, Mountain, Pickaxe, Shield, Sword, TreePine, Zap } from "lucide-react";
import type { LucideIcon } from "lucide-react";

export const ICONS: Record<string, LucideIcon> = {
  box: Box, sword: Sword, pickaxe: Pickaxe, tree: TreePine, flame: Flame, gem: Gem, zap: Zap, shield: Shield, mountain: Mountain,
};

export function ProfileIcon({ icon, size = 48 }: { icon?: string | null; size?: number }) {
  const Icon = ICONS[icon ?? "box"] ?? Box;
  return <div className="p-icon" style={{ width: size, height: size }}><Icon size={Math.round(size * 0.46)} strokeWidth={1.75} /></div>;
}

export function ago(iso: string | null | undefined): string {
  if (!iso) return "noch nie";
  const s = (Date.now() - new Date(iso).getTime()) / 1000;
  if (s < 60) return "gerade eben";
  if (s < 3600) return `vor ${Math.floor(s / 60)} Min.`;
  if (s < 86400) return `vor ${Math.floor(s / 3600)} Std.`;
  const d = Math.floor(s / 86400);
  if (d < 30) return d === 1 ? "gestern" : `vor ${d} Tagen`;
  const m = Math.floor(d / 30);
  return m < 12 ? `vor ${m} Mon.` : `vor ${Math.floor(m / 12)} J.`;
}

export function agoUnix(secs: number) { return ago(new Date(secs * 1000).toISOString()); }

export function playtime(secs?: number): string {
  if (!secs) return "0 Min.";
  const h = Math.floor(secs / 3600), m = Math.floor((secs % 3600) / 60);
  return h ? `${h} Std. ${m} Min.` : `${Math.max(1, m)} Min.`;
}

export function bytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 ** 2) return `${(n / 1024).toFixed(0)} KB`;
  if (n < 1024 ** 3) return `${(n / 1024 ** 2).toFixed(n < 10 * 1024 ** 2 ? 1 : 0)} MB`;
  return `${(n / 1024 ** 3).toFixed(1)} GB`;
}
