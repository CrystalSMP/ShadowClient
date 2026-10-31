mod auth;
mod commands;
mod content;
mod instance;
mod error;
mod minecraft;
mod models;
mod online;
mod presence;
mod storage;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(
            tauri_plugin_log::Builder::new()
                .level(log::LevelFilter::Info)
                .max_file_size(2_000_000)
                .build(),
        )
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .plugin(tauri_plugin_process::init())
        .setup(|app| {
            presence::init();
            let handle = app.handle().clone();
            tauri::async_runtime::spawn(async move { commands::apply_presence_settings(&handle).await; });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::get_system_info,
            commands::load_settings,
            commands::save_settings,
            commands::load_profiles,
            commands::save_profiles,
            commands::auth_start,
            commands::auth_complete,
            commands::auth_cancel,
            commands::list_accounts,
            commands::remove_account,
            commands::is_game_running,
            commands::launch_profile,
            commands::open_instance_folder,
            commands::friends_list,
            commands::friends_action,
            commands::content_installed,
            commands::content_install,
            commands::content_remove,
            commands::cosmetics_get,
            commands::cosmetics_select,
            commands::cosmetics_upload,
            commands::instance_overview,
            commands::instance_list,
            commands::instance_toggle,
            commands::instance_remove,
            commands::instance_read_log,
            commands::instance_open,
            commands::online_get,
            commands::online_post,
        ])
        .run(tauri::generate_context!())
        .expect("ShadowClient konnte nicht gestartet werden");
}
