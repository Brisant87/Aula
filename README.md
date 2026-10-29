# Aula — native Android port (Stage 2 scaffold)

A faithful first native version of the Aula web app (WebView source of truth:
`~/workspace/aula-apk/app-assets/index.html`), written in Kotlin + Jetpack
Compose. Package name **`com.aula.native`** — deliberately distinct from the
WebView app's `com.aula.app`, so both can live on the same phone during the
transition.

Build configuration mirrors the native Cognata donor project
(AGP 9.1.1, Kotlin 2.2.10, Gradle 9.3.1, Compose BOM 2024.09.00,
compileSdk/targetSdk 36, minSdk 24), because that project's GitHub Actions
route is proven.

**Hard rules carried over from the web app:** local-first, deterministic
Mirror rules, no backend, **no Firebase, no Gemini/AI APIs, no network calls
at all** (the manifest declares zero permissions — not even INTERNET).

## What is implemented

Six bottom tabs, Mediterranean Journal identity (sand #F5EDDC, cream #FBF7EC,
terracotta #C05B2E / deep #A34A24, olive #66713F / deep #4E5A30, ink #2C2620,
all-serif typography):

- **Hoy** — the guided daily class, same flow as the web app: focus = top
  active Mirror pattern if one exists, else the 7-day rotation plan;
  2 cognate warm-up recall questions → focused practice (syntax pair /
  cognate pair / listening pair) → the Guide for one word → an unseen
  transfer check → a surprise retention step when a mastered pattern is due
  → closing card with a link to the Espejo.
- **Espejo (Mirror)** — SharedPreferences-backed JSON event store
  (`aula_mirror` / `store_json_v1`, org.json, 500-event cap, corrupt file →
  clean start, never a crash). Same rules as the web app: a pattern is named
  only at **≥3 errors across ≥2 contexts**; "No es mi problema" dismisses it;
  transfer successes on **different days** move Activo → Mejorando →
  Dominado; a Dominado pattern unconfirmed for **≥3 days** is retention-due,
  and a failed retention check honestly returns it to Mejorando with its
  hint. Pattern cards show evidence, support level and status; "Practicar
  esto" runs a mini-class (with or without hint, per support level) followed
  by an unseen transfer check; "Ya dominas" lists mastered patterns with a
  "Confirmar ahora que sigue ahí" retention entry point.
- **Conversaciones** — Escucha listening practice (3 practice items; TTS
  speaks, learner picks what they heard among look-alikes) plus 3 full
  conversations (cafetería, ¿Qué hiciste ayer?, hotel) with turn-by-turn
  choices, per-turn tutor notes, history of completed turns, and an
  Espejo-recommended conversation tag. Every pick logs its pattern.
- **Biblioteca** — Palabra del día, the Guide word index, the six
  cognate-rule chips with detail + one quick practice question each, the
  full cognate catalog with listen buttons, and the 13 formulas.
- **Camino** — the same 4 levels and item sets as the web app
  (N1: t6/t4/t11 · N2: t1/t9/t2 · N3: t5/t10/t12 · N4: t13/t3/t8), locked /
  open / completed states, pass = **≥2/3 first attempts** on unseen items,
  scored from the event log (first attempt per pattern, context "camino").
- **Ajustes** — voice speed (0.7×–1.3×, persisted), Guide on/off, and a
  confirmed full reset of the diary + settings.
- **La Guía (Mira · Escucha · Copia)** — front-facing mouth drawn with
  Compose Canvas primitives (gradient face, lips, cavity, teeth, tongue
  positions) stepping sound-by-sound through each word. One combined
  **"▶ Escuchar y ver"** button speaks the word via TTS *while* the mouth
  steps; "🐢 Lento" does both slowly. Self-rating (Así es ✓ / Casi /
  Todavía no) logs `pronunciation:<word>` events exactly like the web app.
- **Pedagogy guards kept from the web app:** the syntax builder never prints
  the target sentence before an attempt; first-mistake feedback names the
  actual error (a word that doesn't belong, the first wrong position, or the
  missing-word count); the full sentence appears only after a correct build
  or a second failed attempt; an empty "Revisar" tap shows guidance only —
  no attempt burned, no false Mirror event; one event per exercise, logged
  at the first check; answer positions are shuffled everywhere.
- **Voice:** Android TextToSpeech initialized with the **Google engine
  (`com.google.android.tts`) first**, falling back to the system default
  engine if that fails — the deliberate fix for silent TTS on Samsung Galaxy
  devices. A voice failure never crashes and is never graded as a learner
  failure.

Content embedded in `Data.kt`: all 17 syntax items, all 12 transfer items,
18 cognates, 6 cognate rules, 13 formulas, 4 guide words (ship, sheep, went,
three) with full step captions, 3 conversations, all 6 listening items
(3 practice + 3 transfer), 7 day plans, 4 Camino levels.

## What is trimmed vs the web app

- **Copia voice recording is NOT in this scaffold.** The web app records the
  learner (MediaRecorder + live level meter + silence auto-stop). The native
  Guide has voice + stepping + self-rating only, and says so in its own UI
  copy. The manifest therefore needs no RECORD_AUDIO permission yet.
- **No Google Cloud voice and no API-key field in Ajustes.** The native
  scaffold uses device TTS only (Google engine first). If cloud voice is
  wanted later, the key must live only on the user's phone, entered in a
  local-only settings field — never shipped in a build.
- **Content subset:** guide words 4 (web: 20), conversations 3 (web: 9),
  cognates 18 (web: 46). Day plans whose guide words are outside the
  embedded set are remapped to the nearest embedded word (past → went,
  word order → ship, present questions → three, cognates day → sheep).
- **Mouth rendering:** the web app draws a shaded SVG face; the native
  scaffold draws equivalent stylized shapes from simplified per-shape
  parameters (lip/inner width & height, teeth, tongue mode) with Canvas
  primitives — same step captions, same stepping behavior, less detail.
- **`gradle-wrapper.jar` and `gradlew` are intentionally not committed** —
  same as the donor project. The GitHub Actions workflow supplies Gradle
  9.3.1 via `gradle/gradle-build-action`, so CI builds work without them.

## How to build (GitHub Actions route)

1. Create a **new private GitHub repository** (e.g. `aula-native`).
2. Push this whole project directory to it (the workflow is already in
   `.github/workflows/android.yml`).
3. The workflow runs automatically on push to `main`/`master`, or start it
   manually from the repo's **Actions** tab ("Build Aula APK" →
   *Run workflow*). It sets up JDK 17 (Temurin), installs the Android 36
   platform + build-tools, generates the shared `debug.keystore` at the
   repo root if absent (so repeated builds install over each other), and
   runs `assembleDebug`.
4. When the run finishes, open it and download the artifact named
   **`aula-native-debug-apk`** — it contains `app-debug.apk`.
5. Install `app-debug.apk` on the phone (it coexists with the WebView Aula:
   different package, `com.aula.native`).

Local build (optional): install an Android SDK (platform 36, build-tools
36.0.0), write `sdk.dir=...` into `local.properties`, generate the keystore
with the same `keytool` command the workflow uses, and run
`gradle assembleDebug` with Gradle 9.3.1.

## Layout

- `app/src/main/java/com/aula/native/MainActivity.kt` — theme, shell, all
  screens and exercise composables (~2,000 lines).
- `app/src/main/java/com/aula/native/Engine.kt` — Mirror store, settings,
  pattern info, TTS manager (Google-engine-first).
- `app/src/main/java/com/aula/native/Data.kt` — all embedded content.
