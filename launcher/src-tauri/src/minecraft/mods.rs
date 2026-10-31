//! Mods, die der Launcher automatisch pro Profil bereitstellt:
//! - Fabric API (Abhängigkeit der ShadowClient-Mod) über die offizielle Modrinth-API
//! - die ShadowClient-Mod selbst (gebündelt oder aus dem lokalen Gradle-Build)
use std::path::{Path, PathBuf};

use serde::Deserialize;

use super::net::{self, Download};
use crate::error::AppResult;

/// Minecraft-Version, für die die ShadowClient-Mod gebaut ist (minecraft-mod/gradle.properties).
pub const MOD_MC_VERSION: &str = "1.21.11";

#[derive(Debug, Deserialize)]
struct ModrinthVersion {
    files: Vec<ModrinthFile>,
}

#[derive(Debug, Deserialize)]
struct ModrinthFile {
    url: String,
    filename: String,
    primary: bool,
    size: u64,
    hashes: ModrinthHashes,
}

#[derive(Debug, Deserialize)]
struct ModrinthHashes {
    sha1: String,
}

fn encode_list(value: &str) -> String {
    // ["value"] URL-kodiert
    format!("%5B%22{}%22%5D", value)
}

async fn existing_with_prefix(dir: &Path, prefix: &str) -> Vec<PathBuf> {
    let mut out = Vec::new();
    if let Ok(mut rd) = tokio::fs::read_dir(dir).await {
        while let Ok(Some(e)) = rd.next_entry().await {
            let name = e.file_name().to_string_lossy().to_lowercase();
            if name.starts_with(prefix) && name.ends_with(".jar") {
                out.push(e.path());
            }
        }
    }
    out
}

/// Installiert die neueste Fabric API für die MC-Version. Ohne Internet bleibt eine vorhandene Version erhalten.
pub async fn ensure_fabric_api(mods_dir: &Path, mc_version: &str) -> AppResult<String> {
    let url = format!(
        "https://api.modrinth.com/v2/project/fabric-api/version?loaders={}&game_versions={}",
        encode_list("fabric"),
        encode_list(mc_version)
    );
    let existing = existing_with_prefix(mods_dir, "fabric-api").await;
    let versions: Vec<ModrinthVersion> = match net::get_json(&url).await {
        Ok(v) => v,
        Err(e) if !existing.is_empty() => {
            log::warn!("Modrinth nicht erreichbar, behalte vorhandene Fabric API: {e}");
            return Ok("vorhanden (offline)".into());
        }
        Err(e) => return Err(e),
    };
    let Some(file) = versions.first().and_then(|v| v.files.iter().find(|f| f.primary).or(v.files.first())) else {
        return Err(crate::error::AppError::Install(format!("Keine Fabric API für Minecraft {mc_version} gefunden")));
    };
    let target = mods_dir.join(&file.filename);
    // Alte Versionen entfernen, damit keine doppelten Mods geladen werden
    for old in existing.iter().filter(|p| **p != target) {
        let _ = tokio::fs::remove_file(old).await;
    }
    net::download_all(
        vec![Download { url: file.url.clone(), path: target, sha1: Some(file.hashes.sha1.clone()), size: Some(file.size) }],
        1,
        |_, _| {},
    )
    .await?;
    Ok(file.filename.clone())
}

/// Sucht die ShadowClient-Mod: zuerst neben dem Launcher (Release-Bundle), dann im lokalen Gradle-Build.
/// Sucht die neueste ShadowClient-Mod-JAR an allen bekannten Orten:
/// 1. neben dem Launcher (Release-Bundle), 2. `%APPDATA%/dev.shadowclient.launcher/shadowclient-mod/`,
/// 3. Gradle-Build im Repo (`minecraft-mod/build/libs`), 4. eigener Mod-Ordner daneben oder in Downloads (`ShadowClient-Mod*/build/libs`).
pub async fn find_shadowclient_mod(resource_dir: Option<PathBuf>, data_dir: Option<PathBuf>) -> Option<PathBuf> {
    if let Some(dir) = &resource_dir {
        let bundled = dir.join("mods").join("shadowclient.jar");
        if tokio::fs::try_exists(&bundled).await.unwrap_or(false) {
            return Some(bundled);
        }
    }
    let mut dirs: Vec<PathBuf> = Vec::new();
    if let Some(d) = &data_dir { dirs.push(d.join("shadowclient-mod")); }
    let repo = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    dirs.push(repo.join("minecraft-mod/build/libs"));
    let mut parents = vec![repo.join("..")];
    for var in ["USERPROFILE", "HOME"] {
        if let Ok(h) = std::env::var(var) { parents.push(Path::new(&h).join("Downloads")); parents.push(Path::new(&h).join("Desktop")); }
    }
    for parent in parents {
        if let Ok(mut rd) = tokio::fs::read_dir(&parent).await {
            while let Ok(Some(e)) = rd.next_entry().await {
                let n = e.file_name().to_string_lossy().to_lowercase();
                if n.starts_with("shadowclient-mod") || n == "shadowclient" {
                    dirs.push(e.path().join("build/libs"));
                    dirs.push(e.path().join("ShadowClient-Mod/build/libs"));  // entpackt mit Unterordner
                    dirs.push(e.path().join("minecraft-mod/build/libs"));
                }
            }
        }
    }
    let mut best: Option<(std::time::SystemTime, PathBuf)> = None;
    for libs in dirs {
        let Ok(mut rd) = tokio::fs::read_dir(&libs).await else { continue };
        while let Ok(Some(e)) = rd.next_entry().await {
            let name = e.file_name().to_string_lossy().to_lowercase();
            if name.starts_with("shadowclient") && name.ends_with(".jar") && !name.contains("-sources") && !name.contains("-dev") {
                let Some(modified) = e.metadata().await.ok().and_then(|m| m.modified().ok()) else { continue };
                if best.as_ref().map_or(true, |(t, _)| modified > *t) { best = Some((modified, e.path())); }
            }
        }
    }
    best.map(|(_, p)| p)
}

/// Kopiert die Mod als `shadowclient.jar` ins Profil (nur wenn sich die Datei geändert hat).
pub async fn install_shadowclient_mod(source: &Path, mods_dir: &Path) -> AppResult<()> {
    let target = mods_dir.join("shadowclient.jar");
    let src = tokio::fs::read(source).await?;
    if let Ok(existing) = tokio::fs::read(&target).await {
        if existing == src {
            return Ok(());
        }
    }
    net::write_atomic(&target, &src).await
}

#[cfg(test)]
mod tests {
    #[test]
    fn modrinth_list_encoding() {
        assert_eq!(super::encode_list("1.21.4"), "%5B%221.21.4%22%5D");
    }

    #[test]
    fn parses_modrinth_versions() {
        let json = r#"[{"version_number":"0.119.2+1.21.4","files":[{"url":"https://cdn.modrinth.com/x.jar","filename":"fabric-api-0.119.2+1.21.4.jar","primary":true,"size":123,"hashes":{"sha1":"ab","sha512":"cd"}}]}]"#;
        let v: Vec<super::ModrinthVersion> = serde_json::from_str(json).unwrap();
        assert_eq!(v[0].files[0].filename, "fabric-api-0.119.2+1.21.4.jar");
    }
}
