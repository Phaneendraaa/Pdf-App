# Play Store release kit

## One-time setup (you do this, about 10 minutes)

1. **Add the upload key to GitHub** (Repo → Settings → Secrets and variables → Actions → New repository secret):
   - `UPLOAD_KEYSTORE_BASE64`: contents of `pdfkit-upload.jks.base64.txt` (sent in chat)
   - `UPLOAD_STORE_PASSWORD`: the password in `upload-key-password.txt` (sent in chat)
   - `UPLOAD_KEY_ALIAS`: `pdfkit-upload`

   After the next push, each release also has **PdfKit.aab**, signed with this key. Upload that file to Play.
   Keep `pdfkit-upload.jks` and the password somewhere safe (password manager or Drive). With Play App Signing, Google holds the real app key, and you can ask Google to reset a lost upload key.
2. **Turn on the privacy policy page**: Repo → Settings → Pages → Source: *Deploy from a branch* → `main` / `/docs` → Save.
   URL: https://phaneendraaa.github.io/Pdf-App/privacy.html (the app's Settings → Privacy policy already opens it).
3. Optional: put your support email in `app/src/main/java/com/phani/pdfkit/util/Support.kt` (`EMAIL`). Crash reports and feedback then go straight to you. While it's empty, the share sheet opens instead.

## Store listing

- **App name (30):** PDF Scanner & Editor - Pdf Kit
- **Short description (80):** Scan, edit, sign & compress PDFs to any KB. Works offline, no ads, no watermark.
- **Category:** Productivity
- **Tags:** PDF, Scanner, Document editor

**Full description**

Pdf Kit does everything you need with PDFs, right on your phone, without uploading your files anywhere.

**Made for forms and job applications**
• Compress a PDF to under 50, 100, 200 or 500 KB, or any size you type
• Photo & Signature: exact pixels and 20–50 KB for exam and job forms
• Aadhaar or PAN front and back on one page
• Mask Aadhaar numbers and black out private details for good

**Scan**
• Scan documents with automatic edge detection
• Filters that remove shadows: Auto, Gray, Black & White
• Add pages to an existing scan anytime
• Text recognition in English and Hindi, and searchable scans

**Edit, sign and fill**
• Edit the existing text in a PDF, on many pages at once
• Fill forms, tick boxes, add dates and sign (draw, type or photo)
• Highlight, underline, strike, shapes, notes and stamps you can remove later
• Add text, page numbers, margins and watermarks; crop and organise pages

**Convert**
• PDF to Word as real, editable text
• Word to PDF, JPG to PDF, PDF to JPG, or one long image

**Private by design**
• Works fully offline. No account, no ads, no watermark, no daily limits
• Files stay on your phone, in folders you make, with a 30-day trash

## Data safety form answers

- Does your app collect or share any of the required user data types? **No**
- Is all user data encrypted in transit? **Not applicable (no data is transmitted)**
- Do you provide a way for users to request data deletion? **Not applicable (no data collected)**
- Privacy policy URL: https://phaneendraaa.github.io/Pdf-App/privacy.html
- Ads: **No ads**. Target audience: **18+** (simplest review; it's a general productivity app).
- Content rating questionnaire: Utility/Productivity, no user-generated content shared, no violence etc.

## Screenshots to take (phone, 1080×2400, light theme)

1. Home (Scan / Import, tools)
2. Compress to a size (chips + custom box)
3. Photo & Signature sizing
4. Scan review with filters
5. Edit text on a page
6. Fill & Sign with tick/date/signature
7. Redact / Mask Aadhaar
8. PDF to Word result open in Word

## Before each Play release

- Test on a real phone: a 50-page scan, PDF to Word on a scan, compress to 100 KB, redact, a large (300+ page) PDF.
- The release build is minified (R8). If something works in older builds but not now, send the crash report.
