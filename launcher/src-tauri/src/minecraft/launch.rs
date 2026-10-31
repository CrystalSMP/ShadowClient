//! Startet Minecraft mit einem LaunchPlan und streamt die Ausgabe.
use std::collections::{HashMap, VecDeque};
use std::path::Path;
use std::process::Stdio;

use serde::Serialize;
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::Command;

use super::install::LaunchPlan;
use crate::error::{AppError, AppResult};

/// Spielsitzung aus dem Microsoft-Login (Token nur im Speicher).
pub struct GameSession {
    pub name: String,
    pub uuid: String,
    pub access_token: String,
    pub xuid: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GameExit {
    pub code: Option<i32>,
    pub success: bool,
    /// Verständliche Erklärung bei Absturz
    pub hint: Option<String>,
    pub crash_report: Option<String>,
}

const CLASSPATH_SEP: &str = if cfg!(windows) { ";" } else { ":" };

fn substitute(arg: &str, vars: &HashMap<&str, String>) -> String {
    let mut out = arg.to_string();
    for (k, v) in vars {
        out = out.replace(&format!("${{{k}}}"), v);
    }
    out
}

pub fn build_args(plan: &LaunchPlan, session: &GameSession, memory_mb: u32, extra_jvm: &[String]) -> Vec<String> {
    let cp = plan.classpath.iter().map(|p| p.display().to_string()).collect::<Vec<_>>().join(CLASSPATH_SEP);
    let vars: HashMap<&str, String> = HashMap::from([
        ("auth_player_name", session.name.clone()),
        ("auth_uuid", session.uuid.clone()),
        ("auth_access_token", session.access_token.clone()),
        ("auth_xuid", session.xuid.clone()),
        ("clientid", String::new()),
        ("user_type", "msa".into()),
        ("version_name", plan.version_id.clone()),
        ("version_type", plan.version_type.clone()),
        ("game_directory", plan.game_dir.display().to_string()),
        ("assets_root", plan.assets_dir.display().to_string()),
        ("assets_index_name", plan.asset_index.clone()),
        ("natives_directory", plan.natives_dir.display().to_string()),
        ("library_directory", plan.libraries_dir.display().to_string()),
        ("classpath_separator", CLASSPATH_SEP.into()),
        ("launcher_name", "ShadowClient".into()),
        ("launcher_version", env!("CARGO_PKG_VERSION").into()),
        ("classpath", cp),
    ]);

    let mut args = vec![format!("-Xmx{memory_mb}M"), format!("-Xms{}M", (memory_mb / 2).max(512))];
    args.extend(extra_jvm.iter().cloned());
    args.extend(plan.jvm_args.iter().map(|a| substitute(a, &vars)));
    args.push(plan.main_class.clone());
    args.extend(plan.game_args.iter().map(|a| substitute(a, &vars)));
    args
}

/// Ersetzt das Access-Token in Texten, bevor sie geloggt werden.
pub fn redact(text: &str, token: &str) -> String {
    if token.is_empty() { text.to_string() } else { text.replace(token, "***") }
}

pub fn explain_crash(lines: &[String]) -> Option<String> {
    let all = lines.join("\n");
    let checks: [(&str, &str); 7] = [
        ("OutOfMemoryError", "Minecraft hatte zu wenig Arbeitsspeicher. Erhöhe den RAM im Profil."),
        ("UnsupportedClassVersionError", "Falsche Java-Version. Lass den Java-Pfad in den Einstellungen leer, dann wird die passende Version automatisch verwendet."),
        ("Incompatible mods found", "Inkompatible Mods gefunden. Prüfe den mods-Ordner des Profils (Details im Log)."),
        ("Mod resolution failed", "Mods konnten nicht geladen werden (fehlende Abhängigkeit oder falsche Version)."),
        ("Could not find or load main class", "Spieldateien unvollständig. Starte erneut, fehlende Dateien werden nachgeladen."),
        ("Pixel format not accelerated", "Grafiktreiber-Problem. Bitte Grafikkartentreiber aktualisieren."),
        ("Invalid session", "Sitzung ungültig. Bitte im Launcher ab- und neu anmelden."),
    ];
    checks.iter().find(|(needle, _)| all.contains(needle)).map(|(_, hint)| hint.to_string())
}

async fn newest_crash_report(game_dir: &Path, since: std::time::SystemTime) -> Option<String> {
    let mut rd = tokio::fs::read_dir(game_dir.join("crash-reports")).await.ok()?;
    let mut best: Option<(std::time::SystemTime, std::path::PathBuf)> = None;
    while let Ok(Some(e)) = rd.next_entry().await {
        if let Ok(m) = e.metadata().await.and_then(|m| m.modified()) {
            if m >= since && best.as_ref().map_or(true, |(t, _)| m > *t) {
                best = Some((m, e.path()));
            }
        }
    }
    best.map(|(_, p)| p.display().to_string())
}

/// Startet das Spiel und wartet, bis es beendet ist. `on_line(text, ist_stderr)` erhält jede Logzeile.
pub async fn run(
    plan: &LaunchPlan,
    session: &GameSession,
    memory_mb: u32,
    extra_jvm: &[String],
    on_line: impl Fn(String, bool) + Send + Sync + 'static,
) -> AppResult<GameExit> {
    let args = build_args(plan, session, memory_mb, extra_jvm);
    log::info!("Starte: {} {}", plan.java.display(), redact(&args.join(" "), &session.access_token));

    let mut cmd = Command::new(&plan.java);
    cmd.args(&args).current_dir(&plan.game_dir).stdout(Stdio::piped()).stderr(Stdio::piped()).stdin(Stdio::null());
    #[cfg(windows)]
    {
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
    let started = std::time::SystemTime::now();
    let mut child = cmd.spawn().map_err(|e| AppError::Install(format!("Java konnte nicht gestartet werden ({}): {e}", plan.java.display())))?;

    let on_line = std::sync::Arc::new(on_line);
    let tail = std::sync::Arc::new(tokio::sync::Mutex::new(VecDeque::<String>::with_capacity(300)));
    let token = session.access_token.clone();

    let mut readers = Vec::new();
    for (is_err, stream) in [
        (false, child.stdout.take().map(|s| Box::new(s) as Box<dyn tokio::io::AsyncRead + Unpin + Send>)),
        (true, child.stderr.take().map(|s| Box::new(s) as Box<dyn tokio::io::AsyncRead + Unpin + Send>)),
    ] {
        let Some(stream) = stream else { continue };
        let (cb, tail, token) = (on_line.clone(), tail.clone(), token.clone());
        readers.push(tokio::spawn(async move {
            let mut lines = BufReader::new(stream).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                let line = redact(&line, &token);
                {
                    let mut t = tail.lock().await;
                    if t.len() == 300 { t.pop_front(); }
                    t.push_back(line.clone());
                }
                cb(line, is_err);
            }
        }));
    }

    let status = child.wait().await?;
    for r in readers { let _ = r.await; }
    let lines: Vec<String> = tail.lock().await.iter().cloned().collect();
    let success = status.success();
    let exit = GameExit {
        code: status.code(),
        success,
        hint: if success { None } else { explain_crash(&lines).or_else(|| Some("Minecraft wurde unerwartet beendet. Details stehen im Log unten.".into())) },
        crash_report: if success { None } else { newest_crash_report(&plan.game_dir, started).await },
    };
    log::info!("Minecraft beendet: {:?}", exit.code);
    Ok(exit)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    #[test]
    fn substitutes_all_placeholders_and_orders_args() {
        let plan = LaunchPlan {
            java: PathBuf::from("java"),
            main_class: "net.fabricmc.loader.impl.launch.knot.KnotClient".into(),
            classpath: vec![PathBuf::from("a.jar"), PathBuf::from("b.jar")],
            jvm_args: vec!["-Djava.library.path=${natives_directory}".into(), "-cp".into(), "${classpath}".into()],
            game_args: vec!["--username".into(), "${auth_player_name}".into(), "--accessToken".into(), "${auth_access_token}".into(), "--version".into(), "${version_name}".into()],
            version_id: "fabric-loader-0.16.10-1.21.4".into(),
            version_type: "release".into(),
            asset_index: "19".into(),
            assets_dir: PathBuf::from("assets"),
            libraries_dir: PathBuf::from("libs"),
            natives_dir: PathBuf::from("natives"),
            game_dir: PathBuf::from("game"),
            loader_version: "0.16.10".into(),
        };
        let s = GameSession { name: "Steve".into(), uuid: "u".into(), access_token: "TOKEN".into(), xuid: "x".into() };
        let args = build_args(&plan, &s, 4096, &["-XX:+UseG1GC".into()]);
        assert_eq!(args[0], "-Xmx4096M");
        assert_eq!(args[2], "-XX:+UseG1GC");
        assert!(args.contains(&format!("a.jar{CLASSPATH_SEP}b.jar")));
        let main = args.iter().position(|a| a == &plan.main_class).unwrap();
        assert_eq!(&args[main + 1..main + 3], &["--username", "Steve"]);
        assert!(!args.iter().any(|a| a.contains("${")));
        assert_eq!(redact("x TOKEN y", "TOKEN"), "x *** y");
    }

    #[test]
    fn crash_hints() {
        assert!(explain_crash(&["java.lang.OutOfMemoryError: Java heap space".into()]).unwrap().contains("RAM"));
        assert!(explain_crash(&["all good".into()]).is_none());
    }
}
