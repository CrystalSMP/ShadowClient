//! Java-Laufzeit: offizielle Mojang-Runtime automatisch herunterladen
//! (dieselbe Quelle wie der offizielle Launcher) oder eigenen Pfad verwenden.
use std::collections::HashMap;
use std::path::{Path, PathBuf};

use serde::Deserialize;

use super::net::{self, Download};
use crate::error::{AppError, AppResult};

const RUNTIME_INDEX: &str =
    "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

#[derive(Debug, Deserialize)]
struct RuntimeEntry {
    manifest: ManifestRef,
}

#[derive(Debug, Deserialize)]
struct ManifestRef {
    url: String,
}

#[derive(Debug, Deserialize)]
struct RuntimeManifest {
    files: HashMap<String, RuntimeFile>,
}

#[derive(Debug, Deserialize)]
struct RuntimeFile {
    #[serde(rename = "type")]
    kind: String,
    #[serde(default)]
    executable: bool,
    downloads: Option<RuntimeDownloads>,
}

#[derive(Debug, Deserialize)]
struct RuntimeDownloads {
    raw: RawDownload,
}

#[derive(Debug, Deserialize)]
struct RawDownload {
    url: String,
    sha1: String,
    size: u64,
}

fn platform_key() -> &'static str {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("windows", "aarch64") => "windows-arm64",
        ("windows", "x86") => "windows-x86",
        ("windows", _) => "windows-x64",
        ("macos", "aarch64") => "mac-os-arm64",
        ("macos", _) => "mac-os",
        _ => "linux",
    }
}

pub fn java_executable(runtime_dir: &Path) -> PathBuf {
    if cfg!(windows) {
        runtime_dir.join("bin").join("javaw.exe")
    } else if cfg!(target_os = "macos") {
        runtime_dir.join("jre.bundle/Contents/Home/bin/java")
    } else {
        runtime_dir.join("bin").join("java")
    }
}

/// Stellt die benötigte Runtime (z. B. `java-runtime-delta` für 1.21) sicher und gibt den Pfad zur Java-Datei zurück.
pub async fn ensure_runtime(
    runtimes_root: &Path,
    component: &str,
    on_progress: impl FnMut(usize, usize),
) -> AppResult<PathBuf> {
    let dir = runtimes_root.join(component);
    let exe = java_executable(&dir);
    let marker = dir.join(".shadowclient-complete");

    if tokio::fs::try_exists(&marker).await.unwrap_or(false) && tokio::fs::try_exists(&exe).await.unwrap_or(false) {
        return Ok(exe);
    }

    let index: HashMap<String, HashMap<String, Vec<RuntimeEntry>>> = net::get_json(RUNTIME_INDEX).await?;
    let entry = index
        .get(platform_key())
        .and_then(|p| p.get(component))
        .and_then(|v| v.first())
        .ok_or_else(|| AppError::Install(format!("Keine Java-Runtime „{component}“ für {} verfügbar", platform_key())))?;
    let manifest: RuntimeManifest = net::get_json(&entry.manifest.url).await?;

    let mut downloads = Vec::new();
    let mut executables = Vec::new();
    for (rel, f) in &manifest.files {
        let path = dir.join(rel);
        match f.kind.as_str() {
            "directory" => tokio::fs::create_dir_all(&path).await?,
            "file" => {
                if let Some(d) = &f.downloads {
                    downloads.push(Download { url: d.raw.url.clone(), path: path.clone(), sha1: Some(d.raw.sha1.clone()), size: Some(d.raw.size) });
                    if f.executable {
                        executables.push(path);
                    }
                }
            }
            _ => {} // Symlinks werden für den Start nicht benötigt (nur macOS-Legal-Dateien)
        }
    }
    net::download_all(downloads, 16, on_progress).await?;

    #[cfg(unix)]
    for p in executables {
        use std::os::unix::fs::PermissionsExt;
        tokio::fs::set_permissions(&p, std::fs::Permissions::from_mode(0o755)).await?;
    }
    #[cfg(not(unix))]
    let _ = executables;

    if !tokio::fs::try_exists(&exe).await.unwrap_or(false) {
        return Err(AppError::Install(format!("Java wurde installiert, aber {} fehlt", exe.display())));
    }
    tokio::fs::write(&marker, b"ok").await?;
    Ok(exe)
}
