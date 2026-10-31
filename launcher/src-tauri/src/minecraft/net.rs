//! HTTP-Client und verifizierte Downloads.
use std::path::Path;
use std::sync::OnceLock;
use std::time::Duration;

use futures::StreamExt;
use sha1::{Digest, Sha1};
use tokio::{fs, io::AsyncWriteExt};

use crate::error::{AppError, AppResult};

pub fn client() -> &'static reqwest::Client {
    static CLIENT: OnceLock<reqwest::Client> = OnceLock::new();
    CLIENT.get_or_init(|| {
        reqwest::Client::builder()
            .user_agent(concat!("ShadowClient/", env!("CARGO_PKG_VERSION")))
            .connect_timeout(Duration::from_secs(15))
            .timeout(Duration::from_secs(300))
            .build()
            .expect("HTTP-Client")
    })
}

pub async fn get_json<T: serde::de::DeserializeOwned>(url: &str) -> AppResult<T> {
    Ok(client().get(url).send().await?.error_for_status()?.json().await?)
}

pub fn sha1_hex(bytes: &[u8]) -> String {
    let mut h = Sha1::new();
    h.update(bytes);
    h.finalize().iter().map(|b| format!("{b:02x}")).collect()
}

/// Eine herunterzuladende Datei.
#[derive(Debug, Clone)]
pub struct Download {
    pub url: String,
    pub path: std::path::PathBuf,
    pub sha1: Option<String>,
    pub size: Option<u64>,
}

/// Prüft, ob die Datei schon vorhanden ist. Schnelle Größenprüfung; SHA-1 nur
/// wenn keine Größe bekannt ist (vermeidet tausende Hash-Berechnungen pro Start).
async fn is_present(d: &Download) -> bool {
    let Ok(meta) = fs::metadata(&d.path).await else { return false };
    match (d.size, &d.sha1) {
        (Some(size), _) => meta.len() == size,
        (None, Some(sha)) => fs::read(&d.path).await.map(|b| &sha1_hex(&b) == sha).unwrap_or(false),
        (None, None) => meta.len() > 0,
    }
}

async fn download_one(d: &Download) -> AppResult<()> {
    if is_present(d).await {
        return Ok(());
    }
    let mut last_err = None;
    for attempt in 1..=3 {
        match fetch_verified(d).await {
            Ok(()) => return Ok(()),
            Err(e) => {
                log::warn!("Download {} fehlgeschlagen (Versuch {attempt}/3): {e}", d.url);
                last_err = Some(e);
                tokio::time::sleep(Duration::from_millis(500 * attempt)).await;
            }
        }
    }
    Err(last_err.unwrap())
}

async fn fetch_verified(d: &Download) -> AppResult<()> {
    let bytes = client().get(&d.url).send().await?.error_for_status()?.bytes().await?;
    if let Some(expected) = &d.sha1 {
        let actual = sha1_hex(&bytes);
        if &actual != expected {
            return Err(AppError::Install(format!("Prüfsumme falsch für {} (erwartet {expected}, erhalten {actual})", d.url)));
        }
    }
    write_atomic(&d.path, &bytes).await
}

pub async fn write_atomic(path: &Path, bytes: &[u8]) -> AppResult<()> {
    if let Some(p) = path.parent() {
        fs::create_dir_all(p).await?;
    }
    let tmp = path.with_extension("part");
    let mut f = fs::File::create(&tmp).await?;
    f.write_all(bytes).await?;
    f.flush().await?;
    drop(f);
    fs::rename(&tmp, path).await?;
    Ok(())
}

/// Lädt viele Dateien parallel; `on_progress(fertig, gesamt)` wird nach jeder Datei aufgerufen.
pub async fn download_all(items: Vec<Download>, parallel: usize, mut on_progress: impl FnMut(usize, usize)) -> AppResult<()> {
    let total = items.len();
    let mut done = 0;
    let mut stream = futures::stream::iter(items.into_iter().map(|d| async move { download_one(&d).await }))
        .buffer_unordered(parallel);
    while let Some(res) = stream.next().await {
        res?;
        done += 1;
        if done % 25 == 0 || done == total {
            on_progress(done, total);
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    #[test]
    fn sha1_known_value() {
        assert_eq!(super::sha1_hex(b"abc"), "a9993e364706816aba3e25717850c26c9cd0d89d");
    }
}
