//! Offizieller Microsoft-Login (OAuth 2.0 Device Code Flow) → Xbox Live → XSTS → Minecraft.
//! Der Launcher sieht nie ein Passwort: Die Anmeldung passiert im Browser bei Microsoft.
//! Gespeichert wird nur das Refresh-Token, und zwar im Windows-Anmeldeinformations-Manager.
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Duration;

use serde::{Deserialize, Serialize};
use serde_json::json;

use crate::error::{AppError, AppResult};
use crate::minecraft::net::client;

const MS_DEVICE: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const MS_TOKEN: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const XBL_AUTH: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_AUTH: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_PROFILE: &str = "https://api.minecraftservices.com/minecraft/profile";
const SCOPE: &str = "XboxLive.signin offline_access";
const KEYRING_SERVICE: &str = "ShadowClient";

/// Offizielle Azure-App von ShadowClient (öffentliche Client-ID, kein Geheimnis).
pub const DEFAULT_CLIENT_ID: &str = "7339d6c7-637e-4b1c-8d0d-712f90607a77";

/// Client-ID: Einstellungen → Build-Variable SHADOWCLIENT_MS_CLIENT_ID → ShadowClient-Standard.
pub fn resolve_client_id(from_settings: Option<&str>) -> AppResult<String> {
    from_settings
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(String::from)
        .or_else(|| option_env!("SHADOWCLIENT_MS_CLIENT_ID").map(String::from))
        .or_else(|| Some(DEFAULT_CLIENT_ID.to_string()))
        .ok_or_else(|| AppError::Auth("Keine Microsoft-Client-ID konfiguriert.".into()))
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct DeviceCode {
    pub device_code: String,
    pub user_code: String,
    pub verification_uri: String,
    pub expires_in: u64,
    pub interval: u64,
    pub message: String,
}

#[derive(Deserialize)]
struct RawDeviceCode {
    device_code: String,
    user_code: String,
    verification_uri: String,
    expires_in: u64,
    interval: u64,
    message: String,
}

#[derive(Deserialize)]
struct MsToken {
    access_token: String,
    refresh_token: String,
}

#[derive(Deserialize)]
struct MsError {
    error: String,
    error_description: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Account {
    pub uuid: String,
    pub name: String,
    pub skin_url: Option<String>,
}

pub struct McSession {
    pub account: Account,
    pub access_token: String,
    pub xuid: String,
    pub refresh_token: String,
}

static LOGIN_GENERATION: AtomicU64 = AtomicU64::new(0);

pub fn cancel_login() {
    LOGIN_GENERATION.fetch_add(1, Ordering::SeqCst);
}

pub async fn start_device_login(client_id: &str) -> AppResult<DeviceCode> {
    let resp = client().post(MS_DEVICE).form(&[("client_id", client_id), ("scope", SCOPE)]).send().await?;
    if !resp.status().is_success() {
        let err: MsError = resp.json().await.unwrap_or(MsError { error: "unknown".into(), error_description: None });
        return Err(AppError::Auth(ms_error_text(&err)));
    }
    let r: RawDeviceCode = resp.json().await?;
    Ok(DeviceCode { device_code: r.device_code, user_code: r.user_code, verification_uri: r.verification_uri, expires_in: r.expires_in, interval: r.interval, message: r.message })
}

fn ms_error_text(e: &MsError) -> String {
    match e.error.as_str() {
        "authorization_declined" => "Anmeldung wurde im Browser abgelehnt.".into(),
        "expired_token" => "Der Code ist abgelaufen. Bitte erneut anmelden.".into(),
        "invalid_client" | "unauthorized_client" => "Die Client-ID ist ungültig oder die Azure-App erlaubt keine persönlichen Microsoft-Konten / keinen öffentlichen Client-Flow.".into(),
        "invalid_grant" => "Sitzung abgelaufen. Bitte neu anmelden.".into(),
        _ => format!("{} {}", e.error, e.error_description.clone().unwrap_or_default()),
    }
}

/// Wartet, bis der Nutzer den Code im Browser bestätigt hat, und führt dann die Token-Kette aus.
pub async fn complete_device_login(client_id: &str, code: &DeviceCode) -> AppResult<McSession> {
    let generation = LOGIN_GENERATION.load(Ordering::SeqCst);
    let mut interval = code.interval.max(1);
    let deadline = std::time::Instant::now() + Duration::from_secs(code.expires_in);
    let ms = loop {
        tokio::time::sleep(Duration::from_secs(interval)).await;
        if LOGIN_GENERATION.load(Ordering::SeqCst) != generation {
            return Err(AppError::Auth("Anmeldung abgebrochen.".into()));
        }
        if std::time::Instant::now() > deadline {
            return Err(AppError::Auth("Der Code ist abgelaufen. Bitte erneut anmelden.".into()));
        }
        let resp = client()
            .post(MS_TOKEN)
            .form(&[("grant_type", "urn:ietf:params:oauth:grant-type:device_code"), ("client_id", client_id), ("device_code", &code.device_code)])
            .send()
            .await?;
        if resp.status().is_success() {
            break resp.json::<MsToken>().await?;
        }
        let err: MsError = resp.json().await?;
        match err.error.as_str() {
            "authorization_pending" => continue,
            "slow_down" => { interval += 5; continue; }
            _ => return Err(AppError::Auth(ms_error_text(&err))),
        }
    };
    minecraft_chain(ms).await
}

/// Erneuert die Sitzung mit einem gespeicherten Refresh-Token.
pub async fn refresh(client_id: &str, refresh_token: &str) -> AppResult<McSession> {
    let resp = client()
        .post(MS_TOKEN)
        .form(&[("grant_type", "refresh_token"), ("client_id", client_id), ("refresh_token", refresh_token), ("scope", SCOPE)])
        .send()
        .await?;
    if !resp.status().is_success() {
        let err: MsError = resp.json().await?;
        return Err(AppError::Auth(ms_error_text(&err)));
    }
    minecraft_chain(resp.json().await?).await
}

#[derive(Deserialize)]
#[serde(rename_all = "PascalCase")]
struct XboxResponse {
    token: String,
    display_claims: XboxClaims,
}
#[derive(Deserialize)]
struct XboxClaims {
    xui: Vec<XboxUser>,
}
#[derive(Deserialize)]
struct XboxUser {
    uhs: String,
    xid: Option<String>,
}
#[derive(Deserialize)]
struct XstsError {
    #[serde(rename = "XErr")]
    xerr: Option<u64>,
}
#[derive(Deserialize)]
struct McLogin {
    access_token: String,
}
#[derive(Deserialize)]
struct McProfile {
    id: String,
    name: String,
    #[serde(default)]
    skins: Vec<McSkin>,
}
#[derive(Deserialize)]
struct McSkin {
    url: String,
    state: String,
}

async fn minecraft_chain(ms: MsToken) -> AppResult<McSession> {
    // 1. Xbox Live
    let xbl: XboxResponse = client()
        .post(XBL_AUTH)
        .json(&json!({
            "Properties": { "AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com", "RpsTicket": format!("d={}", ms.access_token) },
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT"
        }))
        .send().await?.error_for_status()?.json().await?;

    // 2. XSTS
    let resp = client()
        .post(XSTS_AUTH)
        .json(&json!({
            "Properties": { "SandboxId": "RETAIL", "UserTokens": [xbl.token] },
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT"
        }))
        .send().await?;
    if resp.status() == reqwest::StatusCode::UNAUTHORIZED {
        let e: XstsError = resp.json().await.unwrap_or(XstsError { xerr: None });
        return Err(AppError::Auth(match e.xerr {
            Some(2148916233) => "Dieses Microsoft-Konto hat kein Xbox-Profil. Melde dich einmal auf minecraft.net an, um eines zu erstellen.".into(),
            Some(2148916235) => "Xbox Live ist in deinem Land nicht verfügbar.".into(),
            Some(2148916236) | Some(2148916237) => "Für dieses Konto ist eine Altersverifizierung bei Xbox nötig.".into(),
            Some(2148916238) => "Kinderkonto: Ein Erwachsener muss das Konto zu einer Microsoft-Familie hinzufügen.".into(),
            other => format!("XSTS verweigert (Code {other:?})"),
        }));
    }
    let xsts: XboxResponse = resp.error_for_status()?.json().await?;
    let user = xsts.display_claims.xui.first().ok_or_else(|| AppError::Auth("Xbox-Antwort ohne Benutzer".into()))?;
    let uhs = user.uhs.clone();
    let xuid = user.xid.clone().unwrap_or_default();

    // 3. Minecraft-Token
    let resp = client().post(MC_LOGIN)
        .json(&json!({ "identityToken": format!("XBL3.0 x={uhs};{}", xsts.token), "ensureLegacyEnabled": true }))
        .send().await?;
    if resp.status() == reqwest::StatusCode::FORBIDDEN || resp.status() == reqwest::StatusCode::UNAUTHORIZED {
        let status = resp.status();
        let body = resp.text().await.unwrap_or_default();
        log::warn!("login_with_xbox {status}: {body}");
        let detail: String = body.chars().take(220).collect();
        return Err(AppError::Auth(format!(
            "Minecraft-Dienste lehnen die Anmeldung ab ({status}). Antwort: {detail}"
        )));
    }
    let mc: McLogin = resp.error_for_status()?.json().await?;

    // 4. Profil (prüft gleichzeitig, ob Minecraft gekauft wurde)
    let resp = client().get(MC_PROFILE).bearer_auth(&mc.access_token).send().await?;
    if resp.status() == reqwest::StatusCode::NOT_FOUND {
        return Err(AppError::Auth("Dieses Konto besitzt Minecraft: Java Edition nicht.".into()));
    }
    let profile: McProfile = resp.error_for_status()?.json().await?;
    let skin_url = profile.skins.iter().find(|s| s.state == "ACTIVE").map(|s| s.url.clone());

    Ok(McSession {
        account: Account { uuid: profile.id, name: profile.name, skin_url },
        access_token: mc.access_token,
        xuid,
        refresh_token: ms.refresh_token,
    })
}

// ---------- Sicherer Token-Speicher ----------

pub fn store_refresh_token(uuid: &str, token: &str) -> AppResult<()> {
    keyring::Entry::new(KEYRING_SERVICE, uuid)?.set_password(token)?;
    Ok(())
}

pub fn load_refresh_token(uuid: &str) -> AppResult<String> {
    keyring::Entry::new(KEYRING_SERVICE, uuid)?
        .get_password()
        .map_err(|e| match e {
            keyring::Error::NoEntry => AppError::Auth("Keine gespeicherte Anmeldung. Bitte neu anmelden.".into()),
            other => other.into(),
        })
}

pub fn delete_refresh_token(uuid: &str) {
    if let Ok(e) = keyring::Entry::new(KEYRING_SERVICE, uuid) {
        let _ = e.delete_credential();
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn client_id_resolution() {
        assert_eq!(super::resolve_client_id(Some("  abc  ")).unwrap(), "abc");
        if option_env!("SHADOWCLIENT_MS_CLIENT_ID").is_none() {
            assert_eq!(super::resolve_client_id(Some("")).unwrap(), super::DEFAULT_CLIENT_ID);
            assert_eq!(super::resolve_client_id(None).unwrap(), super::DEFAULT_CLIENT_ID);
        }
    }
}
