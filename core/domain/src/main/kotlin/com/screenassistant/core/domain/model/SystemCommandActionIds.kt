package com.screenassistant.core.domain.model

/**
 * Mapping explícito de SystemCommand a actionId para tracking de patrones.
 *
 * Cada SystemCommand tiene un actionId estable que no cambia bajo R8/ProGuard.
 * Se usa en [com.screenassistant.service.system.SystemActionHandler] para registrar
 * patrones de comportamiento.
 */
val SystemCommand.actionId: String
    get() = when (this) {
        is SystemCommand.Call -> "call"
        is SystemCommand.SendSms -> "send_sms"
        is SystemCommand.SetAlarm -> "set_alarm"
        is SystemCommand.OpenApp -> "open_app"
        is SystemCommand.SearchFile -> "search_file"
        is SystemCommand.QueueMessage -> "queue_message"
        is SystemCommand.OpenAlarms -> "open_alarms"
        is SystemCommand.CancelAlarm -> "cancel_alarm"
        is SystemCommand.SearchGoogle -> "search_google"
        is SystemCommand.OpenYouTube -> "open_youtube"
        is SystemCommand.OpenWhatsApp -> "open_whatsapp"
        is SystemCommand.PlayMusic -> "play_music"
        is SystemCommand.SetVolume -> "set_volume"
        is SystemCommand.SetLanguage -> "set_language"
        is SystemCommand.SetTimer -> "set_timer"
        is SystemCommand.Navigate -> "navigate"
        is SystemCommand.OpenSettings -> "open_settings"
        is SystemCommand.CallNumber -> "call_number"
        is SystemCommand.SaveMemory -> "save_memory"
        is SystemCommand.CreateNote -> "create_note"
        is SystemCommand.ReadNotes -> "read_notes"
        is SystemCommand.ReadNote -> "read_note"
        is SystemCommand.Calculate -> "calculate"
        is SystemCommand.CalculateExpression -> "calculate_expression"
        is SystemCommand.Stopwatch -> "stopwatch"
        is SystemCommand.DeviceInfo -> "device_info"
        is SystemCommand.Clipboard -> "clipboard"
        is SystemCommand.ListContacts -> "list_contacts"
        is SystemCommand.GetWifiInfo -> "get_wifi_info"
        is SystemCommand.SetBluetooth -> "set_bluetooth"
        is SystemCommand.SetBrightness -> "set_brightness"
        is SystemCommand.SetFlashlight -> "set_flashlight"
        is SystemCommand.SetAirplaneMode -> "set_airplane_mode"
        is SystemCommand.SetMobileData -> "set_mobile_data"
        is SystemCommand.OpenFile -> "open_file"
        is SystemCommand.CallHistory -> "call_history"
        is SystemCommand.ScanQr -> "scan_qr"
        is SystemCommand.OcrScan -> "ocr_scan"
        is SystemCommand.TranslateText -> "translate_text"
        is SystemCommand.DetectFace -> "detect_face"
        is SystemCommand.Vibrate -> "vibrate"
        is SystemCommand.GetLocation -> "get_location"
        is SystemCommand.SetWifi -> "set_wifi"
        is SystemCommand.TakePhoto -> "take_photo"
        is SystemCommand.SetAssistantMode -> "set_assistant_mode"
    }
