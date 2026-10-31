//! Tauri-Commands, die das React-Frontend per `invoke` aufruft.
use std::path::PathBuf;

use tauri::{AppHandle, Manager};

use crate::error::{AppError, AppResult};
use crate::models::{LauncherSettings, Profile, SystemInfo};
use crate::storage;

fn data_dir(app: &AppHandle) -> AppResult<PathBuf> {
    app.path().app_data_dir().map_err(|e| AppError::Path(e.to_string()))
}

#[tauri::command]
pub async fn get_system_info(app: AppHandle) -> AppResult<SystemInfo> {
    // sysinfo ist synchron – im Blocking-Pool ausführen, damit die UI nicht hängt.
    let total = tauri::async_runtime::spawn_blocking(|| {
        let mut sys = sysinfo::System::new();
        sys.refresh_memory();
        sys.total_memory() / 1024 / 1024
    })
    .await
    .unwrap_or(0);

    Ok(SystemInfo {
        os: std::env::consts::OS.into(),
        arch: std::env::consts::ARCH.into(),
        total_memory_mb: total,
        app_version: app.package_info().version.to_string(),
        data_dir: data_dir(&app)?.display().to_string(),
    })
}

#[tauri::command]
pub async fn load_settings(app: AppHandle) -> AppResult<LauncherSettings> {
    storage::read_json_or_default(&data_dir(&app)?.join("settings.json")).await
}

pub async fn apply_presence_settings(app: &AppHandle) {
    let Ok(dir) = data_dir(app) else { return };
    let s: LauncherSettings = storage::read_json_or_default(&dir.join("settings.json")).await.unwrap_or_default();
    let id = if s.discord_presence { Some(s.discord_client_id.clone().filter(|v| !v.trim().is_empty()).unwrap_or_else(|| crate::presence::DISCORD_APP_ID.to_string())) } else { None };
    crate::presence::configure(id);
    crate::presence::set(crate::presence::State::Launcher);
}

#[tauri::command]
pub async fn save_settings(app: AppHandle, settings: LauncherSettings) -> AppResult<()> {
    let app2 = app.clone();
    let r = save_settings_inner(app, settings).await;
    apply_presence_settings(&app2).await;
    r
}

async fn save_settings_inner(app: AppHandle, settings: LauncherSettings) -> AppResult<()> {
    settings.validate()?;
    storage::write_json(&data_dir(&app)?.join("settings.json"), &settings).await?;
    log::info!("Einstellungen gespeichert");
    Ok(())
}

#[tauri::command]
pub async fn load_profiles(app: AppHandle) -> AppResult<Vec<Profile>> {
    storage::read_json_or_default(&data_dir(&app)?.join("profiles.json")).await
}

#[tauri::command]
pub async fn save_profiles(app: AppHandle, profiles: Vec<Profile>) -> AppResult<()> {
    for p in &profiles {
        p.validate()?;
    }
    let mut ids: Vec<&str> = profiles.iter().map(|p| p.id.as_str()).collect();
    ids.sort_unstable();
    if ids.windows(2).any(|w| w[0] == w[1]) {
        return Err(AppError::Validation("Doppelte Profil-ID".into()));
    }
    storage::write_json(&data_dir(&app)?.join("profiles.json"), &profiles).await?;
    log::info!("{} Profil(e) gespeichert", profiles.len());
    Ok(())
}

// ---------------- Phase 2: Konten ----------------

use std::sync::atomic::{AtomicBool, Ordering};

use tauri::Emitter;

use crate::auth::{self, Account, DeviceCode};
use crate::minecraft::install::{self, Layout, Progress};
use crate::minecraft::launch::{self, GameSession};

async fn client_id(_app: &AppHandle) -> AppResult<String> {
    // Immer die eingebaute ShadowClient-Azure-App (keine eigene Client-ID mehr einstellbar)
    auth::resolve_client_id(None)
}

async fn read_accounts(app: &AppHandle) -> AppResult<Vec<Account>> {
    storage::read_json_or_default(&data_dir(app)?.join("accounts.json")).await
}

async fn upsert_account(app: &AppHandle, acc: &Account) -> AppResult<()> {
    let mut list = read_accounts(app).await?;
    list.retain(|a| a.uuid != acc.uuid);
    list.insert(0, acc.clone());
    storage::write_json(&data_dir(app)?.join("accounts.json"), &list).await
}

#[tauri::command]
pub async fn auth_start(app: AppHandle) -> AppResult<DeviceCode> {
    auth::start_device_login(&client_id(&app).await?).await
}

#[tauri::command]
pub async fn auth_complete(app: AppHandle, code: DeviceCode) -> AppResult<Account> {
    let session = auth::complete_device_login(&client_id(&app).await?, &code).await?;
    auth::store_refresh_token(&session.account.uuid, &session.refresh_token)?;
    upsert_account(&app, &session.account).await?;
    log::info!("Angemeldet als {}", session.account.name);
    Ok(session.account)
}

#[tauri::command]
pub fn auth_cancel() {
    auth::cancel_login();
}

#[tauri::command]
pub async fn list_accounts(app: AppHandle) -> AppResult<Vec<Account>> {
    read_accounts(&app).await
}

#[tauri::command]
pub async fn remove_account(app: AppHandle, uuid: String) -> AppResult<()> {
    auth::delete_refresh_token(&uuid);
    let mut list = read_accounts(&app).await?;
    list.retain(|a| a.uuid != uuid);
    storage::write_json(&data_dir(&app)?.join("accounts.json"), &list).await
}

// ---------------- Phase 2: Start ----------------

static GAME_RUNNING: AtomicBool = AtomicBool::new(false);

#[derive(Clone, serde::Serialize)]
#[serde(rename_all = "camelCase")]
struct LogLine {
    line: String,
    is_err: bool,
}

#[tauri::command]
pub fn is_game_running() -> bool {
    GAME_RUNNING.load(Ordering::SeqCst)
}

/// Startet Installation + Spiel im Hintergrund. Fortschritt kommt über Events:
/// `launch-progress`, `game-log`, `game-started`, `game-exit`, `launch-error`.
#[tauri::command]
pub async fn launch_profile(app: AppHandle, profile_id: String, account_uuid: String, server: Option<String>) -> AppResult<()> {
    if GAME_RUNNING.swap(true, Ordering::SeqCst) {
        return Err(AppError::Validation("Minecraft läuft bereits oder wird gerade gestartet.".into()));
    }
    let app2 = app.clone();
    tauri::async_runtime::spawn(async move {
        if let Err(e) = launch_inner(&app2, &profile_id, &account_uuid, server.as_deref()).await {
            log::error!("Start fehlgeschlagen: {e}");
            let _ = app2.emit("launch-error", e.to_string());
        }
        GAME_RUNNING.store(false, Ordering::SeqCst);
        crate::presence::set(crate::presence::State::Launcher);
        if let Some(w) = app2.get_webview_window("main") {
            let _ = w.show();
        }
    });
    Ok(())
}

async fn launch_inner(app: &AppHandle, profile_id: &str, account_uuid: &str, server: Option<&str>) -> AppResult<()> {
    let dir = data_dir(app)?;
    let settings: LauncherSettings = storage::read_json_or_default(&dir.join("settings.json")).await?;
    let mut profiles: Vec<Profile> = storage::read_json_or_default(&dir.join("profiles.json")).await?;
    let profile = profiles.iter().find(|p| p.id == profile_id).cloned()
        .ok_or_else(|| AppError::Validation("Profil nicht gefunden".into()))?;

    let emit = |stage: &str, msg: &str| {
        let _ = app.emit("launch-progress", Progress { stage: stage.into(), message: msg.into(), done: 0, total: 0 });
    };

    emit("auth", "Melde bei Microsoft an…");
    let cid = auth::resolve_client_id(None)?;
    let refresh = auth::load_refresh_token(account_uuid)?;
    let session = auth::refresh(&cid, &refresh).await?;
    auth::store_refresh_token(&session.account.uuid, &session.refresh_token)?;
    upsert_account(app, &session.account).await?;

    let layout = Layout::new(dir.clone());
    let app_p = app.clone();
    let progress = move |p: Progress| { let _ = app_p.emit("launch-progress", p); };
    let plan = install::install(&layout, &profile, settings.java_path.as_deref(), &progress).await?;

    // Mods: Fabric API + ShadowClient-Mod
    let mods_dir = plan.game_dir.join("mods");
    emit("mods", "Prüfe Fabric API…");
    let fapi = crate::minecraft::mods::ensure_fabric_api(&mods_dir, &profile.minecraft_version).await?;
    log::info!("Fabric API: {fapi}");
    let mod_supported = profile.shadowclient && profile.minecraft_version == crate::minecraft::mods::MOD_MC_VERSION;
    if !mod_supported {
        // Mod ist nur für eine MC-Version gebaut – sonst würde Fabric den Start verweigern.
        let _ = tokio::fs::remove_file(mods_dir.join("shadowclient.jar")).await;
        log::info!("ShadowClient-Mod übersprungen (nur für {})", crate::minecraft::mods::MOD_MC_VERSION);
    }
    match if mod_supported { crate::minecraft::mods::find_shadowclient_mod(app.path().resource_dir().ok(), Some(dir.clone())).await } else { None } {
        Some(src) => {
            emit("mods", "Installiere ShadowClient-Mod…");
            crate::minecraft::mods::install_shadowclient_mod(&src, &mods_dir).await?;
            log::info!("ShadowClient-Mod aus {} installiert", src.display());
        }
        None if !mod_supported => {}
        None => {
            let hint = dir.join("shadowclient-mod");
            let _ = tokio::fs::create_dir_all(&hint).await;
            log::warn!("ShadowClient-Mod nicht gefunden");
            let _ = app.emit("launch-warning", format!(
                "ShadowClient-Mod nicht gefunden – das Spiel startet ohne. Baue die Mod mit build.bat oder lege die JAR in {}", hint.display()));
        }
    }

    if let Some(p) = profiles.iter_mut().find(|p| p.id == profile_id) {
        p.last_played = Some(now_iso());
    }
    storage::write_json(&dir.join("profiles.json"), &profiles).await?;

    emit("launch", "Starte Minecraft…");
    crate::presence::set(crate::presence::State::Starting(profile.minecraft_version.clone()));
    {
        let (gd, ver) = (plan.game_dir.clone(), profile.minecraft_version.clone());
        tauri::async_runtime::spawn(async move {
            tokio::time::sleep(std::time::Duration::from_secs(8)).await;
            crate::presence::watch_game(gd, ver, || GAME_RUNNING.load(Ordering::SeqCst)).await;
        });
    }
    let _ = app.emit("game-started", plan.loader_version.clone());
    if settings.close_on_launch {
        if let Some(w) = app.get_webview_window("main") { let _ = w.hide(); }
    }

    let game = GameSession {
        name: session.account.name.clone(),
        uuid: session.account.uuid.clone(),
        access_token: session.access_token,
        xuid: session.xuid,
    };
    let app_l = app.clone();
    let mut plan = plan;
    if let Some(ip) = server.map(str::trim).filter(|s| !s.is_empty() && s.chars().all(|c| c.is_ascii_alphanumeric() || ".-:_".contains(c))) {
        // Direkt beitreten (Quick Play, ab MC 1.20)
        plan.game_args.push("--quickPlayMultiplayer".into());
        plan.game_args.push(ip.to_string());
    }
    let started = std::time::Instant::now();
    let exit = launch::run(&plan, &game, profile.memory_mb, &profile.jvm_args, move |line, is_err| {
        let _ = app_l.emit("game-log", LogLine { line, is_err });
    }).await?;
    // Spielzeit gutschreiben
    let secs = started.elapsed().as_secs();
    if let Ok(mut list) = storage::read_json_or_default::<Vec<Profile>>(&dir.join("profiles.json")).await {
        if let Some(p) = list.iter_mut().find(|p| p.id == profile_id) { p.playtime_secs += secs; }
        let _ = storage::write_json(&dir.join("profiles.json"), &list).await;
    }
    let _ = app.emit("game-exit", exit);
    Ok(())
}

fn now_iso() -> String {
    // RFC 3339 in UTC ohne zusätzliche Abhängigkeit.
    let secs = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
    let (days, rem) = (secs / 86_400, secs % 86_400);
    let (h, m, s) = (rem / 3600, (rem % 3600) / 60, rem % 60);
    // Civil-from-days (Howard Hinnant)
    let z = days as i64 + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z.rem_euclid(146_097);
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let mo = if mp < 10 { mp + 3 } else { mp - 9 };
    let y = yoe + era * 400 + if mo <= 2 { 1 } else { 0 };
    format!("{y:04}-{mo:02}-{d:02}T{h:02}:{m:02}:{s:02}Z")
}

#[cfg(test)]
mod tests {
    #[test]
    fn iso_format() {
        let s = super::now_iso();
        assert_eq!(s.len(), 20);
        assert!(s.starts_with("20") && s.ends_with('Z'));
    }
}

/// Öffnet den Spielordner eines Profils im Explorer (mods, screenshots, crash-reports …).
#[tauri::command]
pub async fn open_instance_folder(app: AppHandle, profile_id: String) -> AppResult<()> {
    use tauri_plugin_opener::OpenerExt;
    if profile_id.is_empty() || profile_id.contains(['/', '\\', '.']) {
        return Err(AppError::Validation("Ungültige Profil-ID".into()));
    }
    let dir = Layout::new(data_dir(&app)?).instance(&profile_id);
    tokio::fs::create_dir_all(dir.join("mods")).await?;
    app.opener()
        .open_path(dir.display().to_string(), None::<&str>)
        .map_err(|e| AppError::Path(e.to_string()))
}

// ---------------- Freunde ----------------

/// Sitzungs-Token für das ShadowClient-Backend holen (gecacht, sonst neu anmelden).
async fn online_token(app: &AppHandle, account_uuid: &str) -> AppResult<String> {
    use std::sync::Mutex;
    use std::time::{Duration, Instant};
    static LAST_FAIL: Mutex<Option<(Instant, String)>> = Mutex::new(None);
    if let Some(t) = crate::online::cached_token(account_uuid) {
        return Ok(t);
    }
    // Nach einem Fehlschlag 60 s warten, statt Microsoft/Mojang bei jedem Aktualisieren erneut zu fragen
    if let Some((at, msg)) = LAST_FAIL.lock().unwrap().clone() {
        if at.elapsed() < Duration::from_secs(60) { return Err(AppError::Offline(msg)); }
    }
    let r = online_login(app, account_uuid).await;
    *LAST_FAIL.lock().unwrap() = r.as_ref().err().map(|e| (Instant::now(), e.to_string()));
    r
}

async fn online_login(app: &AppHandle, account_uuid: &str) -> AppResult<String> {
    let cid = client_id(app).await?;
    let refresh = auth::load_refresh_token(account_uuid)?;
    let session = auth::refresh(&cid, &refresh).await?;
    auth::store_refresh_token(&session.account.uuid, &session.refresh_token)?;
    crate::online::login(&session).await
}

#[tauri::command]
pub async fn friends_list(app: AppHandle, account_uuid: String) -> AppResult<crate::online::FriendsData> {
    let token = online_token(&app, &account_uuid).await?;
    match crate::online::list(&account_uuid, &token).await {
        Err(AppError::Auth(_)) => {
            let token = online_token(&app, &account_uuid).await?;
            crate::online::list(&account_uuid, &token).await
        }
        other => other,
    }
}

#[tauri::command]
pub async fn friends_action(app: AppHandle, account_uuid: String, action: String, value: String) -> AppResult<Option<String>> {
    let (path, payload) = match action.as_str() {
        "add" => ("friends/add", serde_json::json!({ "name": value })),
        "accept" => ("friends/accept", serde_json::json!({ "uuid": value })),
        "remove" => ("friends/remove", serde_json::json!({ "uuid": value })),
        _ => return Err(AppError::Validation("Unbekannte Aktion".into())),
    };
    let token = online_token(&app, &account_uuid).await?;
    crate::online::action(&account_uuid, &token, path, payload).await
}

// ---------------- Inhalte (Modrinth) pro Profil
async fn profile_dir(app: &AppHandle, profile_id: &str) -> AppResult<(PathBuf, Profile)> {
    let dir = data_dir(app)?;
    let profiles: Vec<Profile> = storage::read_json_or_default(&dir.join("profiles.json")).await?;
    let p = profiles.into_iter().find(|p| p.id == profile_id).ok_or_else(|| AppError::Validation("Profil nicht gefunden".into()))?;
    Ok((crate::minecraft::install::Layout::new(dir).instance(&p.id), p))
}

#[tauri::command]
pub async fn content_installed(app: AppHandle, profile_id: String) -> AppResult<Vec<crate::content::Installed>> {
    let (dir, _) = profile_dir(&app, &profile_id).await?;
    Ok(crate::content::read_index(&dir).await)
}

#[tauri::command]
pub async fn content_install(app: AppHandle, profile_id: String, project_id: String, kind: String) -> AppResult<Vec<String>> {
    let (dir, p) = profile_dir(&app, &profile_id).await?;
    crate::content::install(&dir, &project_id, &kind, &p.minecraft_version).await
}

#[tauri::command]
pub async fn content_remove(app: AppHandle, profile_id: String, project_id: String) -> AppResult<()> {
    let (dir, _) = profile_dir(&app, &profile_id).await?;
    crate::content::remove(&dir, &project_id).await
}

// ---------------- Cosmetics (Capes) ----------------
#[tauri::command]
pub async fn cosmetics_get(app: AppHandle, account_uuid: String) -> AppResult<serde_json::Value> {
    let token = online_token(&app, &account_uuid).await?;
    match crate::online::get(&account_uuid, &token, "cosmetics").await {
        Err(AppError::Auth(_)) => { let t = online_token(&app, &account_uuid).await?; crate::online::get(&account_uuid, &t, "cosmetics").await }
        other => other,
    }
}

async fn cosmetics_post(app: &AppHandle, uuid: &str, path: &str, payload: serde_json::Value) -> AppResult<Option<String>> {
    let token = online_token(app, uuid).await?;
    match crate::online::action(uuid, &token, path, payload.clone()).await {
        Err(AppError::Auth(_)) => { let t = online_token(app, uuid).await?; crate::online::action(uuid, &t, path, payload).await }
        other => other,
    }
}

#[tauri::command]
pub async fn cosmetics_select(app: AppHandle, account_uuid: String, cape: Option<String>) -> AppResult<Option<String>> {
    cosmetics_post(&app, &account_uuid, "cosmetics/select", serde_json::json!({ "cape": cape })).await
}

#[tauri::command]
pub async fn cosmetics_upload(app: AppHandle, account_uuid: String, png: String) -> AppResult<Option<String>> {
    if png.len() > 120_000 { return Err(AppError::Validation("Bild zu groß (max. 64 KB)".into())); }
    cosmetics_post(&app, &account_uuid, "cosmetics/upload", serde_json::json!({ "png": png })).await
}

// ---------------- Instanz-Inhalte ----------------
#[tauri::command]
pub async fn instance_overview(app: AppHandle, profile_id: String) -> AppResult<crate::instance::Overview> {
    let (dir, _) = profile_dir(&app, &profile_id).await?;
    Ok(tauri::async_runtime::spawn_blocking(move || crate::instance::overview(&dir)).await.unwrap_or_default())
}

#[tauri::command]
pub async fn instance_list(app: AppHandle, profile_id: String, kind: String) -> AppResult<Vec<crate::instance::Entry>> {
    let (dir, _) = profile_dir(&app, &profile_id).await?;
    tauri::async_runtime::spawn_blocking(move || crate::instance::list(&dir, &kind)).await
        .map_err(|e| AppError::Validation(e.to_string()))?
}

#[tauri::command]
pub async fn instance_toggle(app: AppHandle, profile_id: String, kind: String, file: String, enabled: bool) -> AppResult<String> {
    let (dir, _) = profile_dir(&app, &profile_id).await?;
    crate::instance::toggle(&dir, &kind, &file, enabled)
}

#[tauri::command]
pub async fn instance_remove(app: AppHandle, profile_id: String, kind: String, file: String) -> AppResult<()> {
    let (dir, _) = profile_dir(&app, &profile_id).await?;
    crate::instance::remove(&dir, &kind, &file)?;
    // Modrinth-Index mitpflegen
    let clean = file.trim_end_matches(".disabled").to_string();
    if let Some(i) = crate::content::read_index(&dir).await.into_iter().find(|i| i.filename == clean) {
        let _ = crate::content::remove(&dir, &i.project_id).await;
    }
    Ok(())
}

#[tauri::command]
pub async fn instance_read_log(app: AppHandle, profile_id: String, file: String) -> AppResult<String> {
    let (dir, _) = profile_dir(&app, &profile_id).await?;
    tauri::async_runtime::spawn_blocking(move || crate::instance::read_log(&dir, &file)).await
        .map_err(|e| AppError::Validation(e.to_string()))?
}

/// Öffnet die Instanz oder einen Unterordner (mods, screenshots …) im Explorer.
#[tauri::command]
pub async fn instance_open(app: AppHandle, profile_id: String, kind: Option<String>) -> AppResult<()> {
    use tauri_plugin_opener::OpenerExt;
    let (dir, _) = profile_dir(&app, &profile_id).await?;
    let target = match kind.as_deref() { Some(k) => dir.join(crate::instance::folder(k)?), None => dir };
    tokio::fs::create_dir_all(&target).await?;
    app.opener().open_path(target.display().to_string(), None::<&str>).map_err(|e| AppError::Path(e.to_string()))
}

// ---------------- Social (Status, Nachrichten, Party) – generisch, nur erlaubte Pfade ----------------
fn allowed_path(path: &str) -> bool {
    let base = path.split('?').next().unwrap_or("");
    matches!(base, "friends" | "messages" | "messages/send" | "messages/read" | "invite" | "status"
        | "party/create" | "party/leave" | "party/invite" | "party/join" | "party/kick")
        && path.chars().all(|c| c.is_ascii_alphanumeric() || "/?=&-_".contains(c))
}

#[tauri::command]
pub async fn online_get(app: AppHandle, account_uuid: String, path: String) -> AppResult<serde_json::Value> {
    if !allowed_path(&path) { return Err(AppError::Validation("Pfad nicht erlaubt".into())); }
    let token = online_token(&app, &account_uuid).await?;
    match crate::online::get(&account_uuid, &token, &path).await {
        Err(AppError::Auth(_)) => { let t = online_token(&app, &account_uuid).await?; crate::online::get(&account_uuid, &t, &path).await }
        other => other,
    }
}

#[tauri::command]
pub async fn online_post(app: AppHandle, account_uuid: String, path: String, payload: serde_json::Value) -> AppResult<Option<String>> {
    if !allowed_path(&path) { return Err(AppError::Validation("Pfad nicht erlaubt".into())); }
    cosmetics_post(&app, &account_uuid, &path, payload).await
}
