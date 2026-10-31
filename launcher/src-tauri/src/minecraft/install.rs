//! Installiert Minecraft + Fabric + Java für ein Profil und liefert einen Startplan.
use std::collections::HashSet;
use std::path::{Path, PathBuf};

use serde::Serialize;

use super::net::{self, Download};
use super::version::{self, AssetIndex, Library, Manifest, VersionJson};
use super::{fabric, java};
use crate::error::{AppError, AppResult};
use crate::models::Profile;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Progress {
    pub stage: String,
    pub message: String,
    pub done: usize,
    pub total: usize,
}

pub type ProgressFn<'a> = &'a (dyn Fn(Progress) + Send + Sync);

fn report(p: ProgressFn, stage: &str, message: impl Into<String>, done: usize, total: usize) {
    p(Progress { stage: stage.into(), message: message.into(), done, total });
}

/// Ordnerstruktur im App-Datenverzeichnis.
pub struct Layout {
    pub root: PathBuf,
}

impl Layout {
    pub fn new(root: PathBuf) -> Self { Self { root } }
    pub fn versions(&self) -> PathBuf { self.root.join("versions") }
    pub fn libraries(&self) -> PathBuf { self.root.join("libraries") }
    pub fn assets(&self) -> PathBuf { self.root.join("assets") }
    pub fn runtimes(&self) -> PathBuf { self.root.join("runtime") }
    pub fn instance(&self, profile_id: &str) -> PathBuf { self.root.join("instances").join(profile_id) }
}

/// Alles, was zum Starten nötig ist.
#[derive(Debug, Clone)]
pub struct LaunchPlan {
    pub java: PathBuf,
    pub main_class: String,
    pub classpath: Vec<PathBuf>,
    pub jvm_args: Vec<String>,
    pub game_args: Vec<String>,
    pub version_id: String,
    pub version_type: String,
    pub asset_index: String,
    pub assets_dir: PathBuf,
    pub libraries_dir: PathBuf,
    pub natives_dir: PathBuf,
    pub game_dir: PathBuf,
    pub loader_version: String,
}

/// Lädt JSON von einer URL und cached es. Ohne Internet wird der Cache genutzt.
async fn cached_text(url: &str, cache: &Path, expected_sha1: Option<&str>) -> AppResult<String> {
    if let Ok(existing) = tokio::fs::read_to_string(cache).await {
        if expected_sha1.map_or(false, |s| net::sha1_hex(existing.as_bytes()) == s) {
            return Ok(existing);
        }
    }
    match net::client().get(url).send().await.and_then(|r| r.error_for_status()) {
        Ok(resp) => {
            let text = resp.text().await?;
            if let Some(s) = expected_sha1 {
                if net::sha1_hex(text.as_bytes()) != s {
                    return Err(AppError::Install(format!("Prüfsumme falsch: {url}")));
                }
            }
            net::write_atomic(cache, text.as_bytes()).await?;
            Ok(text)
        }
        Err(e) => match tokio::fs::read_to_string(cache).await {
            Ok(text) => { log::warn!("Offline, nutze Cache {}: {e}", cache.display()); Ok(text) }
            Err(_) => Err(e.into()),
        },
    }
}

async fn vanilla_version(layout: &Layout, mc: &str) -> AppResult<VersionJson> {
    let cache = layout.versions().join(mc).join(format!("{mc}.json"));
    let manifest: AppResult<Manifest> = net::get_json(version::MANIFEST_URL).await;
    let text = match manifest {
        Ok(m) => {
            let entry = m.versions.iter().find(|v| v.id == mc)
                .ok_or_else(|| AppError::Install(format!("Minecraft-Version {mc} existiert nicht")))?;
            cached_text(&entry.url, &cache, Some(&entry.sha1)).await?
        }
        Err(e) => tokio::fs::read_to_string(&cache).await.map_err(|_| e)?,
    };
    Ok(serde_json::from_str(&text)?)
}

fn library_download(lib: &Library, libs_dir: &Path) -> Option<Download> {
    if let Some(art) = lib.downloads.as_ref().and_then(|d| d.artifact.as_ref()) {
        let rel = art.path.clone().or_else(|| version::maven_path(&lib.name))?;
        return Some(Download { url: art.url.clone(), path: libs_dir.join(rel), sha1: art.sha1.clone(), size: art.size });
    }
    let rel = version::maven_path(&lib.name)?;
    let base = lib.url.as_deref().unwrap_or("https://libraries.minecraft.net/");
    let base = base.trim_end_matches('/');
    Some(Download { url: format!("{base}/{rel}"), path: libs_dir.join(rel), sha1: lib.sha1.clone(), size: lib.size })
}

/// Fabric-Bibliotheken zuerst, danach Vanilla. Gleiche Bibliothek (ohne Version) nur einmal.
pub fn merge_libraries<'a>(fabric: &'a [Library], vanilla: &'a [Library]) -> Vec<&'a Library> {
    let mut seen = HashSet::new();
    fabric
        .iter()
        .chain(vanilla.iter())
        .filter(|l| version::rules_allow(&l.rules))
        .filter(|l| seen.insert(version::library_key(&l.name)))
        .collect()
}

pub async fn install(layout: &Layout, profile: &Profile, custom_java: Option<&str>, progress: ProgressFn<'_>) -> AppResult<LaunchPlan> {
    let mc = profile.minecraft_version.as_str();

    report(progress, "meta", format!("Lade Versionsdaten für {mc}…"), 0, 0);
    let vanilla = vanilla_version(layout, mc).await?;

    let loader = match &profile.fabric_loader_version {
        Some(l) => l.clone(),
        None => match fabric::latest_stable_loader(mc).await {
            Ok(l) => l,
            Err(e) => find_cached_loader(layout, mc).await.ok_or(e)?,
        },
    };
    report(progress, "meta", format!("Fabric-Loader {loader}"), 0, 0);
    let fabric_id = format!("fabric-loader-{loader}-{mc}");
    let fabric_cache = layout.versions().join(&fabric_id).join(format!("{fabric_id}.json"));
    let fabric_raw = match fabric::profile_json_raw(mc, &loader).await {
        Ok(raw) => { net::write_atomic(&fabric_cache, raw.as_bytes()).await?; raw }
        Err(e @ AppError::Install(_)) => return Err(e),
        Err(e) => tokio::fs::read_to_string(&fabric_cache).await.map_err(|_| e)?,
    };
    let fabric_v = fabric::parse_profile(&fabric_raw)?;

    // Bibliotheken
    let libs_dir = layout.libraries();
    let libs = merge_libraries(&fabric_v.libraries, &vanilla.libraries);
    let mut downloads = Vec::new();
    let mut classpath = Vec::new();
    for lib in libs {
        let d = library_download(lib, &libs_dir)
            .ok_or_else(|| AppError::Install(format!("Bibliothek ohne Download-Quelle: {}", lib.name)))?;
        classpath.push(d.path.clone());
        downloads.push(d);
    }
    let client = vanilla.downloads.as_ref().map(|d| &d.client)
        .ok_or_else(|| AppError::Install("Versionsdaten ohne Client-Download".into()))?;
    let client_jar = layout.versions().join(mc).join(format!("{mc}.jar"));
    downloads.push(Download { url: client.url.clone(), path: client_jar.clone(), sha1: client.sha1.clone(), size: client.size });
    classpath.push(client_jar);

    let n = downloads.len();
    report(progress, "libraries", "Lade Bibliotheken…", 0, n);
    net::download_all(downloads, 12, |d, t| report(progress, "libraries", "Lade Bibliotheken…", d, t)).await?;

    // Assets
    let ai = vanilla.asset_index.as_ref().ok_or_else(|| AppError::Install("Versionsdaten ohne Asset-Index".into()))?;
    let assets_dir = layout.assets();
    let index_text = cached_text(&ai.url, &assets_dir.join("indexes").join(format!("{}.json", ai.id)), Some(&ai.sha1)).await?;
    let index: AssetIndex = serde_json::from_str(&index_text)?;
    let mut unique = HashSet::new();
    let asset_downloads: Vec<Download> = index.objects.values()
        .filter(|o| unique.insert(o.hash.clone()))
        .map(|o| {
            let sub = &o.hash[..2];
            Download {
                url: format!("https://resources.download.minecraft.net/{sub}/{}", o.hash),
                path: assets_dir.join("objects").join(sub).join(&o.hash),
                sha1: Some(o.hash.clone()),
                size: Some(o.size),
            }
        })
        .collect();
    let n = asset_downloads.len();
    report(progress, "assets", "Lade Assets…", 0, n);
    net::download_all(asset_downloads, 24, |d, t| report(progress, "assets", "Lade Assets…", d, t)).await?;

    // Java
    let java = match custom_java.filter(|p| !p.trim().is_empty()) {
        Some(p) => {
            let p = PathBuf::from(p.trim());
            if !tokio::fs::try_exists(&p).await.unwrap_or(false) {
                return Err(AppError::Validation(format!("Eingestellter Java-Pfad existiert nicht: {}", p.display())));
            }
            p
        }
        None => {
            let component = vanilla.java_version.as_ref().map(|j| j.component.as_str()).unwrap_or("java-runtime-delta");
            report(progress, "java", format!("Prüfe Java ({component})…"), 0, 0);
            java::ensure_runtime(&layout.runtimes(), component, |d, t| report(progress, "java", "Lade Java…", d, t)).await?
        }
    };

    let game_dir = layout.instance(&profile.id);
    tokio::fs::create_dir_all(game_dir.join("mods")).await?;
    let natives_dir = layout.versions().join(mc).join("natives");
    tokio::fs::create_dir_all(&natives_dir).await?;

    let vargs = vanilla.arguments.clone().unwrap_or_default();
    let fargs = fabric_v.arguments.clone().unwrap_or_default();
    let mut jvm_args = version::flatten_args(&vargs.jvm);
    jvm_args.extend(version::flatten_args(&fargs.jvm));
    let mut game_args = version::flatten_args(&vargs.game);
    game_args.extend(version::flatten_args(&fargs.game));

    report(progress, "done", "Installation vollständig", 1, 1);
    Ok(LaunchPlan {
        java,
        main_class: fabric_v.main_class.clone(),
        classpath,
        jvm_args,
        game_args,
        version_id: fabric_id,
        version_type: if vanilla.kind.is_empty() { "release".into() } else { vanilla.kind.clone() },
        asset_index: ai.id.clone(),
        assets_dir,
        libraries_dir: libs_dir,
        natives_dir,
        game_dir,
        loader_version: loader,
    })
}

async fn find_cached_loader(layout: &Layout, mc: &str) -> Option<String> {
    let mut rd = tokio::fs::read_dir(layout.versions()).await.ok()?;
    let suffix = format!("-{mc}");
    while let Ok(Some(e)) = rd.next_entry().await {
        let name = e.file_name().to_string_lossy().to_string();
        if let Some(rest) = name.strip_prefix("fabric-loader-") {
            if let Some(l) = rest.strip_suffix(&suffix) {
                return Some(l.to_string());
            }
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    fn lib(name: &str) -> Library {
        serde_json::from_value(serde_json::json!({ "name": name, "url": "https://maven.fabricmc.net/" })).unwrap()
    }

    #[test]
    fn fabric_libraries_win_duplicates() {
        let fabric = vec![lib("org.ow2.asm:asm:9.7.1")];
        let vanilla = vec![lib("org.ow2.asm:asm:9.3"), lib("com.mojang:brigadier:1.3.10")];
        let merged: Vec<&str> = merge_libraries(&fabric, &vanilla).iter().map(|l| l.name.as_str()).collect();
        assert_eq!(merged, vec!["org.ow2.asm:asm:9.7.1", "com.mojang:brigadier:1.3.10"]);
    }

    #[test]
    fn maven_url_built_from_base() {
        let d = library_download(&lib("net.fabricmc:fabric-loader:0.16.10"), Path::new("/libs")).unwrap();
        assert_eq!(d.url, "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.16.10/fabric-loader-0.16.10.jar");
    }
}
