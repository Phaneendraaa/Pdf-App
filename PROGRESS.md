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
- [ ] **Batch 1** - Design system (colors, type, shapes, icons), navigation scaffold, bottom nav, Home screen (greeting, search, quick tools, recent files, FAB create sheet).
- [ ] **Batch 2** - All Tools (sections + search), Files (filters, sort, list/grid, star, actions sheet), Settings (DataStore: compression, theme, app lock, about). File index stored in app storage.
- [ ] **Batch 3** - JPG to PDF: photo picker, reorderable grid, options sheet, real PDF creation, processing screen, success screen (open/share/save), viewer (PdfRenderer, page chip, pages sheet).
- [ ] **Batch 4** - PDF tools with PdfBox: PDF to JPG, Merge, Split, Compress (before/after), Unlock, Protect, Rotate, Reorder, Page numbers, Watermark.
- [ ] **Batch 5** - Scan (ML Kit Document Scanner), Sign / Text / Highlight / Draw in viewer.
- [ ] **Batch 6** - Word to PDF / PDF to Word (basic).
- [ ] **Batch 7** - Onboarding slides, quick tour, contextual hints, sample PDF, permission explainer.
- [ ] **Batch 8** - Share target + "Open with", shortcuts, undo delete, rate prompt, haptics, empty states, dark mode.
- [ ] **Batch 9** - Gemini Nano: Summarize, Smart rename.
- [ ] **Batch 10** - Release hardening: R8 minify, size check, real upload key, Play listing assets.

## Log

- Batch 0: project created. Build runs on GitHub Actions only (Android SDK is not available in the coding environment).

## Next step

Batch 1: design system + Home screen.
