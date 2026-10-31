//! ShadowClient-Backend (Supabase Edge Function): Freundesliste im Launcher.
//! Anmeldung wie in der Mod: Mojang-„join“ mit dem Minecraft-Token, Backend prüft per hasJoined.
use std::sync::Mutex;

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

use crate::auth::McSession;
use crate::error::{AppError, AppResult};
use crate::minecraft::net::{client, sha1_hex};

const BACKEND_URL: &str = "https://qshyrzvaspluttbvicnw.supabase.co/functions/v1/shadowclient";
const PUBLISHABLE_KEY: &str = "sb_publishable__aSiHs7_jEfjOGwZxQ0Wmw_mMzF8k7L";

/// (Minecraft-UUID, Sitzungs-Token) – nur im Speicher.
static SESSION: Mutex<Option<(String, String)>> = Mutex::new(None);

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Friend {
    pub uuid: String,
    pub name: String,
    pub online: bool,
    pub server: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone, Default)]
pub struct FriendsData {
    pub friends: Vec<Friend>,
    pub incoming: Vec<Friend>,
    pub outgoing: Vec<Friend>,
}

pub fn cached_token(uuid: &str) -> Option<String> {
    SESSION.lock().ok()?.as_ref().filter(|(u, _)| u == uuid).map(|(_, t)| t.clone())
}

fn store(uuid: &str, token: Option<String>) {
    if let Ok(mut s) = SESSION.lock() {
        *s = token.map(|t| (uuid.to_string(), t));
    }
}

/// Meldet sich beim Backend an und liefert ein Sitzungs-Token.
pub async fn login(session: &McSession) -> AppResult<String> {
    let server_id = sha1_hex(format!("{}{}", session.account.uuid, std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH).map(|d| d.as_nanos()).unwrap_or(0)).as_bytes());
    let server_id = &server_id[..32];

    // 1. Bei Mojang „join“ (wie beim Betreten eines Servers)
    let res = client()
        .post("https://sessionserver.mojang.com/session/minecraft/join")
        .json(&json!({ "accessToken": session.access_token, "selectedProfile": session.account.uuid, "serverId": server_id }))
        .send()
        .await?;
    if !res.status().is_success() {
        return Err(AppError::Auth(format!("Mojang-Anmeldung fehlgeschlagen ({})", res.status())));
    }

    // 2. Backend prüft per hasJoined und gibt ein Token
    let res = client()
        .post(format!("{BACKEND_URL}/heartbeat"))
        .header("apikey", PUBLISHABLE_KEY)
        .json(&json!({ "username": session.account.name, "serverId": server_id, "client": "launcher" }))
        .send()
        .await?;
    let status = res.status();
    let body: Value = res.json().await.unwrap_or(Value::Null);
    let token = body.get("token").and_then(|t| t.as_str()).map(String::from)
        .ok_or_else(|| AppError::Auth(format!("ShadowClient-Server hat abgelehnt ({status})")))?;
    store(&session.account.uuid, Some(token.clone()));
    Ok(token)
}

fn error_from(body: &Value, status: reqwest::StatusCode) -> AppError {
    let msg = body.get("error").and_then(|e| e.as_str()).map(String::from).unwrap_or_else(|| format!("Serverfehler ({status})"));
    AppError::Validation(msg)
}

pub async fn list(uuid: &str, token: &str) -> AppResult<FriendsData> {
    let res = client().get(format!("{BACKEND_URL}/friends"))
        .header("apikey", PUBLISHABLE_KEY).header("x-shadow-token", token).send().await?;
    let status = res.status();
    if status == reqwest::StatusCode::UNAUTHORIZED {
        store(uuid, None);
        return Err(AppError::Auth("Sitzung abgelaufen".into()));
    }
    let body: Value = res.json().await?;
    if !status.is_success() {
        return Err(error_from(&body, status));
    }
    Ok(serde_json::from_value(body)?)
}

/// Aktion ausführen (friends/add, friends/accept, friends/remove). Gibt eine Meldung zurück, falls vorhanden.
pub async fn action(uuid: &str, token: &str, path: &str, payload: Value) -> AppResult<Option<String>> {
    let res = client().post(format!("{BACKEND_URL}/{path}"))
        .header("apikey", PUBLISHABLE_KEY).header("x-shadow-token", token).json(&payload).send().await?;
    let status = res.status();
    if status == reqwest::StatusCode::UNAUTHORIZED {
        store(uuid, None);
        return Err(AppError::Auth("Sitzung abgelaufen".into()));
    }
    let body: Value = res.json().await.unwrap_or(Value::Null);
    if !status.is_success() {
        return Err(error_from(&body, status));
    }
    Ok(body.get("message").and_then(|m| m.as_str()).map(String::from))
}

/// Beliebige GET-Route mit Sitzung (z. B. cosmetics).
pub async fn get(uuid: &str, token: &str, path: &str) -> AppResult<Value> {
    let res = client().get(format!("{BACKEND_URL}/{path}"))
        .header("apikey", PUBLISHABLE_KEY).header("x-shadow-token", token).send().await?;
    let status = res.status();
    if status == reqwest::StatusCode::UNAUTHORIZED {
        store(uuid, None);
        return Err(AppError::Auth("Sitzung abgelaufen".into()));
    }
    let body: Value = res.json().await?;
    if !status.is_success() { return Err(error_from(&body, status)); }
    Ok(body)
}

#[cfg(test)]
mod tests {
    #[test]
    fn parses_friends_payload() {
        let v = serde_json::json!({
            "friends": [{"uuid": "a", "name": "Alex", "online": true, "server": "opsucht.net"}],
            "incoming": [{"uuid": "b", "name": "Bea", "online": false, "server": null}],
            "outgoing": []
        });
        let d: super::FriendsData = serde_json::from_value(v).unwrap();
        assert_eq!(d.friends[0].server.as_deref(), Some("opsucht.net"));
        assert!(d.incoming[0].server.is_none());
    }
}
