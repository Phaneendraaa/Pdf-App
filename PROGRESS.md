# Pdf Kit build progress

This file is the single source of truth for where the build is. Read it first when resuming.
Update it in the same commit as the work it describes.

## How to resume

1. Read this file, then the "Next step" section.
2. Every push to `main` triggers `.github/workflows/build.yml`, which builds `PdfKit.apk` and publishes it to Releases.
3. Latest APK: https://github.com/Phaneendraaa/Pdf-App/releases/latest/download/PdfKit.apk
4. Design reference: the HTML prototype (8 screens + onboarding) agreed in chat. Colors: blue `#2463EB`, ink `#101217`, surfaces `#F4F5F8`, hairlines `#ECEEF2`, red PDF icon `#EF3B3B`, green privacy `#16A34A`. Font: Inter-like (system sans for now).

## Product decisions (agreed)

- 100% offline, Kotlin only, no backend. App size target: under 100 MB (aim ~20 MB).
- Name: **Pdf Kit**. Play title idea: "JPG to PDF Converter - PDFix" (name still open).
- Quick tools (home grid, by search demand): JPG to PDF, PDF to Word, PDF to JPG, Word to PDF, Merge, Compress, Unlock, More.
- AI: Gemini Nano only (ML Kit GenAI), 0 MB. Features: Summarize (viewer), Smart rename (after scan). Hidden on unsupported phones. Ship in a later batch.
- PDF to Word / Word to PDF: basic text-level quality on-device; label honestly.
- Onboarding: 3 slides (tools / offline privacy / simple by design), then 5-step spotlight quick tour.
- Extras: first-use contextual hints, sample PDF on first run, permission explainer, "Open with" + share target, home-screen shortcuts, resume-unfinished card, undo toast on delete, rate prompt after 3rd success, privacy badge on success, before/after size on compress, haptics, designed empty states, dark mode.

## Batches

- [x] **Batch 0** - Gradle skeleton, dev signing key, CI build, APK published to Releases, this file.
- [x] **Batch 1** - Design system (colors, type, shapes, icons), navigation scaffold, bottom nav, Home screen (greeting, search, quick tools, recent files, FAB create sheet).
- [x] **Batch 2** - All Tools (sections + search), Files (filters, sort, list/grid, star, actions sheet), Settings (DataStore: compression, theme, app lock, about). File index stored in app storage.
- [x] **Batch 3** - JPG to PDF: photo picker, reorderable grid, options sheet, real PDF creation, processing screen, success screen (open/share/save), viewer (PdfRenderer, page chip, pages sheet).
- [x] **Batch 4** - PDF tools with PdfBox: PDF to JPG, Merge, Split, Compress (before/after), Unlock, Protect, Rotate, Reorder, Page numbers, Watermark.
- [x] **Batch 5** - Scan (ML Kit Document Scanner), Sign / Text / Highlight / Draw in viewer.
- [x] **Batch 6** - Word to PDF / PDF to Word (basic).
- [x] **Batch 7** - Onboarding slides, quick tour, contextual hints, sample PDF, permission explainer.
- [ ] **Batch 8** - Share target + "Open with", shortcuts, undo delete, rate prompt, haptics, empty states, dark mode.
- [ ] **Batch 9** - Gemini Nano: Summarize, Smart rename.
- [ ] **Batch 10** - Release hardening: R8 minify, size check, real upload key, Play listing assets.

## Log

- Batch 0: project created. Build runs on GitHub Actions only (Android SDK is not available in the coding environment). CI errors are emitted as check-run annotations: `gh api repos/Phaneendraaa/Pdf-App/check-runs/<id>/annotations`.
- Batch 1+2: theme, icons, components, Home, All Tools (search), Files (filters/sort/grid/star), Settings (compression/theme/app lock/about), import, share, save to Downloads, rename, delete with undo. Tools not built yet show "arrives in the next update".
- Batch 3: JPG to PDF (photo picker, drag reorder with haptics, first-use hint, options with sliding segmented controls, size estimate), PdfBox JPEG-compressed output, Processing (real progress, cancel), Success (rename, open/share/save, privacy line, create another), in-app viewer.
- Batch 4: tool runner screen (pick from library or phone, numbered merge order) + PdfBox tools: merge, compress with before/after note, unlock (also from viewer's locked state), protect, PDF to JPG, split (range or every page), rotate, organize pages (drag + delete), page numbers, watermark.
- Batch 5: ML Kit document scanner (Create sheet + Scan tool, success screen), text recognition screen, viewer page editor: highlight, draw (3 inks), text (tap to add, drag to move), signature (draw pad saved for reuse, drag + size slider), undo, save as copy.
- Batch 6: PDF to Word and Word to PDF (basic, on-device, honest hints in the UI).
- Batch 7: onboarding slides, 5-step quick tour, first-use hints, sample PDF, camera explainer before first scan. MainActivity waits for saved settings before drawing (no onboarding flash).

## Code map

- `PdfKitApp.kt` - Application + `Graph` (Library, Prefs singletons).
- `data/Library.kt` - files live in `getExternalFilesDir()/Pdf Kit`; soft delete + undo, rename, unique names. `Fmt` formatting.
- `data/Prefs.kt` - DataStore: theme, compression, app lock, onboarding/tour flags, starred, hints, success count.
- `ui/theme/Theme.kt` - `PkColors` light/dark, `Pk.colors`, `tint()`, typography, `PdfKitTheme`.
- `ui/icons/PkIcons.kt` - outline icons built from the prototype's SVG paths.
- `ui/components/` - `pressable`, `PdfGlyph`, `FileRow`, `TopBar`, `PrimaryButton`, `EmptyState`, `Toaster`/`ToastHost` (with Undo), `PkSheet`, `SheetOption`, `ChoiceOption`, `RenameDialog`.
- `ui/AppNav.kt` - NavHost, routes, `handleTool()` (tool key -> screen).
- `ui/main/MainScreen.kt` - tabs, bottom nav, Create sheet (scan / images / import), file actions sheet.
- `ui/home`, `ui/files`, `ui/settings`, `ui/tools` - screens.
- `pdf/` - `Bitmaps` (EXIF-aware decode), `Quality` (from compression setting), `ImagesToPdf` (PdfBox JPEG pages), `PdfDoc` (PdfRenderer wrapper: locked/broken detection, bitmap cache), `Jobs.kt` (`ActiveJob` for Processing/Success, `JpgDraft`).
- `pdf/PdfTools.kt` - merge, compress (re-encode images, never larger than input), unlock, protect (AES-128), toImages, parseRange/extract, splitEvery, rotate, reorder, pageNumbers, watermark. Throws `ToolError` with user-facing text.
- `pdf/Scanner.kt` - `rememberScanner` (ML Kit document scanner, PDF result copied to library), `Ocr` (ML Kit text recognition).
- `pdf/PageEdits.kt` + `ui/edit/` - single-page editor (ink, highlight, draggable text, signature with saved pad), flattened with PdfBox into `<name>_edited/_signed.pdf`.
- `ui/ocr/OcrScreen.kt` - text from photo or PDF (25 pages), copy/share.
- `pdf/WordTools.kt` - PDF to .docx (PDFTextStripper + hand-written OOXML zip, no Apache POI) and .docx to PDF (XmlPullParser + PdfBox layout, Roboto from /system/fonts with Helvetica fallback). Library now also lists .docx (`DocFile.isDoc`, `WordGlyph`), opened in an external app.
- `ui/onboarding/` - `OnboardingScreen` (3-slide pager, animated art) shown while `!settings.onboarded`; `QuickTour` spotlight (anchors in `HomeAnchors`) while `!settings.tourDone`. Settings > Welcome tour resets both.
- `util/Hints.kt` `rememberHint(id)` + `HintBanner`: one-time tips (jpg_drag, files_more, viewer_tools, success_rename, camera). `pdf/SampleDoc.kt` creates "Welcome to Pdf Kit.pdf" once (hint id "sample").
- `ui/tools/ToolRunScreen.kt` - generic picker + options + `ActiveJob` for every key in `RUNNABLE_TOOLS`; `PageOrganizer.kt` drag grid for Reorder.
- `ui/jpg`, `ui/process` (Processing + Success), `ui/viewer` (pages, page chip, pinch/double-tap zoom, pages sheet; edit tools are placeholders).
- `util/` - `FileShare` (share, open external, save to Downloads), `Importer`, `PdfThumbs`, `Store`.

## Next step

Batch 8:
- "Open with" + share target: intent-filters on MainActivity for ACTION_VIEW (application/pdf, image/*, docx) and ACTION_SEND / SEND_MULTIPLE (pdf, images). Import into the library, then open the viewer (PDF) or start JPG to PDF with the images (`JpgDraft`).
- Home-screen shortcuts (res/xml/shortcuts.xml): Scan, JPG to PDF, Compress -> MainActivity with extra "tool".
- Resume card on Home when `JpgDraft.images` is not empty.
- Rate prompt on Success after the 3rd success (`settings.successCount >= 3 && !ratePrompted`).
- App lock: androidx.biometric BiometricPrompt (device credential allowed) when `settings.appLock` is on; MainActivity must become FragmentActivity.
