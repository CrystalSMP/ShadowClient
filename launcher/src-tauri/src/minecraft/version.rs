#![allow(dead_code)] // Felder spiegeln das vollständige Mojang-Format
//! Mojang-Versionsformat (version_manifest_v2 / <version>.json) inkl. Regel-Auswertung.
use std::collections::HashMap;

use serde::Deserialize;

pub const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

#[derive(Debug, Deserialize)]
pub struct Manifest {
    pub versions: Vec<ManifestEntry>,
}

#[derive(Debug, Deserialize)]
pub struct ManifestEntry {
    pub id: String,
    #[serde(rename = "type")]
    pub kind: String,
    pub url: String,
    pub sha1: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VersionJson {
    pub id: String,
    #[serde(rename = "type", default)]
    pub kind: String,
    pub main_class: String,
    #[serde(default)]
    pub arguments: Option<Arguments>,
    #[serde(default)]
    pub libraries: Vec<Library>,
    pub asset_index: Option<AssetIndexRef>,
    #[serde(default)]
    pub downloads: Option<VersionDownloads>,
    pub java_version: Option<JavaVersion>,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct Arguments {
    #[serde(default)]
    pub game: Vec<Argument>,
    #[serde(default)]
    pub jvm: Vec<Argument>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(untagged)]
pub enum Argument {
    Plain(String),
    Conditional { rules: Vec<Rule>, value: ArgValue },
}

#[derive(Debug, Clone, Deserialize)]
#[serde(untagged)]
pub enum ArgValue {
    One(String),
    Many(Vec<String>),
}

#[derive(Debug, Clone, Deserialize)]
pub struct Rule {
    pub action: String,
    pub os: Option<OsRule>,
    pub features: Option<HashMap<String, bool>>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct OsRule {
    pub name: Option<String>,
    pub arch: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Library {
    pub name: String,
    #[serde(default)]
    pub downloads: Option<LibraryDownloads>,
    /// Maven-Repository-Basis (Fabric-Format)
    pub url: Option<String>,
    pub sha1: Option<String>,
    pub size: Option<u64>,
    #[serde(default)]
    pub rules: Vec<Rule>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct LibraryDownloads {
    pub artifact: Option<Artifact>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Artifact {
    pub path: Option<String>,
    pub url: String,
    pub sha1: Option<String>,
    pub size: Option<u64>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AssetIndexRef {
    pub id: String,
    pub url: String,
    pub sha1: String,
    pub size: Option<u64>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct VersionDownloads {
    pub client: Artifact,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JavaVersion {
    pub component: String,
    pub major_version: u32,
}

#[derive(Debug, Deserialize)]
pub struct AssetIndex {
    pub objects: HashMap<String, AssetObject>,
}

#[derive(Debug, Deserialize)]
pub struct AssetObject {
    pub hash: String,
    pub size: u64,
}

/// Name des Betriebssystems im Mojang-Format.
pub fn mojang_os() -> &'static str {
    match std::env::consts::OS {
        "windows" => "windows",
        "macos" => "osx",
        _ => "linux",
    }
}

/// Wertet Mojang-Regeln aus. Ohne Regeln: erlaubt. Mit Regeln: Standard „verboten“,
/// die letzte passende Regel gewinnt. Feature-Regeln (Demo, Quick-Play, eigene
/// Auflösung) werden als nicht aktiv behandelt.
pub fn rules_allow(rules: &[Rule]) -> bool {
    if rules.is_empty() {
        return true;
    }
    let mut allowed = false;
    for r in rules {
        let os_ok = r.os.as_ref().map_or(true, |os| {
            os.name.as_deref().map_or(true, |n| n == mojang_os())
                && os.arch.as_deref().map_or(true, |a| a != "x86" || cfg!(target_arch = "x86"))
        });
        let features_ok = r.features.as_ref().map_or(true, |f| f.values().all(|v| !*v));
        if os_ok && features_ok {
            allowed = r.action == "allow";
        }
    }
    allowed
}

/// Flacht eine Argumentliste unter Berücksichtigung der Regeln ab.
pub fn flatten_args(args: &[Argument]) -> Vec<String> {
    let mut out = Vec::new();
    for a in args {
        match a {
            Argument::Plain(s) => out.push(s.clone()),
            Argument::Conditional { rules, value } if rules_allow(rules) => match value {
                ArgValue::One(s) => out.push(s.clone()),
                ArgValue::Many(v) => out.extend(v.iter().cloned()),
            },
            _ => {}
        }
    }
    out
}

/// Maven-Koordinate `group:artifact:version[:classifier]` → relativer Pfad.
pub fn maven_path(name: &str) -> Option<String> {
    let mut parts = name.split(':');
    let group = parts.next()?;
    let artifact = parts.next()?;
    let version = parts.next()?;
    let classifier = parts.next();
    let file = match classifier {
        Some(c) => format!("{artifact}-{version}-{c}.jar"),
        None => format!("{artifact}-{version}.jar"),
    };
    Some(format!("{}/{artifact}/{version}/{file}", group.replace('.', "/")))
}

/// Schlüssel zur Deduplizierung: Koordinate ohne Version (Gruppe, Artefakt, Klassifizierer).
pub fn library_key(name: &str) -> String {
    let p: Vec<&str> = name.split(':').collect();
    match p.len() {
        0..=2 => name.to_string(),
        3 => format!("{}:{}", p[0], p[1]),
        _ => format!("{}:{}:{}", p[0], p[1], p[3..].join(":")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maven_paths() {
        assert_eq!(
            maven_path("net.fabricmc:fabric-loader:0.16.10").unwrap(),
            "net/fabricmc/fabric-loader/0.16.10/fabric-loader-0.16.10.jar"
        );
        assert_eq!(
            maven_path("org.lwjgl:lwjgl:3.3.3:natives-windows").unwrap(),
            "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar"
        );
        assert_eq!(library_key("org.ow2.asm:asm:9.7.1"), "org.ow2.asm:asm");
        assert_eq!(library_key("org.lwjgl:lwjgl:3.3.3:natives-windows"), "org.lwjgl:lwjgl:natives-windows");
    }

    #[test]
    fn parses_version_json_and_rules() {
        let json = format!(r#"{{
          "id": "1.21.4", "type": "release", "mainClass": "net.minecraft.client.main.Main",
          "arguments": {{
            "game": ["--username", "${{auth_player_name}}",
              {{"rules": [{{"action": "allow", "features": {{"is_demo_user": true}}}}], "value": "--demo"}}],
            "jvm": [
              {{"rules": [{{"action": "allow", "os": {{"name": "{os}"}}}}], "value": ["-Dthis.os=1"]}},
              {{"rules": [{{"action": "allow", "os": {{"name": "nope"}}}}], "value": "-Dother.os=1"}},
              "-cp", "${{classpath}}"]
          }},
          "libraries": [
            {{"name": "a:b:1", "downloads": {{"artifact": {{"path": "a/b/1/b-1.jar", "url": "https://x/a.jar", "sha1": "00", "size": 1}}}}}},
            {{"name": "c:d:1:natives-{os}", "downloads": {{"artifact": {{"path": "p", "url": "https://x/c.jar", "sha1": "00", "size": 1}}}},
              "rules": [{{"action": "allow", "os": {{"name": "{os}"}}}}]}}
          ],
          "assetIndex": {{"id": "19", "url": "https://x/19.json", "sha1": "00", "size": 1, "totalSize": 5}},
          "downloads": {{"client": {{"url": "https://x/client.jar", "sha1": "00", "size": 1}}}},
          "javaVersion": {{"component": "java-runtime-delta", "majorVersion": 21}}
        }}"#, os = mojang_os());
        let v: VersionJson = serde_json::from_str(&json).unwrap();
        let args = v.arguments.unwrap();
        assert_eq!(flatten_args(&args.game), vec!["--username", "${auth_player_name}"]);
        assert_eq!(flatten_args(&args.jvm), vec!["-Dthis.os=1", "-cp", "${classpath}"]);
        assert!(v.libraries.iter().all(|l| rules_allow(&l.rules)));
        assert_eq!(v.java_version.unwrap().major_version, 21);
    }

    #[test]
    fn disallow_rule_wins() {
        let rules: Vec<Rule> = serde_json::from_str(&format!(
            r#"[{{"action":"allow"}},{{"action":"disallow","os":{{"name":"{}"}}}}]"#, mojang_os()
        )).unwrap();
        assert!(!rules_allow(&rules));
    }
}
