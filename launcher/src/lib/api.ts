import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import { openUrl } from "@tauri-apps/plugin-opener";
import {
  DEFAULT_SETTINGS, type Account, type DeviceCode, type GameExit, type GameLogLine,
  type FriendsData, type LauncherSettings, type LaunchProgress, type Profile, type SystemInfo,
} from "@shared/types";

export type InstanceKind = "mods" | "resourcepacks" | "shaderpacks" | "datapacks" | "saves" | "screenshots" | "logs";
export interface InstanceEntry { file: string; name: string; version: string | null; description: string | null; authors: string | null; icon: string | null; enabled: boolean; size: number; modified: number; path: string }
export interface InstanceOverview { sizeBytes: number; mods: number; resourcepacks: number; shaderpacks: number; datapacks: number; saves: number; screenshots: number }

/** true, wenn das Frontend in Tauri läuft (nicht im normalen Browser via `npm run dev`). */
export const isTauri = typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;

// Browser-Fallback nur für UI-Entwicklung: hält Daten im Speicher, nichts wird persistiert.
const mem = { settings: { ...DEFAULT_SETTINGS } as LauncherSettings, profiles: [] as Profile[] };

export const api = {
  systemInfo: (): Promise<SystemInfo> =>
    isTauri
      ? invoke("get_system_info")
      : Promise.resolve({ os: "browser", arch: "-", totalMemoryMb: 8192, appVersion: "dev", dataDir: "(Browser-Modus: nicht persistent)" }),
  loadSettings: (): Promise<LauncherSettings> =>
    isTauri ? invoke("load_settings") : Promise.resolve({ ...mem.settings }),
  saveSettings: (settings: LauncherSettings): Promise<void> =>
    isTauri ? invoke("save_settings", { settings }) : Promise.resolve(void (mem.settings = settings)),
  loadProfiles: (): Promise<Profile[]> =>
    isTauri ? invoke("load_profiles") : Promise.resolve([...mem.profiles]),
  saveProfiles: (profiles: Profile[]): Promise<void> =>
    isTauri ? invoke("save_profiles", { profiles }) : Promise.resolve(void (mem.profiles = profiles)),

  // Phase 2 – nur in der Desktop-App verfügbar
  authStart: (): Promise<DeviceCode> => desktopOnly(() => invoke("auth_start")),
  authComplete: (code: DeviceCode): Promise<Account> => desktopOnly(() => invoke("auth_complete", { code })),
  authCancel: (): Promise<void> => (isTauri ? invoke("auth_cancel") : Promise.resolve()),
  listAccounts: (): Promise<Account[]> => (isTauri ? invoke("list_accounts") : Promise.resolve([])),
  removeAccount: (uuid: string): Promise<void> => desktopOnly(() => invoke("remove_account", { uuid })),
  isGameRunning: (): Promise<boolean> => (isTauri ? invoke("is_game_running") : Promise.resolve(false)),
  launchProfile: (profileId: string, accountUuid: string, server?: string): Promise<void> =>
    desktopOnly(() => invoke("launch_profile", { profileId, accountUuid, server: server ?? null })),
  openInstanceFolder: (profileId: string): Promise<void> => desktopOnly(() => invoke("open_instance_folder", { profileId })),
  friendsList: (accountUuid: string): Promise<FriendsData> => desktopOnly(() => invoke("friends_list", { accountUuid })),
  friendsAction: (accountUuid: string, action: "add" | "accept" | "remove", value: string): Promise<string | null> =>
    desktopOnly(() => invoke("friends_action", { accountUuid, action, value })),
  contentInstalled: (profileId: string): Promise<{ projectId: string; filename: string; kind: string }[]> =>
    isTauri ? invoke("content_installed", { profileId }) : Promise.resolve([]),
  contentInstall: (profileId: string, projectId: string, kind: string): Promise<string[]> =>
    desktopOnly(() => invoke("content_install", { profileId, projectId, kind })),
  contentRemove: (profileId: string, projectId: string): Promise<void> =>
    desktopOnly(() => invoke("content_remove", { profileId, projectId })),
  cosmeticsGet: (accountUuid: string): Promise<{ selected: string | null; builtin: string[]; mine: { id: string; status: "pending" | "approved"; url: string }[] }> =>
    desktopOnly(() => invoke("cosmetics_get", { accountUuid })),
  cosmeticsSelect: (accountUuid: string, cape: string | null): Promise<string | null> =>
    desktopOnly(() => invoke("cosmetics_select", { accountUuid, cape })),
  cosmeticsUpload: (accountUuid: string, png: string): Promise<string | null> =>
    desktopOnly(() => invoke("cosmetics_upload", { accountUuid, png })),
  instanceOverview: (profileId: string): Promise<InstanceOverview> =>
    isTauri ? invoke("instance_overview", { profileId }) : Promise.resolve({ sizeBytes: 0, mods: 0, resourcepacks: 0, shaderpacks: 0, datapacks: 0, saves: 0, screenshots: 0 }),
  instanceList: (profileId: string, kind: InstanceKind): Promise<InstanceEntry[]> =>
    isTauri ? invoke("instance_list", { profileId, kind }) : Promise.resolve([]),
  instanceToggle: (profileId: string, kind: InstanceKind, file: string, enabled: boolean): Promise<string> =>
    desktopOnly(() => invoke("instance_toggle", { profileId, kind, file, enabled })),
  instanceRemove: (profileId: string, kind: InstanceKind, file: string): Promise<void> =>
    desktopOnly(() => invoke("instance_remove", { profileId, kind, file })),
  instanceReadLog: (profileId: string, file: string): Promise<string> =>
    desktopOnly(() => invoke("instance_read_log", { profileId, file })),
  instanceOpen: (profileId: string, kind?: InstanceKind): Promise<void> =>
    desktopOnly(() => invoke("instance_open", { profileId, kind: kind ?? null })),
  onlineGet: <T = unknown>(accountUuid: string, path: string): Promise<T> =>
    desktopOnly(() => invoke("online_get", { accountUuid, path })),
  onlinePost: (accountUuid: string, path: string, payload: Record<string, unknown> = {}): Promise<string | null> =>
    desktopOnly(() => invoke("online_post", { accountUuid, path, payload })),
  openUrl: (url: string): Promise<void> => (isTauri ? openUrl(url) : Promise.resolve(void window.open(url, "_blank"))),
};

function desktopOnly<T>(fn: () => Promise<T>): Promise<T> {
  return isTauri ? fn() : Promise.reject("Nur in der Desktop-App verfügbar (npm run tauri dev).");
}

export interface LaunchEvents {
  "launch-progress": LaunchProgress;
  "game-log": GameLogLine;
  "game-started": string;
  "game-exit": GameExit;
  "launch-error": string;
  "launch-warning": string;
}

export async function on<K extends keyof LaunchEvents>(event: K, cb: (payload: LaunchEvents[K]) => void): Promise<UnlistenFn> {
  if (!isTauri) return () => {};
  return listen<LaunchEvents[K]>(event, (e) => cb(e.payload));
}

const MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const FALLBACK_VERSIONS = ["1.21.11", "1.21.10", "1.21.8", "1.21.4", "1.21.1", "1.21"];
/** Version, für die die ShadowClient-Mod gebaut ist */
export const MOD_VERSION = "1.21.11";

/** Liest die offiziellen Release-Versionen 1.21.x aus Mojangs Versionsmanifest. */
export async function fetchMinecraftVersions(): Promise<{ versions: string[]; offline: boolean }> {
  try {
    const res = await fetch(MANIFEST);
    if (!res.ok) throw new Error(String(res.status));
    const data: { versions: { id: string; type: string }[] } = await res.json();
    const versions = data.versions
      .filter((v) => v.type === "release" && /^1\.21(\.\d+)?$/.test(v.id))
      .map((v) => v.id);
    return { versions: versions.length ? versions : FALLBACK_VERSIONS, offline: false };
  } catch {
    return { versions: FALLBACK_VERSIONS, offline: true };
  }
}

export function errorText(e: unknown): string {
  return typeof e === "string" ? e : e instanceof Error ? e.message : JSON.stringify(e);
}
