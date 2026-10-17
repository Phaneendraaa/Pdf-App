package com.phani.pdfkit.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.io.File
import java.util.IdentityHashMap

/** One fillable spot on a page. Radio groups and checkboxes can have several widgets sharing one [field]. */
data class FormWidget(
    val key: String,
    val field: String,
    val label: String,
    val kind: Kind,
    val page: Int,
    val box: Frac,
    val value: String,
    val options: List<String>,
    val onValue: String,
    val multiline: Boolean,
    val readOnly: Boolean,
    val maxLen: Int,
) {
    enum class Kind { TEXT, CHECK, RADIO, CHOICE }
}

/** Reads and fills AcroForm fields (the standard fillable-PDF kind). All local, via PdfBox. */
object FormFill {

    /** Fillable widgets in reading order. Empty if the PDF has no form fields. */
    fun read(file: File): List<FormWidget> = PdfTools.load(file).use { doc ->
        val form = doc.documentCatalog.acroForm
        if (form == null) emptyList() else {
            val pageOf = IdentityHashMap<Any, Int>()
            for (i in 0 until doc.numberOfPages) {
                for (a in runCatching { doc.getPage(i).annotations }.getOrDefault(emptyList())) {
                    if (a is PDAnnotationWidget) pageOf[a.cosObject] = i
                }
            }
            val out = ArrayList<FormWidget>()
            for (f in form.fieldTree) {
                if (f !is PDTerminalField) continue
                val label = (f.alternateFieldName?.takeIf { it.isNotBlank() } ?: f.partialName ?: f.fullyQualifiedName).trim()
                val kind: FormWidget.Kind
                var value = ""
                var options: List<String> = emptyList()
                var multiline = false
                var maxLen = -1
                when (f) {
                    is PDTextField -> { kind = FormWidget.Kind.TEXT; value = f.value.orEmpty(); multiline = f.isMultiline; maxLen = f.maxLen }
                    is PDCheckBox -> { kind = FormWidget.Kind.CHECK; value = if (f.isChecked) "true" else "false" }
                    is PDRadioButton -> { kind = FormWidget.Kind.RADIO; value = f.value.orEmpty().let { if (it == "Off") "" else it } }
                    is PDChoice -> {
                        kind = FormWidget.Kind.CHOICE
                        options = f.optionsDisplayValues.orEmpty()
                        val exports = f.optionsExportValues.orEmpty()
                        val cur = f.value?.firstOrNull().orEmpty()
                        val at = exports.indexOf(cur)
                        value = if (at in options.indices && exports.size == options.size) options[at] else cur
                    }
                    else -> continue
                }
                f.widgets.forEachIndexed { n, w ->
                    val page = pageOf[w.cosObject] ?: return@forEachIndexed
                    val rc = w.rectangle ?: return@forEachIndexed
                    val on = when (f) {
                        is PDCheckBox -> f.onValue.orEmpty()
                        is PDRadioButton -> runCatching {
                            w.appearance?.normalAppearance?.subDictionary?.keys?.map { it.name }?.firstOrNull { it != "Off" }
                        }.getOrNull().orEmpty()
                        else -> ""
                    }
                    out += FormWidget(
                        key = "${f.fullyQualifiedName}#$n", field = f.fullyQualifiedName, label = label, kind = kind, page = page,
                        box = PdfIndex.fracOf(doc.getPage(page), rc), value = value, options = options, onValue = on,
                        multiline = multiline, readOnly = f.isReadOnly, maxLen = maxLen,
                    )
                }
            }
            out.sortedWith(compareBy<FormWidget>({ it.page }, { (it.box.y * 60).toInt() }, { it.box.x }))
        }
    }

    /**
     * Writes [values] (field name to value) into a copy of [src]. Checkboxes use "true"/"false", radios the
     * option's export value. Unless [keepEditable], fields are flattened into the page so every app shows them.
     * Returns how many fields couldn't be written (for example characters the PDF's font can't draw).
     */
    fun apply(src: File, values: Map<String, String>, out: File, keepEditable: Boolean): Int = PdfTools.load(src).use { doc ->
        val form = doc.documentCatalog.acroForm ?: throw ToolError("This PDF has no form fields.")
        ensureFont(form)
        var skipped = 0
        for ((name, v) in values) {
            val f = form.getField(name) ?: continue
            try {
                when (f) {
                    is PDCheckBox -> if (v == "true") f.check() else f.unCheck()
                    is PDRadioButton -> if (v.isNotEmpty()) f.setValue(v)
                    is PDChoice -> {
                        val d = f.optionsDisplayValues.orEmpty(); val e = f.optionsExportValues.orEmpty()
                        val at = d.indexOf(v)
                        f.setValue(if (at >= 0 && e.size == d.size) e[at] else v)
                    }
                    is PDTextField -> f.setValue(v)
                    else -> {}
                }
            } catch (e: Exception) { skipped++ }
        }
        if (!keepEditable) runCatching { form.flatten() }
        doc.save(out)
        skipped
    }

    private fun ensureFont(form: com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm) {
        val res = form.defaultResources ?: PDResources().also { form.defaultResources = it }
        if (res.getFont(COSName.getPDFName("Helv")) == null) res.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
        if (form.defaultAppearance.isNullOrBlank()) form.defaultAppearance = "/Helv 0 Tf 0 g"
    }
}

/** Details saved on this phone so forms can be filled in one tap. Matches field names to those details. */
object Autofill {
    val KEYS = listOf("Full name", "Email", "Phone", "Address", "Date of birth")

    private val NOT_YOU = listOf("father", "mother", "spouse", "guardian", "parent", "nominee", "company", "bank", "employer", "school", "college", "branch")

    /** Which saved detail a field most likely asks for, or null. */
    fun guess(fieldText: String): String? {
        val t = fieldText.lowercase().replace('_', ' ').replace('-', ' ')
        return when {
            "email" in t || "e mail" in t -> "Email"
            "phone" in t || "mobile" in t || "contact no" in t || "telephone" in t -> "Phone"
            "birth" in t || "dob" in t.split(' ', '.', ':') -> "Date of birth"
            "address" in t -> "Address"
            "name" in t && NOT_YOU.none { it in t } -> "Full name"
            else -> null
        }
    }
}
