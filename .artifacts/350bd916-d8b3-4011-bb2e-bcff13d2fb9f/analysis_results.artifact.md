# Build Error Analysis: instrumentation-hierarchy.bin

## Error Description
The error points to a specific file in the Gradle transform cache:
`...\.gradle\caches\9.3.0\transforms\...\workspace\transformed\analysis\instrumentation-hierarchy.bin`

This file is part of the **ASM (Java bytecode manipulation)** instrumentation process used by the Android Gradle Plugin (AGP). It is typically generated when plugins like **Hilt**, **Firebase Performance**, or **AGP's own instrumentation** need to analyze the class hierarchy of your project and its dependencies to perform bytecode transformations.

### Why does this happen?
1.  **Cache Corruption**: The file was partially written or corrupted during a previous build (e.g., due to a crash, power loss, or forced termination).
2.  **File Lock**: Another process (like an antivirus scanner, a background indexing task, or another Gradle daemon) is holding a lock on the file.
3.  **Version Mismatch**: A sudden change in Gradle or AGP versions might have left incompatible artifacts in the cache.
4.  **Incomplete Sync**: The "null" error often suggests that Gradle expected the file to exist based on its internal database, but the physical file is missing or unreadable.

## How to Fix It

### 1. Clean and Rebuild (First Step)
Run the clean task to clear local build artifacts.
```bash
./gradlew clean
```

### 2. Force Clear the Specific Transform Cache
Since the error is in the global `.gradle` cache, a simple `clean` might not work. You should manually delete the corrupted folder:
1.  Close Android Studio.
2.  Navigate to: `C:\Users\QuintiVG\AndroidStudioProjects\ScreenAssistant\.gradle\caches\9.3.0\transforms\`
3.  Delete the folder mentioned in the error: `757b10bdf4f6f56511f0fcddb48c4d0e` (or the entire `transforms` folder if you want a complete reset).
4.  Restart Android Studio and Sync.

### 3. Invalidate Caches in Android Studio
This is the most reliable way to clear IDE-level caches that might be interfering.
1.  Go to **File > Invalidate Caches...**
2.  Select **"Clear file system cache and Local History"** and **"Delete embedded browser caches and cookies"**.
3.  Click **Invalidate and Restart**.

### 4. Stop All Gradle Daemons
Sometimes a "hung" daemon keeps a lock on the cache.
```bash
./gradlew --stop
```

### 5. Check Plugin Versions
Ensure your Hilt and AGP versions are compatible with Gradle 9.3.0.
- **Current project AGP**: 8.7.3
- **Current project Hilt**: 2.53.1
- **Current project Gradle**: 9.3.0

> [!TIP]
> If you are using Gradle 9.3.0 (which is very bleeding edge), ensure your plugins are also on their latest stable or alpha versions to support the new internal APIs.
