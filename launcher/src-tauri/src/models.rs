//! Spiegel von shared/types/index.ts. Feldnamen in camelCase für das Frontend.
use serde::{Deserialize, Serialize};

use crate::error::{AppError, AppResult};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum ThemeMode {
    Dark,
    Light,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct LauncherSettings {
    pub schema_version: u32,
    pub theme: ThemeMode,
    pub accent_color: String,
    pub java_path: Option<String>,
    pub default_memory_mb: u32,
    pub close_on_launch: bool,
    /// Client-ID der eigenen Azure-App für den Microsoft-Login
    #[serde(default)]
    pub microsoft_client_id: Option<String>,
    /// Discord Rich Presence an/aus
    #[serde(default = "yes")]
    pub discord_presence: bool,
    /// Application ID der eigenen Discord-App (Developer Portal)
    #[serde(default)]
    pub discord_client_id: Option<String>,
}

fn yes() -> bool { true }

impl Default for LauncherSettings {
    fn default() -> Self {
        Self {
            schema_version: 1,
            theme: ThemeMode::Dark,
            accent_color: "#22d3ee".into(),
            java_path: None,
            default_memory_mb: 4096,
            close_on_launch: false,
            microsoft_client_id: None,
            discord_presence: true,
            discord_client_id: None,
        }
    }
}

impl LauncherSettings {
    pub fn validate(&self) -> AppResult<()> {
        let c = &self.accent_color;
        if c.len() != 7 || !c.starts_with('#') || !c[1..].chars().all(|ch| ch.is_ascii_hexdigit()) {
            return Err(AppError::Validation(format!("Ungültige Akzentfarbe: {c}")));
        }
        validate_memory(self.default_memory_mb)
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Profile {
    pub id: String,
    pub name: String,
    pub minecraft_version: String,
    pub fabric_loader_version: Option<String>,
    pub memory_mb: u32,
    pub jvm_args: Vec<String>,
    pub created_at: String,
    pub last_played: Option<String>,
    /// gespielte Zeit in Sekunden
    #[serde(default)]
    pub playtime_secs: u64,
    /// Icon-Schlüssel (z. B. "grass", "sword") oder null
    #[serde(default)]
    pub icon: Option<String>,
    /// Gruppe/Ordner zum Filtern
    #[serde(default)]
    pub group: Option<String>,
    /// mit ShadowClient-Mod starten (nur für die unterstützte Version)
    #[serde(default = "yes")]
    pub shadowclient: bool,
}

impl Profile {
    pub fn validate(&self) -> AppResult<()> {
        let name = self.name.trim();
        if name.is_empty() || name.chars().count() > 48 {
            return Err(AppError::Validation("Profilname muss 1–48 Zeichen lang sein".into()));
        }
        if !(self.minecraft_version == "1.21" || self.minecraft_version.starts_with("1.21.")) {
            return Err(AppError::Validation(format!(
                "Nur Minecraft 1.21.x wird unterstützt (erhalten: {})",
                self.minecraft_version
            )));
        }
        validate_memory(self.memory_mb)
    }
}

fn validate_memory(mb: u32) -> AppResult<()> {
    if !(1024..=65536).contains(&mb) {
        return Err(AppError::Validation(format!("RAM muss zwischen 1024 und 65536 MB liegen ({mb})")));
    }
    Ok(())
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SystemInfo {
    pub os: String,
    pub arch: String,
    pub total_memory_mb: u64,
    pub app_version: String,
    pub data_dir: String,
}
