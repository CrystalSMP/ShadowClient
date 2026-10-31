/**
 * Gemeinsame Datenmodelle für Launcher (TypeScript) und Backend.
 * Die Rust-Seite spiegelt diese Strukturen in launcher/src-tauri/src/models.rs,
 * die Fabric-Mod später in minecraft-mod/.../config. Änderungen hier immer
 * auch in shared/schemas/*.schema.json nachziehen.
 */

export type ThemeMode = "dark" | "light";

export interface LauncherSettings {
  schemaVersion: 1;
  theme: ThemeMode;
  /** Hex-Farbe, z. B. "#22d3ee" */
  accentColor: string;
  /** Pfad zu einer java(w).exe; null = automatisch ermitteln (Phase 2) */
  javaPath: string | null;
  /** Standard-RAM für neue Profile in MB */
  defaultMemoryMb: number;
  closeOnLaunch: boolean;
  /** Client-ID der eigenen Azure-App (Microsoft-Login); null = beim Build hinterlegt */
  microsoftClientId: string | null;
  discordPresence: boolean;
  discordClientId: string | null;
}

export interface Profile {
  id: string;
  name: string;
  /** Minecraft-Version, z. B. "1.21.4" */
  minecraftVersion: string;
  /** Fabric-Loader-Version; null = neueste stabile (Phase 2) */
  fabricLoaderVersion: string | null;
  memoryMb: number;
  jvmArgs: string[];
  createdAt: string;
  lastPlayed: string | null;
  /** gespielte Zeit in Sekunden */
  playtimeSecs?: number;
  /** Icon-Schlüssel */
  icon?: string | null;
  /** Gruppe zum Filtern */
  group?: string | null;
  /** mit ShadowClient-Mod starten (Standard: an) */
  shadowclient?: boolean;
}

export interface SystemInfo {
  os: string;
  arch: string;
  totalMemoryMb: number;
  appVersion: string;
  dataDir: string;
}

export const DEFAULT_SETTINGS: LauncherSettings = {
  schemaVersion: 1,
  theme: "dark",
  accentColor: "#22d3ee",
  javaPath: null,
  defaultMemoryMb: 4096,
  closeOnLaunch: false,
  microsoftClientId: null,
  discordPresence: true,
  discordClientId: null,
};

/** Minecraft-Konto. Tokens bleiben im Rust-Backend bzw. im Windows-Anmeldeinformations-Manager. */
export interface Account {
  uuid: string;
  name: string;
  skinUrl: string | null;
}

export interface DeviceCode {
  deviceCode: string;
  userCode: string;
  verificationUri: string;
  expiresIn: number;
  interval: number;
  message: string;
}

export type LaunchStage = "auth" | "meta" | "libraries" | "assets" | "java" | "mods" | "done" | "launch";

export interface LaunchProgress {
  stage: LaunchStage;
  message: string;
  done: number;
  total: number;
}

export interface GameLogLine {
  line: string;
  isErr: boolean;
}

export interface GameExit {
  code: number | null;
  success: boolean;
  hint: string | null;
  crashReport: string | null;
}

export interface Friend {
  uuid: string;
  name: string;
  online: boolean;
  server: string | null;
  status?: string | null;
  unread?: number;
}

export interface InboxMessage { id: number; sender: string; kind: "text" | "invite" | "party"; body: string | null; server: string | null; party_id: string | null; created_at: string; senderName: string }
export interface PartyMember { uuid: string; name: string; online: boolean; server: string | null; status: string | null }
export interface Party { id: string; leader: string; members: PartyMember[] }

export interface FriendsData {
  friends: Friend[];
  incoming: Friend[];
  outgoing: Friend[];
  inbox?: InboxMessage[];
  party?: Party | null;
}
