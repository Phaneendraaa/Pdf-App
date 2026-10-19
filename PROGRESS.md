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
- Quick tools (home grid): JPG to PDF, Edit PDF, PDF to Word, PDF to JPG, Merge, Compress, Unlock, More. (Edit PDF replaced Word to PDF, which stays in All Tools.)
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
- [x] **Batch 8** - Share target + "Open with", shortcuts, undo delete, rate prompt, haptics, empty states, dark mode.
- [x] **Batch 9** - Gemini Nano: Summarize, Smart rename.
- [x] **Batch 11** - Viewer upgrades: search in PDF (highlighted hits, prev/next), clickable links, bookmarks tab in Pages sheet, select and copy text, remember last page. Editor Save asks "Update this PDF" or "Save as a new copy".
- [x] **Batch 12** - Fill forms: AcroForm fields (text, checkbox, radio, dropdown), autofill details saved on the phone (Settings > Autofill details), flatten or keep editable, Update/New copy.
- [x] **Batch 13** - Make Searchable tool (ML Kit OCR -> invisible text layer, `pdf/SearchablePdf.kt`), scans made searchable in the background (Settings > Searchable scans, on by default), JPG to PDF "Look" filter (Original/Enhanced/B&W, `pdf/Filters.kt`). ML Kit scanner already has its own filters.
- [x] **Batch 14** - Crop pages (`ui/edit/CropEditor.kt`, `PdfTools.crop`), page numbers in one go with position/style/start/skip-cover, Print (system print dialog, `util/PrintPdf.kt`), Word to PDF tables + pictures and PDF to Word pictures (`pdf/WordTools.kt`), "Replace the original with this" on tool results (`JobResult.source`), shared `SaveChoiceSheet` + `Library.saveEdit`.
- [x] **Batch 15** - Fix: edit-text box uses the page's paper color (text was invisible in dark mode). Compress to a size (100 KB..2 MB or custom; `PdfTools.compressTo` binary-searches a quality ladder) + Black and white. Page numbers: live preview, position, edge distance, style (1 / Page 1 / 1 of N / - 1 -), size, color, bold, start page, first number (`PdfTools.NumberStyle`). Organize Pages (`PageItems` + `PdfTools.organize`): rotate one page, duplicate, blank page, delete, drag; tools Add Blank Page / Delete Pages; viewer menu shortcuts (organize, numbers, compress, crop). Sheets scroll.
- [x] **Batch 16** - Editor redo (redo stack, cleared on new action via `record()`), tap page chip to go to a page, night reading (viewer menu, `Prefs.nightReading`, inverted pages), screen stays on in the viewer, show/hide password, PDF to JPG for chosen pages (`toImages(only=)`).
- [x] **Batch 17** - Files: long-press or "Select" for multi-select (share, merge via `mergefiles:` key in `handleTool`, delete with undo, select all), Duplicate and Details in the file menu, rotate a photo in JPG to PDF (`JpgDraft.rotations`).
- [x] **Batch 18** - Fast editing: one editor for all pages (arrows + page grid with edited marks, per-page `PageWork` state), "Save N pages" applies everything in one pass (`PageEdits.applyAll`). Save follows Settings > "When I save edits" (default Update PDF, no question) with a 6 s Undo toast (`Library.saveEdit` keeps `.undo_backup.pdf`, `undoSave`, `ui/edit/SaveFlow.kt` `rememberSave` + `announceSaved`); same for Fill form and Crop. Find and replace on all pages (`TextEdit.readAll`). Edit sheet: auto-focus, Done key or swiping away applies the change.
- [x] **Batch 19** - Opens Word files in-app (`ui/viewer/DocViews.kt`: docx laid out to a cached PDF in cache/docview, read-only pages, Save as PDF / Open in Word) and images (Convert to PDF, Copy text via `OcrInput`). Open-with / share filters for .docx and images; importer accepts them; Files "Word" filter. Add Margins tool (`PdfTools.addMargins`: grow pages or keep size and shrink content; all or chosen pages; per-side mm; live preview).
- [x] **Batch 20** - Rebuilt PDF to Word (`pdf/PdfToWord.kt`): per-word font/size/bold/italic/color (color operators added to the stripper), headings (Heading1/2 styles), centered/right/justified, indents and bullets, paragraph joining by gaps, simple tables from aligned columns, pictures placed in reading order (PDFStreamEngine image finder), page size and margins from the PDF, OCR for scanned pages, "Exact look" mode (pages as pictures). Old `WordTools.pdfToDocx` kept but unused.
- [x] **Batch 21** - Compress simplified: one screen, no mode switch. Four size chips (100 KB, 200 KB, 500 KB, 1 MB) plus an always-visible size box with KB/MB, and the Black and white switch. Chips fill the box. Removed the By-quality mode and the hidden Custom chip.
- [x] **Batch 22** - PDF to Word is editable text only (like iLovePDF): page backgrounds and scan images are dropped (images > 50% of the page, > 25% when the text is a hidden OCR layer or the page was OCR'd), scanned pages always OCR'd (2000 px), "Exact look" mode and the OCR switch removed. Two-column pages become Word columns (gutter finder + continuous sections), line spacing from line gaps, sizes snapped to 0.5 pt, near-white text made dark, pictures aligned left/center/right.
- [x] **Batch 23** - New filters (`pdf/Filters.kt`): Original, Auto, Gray, B&W. Auto/Gray/B&W divide by an estimated paper background (downscale to ~96 px, max filter to erase text, box blur twice, bilinear back), which removes shadows and color casts; Auto adds levels + 1.3x saturation, B&W uses a soft threshold. Old "Enhanced" maps to Auto.
- [x] **Batch 24** - Scanner review (`ui/scan/ScanReviewScreen.kt`, route `scanreview`): ML Kit now runs in BASE mode with JPEG pages (Google screen does edges, crop, rotate, reorder); pages copied to cache/scan (`DocScanner.keep`), held in `ScanDraft`. Review: swipe pages with true filtered previews (LruCache 64 MB), filter tiles with live thumbnails, All pages / This page, rotate, delete with Undo, Add pages (rescans and appends), discard sheet. `saveScan` builds the PDF via `ImagesToPdf` (per-page filters + rotations, Fit to image) and still runs Searchable in the background.
- [x] **Batch 25** - Design pass: font scale capped at 1.15x in `PdfKitTheme` (huge system fonts broke rows), Segmented labels shrink to fit with ellipsis, TopBar subtitle and PrimaryButton single line, Home quick-tile grid keeps even widths on a short last row, tool names ellipsize next to the New pill, Success screen long names wrap to 2 lines and long notes show as a soft box, viewer toolbar buttons equal width. Waiting for user screenshots of other breaks.
- [x] **Batch 26** - Scan more pages into an existing PDF: `ScanDraft.appendTo` + `appendScan` (pdf/Scanner.kt) builds the new pages, merges after the target via `Library.saveEdit` (in-place with backup for library files, `_plus` copy otherwise). Entry points: viewer ⋮ "Scan more pages", file menu "Scan more pages", Success screen "Add more pages to this scan". Review screen shows "Add N pages to PDF"; result offers "Add more pages" again.
- [x] **Batch 27** - Home header and Settings banner redesign: Settings gradient "100% offline" card replaced by one quiet line with a small shield; Home top is now a small blue app mark + wordmark + bell, a large greeting with a green-dot offline line, outlined search field; quick-tool labels may wrap to 2 lines (no more "JPG to P...").
- [x] **Batch 28** - Form uploads kit (new "Form uploads" section first in All Tools): Photo & Signature (`ui/tools/PhotoSizeScreen.kt` + `pdf/PhotoSize.kt`: fixed-ratio crop frame, presets Photo 350x450 20-50 KB / Signature 350x150 10-20 KB / Thumb / Custom, min-max KB via JPEG quality binary search, grows or shrinks pixels if needed, optional clean background), ID Card to PDF (`IdCardScreen.kt` + `ImagesToPdf.idCard`: front/back by scan or photo, actual 85.6 mm or larger, filter, under 100/200/300 KB), Redact & Mask Aadhaar (`pdf/Redact.kt` + `ui/edit/RedactEditor.kt`: drag boxes, tap to remove, undo, find text, auto-mask first 8 digits via text layer or OCR words; saving rasterizes boxed pages with boxes burned in, clears metadata, always a `_redacted` copy). Compress presets now 50/100/200/500 KB. Home quick tiles: Photo & Sign replaces Unlock.
- [x] **Batch 29** - Home and Success redesign: Home drops the FAB and the do-nothing bell; two action cards under search (Scan filled blue, Import outlined; tour step "fab" now points at them), 4 recent PDFs. `FileThumb` shows the real first page (rememberPdfThumb) with a hairline frame everywhere files are listed. Success: smaller check, big first-page preview, trust line removed, "Next" chips (Add pages for scans, Compress, Sign, Password, To Word, Page numbers, Redact; Make PDF for images) routed via `onNext`. Accessibility: ink3 darkened (#737985 light, #858B97 dark) for contrast, IconBtn touch target 48 dp.
- [x] **Batch 30** - Fill & Sign + editable marks. `pdf/Annotations.kt`: real annotations with appearance streams (Ink, Highlight/Underline/StrikeOut text markup, Square/Circle, sticky Note, rubber Stamp with drawn box+label), `list` and `remove`. PageEditor toolbar now scrolls: Edit text, Add text, Tick & date (✓/✗ drawn as flat strokes, date dd/MM/yyyy), Sign, Highlight (tap a line or drag), Underline, Strike (tap a line), Draw, Shapes (rect/circle), Note, Stamp (Approved/Paid/Received/Draft/Confidential/Rejected); pen and highlighter strokes are saved as Ink annotations (removable), ticks/text/signature stay flattened. SignaturePad: Draw / Type (cursive) / Photo (paper made transparent, cropped). Viewer ⋮ "Comments & marks" lists all marks with delete (saved via saveEdit with Undo).
- [x] **Batch 31** - Scanner and OCR: Hindi + English text recognition (play-services-mlkit-text-recognition-devanagari 16.0.1; `Ocr.useDevanagari` from Settings › Text recognition), searchable layer writes Devanagari with a system Noto Sans Devanagari font. Scan review has Layout: Full pages / ID card · 2 per page (`ImagesToPdf.idCard` now takes a list of `Card`s with rotation and filter, 2 per A4). PDF to JPG has "One long image" (`PdfTools.toLongImage`, up to 1200 px wide, max ~20000 px tall).
- [x] **Batch 32** - Workflow: folders (one level of subfolders in the library; `DocFile.folder`, `Library.folders/createFolder/deleteFolder/move`; Files shows a folder strip + New folder at the top level, open a folder with back, delete folder moves files out; ⋮ "Move to folder" and multi-select Move via `MoveSheet`; saveEdit copies and rename stay in the file's folder). Trash: deletes are kept 30 days (`.trash_<ts>_<urlencoded folder/name>`, purged on start), Files › Trash chip with Restore / Delete for good / Empty trash. Batch: multi-select Compress (under 100/200/500 KB/1 MB each, `batch:compress:` job, copies saved next to originals) and Make PDF for selected images.
- [ ] **Batch 10** - Release hardening: R8 minify, size check, real upload key, Play listing assets.

## Log

- Batch 0: project created. Build runs on GitHub Actions only (Android SDK is not available in the coding environment). CI errors are emitted as check-run annotations: `gh api repos/Phaneendraaa/Pdf-App/check-runs/<id>/annotations`.
- Batch 1+2: theme, icons, components, Home, All Tools (search), Files (filters/sort/grid/star), Settings (compression/theme/app lock/about), import, share, save to Downloads, rename, delete with undo. Tools not built yet show "arrives in the next update".
- Batch 3: JPG to PDF (photo picker, drag reorder with haptics, first-use hint, options with sliding segmented controls, size estimate), PdfBox JPEG-compressed output, Processing (real progress, cancel), Success (rename, open/share/save, privacy line, create another), in-app viewer.
- Batch 4: tool runner screen (pick from library or phone, numbered merge order) + PdfBox tools: merge, compress with before/after note, unlock (also from viewer's locked state), protect, PDF to JPG, split (range or every page), rotate, organize pages (drag + delete), page numbers, watermark.
- Batch 5: ML Kit document scanner (Create sheet + Scan tool, success screen), text recognition screen, viewer page editor: highlight, draw (3 inks), text (tap to add, drag to move), signature (draw pad saved for reuse, drag + size slider), undo, save as copy.
- Batch 6: PDF to Word and Word to PDF (basic, on-device, honest hints in the UI).
- CI now deletes older `build-*` releases so only the newest APK exists; release notes show the build/version; Settings has "Get the latest version".
- Fix: onboarding swallows taps so they can't hit Home underneath.
- Batch 9: Gemini Nano summary in the viewer (sparkle button only when ML Kit GenAI reports the feature; download via AICore; chunked to the 4k-token limit; copy) and smart rename suggestion after scanning (OCR first page + rule-based `SmartName`, works on every phone).
- Batch 8: share target + Open with (VIEW pdf opens from cache/opened with "Save to Pdf Kit"; SEND pdfs import; SEND images start JPG to PDF), launcher shortcuts (Scan, JPG to PDF, Compress), resume-draft card on Home, rate prompt after 3rd success, real app lock (BiometricPrompt, relocks after 30 s; MainActivity is now FragmentActivity).
- Batch 7: onboarding slides, 5-step quick tour, first-use hints, sample PDF, camera explainer before first scan. MainActivity waits for saved settings before drawing (no onboarding flash).

## Code map

- Batch 11/12 files: `pdf/PdfIndex.kt` (search index with per-character boxes, links, bookmarks, `fracOf` for annotation rects), `pdf/FormFill.kt` (read/apply AcroForm, `Autofill` guesses which saved detail a field wants), `ui/forms/FormFiller.kt` (+ `AutofillSheet`), viewer pieces in `ui/viewer/ViewerScreen.kt` (SearchBar, CopyTextSheet, link overlay, "Fill form" pill). `Library.tempFile()/replaceWith()` write an edit then swap it over the original. `Prefs.lastPage/setLastPage`, `Prefs.autofill/setAutofill`.
- Rule: edits never leave extra copies unless the user picks "Save as a new copy".

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
- `util/Incoming.kt` (intents from other apps/shortcuts -> handled in `PdfKitApp`), `util/AppLock.kt` (BiometricPrompt).
- `ai/NanoAi.kt` (ML Kit GenAI summarization `com.google.mlkit:genai-summarization:1.0.0-beta1`), `ai/SmartName.kt`, `pdf/PdfText.kt`, `ui/viewer/SummarySheet.kt`.
- `ui/tools/ToolRunScreen.kt` - generic picker + options + `ActiveJob` for every key in `RUNNABLE_TOOLS`; `PageOrganizer.kt` drag grid for Reorder.
- `ui/jpg`, `ui/process` (Processing + Success), `ui/viewer` (pages, page chip, pinch/double-tap zoom, pages sheet; edit tools are placeholders).
- `util/` - `FileShare` (share, open external, save to Downloads), `Importer`, `PdfThumbs`, `Store`.

## Next step

Needed-8 plan (agreed 17 Oct): batches 11-14 done. Waiting for real-phone feedback, then Batch 10 hardening. Older notes below.

Edit existing PDF text (major requirement), in 3 parts:
- [x] Part 1: engine `pdf/TextEdit.kt` - `read(file, page)` returns text lines (`TextBlock`: position as page fractions, baseline, size, bold/italic, family) via a PDFTextStripper subclass; `applyTo(doc, page, ops)` covers each old line with its sampled background color and writes the new text on the same baseline with a matching standard-14 font (Roboto Type0 fallback for wider Unicode). `PageEdits.apply(..., textEdits)` runs it before ink/marks.
- [x] Part 2: `ui/edit/PageEditor.kt` tool "edit" (+ `ui/edit/TextEditLayer.kt`: `sampleColors`, overlay, `EditLineSheet`): outline every line, tap to open an edit sheet (text, size -/+, bold, color incl. sampled original, delete), live preview (background patch + new text), undo, save.
- [x] Part 3: viewer toolbar "Edit" (replaces "Text"; Add text lives in the editor chips), All Tools "Edit PDF Text" (key `edittext`, picks a PDF then opens the viewer in edit mode), Home quick tile "Edit PDF" (replaced Word to PDF, still in All Tools), file menu "Edit text", hint text updated.
- Note: edits cover the old line and write new text on top; the original text still exists underneath in the file (fine for edits, not a true redaction).

Then Batch 10 (release hardening) after real-device feedback:
- APK is 18.4 MB at build 9 without R8, already under target. Turn on R8 (`isMinifyEnabled = true`, `isShrinkResources = true`) with keep rules for PdfBox/ML Kit; check size.
- Private upload key via GitHub secrets, AAB, Play listing.

## Known issues

- (none reported yet)
