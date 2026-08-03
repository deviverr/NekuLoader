/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader.utils;

import java.util.HashMap;
import java.util.Map;

public class LanguageManager {
    private String currentLanguage;
    private Map<String, String> translations;

    public LanguageManager(String language) {
        this.currentLanguage = language;
        this.translations = new HashMap<String, String>();
        this.loadLanguage(language);
    }

    private void loadLanguage(String language) {
        this.translations.clear();
        switch (language) {
            case "\u0423\u043a\u0440\u0430\u0457\u043d\u0441\u044c\u043a\u0430": {
                this.loadUkrainian();
                break;
            }
            case "Espa\u00f1ol": {
                this.loadSpanish();
                break;
            }
            case "Fran\u00e7ais": {
                this.loadFrench();
                break;
            }
            case "Deutsch": {
                this.loadGerman();
                break;
            }
            case "\u0420\u0443\u0441\u0441\u043a\u0438\u0439": {
                this.loadRussian();
                break;
            }
            default: {
                this.loadEnglish();
            }
        }
    }

    private void loadEnglish() {
        this.translations.put("title", "NekuLoader \u208d^. .^\u208e\u27c6");
        this.translations.put("source", "Source:");
        this.translations.put("type", "Type:");
        this.translations.put("url", "URL:");
        this.translations.put("format", "Format:");
        this.translations.put("add_track_numbers", "Add Track Numbers");
        this.translations.put("download", "\u2193 DOWNLOAD");
        this.translations.put("stop", "\u25a0 STOP");
        this.translations.put("folder", "\ud83d\udcc1 FOLDER");
        this.translations.put("open", "\ud83d\uddc1 OPEN");
        this.translations.put("settings", "\u2699 SETTINGS");
        this.translations.put("download_log", "Download Log:");
        this.translations.put("status", "Status:");
        this.translations.put("status_idle", "Idle");
        this.translations.put("status_downloading", "Downloading...");
        this.translations.put("status_complete", "Complete! \u2713");
        this.translations.put("status_error", "Error!");
        this.translations.put("status_stopped", "Process stopped");
        this.translations.put("eta", "ETA:");
        this.translations.put("approximate_time", "Approximate time:");
        this.translations.put("total_time", "Total time:");
        this.translations.put("single_track", "Single Track");
        this.translations.put("album", "Album");
        this.translations.put("playlist", "Playlist");
        this.translations.put("menu_file", "File");
        this.translations.put("menu_help", "Help");
        this.translations.put("menu_settings", "Settings");
        this.translations.put("menu_exit", "Exit");
        this.translations.put("menu_about", "About");
        this.translations.put("customTheme", "Custom Theme");
        this.translations.put("bgStartColor", "Background Start Color");
        this.translations.put("bgEndColor", "Background End Color");
        this.translations.put("titleColor", "Title Color");
        this.translations.put("textColor", "Text Color");
        this.translations.put("starColor", "Star Color");
        this.translations.put("chooseColor", "Choose Color");
        this.translations.put("preview", "Preview");
        this.translations.put("previewText", "Sample text preview");
        this.translations.put("save", "Save");
        this.translations.put("cancel", "Cancel");
        this.translations.put("customizeTheme", "Customize Theme...");
        this.translations.put("checking_write_access", "Checking write access to destination...");
        this.translations.put("write_test_success", "\u2713 Write access confirmed");
        this.translations.put("write_test_failed", "\u2717 Cannot write to destination");
        this.translations.put("device_not_accessible", "Device is not accessible. Please check USB connection.");
        this.translations.put("testing_device_access", "Testing device accessibility...");
        this.translations.put("downloading_to_device", "Downloading to device:");
        this.translations.put("download_progress", "Progress:");
        this.translations.put("speed", "Speed:");
        this.translations.put("remaining", "Remaining:");
        this.translations.put("calculating", "Calculating...");
        this.translations.put("track_x_of_y", "Track %d of %d");
        this.translations.put("process_stopped_by_user", "Process stopped by user");
        this.translations.put("estimated_time", "Estimated time:");
        this.translations.put("analyzing_download", "Analyzing download...");
        this.translations.put("transfer_to_device", "Transfer to Device");
        this.translations.put("device_transfer", "\ud83d\udcf1 Device Transfer");
        this.translations.put("detect_devices", "Detect Devices");
        this.translations.put("android_device", "Android Device");
        this.translations.put("ios_device", "iOS Device");
        this.translations.put("no_devices_found", "No devices found");
        this.translations.put("devices_connected", "Devices connected:");
        this.translations.put("transfer_after_download", "Transfer to device after download");
        this.translations.put("device_destination", "Device Destination:");
        this.translations.put("select_device", "Select Device");
        this.translations.put("transferring_files", "Transferring files...");
        this.translations.put("transfer_complete", "Transfer complete!");
        this.translations.put("transfer_failed", "Transfer failed");
        this.translations.put("adb_not_installed", "ADB not installed");
        this.translations.put("install_adb", "Install ADB");
        this.translations.put("enable_usb_debugging", "Enable USB Debugging on your device");
    }

    private void loadUkrainian() {
        this.translations.put("title", "NekuLoader \u208d^. .^\u208e\u27c6");
        this.translations.put("source", "\u0414\u0436\u0435\u0440\u0435\u043b\u043e:");
        this.translations.put("type", "\u0422\u0438\u043f:");
        this.translations.put("url", "URL:");
        this.translations.put("format", "\u0424\u043e\u0440\u043c\u0430\u0442:");
        this.translations.put("add_track_numbers", "\u0414\u043e\u0434\u0430\u0442\u0438 \u043d\u043e\u043c\u0435\u0440\u0438 \u0442\u0440\u0435\u043a\u0456\u0432");
        this.translations.put("download", "\u2193 \u0417\u0410\u0412\u0410\u041d\u0422\u0410\u0416\u0418\u0422\u0418");
        this.translations.put("stop", "\u25a0 \u0421\u0422\u041e\u041f");
        this.translations.put("folder", "\ud83d\udcc1 \u041f\u0410\u041f\u041a\u0410");
        this.translations.put("open", "\ud83d\uddc1 \u0412\u0406\u0414\u041a\u0420\u0418\u0422\u0418");
        this.translations.put("settings", "\u2699 \u041d\u0410\u041b\u0410\u0428\u0422\u0423\u0412\u0410\u041d\u041d\u042f");
        this.translations.put("download_log", "\u0416\u0443\u0440\u043d\u0430\u043b \u0437\u0430\u0432\u0430\u043d\u0442\u0430\u0436\u0435\u043d\u043d\u044f:");
        this.translations.put("status", "\u0421\u0442\u0430\u0442\u0443\u0441:");
        this.translations.put("status_idle", "\u041e\u0447\u0456\u043a\u0443\u0432\u0430\u043d\u043d\u044f");
        this.translations.put("status_downloading", "\u0417\u0430\u0432\u0430\u043d\u0442\u0430\u0436\u0435\u043d\u043d\u044f...");
        this.translations.put("status_complete", "\u0417\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u043e! \u2713");
        this.translations.put("status_error", "\u041f\u043e\u043c\u0438\u043b\u043a\u0430!");
        this.translations.put("status_stopped", "\u041f\u0440\u043e\u0446\u0435\u0441 \u0437\u0443\u043f\u0438\u043d\u0435\u043d\u043e");
        this.translations.put("eta", "\u0427\u0430\u0441:");
        this.translations.put("approximate_time", "\u041f\u0440\u0438\u0431\u043b\u0438\u0437\u043d\u0438\u0439 \u0447\u0430\u0441:");
        this.translations.put("total_time", "\u0417\u0430\u0433\u0430\u043b\u044c\u043d\u0438\u0439 \u0447\u0430\u0441:");
        this.translations.put("single_track", "\u041e\u0434\u0438\u043d \u0442\u0440\u0435\u043a");
        this.translations.put("album", "\u0410\u043b\u044c\u0431\u043e\u043c");
        this.translations.put("playlist", "\u041f\u043b\u0435\u0439\u043b\u0438\u0441\u0442");
        this.translations.put("menu_file", "\u0424\u0430\u0439\u043b");
        this.translations.put("menu_help", "\u0414\u043e\u043f\u043e\u043c\u043e\u0433\u0430");
        this.translations.put("menu_settings", "\u041d\u0430\u043b\u0430\u0448\u0442\u0443\u0432\u0430\u043d\u043d\u044f");
        this.translations.put("menu_exit", "\u0412\u0438\u0445\u0456\u0434");
        this.translations.put("menu_about", "\u041f\u0440\u043e \u043f\u0440\u043e\u0433\u0440\u0430\u043c\u0443");
        this.translations.put("customTheme", "\u0412\u043b\u0430\u0441\u043d\u0430 \u0442\u0435\u043c\u0430");
        this.translations.put("bgStartColor", "\u041a\u043e\u043b\u0456\u0440 \u043f\u043e\u0447\u0430\u0442\u043a\u0443 \u0444\u043e\u043d\u0443");
        this.translations.put("bgEndColor", "\u041a\u043e\u043b\u0456\u0440 \u043a\u0456\u043d\u0446\u044f \u0444\u043e\u043d\u0443");
        this.translations.put("titleColor", "\u041a\u043e\u043b\u0456\u0440 \u0437\u0430\u0433\u043e\u043b\u043e\u0432\u043a\u0443");
        this.translations.put("textColor", "\u041a\u043e\u043b\u0456\u0440 \u0442\u0435\u043a\u0441\u0442\u0443");
        this.translations.put("starColor", "\u041a\u043e\u043b\u0456\u0440 \u0437\u0456\u0440\u043e\u043a");
        this.translations.put("chooseColor", "\u0412\u0438\u0431\u0435\u0440\u0456\u0442\u044c \u043a\u043e\u043b\u0456\u0440");
        this.translations.put("preview", "\u041f\u043e\u043f\u0435\u0440\u0435\u0434\u043d\u0456\u0439 \u043f\u0435\u0440\u0435\u0433\u043b\u044f\u0434");
        this.translations.put("previewText", "\u0417\u0440\u0430\u0437\u043e\u043a \u0442\u0435\u043a\u0441\u0442\u0443");
        this.translations.put("save", "\u0417\u0431\u0435\u0440\u0435\u0433\u0442\u0438");
        this.translations.put("cancel", "\u0421\u043a\u0430\u0441\u0443\u0432\u0430\u0442\u0438");
        this.translations.put("customizeTheme", "\u041d\u0430\u043b\u0430\u0448\u0442\u0443\u0432\u0430\u0442\u0438 \u0442\u0435\u043c\u0443...");
        this.translations.put("checking_write_access", "\u041f\u0435\u0440\u0435\u0432\u0456\u0440\u043a\u0430 \u0434\u043e\u0441\u0442\u0443\u043f\u0443 \u0434\u043e \u0437\u0430\u043f\u0438\u0441\u0443...");
        this.translations.put("write_test_success", "\u2713 \u0414\u043e\u0441\u0442\u0443\u043f \u043f\u0456\u0434\u0442\u0432\u0435\u0440\u0434\u0436\u0435\u043d\u043e");
        this.translations.put("write_test_failed", "\u2717 \u041d\u0435\u043c\u043e\u0436\u043b\u0438\u0432\u043e \u0437\u0430\u043f\u0438\u0441\u0430\u0442\u0438 \u0443 \u043f\u0440\u0438\u0437\u043d\u0430\u0447\u0435\u043d\u043d\u044f");
        this.translations.put("device_not_accessible", "\u041f\u0440\u0438\u0441\u0442\u0440\u0456\u0439 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0438\u0439. \u041f\u0435\u0440\u0435\u0432\u0456\u0440\u0442\u0435 USB \u0437'\u0454\u0434\u043d\u0430\u043d\u043d\u044f.");
        this.translations.put("testing_device_access", "\u0422\u0435\u0441\u0442\u0443\u0432\u0430\u043d\u043d\u044f \u0434\u043e\u0441\u0442\u0443\u043f\u0443 \u0434\u043e \u043f\u0440\u0438\u0441\u0442\u0440\u043e\u044e...");
        this.translations.put("downloading_to_device", "\u0417\u0430\u0432\u0430\u043d\u0442\u0430\u0436\u0435\u043d\u043d\u044f \u043d\u0430 \u043f\u0440\u0438\u0441\u0442\u0440\u0456\u0439:");
        this.translations.put("download_progress", "\u041f\u0440\u043e\u0433\u0440\u0435\u0441:");
        this.translations.put("speed", "\u0428\u0432\u0438\u0434\u043a\u0456\u0441\u0442\u044c:");
        this.translations.put("remaining", "\u0417\u0430\u043b\u0438\u0448\u0438\u043b\u043e\u0441\u044c:");
        this.translations.put("calculating", "\u041e\u0431\u0447\u0438\u0441\u043b\u0435\u043d\u043d\u044f...");
        this.translations.put("track_x_of_y", "\u0422\u0440\u0435\u043a %d \u0437 %d");
        this.translations.put("process_stopped_by_user", "\u041f\u0440\u043e\u0446\u0435\u0441 \u0437\u0443\u043f\u0438\u043d\u0435\u043d\u043e \u043a\u043e\u0440\u0438\u0441\u0442\u0443\u0432\u0430\u0447\u0435\u043c");
        this.translations.put("estimated_time", "\u041f\u0440\u0438\u0431\u043b\u0438\u0437\u043d\u0438\u0439 \u0447\u0430\u0441:");
        this.translations.put("analyzing_download", "\u0410\u043d\u0430\u043b\u0456\u0437 \u0437\u0430\u0432\u0430\u043d\u0442\u0430\u0436\u0435\u043d\u043d\u044f...");
        this.translations.put("transfer_to_device", "\u041f\u0435\u0440\u0435\u0434\u0430\u0442\u0438 \u043d\u0430 \u043f\u0440\u0438\u0441\u0442\u0440\u0456\u0439");
        this.translations.put("device_transfer", "\ud83d\udcf1 \u041f\u0435\u0440\u0435\u0434\u0430\u0447\u0430 \u043d\u0430 \u043f\u0440\u0438\u0441\u0442\u0440\u0456\u0439");
        this.translations.put("detect_devices", "\u0417\u043d\u0430\u0439\u0442\u0438 \u043f\u0440\u0438\u0441\u0442\u0440\u043e\u0457");
        this.translations.put("android_device", "Android \u043f\u0440\u0438\u0441\u0442\u0440\u0456\u0439");
        this.translations.put("ios_device", "iOS \u043f\u0440\u0438\u0441\u0442\u0440\u0456\u0439");
        this.translations.put("no_devices_found", "\u041f\u0440\u0438\u0441\u0442\u0440\u043e\u0457 \u043d\u0435 \u0437\u043d\u0430\u0439\u0434\u0435\u043d\u043e");
        this.translations.put("devices_connected", "\u041f\u0456\u0434\u043a\u043b\u044e\u0447\u0435\u043d\u0456 \u043f\u0440\u0438\u0441\u0442\u0440\u043e\u0457:");
        this.translations.put("transfer_after_download", "\u041f\u0435\u0440\u0435\u0434\u0430\u0442\u0438 \u043d\u0430 \u043f\u0440\u0438\u0441\u0442\u0440\u0456\u0439 \u043f\u0456\u0441\u043b\u044f \u0437\u0430\u0432\u0430\u043d\u0442\u0430\u0436\u0435\u043d\u043d\u044f");
        this.translations.put("device_destination", "\u041f\u0440\u0438\u0437\u043d\u0430\u0447\u0435\u043d\u043d\u044f \u043d\u0430 \u043f\u0440\u0438\u0441\u0442\u0440\u043e\u0457:");
        this.translations.put("select_device", "\u0412\u0438\u0431\u0440\u0430\u0442\u0438 \u043f\u0440\u0438\u0441\u0442\u0440\u0456\u0439");
        this.translations.put("transferring_files", "\u041f\u0435\u0440\u0435\u0434\u0430\u0447\u0430 \u0444\u0430\u0439\u043b\u0456\u0432...");
        this.translations.put("transfer_complete", "\u041f\u0435\u0440\u0435\u0434\u0430\u0447\u0430 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0430!");
        this.translations.put("transfer_failed", "\u041f\u043e\u043c\u0438\u043b\u043a\u0430 \u043f\u0435\u0440\u0435\u0434\u0430\u0447\u0456");
        this.translations.put("adb_not_installed", "ADB \u043d\u0435 \u0432\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u043e");
        this.translations.put("install_adb", "\u0412\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0438 ADB");
        this.translations.put("enable_usb_debugging", "\u0423\u0432\u0456\u043c\u043a\u043d\u0456\u0442\u044c \u043d\u0430\u043b\u0430\u0433\u043e\u0434\u0436\u0435\u043d\u043d\u044f USB \u043d\u0430 \u0432\u0430\u0448\u043e\u043c\u0443 \u043f\u0440\u0438\u0441\u0442\u0440\u043e\u0457");
    }

    private void loadSpanish() {
        this.translations.put("title", "NekuLoader \u208d^. .^\u208e\u27c6");
        this.translations.put("source", "Fuente:");
        this.translations.put("type", "Tipo:");
        this.translations.put("url", "URL:");
        this.translations.put("format", "Formato:");
        this.translations.put("add_track_numbers", "A\u00f1adir n\u00fameros de pista");
        this.translations.put("download", "\u2193 DESCARGAR");
        this.translations.put("stop", "\u25a0 DETENER");
        this.translations.put("folder", "\ud83d\udcc1 CARPETA");
        this.translations.put("open", "\ud83d\uddc1 ABRIR");
        this.translations.put("settings", "\u2699 AJUSTES");
        this.translations.put("download_log", "Registro de descarga:");
        this.translations.put("status", "Estado:");
        this.translations.put("status_idle", "Inactivo");
        this.translations.put("status_downloading", "Descargando...");
        this.translations.put("status_complete", "\u00a1Completo! \u2713");
        this.translations.put("status_error", "\u00a1Error!");
        this.translations.put("status_stopped", "Proceso detenido");
        this.translations.put("eta", "Tiempo:");
        this.translations.put("approximate_time", "Tiempo aproximado:");
        this.translations.put("total_time", "Tiempo total:");
        this.translations.put("single_track", "Pista \u00fanica");
        this.translations.put("album", "\u00c1lbum");
        this.translations.put("playlist", "Lista de reproducci\u00f3n");
        this.translations.put("menu_file", "Archivo");
        this.translations.put("menu_help", "Ayuda");
        this.translations.put("menu_settings", "Ajustes");
        this.translations.put("menu_exit", "Salir");
        this.translations.put("menu_about", "Acerca de");
        this.translations.put("customTheme", "Tema Personalizado");
        this.translations.put("bgStartColor", "Color de inicio de fondo");
        this.translations.put("bgEndColor", "Color de fin de fondo");
        this.translations.put("titleColor", "Color del t\u00edtulo");
        this.translations.put("textColor", "Color del texto");
        this.translations.put("starColor", "Color de las estrellas");
        this.translations.put("chooseColor", "Elegir color");
        this.translations.put("preview", "Vista previa");
        this.translations.put("previewText", "Texto de muestra");
        this.translations.put("save", "Guardar");
        this.translations.put("cancel", "Cancelar");
        this.translations.put("customizeTheme", "Personalizar tema...");
        this.translations.put("checking_write_access", "Comprobando acceso de escritura...");
        this.translations.put("write_test_success", "\u2713 Acceso confirmado");
        this.translations.put("write_test_failed", "\u2717 No se puede escribir en el destino");
        this.translations.put("device_not_accessible", "El dispositivo no es accesible. Verifique la conexi\u00f3n USB.");
        this.translations.put("testing_device_access", "Probando accesibilidad del dispositivo...");
        this.translations.put("downloading_to_device", "Descargando al dispositivo:");
        this.translations.put("download_progress", "Progreso:");
        this.translations.put("speed", "Velocidad:");
        this.translations.put("remaining", "Restante:");
        this.translations.put("calculating", "Calculando...");
        this.translations.put("track_x_of_y", "Pista %d de %d");
        this.translations.put("process_stopped_by_user", "Proceso detenido por el usuario");
        this.translations.put("estimated_time", "Tiempo estimado:");
        this.translations.put("analyzing_download", "Analizando descarga...");
        this.translations.put("transfer_to_device", "Transferir al dispositivo");
        this.translations.put("device_transfer", "\ud83d\udcf1 Transferencia de dispositivo");
        this.translations.put("detect_devices", "Detectar dispositivos");
        this.translations.put("android_device", "Dispositivo Android");
        this.translations.put("ios_device", "Dispositivo iOS");
        this.translations.put("no_devices_found", "No se encontraron dispositivos");
        this.translations.put("devices_connected", "Dispositivos conectados:");
        this.translations.put("transfer_after_download", "Transferir al dispositivo despu\u00e9s de descargar");
        this.translations.put("device_destination", "Destino del dispositivo:");
        this.translations.put("select_device", "Seleccionar dispositivo");
        this.translations.put("transferring_files", "Transfiriendo archivos...");
        this.translations.put("transfer_complete", "\u00a1Transferencia completa!");
        this.translations.put("transfer_failed", "Fall\u00f3 la transferencia");
        this.translations.put("adb_not_installed", "ADB no instalado");
        this.translations.put("install_adb", "Instalar ADB");
        this.translations.put("enable_usb_debugging", "Habilite la depuraci\u00f3n USB en su dispositivo");
    }

    private void loadFrench() {
        this.translations.put("title", "NekuLoader \u208d^. .^\u208e\u27c6");
        this.translations.put("source", "Source:");
        this.translations.put("type", "Type:");
        this.translations.put("url", "URL:");
        this.translations.put("format", "Format:");
        this.translations.put("add_track_numbers", "Ajouter les num\u00e9ros de piste");
        this.translations.put("download", "\u2193 T\u00c9L\u00c9CHARGER");
        this.translations.put("stop", "\u25a0 ARR\u00caTER");
        this.translations.put("folder", "\ud83d\udcc1 DOSSIER");
        this.translations.put("open", "\ud83d\uddc1 OUVRIR");
        this.translations.put("settings", "\u2699 PARAM\u00c8TRES");
        this.translations.put("download_log", "Journal de t\u00e9l\u00e9chargement:");
        this.translations.put("status", "Statut:");
        this.translations.put("status_idle", "Inactif");
        this.translations.put("status_downloading", "T\u00e9l\u00e9chargement...");
        this.translations.put("status_complete", "Termin\u00e9! \u2713");
        this.translations.put("status_error", "Erreur!");
        this.translations.put("status_stopped", "Processus arr\u00eat\u00e9");
        this.translations.put("eta", "Temps:");
        this.translations.put("approximate_time", "Temps approximatif:");
        this.translations.put("total_time", "Temps total:");
        this.translations.put("single_track", "Piste unique");
        this.translations.put("album", "Album");
        this.translations.put("playlist", "Liste de lecture");
        this.translations.put("menu_file", "Fichier");
        this.translations.put("menu_help", "Aide");
        this.translations.put("menu_settings", "Param\u00e8tres");
        this.translations.put("menu_exit", "Quitter");
        this.translations.put("menu_about", "\u00c0 propos");
        this.translations.put("customTheme", "Th\u00e8me Personnalis\u00e9");
        this.translations.put("bgStartColor", "Couleur haut de fond");
        this.translations.put("bgEndColor", "Couleur bas de fond");
        this.translations.put("titleColor", "Couleur du titre");
        this.translations.put("textColor", "Couleur du texte");
        this.translations.put("starColor", "Couleur des \u00e9toiles");
        this.translations.put("chooseColor", "Choisir une couleur");
        this.translations.put("preview", "Aper\u00e7u");
        this.translations.put("previewText", "Texte d'exemple");
        this.translations.put("save", "Sauvegarder");
        this.translations.put("cancel", "Abandonner");
        this.translations.put("customizeTheme", "Personnaliser le th\u00e8me...");
        this.translations.put("checking_write_access", "V\u00e9rification de l'acc\u00e8s en \u00e9criture...");
        this.translations.put("write_test_success", "\u2713 Acc\u00e8s confirm\u00e9");
        this.translations.put("write_test_failed", "\u2717 Impossible d'\u00e9crire sur la destination");
        this.translations.put("device_not_accessible", "L'appareil n'est pas accessible. V\u00e9rifiez la connexion USB.");
        this.translations.put("testing_device_access", "Test de l'accessibilit\u00e9 de l'appareil...");
        this.translations.put("downloading_to_device", "T\u00e9l\u00e9chargement vers l'appareil:");
        this.translations.put("download_progress", "Progression:");
        this.translations.put("speed", "Vitesse:");
        this.translations.put("remaining", "Restant:");
        this.translations.put("calculating", "Calcul...");
        this.translations.put("track_x_of_y", "Piste %d sur %d");
        this.translations.put("process_stopped_by_user", "Processus arr\u00eat\u00e9 par l'utilisateur");
        this.translations.put("estimated_time", "Temps estim\u00e9:");
        this.translations.put("analyzing_download", "Analyse du t\u00e9l\u00e9chargement...");
        this.translations.put("transfer_to_device", "Transf\u00e9rer vers l'appareil");
        this.translations.put("device_transfer", "\ud83d\udcf1 Transfert d'appareil");
        this.translations.put("detect_devices", "D\u00e9tecter les appareils");
        this.translations.put("android_device", "Appareil Android");
        this.translations.put("ios_device", "Appareil iOS");
        this.translations.put("no_devices_found", "Aucun appareil trouv\u00e9");
        this.translations.put("devices_connected", "Appareils connect\u00e9s:");
        this.translations.put("transfer_after_download", "Transf\u00e9rer vers l'appareil apr\u00e8s le t\u00e9l\u00e9chargement");
        this.translations.put("device_destination", "Destination de l'appareil:");
        this.translations.put("select_device", "S\u00e9lectionner l'appareil");
        this.translations.put("transferring_files", "Transfert de fichiers...");
        this.translations.put("transfer_complete", "Transfert termin\u00e9!");
        this.translations.put("transfer_failed", "\u00c9chec du transfert");
        this.translations.put("adb_not_installed", "ADB non install\u00e9");
        this.translations.put("install_adb", "Installer ADB");
        this.translations.put("enable_usb_debugging", "Activez le d\u00e9bogage USB sur votre appareil");
    }

    private void loadGerman() {
        this.translations.put("title", "NekuLoader \u208d^. .^\u208e\u27c6");
        this.translations.put("source", "Quelle:");
        this.translations.put("type", "Typ:");
        this.translations.put("url", "URL:");
        this.translations.put("format", "Format:");
        this.translations.put("add_track_numbers", "Titelnummern hinzuf\u00fcgen");
        this.translations.put("download", "\u2193 HERUNTERLADEN");
        this.translations.put("stop", "\u25a0 STOPP");
        this.translations.put("folder", "\ud83d\udcc1 ORDNER");
        this.translations.put("open", "\ud83d\uddc1 \u00d6FFNEN");
        this.translations.put("settings", "\u2699 EINSTELLUNGEN");
        this.translations.put("download_log", "Download-Protokoll:");
        this.translations.put("status", "Status:");
        this.translations.put("status_idle", "Unt\u00e4tig");
        this.translations.put("status_downloading", "Herunterladen...");
        this.translations.put("status_complete", "Abgeschlossen! \u2713");
        this.translations.put("status_error", "Fehler!");
        this.translations.put("status_stopped", "Prozess gestoppt");
        this.translations.put("eta", "Zeit:");
        this.translations.put("approximate_time", "Ungef\u00e4hre Zeit:");
        this.translations.put("total_time", "Gesamtzeit:");
        this.translations.put("single_track", "Einzeltitel");
        this.translations.put("album", "Album");
        this.translations.put("playlist", "Wiedergabeliste");
        this.translations.put("menu_file", "Datei");
        this.translations.put("menu_help", "Hilfe");
        this.translations.put("menu_settings", "Einstellungen");
        this.translations.put("menu_exit", "Beenden");
        this.translations.put("menu_about", "\u00dcber");
        this.translations.put("customTheme", "Benutzerdefiniertes Thema");
        this.translations.put("bgStartColor", "Hintergrund Startfarbe");
        this.translations.put("bgEndColor", "Hintergrund Endfarbe");
        this.translations.put("titleColor", "Titelfarbe");
        this.translations.put("textColor", "Textfarbe");
        this.translations.put("starColor", "Sternfarbe");
        this.translations.put("chooseColor", "Farbe w\u00e4hlen");
        this.translations.put("preview", "Vorschau");
        this.translations.put("previewText", "Beispieltext");
        this.translations.put("save", "Speichern");
        this.translations.put("cancel", "Abbrechen");
        this.translations.put("customizeTheme", "Thema anpassen...");
        this.translations.put("checking_write_access", "\u00dcberpr\u00fcfung des Schreibzugriffs...");
        this.translations.put("write_test_success", "\u2713 Zugriff best\u00e4tigt");
        this.translations.put("write_test_failed", "\u2717 Kann nicht auf Ziel schreiben");
        this.translations.put("device_not_accessible", "Ger\u00e4t ist nicht erreichbar. Bitte USB-Verbindung pr\u00fcfen.");
        this.translations.put("testing_device_access", "Testen der Ger\u00e4tezug\u00e4nglichkeit...");
        this.translations.put("downloading_to_device", "Herunterladen auf Ger\u00e4t:");
        this.translations.put("download_progress", "Fortschritt:");
        this.translations.put("speed", "Geschwindigkeit:");
        this.translations.put("remaining", "Verbleibend:");
        this.translations.put("calculating", "Berechnung...");
        this.translations.put("track_x_of_y", "Titel %d von %d");
        this.translations.put("process_stopped_by_user", "Prozess vom Benutzer gestoppt");
        this.translations.put("estimated_time", "Gesch\u00e4tzte Zeit:");
        this.translations.put("analyzing_download", "Download analysieren...");
        this.translations.put("transfer_to_device", "Auf Ger\u00e4t \u00fcbertragen");
        this.translations.put("device_transfer", "\ud83d\udcf1 Ger\u00e4te\u00fcbertragung");
        this.translations.put("detect_devices", "Ger\u00e4te erkennen");
        this.translations.put("android_device", "Android-Ger\u00e4t");
        this.translations.put("ios_device", "iOS-Ger\u00e4t");
        this.translations.put("no_devices_found", "Keine Ger\u00e4te gefunden");
        this.translations.put("devices_connected", "Verbundene Ger\u00e4te:");
        this.translations.put("transfer_after_download", "Nach dem Download auf Ger\u00e4t \u00fcbertragen");
        this.translations.put("device_destination", "Ger\u00e4teziel:");
        this.translations.put("select_device", "Ger\u00e4t ausw\u00e4hlen");
        this.translations.put("transferring_files", "Dateien \u00fcbertragen...");
        this.translations.put("transfer_complete", "\u00dcbertragung abgeschlossen!");
        this.translations.put("transfer_failed", "\u00dcbertragung fehlgeschlagen");
        this.translations.put("adb_not_installed", "ADB nicht installiert");
        this.translations.put("install_adb", "ADB installieren");
        this.translations.put("enable_usb_debugging", "Aktivieren Sie USB-Debugging auf Ihrem Ger\u00e4t");
    }

    private void loadRussian() {
        this.translations.put("title", "NekuLoader \u208d^. .^\u208e\u27c6");
        this.translations.put("source", "\u0418\u0441\u0442\u043e\u0447\u043d\u0438\u043a:");
        this.translations.put("type", "\u0422\u0438\u043f:");
        this.translations.put("url", "URL:");
        this.translations.put("format", "\u0424\u043e\u0440\u043c\u0430\u0442:");
        this.translations.put("add_track_numbers", "\u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u043d\u043e\u043c\u0435\u0440\u0430 \u0442\u0440\u0435\u043a\u043e\u0432");
        this.translations.put("download", "\u2193 \u0421\u041a\u0410\u0427\u0410\u0422\u042c");
        this.translations.put("stop", "\u25a0 \u0421\u0422\u041e\u041f");
        this.translations.put("folder", "\ud83d\udcc1 \u041f\u0410\u041f\u041a\u0410");
        this.translations.put("open", "\ud83d\uddc1 \u041e\u0422\u041a\u0420\u042b\u0422\u042c");
        this.translations.put("settings", "\u2699 \u041d\u0410\u0421\u0422\u0420\u041e\u0419\u041a\u0418");
        this.translations.put("download_log", "\u0416\u0443\u0440\u043d\u0430\u043b \u0437\u0430\u0433\u0440\u0443\u0437\u043a\u0438:");
        this.translations.put("status", "\u0421\u0442\u0430\u0442\u0443\u0441:");
        this.translations.put("status_idle", "\u041e\u0436\u0438\u0434\u0430\u043d\u0438\u0435");
        this.translations.put("status_downloading", "\u0417\u0430\u0433\u0440\u0443\u0437\u043a\u0430...");
        this.translations.put("status_complete", "\u0417\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u043e! \u2713");
        this.translations.put("status_error", "\u041e\u0448\u0438\u0431\u043a\u0430!");
        this.translations.put("status_stopped", "\u041f\u0440\u043e\u0446\u0435\u0441\u0441 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d");
        this.translations.put("eta", "\u0412\u0440\u0435\u043c\u044f:");
        this.translations.put("approximate_time", "\u041f\u0440\u0438\u043c\u0435\u0440\u043d\u043e\u0435 \u0432\u0440\u0435\u043c\u044f:");
        this.translations.put("total_time", "\u041e\u0431\u0449\u0435\u0435 \u0432\u0440\u0435\u043c\u044f:");
        this.translations.put("single_track", "\u041e\u0434\u0438\u043d \u0442\u0440\u0435\u043a");
        this.translations.put("album", "\u0410\u043b\u044c\u0431\u043e\u043c");
        this.translations.put("playlist", "\u041f\u043b\u0435\u0439\u043b\u0438\u0441\u0442");
        this.translations.put("menu_file", "\u0424\u0430\u0439\u043b");
        this.translations.put("menu_help", "\u041f\u043e\u043c\u043e\u0449\u044c");
        this.translations.put("menu_settings", "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438");
        this.translations.put("menu_exit", "\u0412\u044b\u0445\u043e\u0434");
        this.translations.put("menu_about", "\u041e \u043f\u0440\u043e\u0433\u0440\u0430\u043c\u043c\u0435");
        this.translations.put("customTheme", "\u041f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044c\u0441\u043a\u0430\u044f \u0442\u0435\u043c\u0430");
        this.translations.put("bgStartColor", "\u041d\u0430\u0447\u0430\u043b\u044c\u043d\u044b\u0439 \u0446\u0432\u0435\u0442 \u0444\u043e\u043d\u0430");
        this.translations.put("bgEndColor", "\u041a\u043e\u043d\u0435\u0447\u043d\u044b\u0439 \u0446\u0432\u0435\u0442 \u0444\u043e\u043d\u0430");
        this.translations.put("titleColor", "\u0426\u0432\u0435\u0442 \u0437\u0430\u0433\u043e\u043b\u043e\u0432\u043a\u0430");
        this.translations.put("textColor", "\u0426\u0432\u0435\u0442 \u0442\u0435\u043a\u0441\u0442\u0430");
        this.translations.put("starColor", "\u0426\u0432\u0435\u0442 \u0437\u0432\u0451\u0437\u0434");
        this.translations.put("chooseColor", "\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0446\u0432\u0435\u0442");
        this.translations.put("preview", "\u041f\u0440\u0435\u0434\u043f\u0440\u043e\u0441\u043c\u043e\u0442\u0440");
        this.translations.put("previewText", "\u041e\u0431\u0440\u0430\u0437\u0435\u0446 \u0442\u0435\u043a\u0441\u0442\u0430");
        this.translations.put("save", "\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c");
        this.translations.put("cancel", "\u041e\u0442\u043c\u0435\u043d\u0430");
        this.translations.put("customizeTheme", "\u041d\u0430\u0441\u0442\u0440\u043e\u0438\u0442\u044c \u0442\u0435\u043c\u0443...");
        this.translations.put("checking_write_access", "\u041f\u0440\u043e\u0432\u0435\u0440\u043a\u0430 \u0434\u043e\u0441\u0442\u0443\u043f\u0430 \u043d\u0430 \u0437\u0430\u043f\u0438\u0441\u044c...");
        this.translations.put("write_test_success", "\u2713 \u0414\u043e\u0441\u0442\u0443\u043f \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0451\u043d");
        this.translations.put("write_test_failed", "\u2717 \u041d\u0435\u0432\u043e\u0437\u043c\u043e\u0436\u043d\u043e \u0437\u0430\u043f\u0438\u0441\u0430\u0442\u044c \u0432 \u043c\u0435\u0441\u0442\u043e \u043d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u044f");
        this.translations.put("device_not_accessible", "\u0423\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u043e \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u043e. \u041f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435 USB-\u0441\u043e\u0435\u0434\u0438\u043d\u0435\u043d\u0438\u0435.");
        this.translations.put("testing_device_access", "\u0422\u0435\u0441\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435 \u0434\u043e\u0441\u0442\u0443\u043f\u0430 \u043a \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u0443...");
        this.translations.put("downloading_to_device", "\u0417\u0430\u0433\u0440\u0443\u0437\u043a\u0430 \u043d\u0430 \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u043e:");
        this.translations.put("download_progress", "\u041f\u0440\u043e\u0433\u0440\u0435\u0441\u0441:");
        this.translations.put("speed", "\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c:");
        this.translations.put("remaining", "\u041e\u0441\u0442\u0430\u043b\u043e\u0441\u044c:");
        this.translations.put("calculating", "\u0412\u044b\u0447\u0438\u0441\u043b\u0435\u043d\u0438\u0435...");
        this.translations.put("track_x_of_y", "\u0422\u0440\u0435\u043a %d \u0438\u0437 %d");
        this.translations.put("process_stopped_by_user", "\u041f\u0440\u043e\u0446\u0435\u0441\u0441 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u0435\u043c");
        this.translations.put("estimated_time", "\u041f\u0440\u0438\u0431\u043b\u0438\u0437\u0438\u0442\u0435\u043b\u044c\u043d\u043e\u0435 \u0432\u0440\u0435\u043c\u044f:");
        this.translations.put("analyzing_download", "\u0410\u043d\u0430\u043b\u0438\u0437 \u0437\u0430\u0433\u0440\u0443\u0437\u043a\u0438...");
        this.translations.put("transfer_to_device", "\u041f\u0435\u0440\u0435\u0434\u0430\u0442\u044c \u043d\u0430 \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u043e");
        this.translations.put("device_transfer", "\ud83d\udcf1 \u041f\u0435\u0440\u0435\u0434\u0430\u0447\u0430 \u043d\u0430 \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u043e");
        this.translations.put("detect_devices", "\u041e\u0431\u043d\u0430\u0440\u0443\u0436\u0438\u0442\u044c \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u0430");
        this.translations.put("android_device", "Android \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u043e");
        this.translations.put("ios_device", "iOS \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u043e");
        this.translations.put("no_devices_found", "\u0423\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u0430 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u044b");
        this.translations.put("devices_connected", "\u041f\u043e\u0434\u043a\u043b\u044e\u0447\u0435\u043d\u043d\u044b\u0435 \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u0430:");
        this.translations.put("transfer_after_download", "\u041f\u0435\u0440\u0435\u0434\u0430\u0442\u044c \u043d\u0430 \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u043e \u043f\u043e\u0441\u043b\u0435 \u0437\u0430\u0433\u0440\u0443\u0437\u043a\u0438");
        this.translations.put("device_destination", "\u041d\u0430\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435 \u043d\u0430 \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u0435:");
        this.translations.put("select_device", "\u0412\u044b\u0431\u0440\u0430\u0442\u044c \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u043e");
        this.translations.put("transferring_files", "\u041f\u0435\u0440\u0435\u0434\u0430\u0447\u0430 \u0444\u0430\u0439\u043b\u043e\u0432...");
        this.translations.put("transfer_complete", "\u041f\u0435\u0440\u0435\u0434\u0430\u0447\u0430 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0430!");
        this.translations.put("transfer_failed", "\u041e\u0448\u0438\u0431\u043a\u0430 \u043f\u0435\u0440\u0435\u0434\u0430\u0447\u0438");
        this.translations.put("adb_not_installed", "ADB \u043d\u0435 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d");
        this.translations.put("install_adb", "\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c ADB");
        this.translations.put("enable_usb_debugging", "\u0412\u043a\u043b\u044e\u0447\u0438\u0442\u0435 \u043e\u0442\u043b\u0430\u0434\u043a\u0443 USB \u043d\u0430 \u0432\u0430\u0448\u0435\u043c \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u0435");
    }

    public String get(String key) {
        return this.translations.getOrDefault(key, key);
    }

    public String get(String key, boolean catMode) {
        String text = this.translations.getOrDefault(key, key);
        return catMode ? text + " :3" : text;
    }

    public String getCurrentLanguage() {
        return this.currentLanguage;
    }
}
