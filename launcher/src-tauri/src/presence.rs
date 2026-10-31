//! Discord Rich Presence: Launcher zeigt "Im Launcher", im Spiel "Spielt ShadowClient" + Server.
//! Der Mod schreibt den aktuellen Server nach `<instanz>/shadowclient/presence.json`, der Launcher liest die Datei.
use std::path::PathBuf;
use std::sync::mpsc::{channel, Receiver, RecvTimeoutError, Sender};
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use discord_rich_presence::activity::{Activity, Assets, Button, Timestamps};
use discord_rich_presence::{DiscordIpc, DiscordIpcClient};
use serde::Deserialize;

/// Fest eingebaute Application ID der ShadowClient-Discord-App (wie bei anderen Clients – Nutzer müssen nichts eintragen).
pub const DISCORD_APP_ID: &str = "1565792920433463416";

#[derive(Clone, Debug, PartialEq)]
pub enum State {
    Launcher,
    Starting(String),
    InGame { version: String, server: Option<String>, singleplayer: bool, since: i64 },
    Off,
}

enum Msg { Config(Option<String>), Set(State) }

static TX: OnceLock<Mutex<Sender<Msg>>> = OnceLock::new();

fn now() -> i64 { SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs() as i64).unwrap_or(0) }

pub fn init() {
    let (tx, rx) = channel();
    let _ = TX.set(Mutex::new(tx));
    std::thread::Builder::new().name("discord-rpc".into()).spawn(move || worker(rx)).ok();
}

fn send(m: Msg) { if let Some(tx) = TX.get() { let _ = tx.lock().map(|t| t.send(m)); } }

/// `client_id` = Application ID aus dem Discord Developer Portal; `None` schaltet die Anzeige aus.
pub fn configure(client_id: Option<String>) { send(Msg::Config(client_id.filter(|s| !s.trim().is_empty() && s.chars().all(|c| c.is_ascii_digit())))) }
pub fn set(state: State) { send(Msg::Set(state)) }

fn worker(rx: Receiver<Msg>) {
    let mut id: Option<String> = None;
    let mut state = State::Launcher;
    let mut client: Option<DiscordIpcClient> = None;
    let mut dirty = true;
    let launcher_since = now();
    loop {
        match rx.recv_timeout(Duration::from_secs(15)) {
            Ok(Msg::Config(new)) => { if new != id { if let Some(mut c) = client.take() { let _ = c.close(); } id = new; } dirty = true; }
            Ok(Msg::Set(s)) => { if s != state { state = s; dirty = true; } }
            Err(RecvTimeoutError::Timeout) => { if client.is_none() { dirty = true; } }  // Discord evtl. neu gestartet
            Err(RecvTimeoutError::Disconnected) => return,
        }
        if !dirty { continue; }
        let Some(app_id) = id.clone() else { continue };
        if client.is_none() {
            match DiscordIpcClient::new(&app_id) {
                Ok(mut c) => if c.connect().is_ok() { client = Some(c) },
                Err(_) => {}
            }
        }
        let Some(c) = client.as_mut() else { continue };
        let res = match &state {
            State::Off => c.clear_activity(),
            State::Launcher => c.set_activity(base("Im Launcher", "Wählt ein Profil", launcher_since)),
            State::Starting(v) => c.set_activity(base("Startet Minecraft", &format!("Version {v}"), now())),
            State::InGame { version, server, singleplayer, since } => {
                let details = match (server, singleplayer) {
                    (Some(s), _) => format!("Spielt auf {s}"),
                    (None, true) => "Spielt Einzelspieler".to_string(),
                    _ => "Im Hauptmenü".to_string(),
                };
                c.set_activity(base(&details, &format!("Minecraft {version} · ShadowClient"), *since))
            }
        };
        if res.is_err() { client = None; } else { dirty = false; }
    }
}

fn base<'a>(details: &'a str, state: &'a str, since: i64) -> Activity<'a> {
    Activity::new()
        .details(details)
        .state(state)
        .timestamps(Timestamps::new().start(since))
        .assets(Assets::new().large_image("logo").large_text("ShadowClient").small_image("minecraft").small_text("Minecraft Java"))
        .buttons(vec![Button::new("ShadowClient", "https://github.com/")])
}

#[derive(Deserialize)]
struct ModPresence { server: Option<String>, #[serde(default)] singleplayer: bool }

/// Liest während das Spiel läuft regelmäßig die vom Mod geschriebene Datei.
pub async fn watch_game(game_dir: PathBuf, version: String, running: impl Fn() -> bool) {
    let file = game_dir.join("shadowclient").join("presence.json");
    let _ = tokio::fs::remove_file(&file).await;
    let since = now();
    set(State::InGame { version: version.clone(), server: None, singleplayer: false, since });
    while running() {
        if let Ok(txt) = tokio::fs::read_to_string(&file).await {
            if let Ok(p) = serde_json::from_str::<ModPresence>(&txt) {
                set(State::InGame { version: version.clone(), server: p.server, singleplayer: p.singleplayer, since });
            }
        }
        tokio::time::sleep(Duration::from_secs(5)).await;
    }
}
