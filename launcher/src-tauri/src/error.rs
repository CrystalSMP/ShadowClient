use serde::Serialize;

/// Fehler, die an das Frontend gemeldet werden. Werden als lesbarer Text serialisiert.
#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("Dateifehler: {0}")]
    Io(#[from] std::io::Error),
    #[error("Ungültige Daten: {0}")]
    Json(#[from] serde_json::Error),
    #[error("Netzwerkfehler: {0}")]
    Http(String),
    #[error("{0}")]
    Validation(String),
    #[error("Anmeldung fehlgeschlagen: {0}")]
    Auth(String),
    #[error("Installation fehlgeschlagen: {0}")]
    Install(String),
    #[error("Pfad nicht verfügbar: {0}")]
    Path(String),
    #[error("{0}")]
    Offline(String),
    #[error("Sicherer Speicher (Windows-Anmeldeinformationen): {0}")]
    Keyring(String),
}

impl From<reqwest::Error> for AppError {
    fn from(e: reqwest::Error) -> Self {
        let msg = if e.is_timeout() {
            "Zeitüberschreitung – bitte Internetverbindung prüfen".to_string()
        } else if e.is_connect() {
            "Server nicht erreichbar – bitte Internetverbindung prüfen".to_string()
        } else if let Some(status) = e.status() {
            format!("HTTP {status} bei {}", e.url().map(|u| u.as_str()).unwrap_or("?"))
        } else {
            e.to_string()
        };
        AppError::Http(msg)
    }
}

impl From<keyring::Error> for AppError {
    fn from(e: keyring::Error) -> Self {
        AppError::Keyring(e.to_string())
    }
}

impl Serialize for AppError {
    fn serialize<S: serde::Serializer>(&self, s: S) -> Result<S::Ok, S::Error> {
        s.serialize_str(&self.to_string())
    }
}

pub type AppResult<T> = Result<T, AppError>;
