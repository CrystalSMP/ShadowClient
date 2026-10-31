//! Inhalte pro Profil über die Modrinth-API: Mods, Resourcepacks, Shader, Datapacks.
use std::collections::HashSet;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::error::{AppError, AppResult};
use crate::minecraft::net::{self, Download};

#[derive(Deserialize)]
struct Version { id: String, project_id: String, files: Vec<File>, #[serde(default)] dependencies: Vec<Dep> }
#[derive(Deserialize)]
struct File { url: String, filename: String, primary: bool, size: u64, hashes: Hashes }
#[derive(Deserialize)]
struct Hashes { sha1: String }
#[derive(Deserialize)]
struct Dep { project_id: Option<String>, version_id: Option<String>, dependency_type: String }

#[derive(Serialize, Deserialize, Clone, Default)]
#[serde(rename_all = "camelCase")]
pub struct Installed { pub project_id: String, pub filename: String, pub kind: String }

pub fn folder(kind: &str) -> AppResult<&'static str> {
    Ok(match kind {
        "mod" => "mods", "resourcepack" => "resourcepacks", "shader" => "shaderpacks", "datapack" => "datapacks",
        _ => return Err(AppError::Validation(format!("Unbekannter Typ: {kind}"))),
    })
}

fn loaders(kind: &str) -> &'static [&'static str] {
    match kind { "mod" => &["fabric"], "resourcepack" => &["minecraft"], "shader" => &["iris", "optifine", "canvas"], _ => &["datapack"] }
}

fn enc(list: &[&str]) -> String {
    let inner = list.iter().map(|s| format!("%22{s}%22")).collect::<Vec<_>>().join(",");
    format!("%5B{inner}%5D")
}

async fn best_version(project: &str, kind: &str, mc: &str) -> AppResult<Version> {
    let url = format!("https://api.modrinth.com/v2/project/{project}/version?loaders={}&game_versions={}", enc(loaders(kind)), enc(&[mc]));
    let mut v: Vec<Version> = net::get_json(&url).await?;
    if v.is_empty() { return Err(AppError::Install(format!("Keine Version für Minecraft {mc} verfügbar"))); }
    Ok(v.remove(0))
}

fn index_path(dir: &Path) -> PathBuf { dir.join("shadowclient").join("content.json") }

pub async fn read_index(dir: &Path) -> Vec<Installed> {
    crate::storage::read_json_or_default(&index_path(dir)).await.unwrap_or_default()
}

async fn write_index(dir: &Path, list: &[Installed]) -> AppResult<()> {
    if let Some(p) = index_path(dir).parent() { tokio::fs::create_dir_all(p).await?; }
    crate::storage::write_json(&index_path(dir), &list.to_vec()).await
}

/// Installiert ein Projekt (inkl. benötigter Abhängigkeiten bei Mods). Gibt die installierten Dateien zurück.
pub async fn install(dir: &Path, project: &str, kind: &str, mc: &str) -> AppResult<Vec<String>> {
    let mut index = read_index(dir).await;
    let mut queue = vec![(project.to_string(), None::<String>)];
    let mut seen = HashSet::new();
    let mut done = Vec::new();
    while let Some((pid, vid)) = queue.pop() {
        if !seen.insert(pid.clone()) || pid == "P7dR8mSH" { continue; }  // Fabric API installiert der Launcher selbst
        if pid != project && index.iter().any(|i| i.project_id == pid) { continue; }
        let ver: Version = match vid {
            Some(v) => net::get_json(&format!("https://api.modrinth.com/v2/version/{v}")).await?,
            None => best_version(&pid, kind, mc).await?,
        };
        let file = ver.files.iter().find(|f| f.primary).or(ver.files.first())
            .ok_or_else(|| AppError::Install("Version ohne Datei".into()))?;
        let sub = dir.join(folder(kind)?);
        tokio::fs::create_dir_all(&sub).await?;
        // alte Datei desselben Projekts entfernen
        if let Some(old) = index.iter().find(|i| i.project_id == ver.project_id) {
            if old.filename != file.filename { let _ = tokio::fs::remove_file(sub.join(&old.filename)).await; }
        }
        net::download_all(vec![Download { url: file.url.clone(), path: sub.join(&file.filename), sha1: Some(file.hashes.sha1.clone()), size: Some(file.size) }], 1, |_, _| {}).await?;
        index.retain(|i| i.project_id != ver.project_id);
        index.push(Installed { project_id: ver.project_id.clone(), filename: file.filename.clone(), kind: kind.into() });
        done.push(file.filename.clone());
        let _ = &ver.id;
        if kind == "mod" {
            for d in ver.dependencies.iter().filter(|d| d.dependency_type == "required") {
                if let Some(p) = &d.project_id { queue.push((p.clone(), d.version_id.clone())); }
            }
        }
    }
    write_index(dir, &index).await?;
    Ok(done)
}

pub async fn remove(dir: &Path, project: &str) -> AppResult<()> {
    let mut index = read_index(dir).await;
    if let Some(i) = index.iter().find(|i| i.project_id == project) {
        let _ = tokio::fs::remove_file(dir.join(folder(&i.kind)?).join(&i.filename)).await;
    }
    index.retain(|i| i.project_id != project);
    write_index(dir, &index).await
}
