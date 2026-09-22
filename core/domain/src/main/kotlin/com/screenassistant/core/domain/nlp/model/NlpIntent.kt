package com.screenassistant.core.domain.nlp.model

/**
 * Intenciones NLP que mapean a variantes de SystemCommand.
 * Cada intent representa "qué quiere hacer el usuario".
 */
enum class NlpIntent {
    // === Notas ===
    CREATE_NOTE,
    READ_NOTE,
    READ_ALL_NOTES,

    // === Llamadas ===
    CALL_CONTACT,
    CALL_NUMBER,
    CALL_HISTORY,
    LIST_CONTACTS,

    // === Alarmas / Temporizador ===
    SET_ALARM,
    CANCEL_ALARM,
    OPEN_ALARMS,
    SET_TIMER,

    // === Apps / Búsqueda ===
    OPEN_APP,
    SEARCH_GOOGLE,
    OPEN_SETTINGS,

    // === Mensajería ===
    SEND_SMS,

    // === Multimedia ===
    PLAY_MUSIC,

    // === Sistema ===
    SET_VOLUME,
    SET_LANGUAGE,
    NAVIGATE,
    SAVE_MEMORY,
    OPEN_FILE,

    // === Hardware ===
    SET_BLUETOOTH,
    SET_BRIGHTNESS,
    SET_FLASHLIGHT,
    SET_AIRPLANE_MODE,
    SET_MOBILE_DATA,
    SET_WIFI,
    VIBRATE,
    GET_LOCATION,
    TAKE_PHOTO,

    // === Dispositivo ===
    DEVICE_INFO_MODEL,
    DEVICE_INFO_BATTERY,
    DEVICE_INFO_STORAGE,
    GET_WIFI_INFO,
    CLIPBOARD_COPY,
    CLIPBOARD_PASTE,
    CLIPBOARD_SHOW,
    CALCULATOR,
    STOPWATCH_START,
    STOPWATCH_STOP,
    STOPWATCH_GET_TIME,

    // === ML Kit ===
    SCAN_QR,
    OCR_SCAN,
    TRANSLATE_TEXT,
    DETECT_FACE,

    // === UI Markers ===
    HELP,
    REPEAT,
    START_MONITORING,
    STOP_MONITORING,
    ANALYZE_SCREEN,

    // === Fallback ===
    UNKNOWN
}
