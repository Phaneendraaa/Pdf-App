package com.phani.pdfkit.ai

import java.util.Calendar
import java.util.Locale

/**
 * Suggests a file name from a document's text ("Electricity_Bill_Oct_2026").
 * Rule-based, so it works offline on every phone, not just AI-capable ones.
 */
object SmartName {
    private val kinds = listOf(
        listOf("electricity", "power bill", "kwh", "units consumed") to "Electricity_Bill",
        listOf("water bill", "water charges") to "Water_Bill",
        listOf("gas bill", "lpg", "cylinder") to "Gas_Bill",
        listOf("broadband", "internet bill", "postpaid", "mobile bill") to "Phone_Bill",
        listOf("tax invoice", "invoice no", "invoice number", "invoice") to "Invoice",
        listOf("receipt", "amount paid", "payment received") to "Receipt",
        listOf("aadhaar", "unique identification") to "Aadhaar",
        listOf("permanent account number", "income tax department") to "PAN_Card",
        listOf("passport") to "Passport",
        listOf("driving licence", "driving license") to "Driving_Licence",
        listOf("curriculum vitae", "resume", "work experience", "education") to "Resume",
        listOf("offer letter", "letter of appointment", "appointment letter") to "Offer_Letter",
        listOf("marks memo", "marksheet", "mark sheet", "grade sheet", "cgpa", "sgpa") to "Marksheet",
        listOf("certificate", "certify that") to "Certificate",
        listOf("bank statement", "account statement", "statement of account") to "Bank_Statement",
        listOf("salary slip", "payslip", "pay slip") to "Salary_Slip",
        listOf("prescription", "rx", "dosage") to "Prescription",
        listOf("rent agreement", "lease agreement", "tenancy") to "Rent_Agreement",
        listOf("boarding pass", "pnr", "e-ticket", "booking id") to "Ticket",
        listOf("question paper", "assignment", "lab record") to "Assignment",
    )
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    fun suggest(text: String): String? {
        val t = text.lowercase(Locale.US)
        if (t.isBlank()) return null
        val kind = kinds.firstOrNull { (keys, _) -> keys.any { it in t } }?.second
            ?: titleFrom(text) ?: return null
        return listOfNotNull(kind, dateFrom(t)).joinToString("_")
    }

    private fun dateFrom(t: String): String? {
        val now = Calendar.getInstance().get(Calendar.YEAR)
        Regex("\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[\\s,.-]*(\\d{1,2}[\\s,]+)?(20\\d{2})").find(t)?.let { m ->
            return "${m.groupValues[1].replaceFirstChar { it.uppercase() }}_${m.groupValues[3]}"
        }
        Regex("\\b(\\d{1,2})[/.-](\\d{1,2})[/.-](20\\d{2})\\b").find(t)?.let { m ->
            val mon = m.groupValues[2].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
            val y = m.groupValues[3].toInt().takeIf { it in 2000..now + 1 } ?: return null
            return "${months[mon - 1].replaceFirstChar { it.uppercase() }}_$y"
        }
        return null
    }

    /** First short line that looks like a title: 2-5 words, mostly letters. */
    private fun titleFrom(text: String): String? = text.lines().map { it.trim() }
        .firstOrNull { line -> line.split(Regex("\\s+")).size in 2..5 && line.count { it.isLetter() } > line.length * 0.7 && line.length in 6..40 }
        ?.split(Regex("\\s+"))
        ?.joinToString("_") { w -> w.lowercase(Locale.US).replaceFirstChar { it.uppercase() }.filter { it.isLetterOrDigit() } }
        ?.takeIf { it.length >= 4 }
}
