//! Inhalte einer Instanz anzeigen & verwalten (Mods, Resourcepacks, Shader, Datapacks, Welten, Screenshots, Logs).
use std::io::Read;
use std::path::{Path, PathBuf};

use serde::Serialize;

use crate::error::{AppError, AppResult};

#[derive(Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct Entry {
    pub file: String,
    pub name: String,
    pub version: Option<String>,
    pub description: Option<String>,
    pub authors: Option<String>,
    pub icon: Option<String>,
    pub enabled: bool,
    pub size: u64,
    pub modified: u64,
    pub path: String,
}

#[derive(Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct Overview {
    pub size_bytes: u64,
    pub mods: usize,
    pub resourcepacks: usize,
    pub shaderpacks: usize,
    pub datapacks: usize,
    pub saves: usize,
    pub screenshots: usize,
}

pub fn folder(kind: &str) -> AppResult<&'static str> {
    Ok(match kind {
        "mods" => "mods", "resourcepacks" => "resourcepacks", "shaderpacks" => "shaderpacks", "datapacks" => "datapacks",
        "saves" => "saves", "screenshots" => "screenshots", "logs" => "logs",
        _ => return Err(AppError::Validation(format!("Unbekannter Bereich: {kind}"))),
    })
}

/// Dateiname darf keinen Pfad enthalten.
pub fn safe_name(name: &str) -> AppResult<&str> {
    if name.is_empty() || name.contains(['/', '\\']) || name == "." || name == ".." {
        return Err(AppError::Validation("Ungültiger Dateiname".into()));
    }
    Ok(name)
}

fn mtime(m: &std::fs::Metadata) -> u64 {
    m.modified().ok().and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok()).map(|d| d.as_secs()).unwrap_or(0)
}

fn dir_size(p: &Path) -> u64 {
    let Ok(rd) = std::fs::read_dir(p) else { return 0 };
    rd.flatten().map(|e| match e.metadata() {
        Ok(m) if m.is_dir() => dir_size(&e.path()),
        Ok(m) => m.len(),
        Err(_) => 0,
    }).sum()
}

fn count(p: &Path, dirs: bool) -> usize {
    std::fs::read_dir(p).map(|rd| rd.flatten().filter(|e| e.file_type().map(|t| t.is_dir() == dirs).unwrap_or(false)).count()).unwrap_or(0)
}

pub fn overview(dir: &Path) -> Overview {
    Overview {
        size_bytes: dir_size(dir),
        mods: count(&dir.join("mods"), false),
        resourcepacks: count(&dir.join("resourcepacks"), false) + count(&dir.join("resourcepacks"), true),
        shaderpacks: count(&dir.join("shaderpacks"), false),
        datapacks: count(&dir.join("datapacks"), false),
        saves: count(&dir.join("saves"), true),
        screenshots: count(&dir.join("screenshots"), false),
    }
}

fn data_url(bytes: &[u8]) -> Option<String> {
    use base64::Engine;
    if bytes.len() > 96 * 1024 || bytes.len() < 8 || bytes[..4] != [0x89, b'P', b'N', b'G'] { return None; }
    Some(format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(bytes)))
}

fn zip_read(zip: &mut zip::ZipArchive<std::fs::File>, name: &str) -> Option<Vec<u8>> {
    let mut f = zip.by_name(name.trim_start_matches('/')).ok()?;
    if f.size() > 512 * 1024 { return None; }
    let mut v = Vec::new();
    f.read_to_end(&mut v).ok()?;
    Some(v)
}

/// Liest Name, Version, Beschreibung und Icon aus einer Mod-JAR (fabric.mod.json).
fn mod_meta(path: &Path, e: &mut Entry) {
    let Ok(file) = std::fs::File::open(path) else { return };
    let Ok(mut zip) = zip::ZipArchive::new(file) else { return };
    let Some(raw) = zip_read(&mut zip, "fabric.mod.json") else { return };
    // fabric.mod.json darf Steuerzeichen in Strings enthalten – vorsichtig parsen
    let txt = String::from_utf8_lossy(&raw).replace(['\n', '\r', '\t'], " ");
    let Ok(j) = serde_json::from_str::<serde_json::Value>(&txt) else { return };
    if let Some(n) = j.get("name").and_then(|v| v.as_str()) { e.name = n.to_string(); }
    e.version = j.get("version").and_then(|v| v.as_str()).map(String::from);
    e.description = j.get("description").and_then(|v| v.as_str()).map(|s| s.chars().take(200).collect());
    e.authors = j.get("authors").and_then(|v| v.as_array()).map(|a| a.iter()
        .filter_map(|x| x.as_str().map(String::from).or_else(|| x.get("name").and_then(|n| n.as_str()).map(String::from)))
        .take(3).collect::<Vec<_>>().join(", ")).filter(|s| !s.is_empty());
    let icon = match j.get("icon") {
        Some(serde_json::Value::String(s)) => Some(s.clone()),
        Some(serde_json::Value::Object(m)) => m.iter().max_by_key(|(k, _)| k.parse::<u32>().unwrap_or(0)).and_then(|(_, v)| v.as_str().map(String::from)),
        _ => None,
    };
    if let Some(ic) = icon { e.icon = zip_read(&mut zip, &ic).and_then(|b| data_url(&b)); }
}

fn pack_meta(path: &Path, e: &mut Entry) {
    let (icon, mcmeta) = if path.is_dir() {
        (std::fs::read(path.join("pack.png")).ok(), std::fs::read(path.join("pack.mcmeta")).ok())
    } else {
        let Ok(file) = std::fs::File::open(path) else { return };
        let Ok(mut zip) = zip::ZipArchive::new(file) else { return };
        (zip_read(&mut zip, "pack.png"), zip_read(&mut zip, "pack.mcmeta"))
    };
    e.icon = icon.and_then(|b| data_url(&b));
    if let Some(m) = mcmeta.and_then(|b| serde_json::from_slice::<serde_json::Value>(&b).ok()) {
        let d = &m["pack"]["description"];
        e.description = d.as_str().map(String::from).or_else(|| d.get("text").and_then(|t| t.as_str()).map(String::from))
            .map(|s| strip_codes(&s).chars().take(160).collect());
    }
}

fn strip_codes(s: &str) -> String {
    let mut out = String::new(); let mut skip = false;
    for c in s.chars() { if skip { skip = false; continue; } if c == '§' { skip = true; continue; } out.push(c); }
    out
}

pub fn list(dir: &Path, kind: &str) -> AppResult<Vec<Entry>> {
    let base = dir.join(folder(kind)?);
    let Ok(rd) = std::fs::read_dir(&base) else { return Ok(vec![]) };
    let mut out = Vec::new();
    for ent in rd.flatten() {
        let Ok(meta) = ent.metadata() else { continue };
        let file = ent.file_name().to_string_lossy().to_string();
        let path = ent.path();
        let enabled = !file.ends_with(".disabled");
        let clean = file.trim_end_matches(".disabled").to_string();
        let mut e = Entry { file: file.clone(), name: clean.clone(), enabled, size: meta.len(), modified: mtime(&meta), path: path.display().to_string(), ..Default::default() };
        match kind {
            "mods" => { if !clean.ends_with(".jar") { continue; } e.name = clean.trim_end_matches(".jar").to_string(); mod_meta(&path, &mut e); }
            "resourcepacks" | "datapacks" => { if !(meta.is_dir() || clean.ends_with(".zip")) { continue; } e.name = clean.trim_end_matches(".zip").to_string(); pack_meta(&path, &mut e); }
            "shaderpacks" => { if !(meta.is_dir() || clean.ends_with(".zip")) { continue; } e.name = clean.trim_end_matches(".zip").to_string(); }
            "saves" => {
                if !meta.is_dir() { continue; }
                e.icon = std::fs::read(path.join("icon.png")).ok().and_then(|b| data_url(&b));
                e.size = dir_size(&path);
                if let Ok(m) = std::fs::metadata(path.join("level.dat")) { e.modified = mtime(&m); }
            }
            "screenshots" => { if !clean.to_lowercase().ends_with(".png") { continue; } }
            "logs" => { if !(clean.ends_with(".log") || clean.ends_with(".log.gz")) { continue; } }
            _ => {}
        }
        out.push(e);
    }
    out.sort_by(|a, b| if kind == "screenshots" || kind == "saves" || kind == "logs" { b.modified.cmp(&a.modified) } else { a.name.to_lowercase().cmp(&b.name.to_lowercase()) });
    Ok(out)
}

pub fn toggle(dir: &Path, kind: &str, file: &str, enabled: bool) -> AppResult<String> {
    let base = dir.join(folder(kind)?);
    let file = safe_name(file)?;
    let clean = file.trim_end_matches(".disabled");
    let target = if enabled { clean.to_string() } else { format!("{clean}.disabled") };
    if target != file { std::fs::rename(base.join(file), base.join(&target))?; }
    Ok(target)
}

pub fn remove(dir: &Path, kind: &str, file: &str) -> AppResult<()> {
    let p: PathBuf = dir.join(folder(kind)?).join(safe_name(file)?);
    if p.is_dir() { std::fs::remove_dir_all(p)?; } else { std::fs::remove_file(p)?; }
    Ok(())
}

pub fn read_log(dir: &Path, file: &str) -> AppResult<String> {
    let p = dir.join("logs").join(safe_name(file)?);
    let bytes = std::fs::read(&p)?;
    let text = if file.ends_with(".gz") {
        let mut s = String::new();
        flate2::read::GzDecoder::new(&bytes[..]).read_to_string(&mut s).map_err(|e| AppError::Validation(e.to_string()))?;
        s
    } else { String::from_utf8_lossy(&bytes).to_string() };
    let lines: Vec<&str> = text.lines().collect();
    Ok(lines[lines.len().saturating_sub(3000)..].join("\n"))
}
