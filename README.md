# AllDown – Universal Media Downloader

<p align="center">
  <img src="app/src/main/res/drawable/ic_logo_alldown.xml" width="100" height="100" alt="AllDown Logo" />
</p>

<p align="center">
  <b>“Paste. Choose. Download.”</b>
</p>

<p align="center">
  A clean, fast, and modern Native Android downloader application built with Kotlin, Material 3, OkHttp, Jsoup, and Android Foreground Services.
</p>

---

## 🌟 Key Highlights

* **100% In-App Experience**: URL analysis, format discovery, download progress, and file management happen completely inside the app. The user is **never** redirected to external browsers.
* **Smart Webpage & Media Discovery Engine**: Automatically inspects normal webpage URLs (`https://example.com/page/...`) for OpenGraph tags (`og:image`, `og:video`), HTML5 elements (`<video>`, `<audio>`, `<source>`), responsive image sets (`<img srcset>`), and direct document download links.
* **Direct File Detection**: Instantly identifies direct video, audio, image, document, and archive endpoints without unnecessary overhead.
* **In-App Download Manager**: Multi-threaded queue with pause, resume (HTTP `Range` requests), cancel, retry, and live speed (`MB/s`) calculation.
* **Foreground Service Notifications**: Real-time progress bar, percentage, speed, and interactive action buttons (`Pause` / `Cancel`) on Android notifications.
* **Scoped Storage & MediaStore Compliant**: Saves downloaded files safely so they are immediately accessible in your device's Gallery, Music player, and File Manager.
* **Built-in File Manager & History**: Filter files by category (*Video, Audio, Images, Documents, Other*), live search, sort (*Date, Size, Name*), rename, open, and share.
* **Share-to-App Target**: Directly share any link from Chrome, social media, or other apps into AllDown to start instant analysis.
* **Clipboard Auto-Detection**: Prompts to analyze copied links as soon as the app is opened.
* **Legal & DRM Compliant**: Strictly respects DRM, authentication, paywalls, and access controls. Gracefully displays clear limitation messages for protected services instead of crashing.

---

## 🛠️ Architecture & Technology Stack

| Layer | Technologies / Libraries |
| :--- | :--- |
| **Language** | Kotlin 100% |
| **UI Framework** | Material 3 Components, ViewBinding, ViewPager2, BottomNavigationView, Custom Dynamic Theme (Day/Night) |
| **Networking & HTTP** | OkHttp 4.12.0 (Redirects, HTTP Range headers, Stream handling) |
| **HTML Webpage Parsing** | Jsoup 1.18.1 (OpenGraph, HTML5 video/audio, srcset parser, relative URL resolver) |
| **Image Loading** | Coil 2.7.0 (Asynchronous memory/disk caching & thumbnail previews) |
| **Concurrency & Reactive** | Kotlin Coroutines (`Dispatchers.IO`, `SupervisorJob`), `StateFlow`, `SharedFlow` |
| **Local Database** | High-performance Android SQLite (`DownloadDatabaseHelper`) with reactive change notifications |
| **Background Processing** | Android Foreground Service (`DownloadService`) with Android 14+ `dataSync` support |
| **Storage & Sharing** | Android Scoped Storage, `androidx.core.content.FileProvider`, `StatFs` |

---

## 📂 Project Structure

```text
AllDownloader/
├── README.md                              # Complete Project Documentation
├── build.gradle.kts                       # Root build configuration
├── settings.gradle.kts                    # Project settings & repositories
├── gradle/
│   ├── libs.versions.toml                 # Gradle Version Catalog
│   └── wrapper/                           # Gradle Wrapper (9.6.0)
└── app/
    ├── build.gradle.kts                   # App-level build script & dependencies
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml        # Permissions, Foreground Service & Intent Filters
        │   ├── java/com/example/alldownloader/
        │   │   ├── AllDownApp.kt          # Application class & Theme initializer
        │   │   ├── MainActivity.kt        # ViewPager2, BottomNav, Share-Intent & Badge handler
        │   │   ├── data/
        │   │   │   ├── db/
        │   │   │   │   ├── DownloadDatabaseHelper.kt # SQLite database manager
        │   │   │   │   └── DownloadRepository.kt     # Reactive data repository
        │   │   │   └── model/
        │   │   │       ├── AppSettings.kt            # Settings configuration model
        │   │   │       ├── AudioVariant.kt           # Audio stream metadata
        │   │   │       ├── DiscoveredResource.kt     # Multi-item discovered resource model
        │   │   │       ├── DownloadItem.kt           # Download task entity
        │   │   │       ├── DownloadStatus.kt         # Task status enum (QUEUED, DOWNLOADING, etc.)
        │   │   │       ├── MediaAnalysisResult.kt    # URL analysis output & diagnostic payload
        │   │   │       ├── MediaCategory.kt          # Categories (VIDEO, AUDIO, IMAGE, DOC, OTHER)
        │   │   │       ├── MediaVariant.kt           # Video stream resolution model
        │   │   │       └── StorageInfo.kt            # Storage breakdown model
        │   │   ├── network/
        │   │   │   └── UrlAnalyzerEngine.kt          # Smart Webpage & Media Analyzer Engine
        │   │   ├── service/
        │   │   │   ├── DownloadManager.kt            # Download worker queue & speed calculator
        │   │   │   └── DownloadService.kt            # Android Foreground Service & Notifications
        │   │   ├── ui/
        │   │   │   ├── adapter/
        │   │   │   │   ├── AudioVariantAdapter.kt    # Audio format selector
        │   │   │   │   ├── DiscoveredResourceAdapter.kt # Discovered items list adapter
        │   │   │   │   ├── DownloadTaskAdapter.kt    # Active downloads queue adapter
        │   │   │   │   ├── FileItemAdapter.kt        # Downloaded files manager adapter
        │   │   │   │   ├── HistoryAdapter.kt         # Download history adapter
        │   │   │   │   ├── MainPagerAdapter.kt       # ViewPager2 tabs adapter
        │   │   │   │   └── VideoVariantAdapter.kt    # Video resolution selector
        │   │   │   ├── analyzer/
        │   │   │   │   └── AnalyzeResultBottomSheet.kt # Multi-resource analysis bottom sheet
        │   │   │   ├── downloads/
        │   │   │   │   └── DownloadsFragment.kt      # In-app file manager with search & sort
        │   │   │   ├── history/
        │   │   │   │   └── HistoryFragment.kt        # Past download logs with clear action
        │   │   │   ├── home/
        │   │   │   │   └── HomeFragment.kt           # URL input, test links & storage bar
        │   │   │   ├── queue/
        │   │   │   │   └── QueueFragment.kt          # Real-time active download manager
        │   │   │   └── settings/
        │   │   │       └── SettingsFragment.kt       # Theme, folder, concurrency & toggles
        │   │   └── utils/
        │   │       ├── ClipboardUtils.kt             # Clipboard inspection & URL validator
        │   │       ├── FileUtils.kt                  # File operations, formatting & FileProvider
        │   │       ├── PreferencesManager.kt         # SharedPreferences manager
        │   │       ├── StorageUtils.kt               # Device storage query via StatFs
        │   │       └── ThemeUtils.kt                 # Material 3 Day/Night theme switcher
        │   └── res/
        │       ├── drawable/                         # Modern vector icons & rounded drawables
        │       ├── layout/                           # Clean Material 3 XML layouts
        │       ├── menu/                             # Bottom navigation menu
        │       ├── values/                           # Colors, strings, dimensions & light theme
        │       ├── values-night/                     # Slate dark theme
        │       └── xml/                              # FileProvider paths & backup rules
        └── test/
            └── java/com/example/alldownloader/
                └── UrlAnalyzerUnitTest.kt            # Unit tests for HTML parsing & discovery
```

---

## 🚀 How the Smart URL Analyzer Works

1. **Step 1 – URL Validation**: Validates `http://` or `https://` protocol and host syntax.
2. **Step 2 – HTTP Probe**: Sends a lightweight HTTP GET request with standard browser User-Agent and automated redirect tracking.
3. **Step 3 – Direct Resource Detection**:
   * If `Content-Type` is `video/*`, `audio/*`, `image/*`, `application/pdf`, `application/zip`, `application/octet-stream`, or `application/vnd.android.package-archive` -> Instantly constructs a Direct Download resource.
4. **Step 4 – HTML Webpage Media Discovery (`Jsoup`)**:
   * If `Content-Type` is `text/html`, reads the markup and discovers:
     * **OpenGraph Meta Tags**: `og:image:secure_url`, `og:image`, `og:video:secure_url`, `og:video`, `twitter:image`, `twitter:player:stream`, `link[rel=image_src]`.
     * **HTML5 Media**: `<video src="...">`, `<video><source src="...">`, `<audio src="...">`, `<audio><source src="...">`.
     * **Responsive Images**: `<img srcset="...">` parses resolution pairs (e.g. `1920w`, `2x`) and selects the highest resolution image.
     * **Downloadable Document Links**: `<a href="...">` pointing to `.pdf`, `.zip`, `.rar`, `.7z`, `.docx`, `.xlsx`, `.apk`, etc.
   * **URL Normalization**: Resolves relative paths (`/images/pic.jpg`) against the base URL into absolute URLs (`https://example.com/images/pic.jpg`).
   * **Deduplication & Anti-Tracker Filtering**: Filters out tracking pixels (`1x1`, beacon, analytics), favicons, badges, and social share endpoints.
5. **Step 5 – Parallel Size Probing**: Executes lightweight HEAD requests on discovered resources to display actual file sizes.
6. **Step 6 – Diagnostics & Debug Logging**: Generates an exhaustive diagnostic trace (HTTP status, redirect path, candidate count, filter reasons) available in the expandable Diagnostic Panel.

---

## 🔒 Android Permissions & Security

Declared in [`AndroidManifest.xml`](file:///c:/Users/BAPS/AndroidStudioProjects/AllDownloader/app/src/main/AndroidManifest.xml):

* `android.permission.INTERNET`: For network calls and media streaming.
* `android.permission.ACCESS_NETWORK_STATE`: For checking Wi-Fi connectivity when the Wi-Fi only preference is active.
* `android.permission.POST_NOTIFICATIONS`: Android 13+ runtime notification permissions for background download alerts.
* `android.permission.FOREGROUND_SERVICE` & `android.permission.FOREGROUND_SERVICE_DATA_SYNC`: Background download execution on Android 14+.
* `androidx.core.content.FileProvider`: Secure file sharing and opening via `content://` URIs without exposing raw storage paths.

---

## 🛠️ Build & Run Commands

### Prerequisites
* Java Development Kit (JDK 17 or Android Studio JBR)
* Android SDK Platform 34+ / 35

### 1. Set JAVA_HOME (Windows PowerShell)
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

### 2. Run Unit Tests
```powershell
.\gradlew.bat testDebugUnitTest
```

### 3. Build Debug APK
```powershell
.\gradlew.bat assembleDebug
```
> The generated APK will be available at:
> `app/build/outputs/apk/debug/app-debug.apk`

### 4. Install on Connected Device / Emulator
```powershell
.\gradlew.bat installDebug
adb shell am start -n com.example.alldownloader/.MainActivity
```

### 5. Build Release APK
```powershell
.\gradlew.bat assembleRelease
```

---

## ⚡ Quick Test URLs (Included in App)

The Home screen includes interactive 1-tap quick test buttons:

| Test Target | Sample URL | Discovered Content |
| :--- | :--- | :--- |
| **Direct JPG** | `https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1600&q=80` | High-Resolution Mountain Photo (JPG) |
| **Direct MP4** | `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4` | Public Big Buck Bunny Clip (MP4) |
| **Direct PDF** | `https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf` | W3C Standard Sample PDF Document |
| **Webpage (og:image)** | `https://en.wikipedia.org/wiki/Aurora` | Wikipedia Article with OpenGraph & High-Res Illustrations |
| **Webpage (Video)** | `https://html5demos.com/video/` | HTML5 Video Demo Page |

---

## ⚖️ Legal & DRM Compliance

* AllDown is designed exclusively for downloading direct, user-owned, and legally accessible public media and documents.
* AllDown does **not** bypass DRM (Widevine, FairPlay, PlayReady), bypass authentication/paywalls, or strip copyright watermarks.
* For protected platforms, the analyzer displays a respectful notice explaining that the protected resource cannot be accessed.

---

## 📄 License
This project is developed as an open architecture Android media download utility. Built with standard AndroidX and Google Material 3 libraries.
