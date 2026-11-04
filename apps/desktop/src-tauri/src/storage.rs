use std::{collections::HashMap, fs, path::PathBuf};

use chrono::{DateTime, Utc};
use rusqlite::{Connection, OptionalExtension, params};

use crate::models::{
    AppSettings, CustomThemeColors, DownloadJob, HistoryEntry, JobStatus, PlaybackState,
};

#[derive(Debug, Clone)]
pub struct Database {
    path: PathBuf,
}

impl Database {
    pub fn new(path: PathBuf) -> Result<Self, String> {
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).map_err(|error| error.to_string())?;
        }
        let database = Self { path };
        database.initialize()?;
        Ok(database)
    }

    fn connect(&self) -> Result<Connection, String> {
        let connection = Connection::open(&self.path).map_err(|error| error.to_string())?;
        connection
            .pragma_update(None, "foreign_keys", "ON")
            .map_err(|error| error.to_string())?;
        connection
            .pragma_update(None, "journal_mode", "WAL")
            .map_err(|error| error.to_string())?;
        Ok(connection)
    }

    fn initialize(&self) -> Result<(), String> {
        self.connect()?
            .execute_batch(
                "
                CREATE TABLE IF NOT EXISTS app_meta (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS settings (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    data TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS jobs (
                    id TEXT PRIMARY KEY,
                    status TEXT NOT NULL,
                    data TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    legacy_text TEXT,
                    job_data TEXT,
                    created_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS playback_state (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    data TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS custom_themes (
                    name TEXT PRIMARY KEY,
                    data TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_jobs_updated ON jobs(updated_at DESC);
                CREATE INDEX IF NOT EXISTS idx_history_created ON history(created_at DESC);
                ",
            )
            .map_err(|error| error.to_string())?;
        Ok(())
    }

    pub fn load_settings(&self, fallback: AppSettings) -> Result<AppSettings, String> {
        let connection = self.connect()?;
        let data: Option<String> = connection
            .query_row("SELECT data FROM settings WHERE id = 1", [], |row| {
                row.get(0)
            })
            .optional()
            .map_err(|error| error.to_string())?;
        match data {
            Some(json) => serde_json::from_str(&json).map_err(|error| error.to_string()),
            None => Ok(fallback),
        }
    }

    pub fn save_settings(&self, settings: &AppSettings) -> Result<(), String> {
        let data = serde_json::to_string(settings).map_err(|error| error.to_string())?;
        self.connect()?
            .execute(
                "INSERT INTO settings(id, data, updated_at) VALUES(1, ?1, ?2)
                 ON CONFLICT(id) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at",
                params![data, Utc::now().to_rfc3339()],
            )
            .map_err(|error| error.to_string())?;
        Ok(())
    }

    pub fn load_playback_state(&self) -> Result<PlaybackState, String> {
        let connection = self.connect()?;
        let data: Option<String> = connection
            .query_row("SELECT data FROM playback_state WHERE id = 1", [], |row| {
                row.get(0)
            })
            .optional()
            .map_err(|error| error.to_string())?;
        // A queue that cannot be parsed is not worth failing startup over.
        Ok(data
            .and_then(|json| serde_json::from_str(&json).ok())
            .unwrap_or_default())
    }

    pub fn save_playback_state(&self, state: &PlaybackState) -> Result<(), String> {
        let data = serde_json::to_string(state).map_err(|error| error.to_string())?;
        self.connect()?
            .execute(
                "INSERT INTO playback_state(id, data, updated_at) VALUES(1, ?1, ?2)
                 ON CONFLICT(id) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at",
                params![data, Utc::now().to_rfc3339()],
            )
            .map_err(|error| error.to_string())?;
        Ok(())
    }

    pub fn save_custom_theme(&self, theme: &CustomThemeColors) -> Result<(), String> {
        let data = serde_json::to_string(theme).map_err(|error| error.to_string())?;
        self.connect()?
            .execute(
                "INSERT INTO custom_themes(name, data, updated_at) VALUES(?1, ?2, ?3)
                 ON CONFLICT(name) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at",
                params![theme.name, data, Utc::now().to_rfc3339()],
            )
            .map_err(|error| error.to_string())?;
        Ok(())
    }

    pub fn list_custom_themes(&self) -> Result<Vec<CustomThemeColors>, String> {
        let connection = self.connect()?;
        let mut statement = connection
            .prepare("SELECT data FROM custom_themes ORDER BY name ASC")
            .map_err(|error| error.to_string())?;
        let values = statement
            .query_map([], |row| row.get::<_, String>(0))
            .map_err(|error| error.to_string())?;
        let mut themes = Vec::new();
        for value in values {
            let json = value.map_err(|error| error.to_string())?;
            // Skip individually corrupt rows rather than losing the whole list.
            if let Ok(theme) = serde_json::from_str(&json) {
                themes.push(theme);
            }
        }
        Ok(themes)
    }

    pub fn delete_custom_theme(&self, name: &str) -> Result<(), String> {
        self.connect()?
            .execute("DELETE FROM custom_themes WHERE name = ?1", params![name])
            .map_err(|error| error.to_string())?;
        Ok(())
    }

    pub fn save_job(&self, job: &DownloadJob) -> Result<(), String> {
        let data = serde_json::to_string(job).map_err(|error| error.to_string())?;
        let status = serde_json::to_string(&job.status).map_err(|error| error.to_string())?;
        self.connect()?
            .execute(
                "INSERT INTO jobs(id, status, data, created_at, updated_at) VALUES(?1, ?2, ?3, ?4, ?5)
                 ON CONFLICT(id) DO UPDATE SET status = excluded.status, data = excluded.data, updated_at = excluded.updated_at",
                params![
                    job.id,
                    status,
                    data,
                    job.created_at.to_rfc3339(),
                    job.updated_at.to_rfc3339()
                ],
            )
            .map_err(|error| error.to_string())?;
        Ok(())
    }

    pub fn get_job(&self, id: &str) -> Result<Option<DownloadJob>, String> {
        let connection = self.connect()?;
        let data: Option<String> = connection
            .query_row("SELECT data FROM jobs WHERE id = ?1", [id], |row| {
                row.get(0)
            })
            .optional()
            .map_err(|error| error.to_string())?;
        data.map(|json| serde_json::from_str(&json).map_err(|error| error.to_string()))
            .transpose()
    }

    pub fn list_jobs(&self) -> Result<Vec<DownloadJob>, String> {
        let connection = self.connect()?;
        let mut statement = connection
            .prepare("SELECT data FROM jobs ORDER BY created_at DESC")
            .map_err(|error| error.to_string())?;
        let values = statement
            .query_map([], |row| row.get::<_, String>(0))
            .map_err(|error| error.to_string())?;
        let mut jobs = Vec::new();
        for value in values {
            let json = value.map_err(|error| error.to_string())?;
            jobs.push(serde_json::from_str(&json).map_err(|error| error.to_string())?);
        }
        Ok(jobs)
    }

    pub fn mark_running_jobs_interrupted(&self) -> Result<(), String> {
        for mut job in self.list_jobs()? {
            if matches!(
                job.status,
                JobStatus::Preparing | JobStatus::Downloading | JobStatus::Converting
            ) {
                job.status = JobStatus::Interrupted;
                job.stage = "Interrupted — ready to resume".into();
                job.updated_at = Utc::now();
                self.save_job(&job)?;
            }
        }
        Ok(())
    }

    pub fn add_history_job(&self, job: &DownloadJob) -> Result<(), String> {
        let data = serde_json::to_string(job).map_err(|error| error.to_string())?;
        self.connect()?
            .execute(
                "INSERT INTO history(legacy_text, job_data, created_at) VALUES(NULL, ?1, ?2)",
                params![data, Utc::now().to_rfc3339()],
            )
            .map_err(|error| error.to_string())?;
        Ok(())
    }

    pub fn list_history(&self) -> Result<Vec<HistoryEntry>, String> {
        let connection = self.connect()?;
        let mut statement = connection
            .prepare(
                "SELECT id, legacy_text, job_data, created_at
                 FROM history ORDER BY id DESC LIMIT 500",
            )
            .map_err(|error| error.to_string())?;
        let rows = statement
            .query_map([], |row| {
                Ok((
                    row.get::<_, i64>(0)?,
                    row.get::<_, Option<String>>(1)?,
                    row.get::<_, Option<String>>(2)?,
                    row.get::<_, String>(3)?,
                ))
            })
            .map_err(|error| error.to_string())?;
        let mut history = Vec::new();
        for row in rows {
            let (id, legacy_text, job_data, created_at) = row.map_err(|error| error.to_string())?;
            history.push(HistoryEntry {
                id,
                legacy_text,
                job: job_data
                    .map(|value| serde_json::from_str(&value).map_err(|error| error.to_string()))
                    .transpose()?,
                created_at: DateTime::parse_from_rfc3339(&created_at)
                    .map_err(|error| error.to_string())?
                    .with_timezone(&Utc),
            });
        }
        Ok(history)
    }

    pub fn import_legacy(&self, home: &str) -> Result<bool, String> {
        if self.meta("legacy_imported")?.as_deref() == Some("true") {
            return Ok(false);
        }

        let legacy_dir = PathBuf::from(home).join(".pixel-music-downloader");
        let settings_path = legacy_dir.join("settings.properties");
        let history_path = legacy_dir.join("download-history.log");
        let mut imported = false;

        if settings_path.is_file() {
            let properties = parse_java_properties(
                &fs::read(&settings_path).map_err(|error| error.to_string())?,
            );
            let mut settings = AppSettings::defaults(home);
            if let Some(value) = properties.get("outputFolder") {
                settings.output_dir = value.clone();
            }
            if let Some(value) = properties.get("soundEnabled") {
                settings.sound_enabled = value.parse().unwrap_or(true);
            }
            if let Some(value) = properties.get("animateBackground") {
                settings.animate_background = value.parse().unwrap_or(true);
            }
            if let Some(value) = properties.get("language") {
                settings.language = value.clone();
            }
            settings.theme = "Classic Legacy".into();
            self.save_settings(&settings)?;
            imported = true;
        }

        if history_path.is_file() {
            let text = String::from_utf8_lossy(
                &fs::read(&history_path).map_err(|error| error.to_string())?,
            )
            .into_owned();
            let connection = self.connect()?;
            for line in text.lines().filter(|line| !line.trim().is_empty()) {
                connection
                    .execute(
                        "INSERT INTO history(legacy_text, job_data, created_at) VALUES(?1, NULL, ?2)",
                        params![line.trim(), Utc::now().to_rfc3339()],
                    )
                    .map_err(|error| error.to_string())?;
            }
            imported = true;
        }

        self.set_meta("legacy_imported", "true")?;
        Ok(imported)
    }

    fn meta(&self, key: &str) -> Result<Option<String>, String> {
        self.connect()?
            .query_row("SELECT value FROM app_meta WHERE key = ?1", [key], |row| {
                row.get(0)
            })
            .optional()
            .map_err(|error| error.to_string())
    }

    fn set_meta(&self, key: &str, value: &str) -> Result<(), String> {
        self.connect()?
            .execute(
                "INSERT INTO app_meta(key, value) VALUES(?1, ?2)
                 ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                params![key, value],
            )
            .map_err(|error| error.to_string())?;
        Ok(())
    }
}

fn parse_java_properties(bytes: &[u8]) -> HashMap<String, String> {
    let source: String = bytes.iter().map(|byte| char::from(*byte)).collect();
    source
        .lines()
        .filter_map(|line| {
            let line = line.trim();
            if line.is_empty() || line.starts_with('#') || line.starts_with('!') {
                return None;
            }
            let index = line.find(['=', ':']).unwrap_or(line.len());
            let key = decode_property(line[..index].trim());
            let value = decode_property(line.get(index + 1..).unwrap_or("").trim());
            Some((key, value))
        })
        .collect()
}

fn decode_property(value: &str) -> String {
    let chars: Vec<char> = value.chars().collect();
    let mut decoded = String::new();
    let mut index = 0;
    while index < chars.len() {
        if chars[index] == '\\' && index + 1 < chars.len() {
            match chars[index + 1] {
                'u' if index + 5 < chars.len() => {
                    let code: String = chars[index + 2..=index + 5].iter().collect();
                    if let Ok(number) = u32::from_str_radix(&code, 16)
                        && let Some(character) = char::from_u32(number)
                    {
                        decoded.push(character);
                        index += 6;
                        continue;
                    }
                }
                'n' => decoded.push('\n'),
                'r' => decoded.push('\r'),
                't' => decoded.push('\t'),
                other => decoded.push(other),
            }
            index += 2;
        } else {
            decoded.push(chars[index]);
            index += 1;
        }
    }
    decoded
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_java_unicode_properties() {
        let properties = parse_java_properties(
            b"language=\\u0423\\u043a\\u0440\\u0430\\u0457\\u043d\\u0441\\u044c\\u043a\\u0430\n",
        );
        assert_eq!(properties.get("language").unwrap(), "Українська");
    }
}
