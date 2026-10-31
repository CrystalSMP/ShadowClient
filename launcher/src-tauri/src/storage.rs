//! Asynchrone JSON-Persistenz im App-Datenverzeichnis
//! (Windows: %APPDATA%\dev.shadowclient.launcher).
use std::path::{Path, PathBuf};

use serde::{de::DeserializeOwned, Serialize};
use tokio::fs;

use crate::error::AppResult;

pub async fn read_json_or_default<T: DeserializeOwned + Default>(path: &Path) -> AppResult<T> {
    match fs::read(path).await {
        Ok(bytes) => match serde_json::from_slice(&bytes) {
            Ok(v) => Ok(v),
            Err(e) => {
                // Beschädigte Datei sichern statt den Launcher zu blockieren.
                let backup = path.with_extension("json.broken");
                log::warn!("{} ist ungültig ({e}), sichere nach {}", path.display(), backup.display());
                let _ = fs::rename(path, &backup).await;
                Ok(T::default())
            }
        },
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(T::default()),
        Err(e) => Err(e.into()),
    }
}

/// Schreibt atomar: erst in eine Temp-Datei, dann umbenennen.
pub async fn write_json<T: Serialize>(path: &Path, value: &T) -> AppResult<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).await?;
    }
    let tmp: PathBuf = path.with_extension("json.tmp");
    fs::write(&tmp, serde_json::to_vec_pretty(value)?).await?;
    fs::rename(&tmp, path).await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::LauncherSettings;

    #[tokio::test]
    async fn roundtrip_and_corrupt_recovery() {
        let dir = tempfile::tempdir().unwrap();
        let p = dir.path().join("settings.json");

        let missing: LauncherSettings = read_json_or_default(&p).await.unwrap();
        assert_eq!(missing, LauncherSettings::default());

        let mut s = LauncherSettings::default();
        s.accent_color = "#ff0088".into();
        write_json(&p, &s).await.unwrap();
        let back: LauncherSettings = read_json_or_default(&p).await.unwrap();
        assert_eq!(back.accent_color, "#ff0088");

        fs::write(&p, b"{kaputt").await.unwrap();
        let rec: LauncherSettings = read_json_or_default(&p).await.unwrap();
        assert_eq!(rec, LauncherSettings::default());
        assert!(p.with_extension("json.broken").exists());
    }
}
