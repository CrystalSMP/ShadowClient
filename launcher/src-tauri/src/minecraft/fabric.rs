//! Fabric-Loader über die offizielle Meta-API (https://meta.fabricmc.net).
use serde::Deserialize;

use super::net;
use super::version::VersionJson;
use crate::error::{AppError, AppResult};

const META: &str = "https://meta.fabricmc.net/v2";

#[derive(Debug, Deserialize)]
struct LoaderEntry {
    loader: LoaderInfo,
}

#[derive(Debug, Deserialize)]
struct LoaderInfo {
    version: String,
    stable: bool,
}

/// Neueste stabile Loader-Version für eine Minecraft-Version.
pub async fn latest_stable_loader(mc_version: &str) -> AppResult<String> {
    let list: Vec<LoaderEntry> = net::get_json(&format!("{META}/versions/loader/{mc_version}")).await?;
    if list.is_empty() {
        return Err(AppError::Install(format!("Fabric unterstützt Minecraft {mc_version} (noch) nicht")));
    }
    Ok(list
        .iter()
        .find(|e| e.loader.stable)
        .unwrap_or(&list[0])
        .loader
        .version
        .clone())
}

/// Launcher-Profil (Bibliotheken, Hauptklasse, Argumente) für MC + Loader.
pub async fn profile_json_raw(mc_version: &str, loader: &str) -> AppResult<String> {
    let url = format!("{META}/versions/loader/{mc_version}/{loader}/profile/json");
    let resp = net::client().get(&url).send().await?;
    if resp.status() == reqwest::StatusCode::NOT_FOUND || resp.status() == reqwest::StatusCode::BAD_REQUEST {
        return Err(AppError::Install(format!("Fabric-Loader {loader} ist nicht mit Minecraft {mc_version} kompatibel")));
    }
    Ok(resp.error_for_status()?.text().await?)
}

pub fn parse_profile(raw: &str) -> AppResult<VersionJson> {
    Ok(serde_json::from_str(raw)?)
}

#[cfg(test)]
mod tests {
    #[test]
    fn parses_fabric_profile() {
        let raw = r#"{"id":"fabric-loader-0.16.10-1.21.4","inheritsFrom":"1.21.4","type":"release",
          "mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient",
          "arguments":{"game":[],"jvm":["-DFabricMcEmu= net.minecraft.client.main.Main "]},
          "libraries":[{"name":"org.ow2.asm:asm:9.7.1","url":"https://maven.fabricmc.net/","sha1":"abc","size":10},
                       {"name":"net.fabricmc:fabric-loader:0.16.10","url":"https://maven.fabricmc.net/"}]}"#;
        let v = super::parse_profile(raw).unwrap();
        assert_eq!(v.main_class, "net.fabricmc.loader.impl.launch.knot.KnotClient");
        assert_eq!(v.libraries.len(), 2);
        assert_eq!(v.libraries[0].url.as_deref(), Some("https://maven.fabricmc.net/"));
    }
}
