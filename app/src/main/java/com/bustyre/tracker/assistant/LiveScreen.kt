package com.bustyre.tracker.assistant

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.bustyre.tracker.data.AddResult
import com.bustyre.tracker.data.CONDITION_NEW
import com.bustyre.tracker.data.CONDITION_RESOLE
import com.bustyre.tracker.data.FleetRepository
import com.bustyre.tracker.data.SPARE
import com.bustyre.tracker.data.TYRE_POSITIONS
import com.bustyre.tracker.data.TyreEntity
import com.bustyre.tracker.ui.ratingColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// Live: a hands-free voice assistant. The microphone stays open (it restarts itself the moment a
// listening turn ends), it listens again as soon as it has finished speaking, and it understands
// whole sentences such as "tell me about F3334" or "history of F3334 last week".
// It does everything the old Assistant does, and also adds vehicles.
// ---------------------------------------------------------------------------------------------

enum class LiveStep {
    START, ASK_LANG, MAIN, ASK_NUMBER, ASK_TYRES, REVIEW,
    ASK_VEHICLE, CONFIRM_DELETE, H_POSITION, H_RANGE
}

/** One tyre entered by voice. present = false means "no spare" (only used for the SPARE slot). */
data class LiveTyre(val condition: String?, val rating: Int?, val present: Boolean)

private enum class Want { ADD, INFO, EDIT, DELETE, HISTORY, STARS, COUNT, EXPORT, HELP, REPEAT }

// ----- Understanding spoken words -----

private val DIGIT_WORDS = mapOf(
    "zero" to "0", "one" to "1", "won" to "1", "two" to "2", "to" to "2", "too" to "2",
    "three" to "3", "four" to "4", "for" to "4", "five" to "5", "six" to "6", "seven" to "7",
    "eight" to "8", "ate" to "8", "nine" to "9",
    "ಸೊನ್ನೆ" to "0", "ಒಂದು" to "1", "ಎರಡು" to "2", "ಮೂರು" to "3", "ನಾಲ್ಕು" to "4",
    "ಐದು" to "5", "ಆರು" to "6", "ಏಳು" to "7", "ಎಂಟು" to "8", "ಒಂಬತ್ತು" to "9"
)

/** Letter names as they are usually heard. "and" is often heard for the letter N. */
private val LETTER_WORDS = mapOf(
    "bee" to "B", "be" to "B", "see" to "C", "sea" to "C", "dee" to "D", "ef" to "F", "eff" to "F",
    "gee" to "G", "aitch" to "H", "eye" to "I", "jay" to "J", "kay" to "K", "el" to "L", "ell" to "L",
    "em" to "M", "en" to "N", "and" to "N", "oh" to "O", "pee" to "P", "queue" to "Q", "cue" to "Q",
    "are" to "R", "ar" to "R", "our" to "R", "ess" to "S", "tea" to "T", "tee" to "T", "you" to "U",
    "vee" to "V", "why" to "Y", "zed" to "Z", "zee" to "Z", "ex" to "X",
    "ಎ" to "A", "ಬಿ" to "B", "ಸಿ" to "C", "ಡಿ" to "D", "ಇ" to "E", "ಎಫ್" to "F", "ಜಿ" to "G",
    "ಎಚ್" to "H", "ಐ" to "I", "ಜೆ" to "J", "ಕೆ" to "K", "ಎಲ್" to "L", "ಎಂ" to "M", "ಎನ್" to "N",
    "ಒ" to "O", "ಪಿ" to "P", "ಕ್ಯೂ" to "Q", "ಆರ್" to "R", "ಎಸ್" to "S", "ಟಿ" to "T", "ಯು" to "U",
    "ವಿ" to "V", "ಡಬ್ಲ್ಯು" to "W", "ಎಕ್ಸ್" to "X", "ವೈ" to "Y", "ಝಡ್" to "Z"
)

/** Words people say around a vehicle number. They are ignored. */
private val VEHICLE_FILLER = setOf(
    "the", "number", "is", "it", "its", "it's", "vehicle", "bus", "my", "please", "ok", "okay",
    "sure", "yes", "ಸಂಖ್ಯೆ", "ವಾಹನ", "ಇದು", "ದಯವಿಟ್ಟು"
)

/** Extra words ignored when the number comes in the same sentence as "add vehicle". */
private val ADD_FILLER = setOf(
    "add", "adding", "new", "create", "a", "an", "i", "want", "wanna", "to", "can", "you", "could",
    "would", "like", "let", "lets", "let's", "us", "ಸೇರಿಸಿ", "ಸೇರಿಸು", "ಹೊಸ", "ಆ್ಯಡ್", "ಆಡ್", "ವೆಹಿಕಲ್"
)

private val YES_WORDS = setOf(
    "yes", "yeah", "yep", "yup", "sure", "ok", "okay", "haan", "ha", "ಹೌದು", "ಹೌದ", "ಹಾ", "ಸರಿ", "ಬೇಕು"
)
private val NO_WORDS = setOf("no", "nope", "nah", "not", "illa", "beda", "ಇಲ್ಲ", "ಇಲ್ಲಾ", "ಬೇಡ")

private fun wordsOf(text: String): List<String> =
    text.lowercase().split(Regex("[\\s,.!?]+")).filter { it.isNotEmpty() }

/**
 * Turns spoken text into a compact string of capital letters and digits.
 * "F 3 3 3 4", "f3334", "eff three three three four" all become F3334.
 * In tyre mode a spoken "X" or "no spare" becomes '#'.
 */
private fun compact(text: String, tyre: Boolean, spelled: Boolean = true): String {
    val sb = StringBuilder()
    var times = 1
    for (raw in text.lowercase().split(Regex("[\\s,.;:_/\\-]+"))) {
        if (raw.isEmpty()) continue
        if (!tyre && raw in VEHICLE_FILLER) continue
        if (raw == "double") { times = 2; continue }
        if (raw == "triple") { times = 3; continue }
        val piece: String = when {
            tyre && (raw == "x" || raw == "ex" || raw == "ಎಕ್ಸ್") -> "#"
            spelled && DIGIT_WORDS.containsKey(raw) -> DIGIT_WORDS.getValue(raw)
            spelled && LETTER_WORDS.containsKey(raw) -> LETTER_WORDS.getValue(raw)
            else -> raw.map { ch ->
                if (ch in '೦'..'೯') ('0' + (ch - '೦')).toString() else ch.toString()
            }.joinToString("")
        }
        for (k in 1..times) sb.append(piece)
        times = 1
    }
    return sb.toString().uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '#' }
}

/** Letters first, then digits, at most 5 characters in all, for example F3334. */
private fun parseVehicleNumber(text: String): String? {
    val c = compact(text, tyre = false)
    return if (c.length <= 5 && Regex("^[A-Z]+[0-9]+$").matches(c)) c else null
}

/** Finds an existing vehicle number inside a whole sentence. The longest match wins. */
private fun findKnown(text: String, known: List<String>): String? {
    // First look at the text as typed or written ("F3334"), where "for" or "one" are just words.
    // Only if that finds nothing, read spelled-out numbers ("eff three three three four").
    for (spelled in listOf(false, true)) {
        val c = compact(text, tyre = false, spelled = spelled)
        val found = known.filter { c.contains(it) }.maxByOrNull { it.length }
        if (found != null) return found
    }
    return null
}

/** Reads codes like N1 R2 R1 from speech. A null entry means "no spare". */
private fun parseTyreCodes(text: String): List<LiveTyre?> {
    val cleaned = text.lowercase().replace(
        Regex("no\\s*spa\\w*|spa\\w*\\s+(no|none)|none|without\\s+spa\\w*|ಸ್ಪೇರ್\\s*ಇಲ್ಲ.?"),
        " # "
    )
    val c = compact(cleaned, tyre = true)
    return Regex("([NR])([123])|#").findAll(c).map { m ->
        if (m.value == "#") {
            null
        } else {
            val cond = if (m.groupValues[1] == "N") CONDITION_NEW else CONDITION_RESOLE
            LiveTyre(cond, m.groupValues[2].toInt(), true)
        }
    }.toList()
}

/** One tyre position from a short answer: "OF", "o f", "NRI", "spare". */
private fun positionFrom(text: String): String? {
    val low = text.lowercase()
    if (low.contains("spare") || low.contains("spair") || low.contains("ಸ್ಪೇರ್")) return SPARE
    val c = compact(text, tyre = false)
    val order = listOf("ORO", "ORI", "NRO", "NRI", "OF", "NF")
    return order.firstOrNull { c == it } ?: order.firstOrNull { c.contains(it) }
}

/** "3 star vehicles", "three stars", "ಮೂರು ನಕ್ಷತ್ರ" -> 3. Null if no star list was asked for. */
private fun starsFrom(low: String): Int? {
    val spaced = low.replace("-", " ")
    val hasStar = wordsOf(spaced).any { it == "star" || it == "stars" } || low.contains("ನಕ್ಷತ್ರ")
    if (!hasStar) return null
    for (w in wordsOf(spaced)) {
        when (w) {
            "1", "one", "won", "ಒಂದು", "೧" -> return 1
            "2", "two", "to", "too", "ಎರಡು", "೨" -> return 2
            "3", "three", "ಮೂರು", "೩" -> return 3
        }
    }
    return Regex("[123]").find(spaced)?.value?.toInt()
}

/** Found?, and the number of days (null = complete history). */
private fun rangeFrom(low: String): Pair<Boolean, Int?> {
    val w = wordsOf(low)
    val two = w.any { it == "two" || it == "2" || it == "ಎರಡು" }
    val completeWords = setOf("complete", "full", "all", "whole", "everything", "entire")
    return when {
        w.any { it in completeWords } || low.contains("ಪೂರ್ಣ") -> true to null
        low.contains("year") || low.contains("ವರ್ಷ") -> true to 365
        low.contains("month") || low.contains("ತಿಂಗಳ") -> true to 30
        low.contains("week") || low.contains("ವಾರ") -> true to 7
        two && (low.contains("day") || low.contains("ದಿನ")) -> true to 2
        low.contains("day") || low.contains("ದಿನ") -> true to 1
        else -> false to null
    }
}

private fun detectWant(low: String, hasVehicle: Boolean): Want? {
    val spoken = wordsOf(low).toSet()
    // English single words must match as whole words ("account" is not "count").
    // Phrases and Kannada words are matched anywhere in the sentence.
    fun has(vararg keys: String) = keys.any { k ->
        if (k.contains(' ') || k.any { it.code > 127 }) low.contains(k) else k in spoken
    }
    return when {
        starsFrom(low) != null -> Want.STARS
        has("history", "changes", "changed", "ಇತಿಹಾಸ", "ಬದಲಾವಣೆ") -> Want.HISTORY
        has("export", "pdf", "ರಫ್ತು") -> Want.EXPORT
        has("delete", "deleted", "remove", "ಅಳಿಸ") -> Want.DELETE
        has("edit", "open", "modify", "update", "change", "ತಿದ್ದ", "ತೆರೆ", "ಬದಲಿಸ") -> Want.EDIT
        has("how many", "count", "counts", "total", "ಎಣಿ") -> Want.COUNT
        has("repeat", "say that again", "once more") -> Want.REPEAT
        has("help", "what can you", "options") -> Want.HELP
        has("add", "adding", "new", "create", "ಸೇರಿಸ", "ಹೊಸ", "ಆ್ಯಡ್", "ಆಡ್") -> Want.ADD
        has("info", "details", "detail", "know", "tell", "about", "show", "status", "condition", "ವಿವರ", "ತಿಳಿ") -> Want.INFO
        hasVehicle -> Want.INFO
        else -> null
    }
}

// ----- The conversation -----

class LiveEngine(
    private val repo: FleetRepository,
    private val scope: CoroutineScope,
    private val speak: (String, Boolean, () -> Unit) -> Unit,
    private val startListening: (Boolean) -> Unit,
    private val stopListening: () -> Unit,
    private val stopSpeaking: () -> Unit,
    private val openVehicle: (String) -> Unit,
    private val exportPdf: () -> Unit
) {
    var lang by mutableStateOf(Lang.EN)
    var step by mutableStateOf(LiveStep.START)
    var number by mutableStateOf("")
    var speaking by mutableStateOf(false)
    var listening by mutableStateOf(false)
    /** The user's mic switch. On by default. */
    var micOn by mutableStateOf(true)
    /** True while the "stop adding?" popup is open. */
    var paused by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var notice by mutableStateOf("")
    /** Words heard so far in the current sentence (live caption). */
    var heard by mutableStateOf("")
    val tyres = mutableStateListOf<LiveTyre>()
    val transcript = mutableStateListOf<Pair<Boolean, String>>()   // true = you, false = assistant

    /** Changes every time speech is interrupted, so late callbacks are ignored. */
    private var token = 0
    private var lastSpoken = ""
    private var pendingWant: Want? = null
    private var pendingLow = ""
    private var historyVehicle = ""
    private var historyTyre = ""
    private var historyDays: Int? = null
    private var historyRangeKnown = false

    val adding: Boolean
        get() = step == LiveStep.ASK_NUMBER || step == LiveStep.ASK_TYRES || step == LiveStep.REVIEW

    fun tr(en: String, kn: String): String = if (lang == Lang.KN) kn else en

    // ----- speaking and listening -----

    private fun resumeListening() {
        if (micOn && !paused && !saving && step != LiveStep.START) startListening(lang == Lang.KN)
    }

    /** Speaks, and goes back to listening the moment it is done. The mic is closed only while it talks. */
    private fun speakThen(text: String, after: (() -> Unit)? = null) {
        lastSpoken = text
        val t = token
        speaking = true
        stopListening()
        speak(text, lang == Lang.KN) {
            if (t == token) {
                speaking = false
                if (after != null) after() else if (!paused) resumeListening()
            }
        }
    }

    private fun say(text: String, after: (() -> Unit)? = null) {
        transcript.add(false to text)
        speakThen(text, after)
    }

    /** Speaks a short version but shows the full text (lists, history). */
    private fun sayDetail(spoken: String, full: String) {
        transcript.add(false to full)
        speakThen(spoken)
    }

    /** "Stop talking" button: ends the speech and listens at once. */
    fun interruptSpeech() {
        token++
        speaking = false
        stopSpeaking()
        if (!paused) resumeListening()
    }

    private fun interrupt() {
        token++
        speaking = false
        stopSpeaking()
        stopListening()
    }

    /** The app left the screen: stop talking and listening so the mic is released and sounds are unmuted. */
    fun onBackground() {
        if (step == LiveStep.START) return
        interrupt()
    }

    /** The app is back: carry on listening if the mic switch is on. */
    fun onForeground() {
        if (step == LiveStep.START || paused || saving) return
        if (micOn && !speaking) resumeListening()
    }

    fun micToggle() {
        micOn = !micOn
        notice = ""
        if (micOn) {
            if (!speaking) resumeListening()
        } else {
            stopListening()
        }
    }

    fun onPartial(text: String) {
        heard = text
    }

    fun onUnavailable() {
        micOn = false
        notice = tr(
            "Voice input is not available. Please type below.",
            "ಧ್ವನಿ ಇನ್‌ಪುಟ್ ಲಭ್ಯವಿಲ್ಲ. ಕೆಳಗೆ ಟೈಪ್ ಮಾಡಿ."
        )
    }

    fun onFail(code: Int) {
        micOn = false
        notice = tr(
            "The voice service stopped (code $code). Tap the mic button to try again.",
            "ಧ್ವನಿ ಸೇವೆ ನಿಂತಿದೆ (ಕೋಡ್ $code). ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಲು ಮೈಕ್ ಬಟನ್ ಒತ್ತಿ."
        )
    }

    // ----- start: language -----

    fun begin() {
        if (step != LiveStep.START) return
        lang = Lang.EN
        step = LiveStep.ASK_LANG
        say("Namaste. Do you want to change the language to Kannada? Say yes or no.")
    }

    private fun askMain() {
        step = LiveStep.MAIN
        say(tr("How may I help you now?", "ನಾನು ನಿಮಗೆ ಹೇಗೆ ಸಹಾಯ ಮಾಡಬಲ್ಲೆ ಈಗ?"))
    }

    private fun helpText() = tr(
        "You can say: add vehicle. 3 star vehicles. How many ratings. Tell me about a vehicle. " +
            "History of a vehicle. Edit a vehicle. Delete a vehicle. Or export PDF.",
        "ನೀವು ಹೇಳಬಹುದು: ವಾಹನ ಸೇರಿಸಿ. 3 ನಕ್ಷತ್ರ ವಾಹನಗಳು. ರೇಟಿಂಗ್ ಎಣಿಕೆ. ವಾಹನದ ವಿವರ. " +
            "ವಾಹನದ ಇತಿಹಾಸ. ವಾಹನ ತಿದ್ದಿ. ವಾಹನ ಅಳಿಸಿ. ಅಥವಾ PDF ರಫ್ತು."
    )

    // ----- what the user said -----

    /** Called with spoken or typed text. */
    fun onHeard(raw: String) {
        heard = ""
        if (paused || saving) return
        val text = raw.trim()
        if (text.isEmpty() || step == LiveStep.START) return
        if (speaking) interruptSpeech()
        notice = ""
        val low = text.lowercase()

        if (step != LiveStep.ASK_LANG && isCancel(low)) {
            if (adding) {
                requestStop()
            } else {
                transcript.add(true to text)
                pendingWant = null
                step = LiveStep.MAIN
                say(tr("Okay.", "ಸರಿ."))
            }
            return
        }

        when (step) {
            LiveStep.START -> Unit
            LiveStep.ASK_LANG -> {
                transcript.add(true to text)
                val words = wordsOf(low)
                when {
                    words.any { it in NO_WORDS } -> { lang = Lang.EN; askMain() }
                    words.any { it in YES_WORDS } -> { lang = Lang.KN; askMain() }
                    else -> say("Please say yes or no. Do you want to change the language to Kannada?")
                }
            }
            LiveStep.MAIN -> {
                transcript.add(true to text)
                handleMain(text, low)
            }
            LiveStep.ASK_VEHICLE -> {
                transcript.add(true to text)
                handleVehicleAnswer(text)
            }
            LiveStep.CONFIRM_DELETE -> {
                transcript.add(true to text)
                handleConfirmDelete(low)
            }
            LiveStep.H_POSITION -> {
                transcript.add(true to text)
                handleHistoryPosition(text)
            }
            LiveStep.H_RANGE -> {
                transcript.add(true to text)
                handleHistoryRange(low)
            }
            LiveStep.ASK_NUMBER -> {
                transcript.add(true to text)
                handleNumber(parseVehicleNumber(text))
            }
            LiveStep.ASK_TYRES -> handleTyres(text, low)
            LiveStep.REVIEW -> {
                val redo = listOf("re-enter", "reenter", "redo", "wrong", "incorrect", "not correct", "again")
                val said = listOf("all correct", "correct", "confirm", "save", "done")
                when {
                    redo.any { low.contains(it) } -> reEnter()
                    said.any { low.contains(it) } -> confirmAll()
                    else -> Unit
                }
            }
        }
    }

    private fun isCancel(low: String): Boolean =
        wordsOf(low).contains("cancel") || low.contains("never mind") || low.contains("ರದ್ದು")

    // ----- main commands -----

    private fun handleMain(text: String, low: String) {
        if (low.contains("kannada") || text.contains("ಕನ್ನಡ")) {
            lang = Lang.KN
            say("ಸರಿ, ಕನ್ನಡ. ನಾನು ನಿಮಗೆ ಹೇಗೆ ಸಹಾಯ ಮಾಡಬಲ್ಲೆ ಈಗ?")
            return
        }
        if (low.contains("english") || text.contains("ಇಂಗ್ಲಿಷ್")) {
            lang = Lang.EN
            say("Okay, English. How may I help you now?")
            return
        }
        scope.launch {
            val known = repo.snapshotForExport().map { it.first }
            val vehicle = findKnown(text, known)
            val want = detectWant(low, vehicle != null)
            when (want) {
                Want.STARS -> showStars(starsFrom(low) ?: 3)
                Want.COUNT -> showCounts()
                Want.EXPORT -> {
                    say(tr("Exporting PDF.", "PDF ರಫ್ತು ಮಾಡಲಾಗುತ್ತಿದೆ."))
                    exportPdf()
                }
                Want.HELP -> say(helpText())
                Want.REPEAT -> say(if (lastSpoken.isBlank()) helpText() else lastSpoken)
                Want.ADD -> startAdd(low)
                Want.INFO, Want.EDIT, Want.DELETE, Want.HISTORY -> {
                    if (vehicle != null) {
                        runWithVehicle(want, vehicle, low)
                    } else {
                        pendingWant = want
                        pendingLow = low
                        step = LiveStep.ASK_VEHICLE
                        say(tr("Which vehicle number?", "ಯಾವ ವಾಹನ ಸಂಖ್ಯೆ?"))
                    }
                }
                null -> {
                    // The mic is always open, so short noises and background talk are ignored.
                    if (wordsOf(low).size >= 3) {
                        say(tr("Sorry, I did not get that. " + helpText(), "ಕ್ಷಮಿಸಿ, ಅರ್ಥವಾಗಲಿಲ್ಲ. " + helpText()))
                    }
                }
            }
        }
    }

    private fun handleVehicleAnswer(text: String) {
        scope.launch {
            val known = repo.snapshotForExport().map { it.first }
            val vehicle = findKnown(text, known)
            val want = pendingWant
            if (vehicle == null || want == null) {
                say(tr(
                    "Vehicle not found. Say another number, or say cancel.",
                    "ವಾಹನ ಸಿಗಲಿಲ್ಲ. ಬೇರೆ ಸಂಖ್ಯೆ ಹೇಳಿ, ಅಥವಾ ರದ್ದು ಎಂದು ಹೇಳಿ."
                ))
            } else {
                runWithVehicle(want, vehicle, pendingLow)
            }
        }
    }

    private fun runWithVehicle(want: Want, vehicle: String, low: String) {
        pendingWant = null
        when (want) {
            Want.INFO -> showInfo(vehicle)
            Want.EDIT -> {
                step = LiveStep.MAIN
                // Say it first, then leave this screen, so the spoken line is not cut off.
                say(tr("Opening $vehicle.", "$vehicle ತೆರೆಯಲಾಗುತ್ತಿದೆ.")) { openVehicle(vehicle) }
            }
            Want.DELETE -> {
                number = vehicle
                step = LiveStep.CONFIRM_DELETE
                say(tr("Delete $vehicle? Say yes or no.", "$vehicle ಅಳಿಸಬೇಕೇ? ಹೌದು ಅಥವಾ ಇಲ್ಲ ಎಂದು ಹೇಳಿ."))
            }
            Want.HISTORY -> {
                historyVehicle = vehicle
                val (found, days) = rangeFrom(low)
                historyRangeKnown = found
                historyDays = days
                step = LiveStep.H_POSITION
                say(tr(
                    "Which tyre? OF, NF, ORO, ORI, NRO, NRI, or spare.",
                    "ಯಾವ ಟೈರ್? OF, NF, ORO, ORI, NRO, NRI, ಅಥವಾ ಸ್ಪೇರ್."
                ))
            }
            else -> Unit
        }
    }

    private fun handleConfirmDelete(low: String) {
        val words = wordsOf(low)
        when {
            words.any { it in NO_WORDS } -> {
                step = LiveStep.MAIN
                say(tr("Okay, not deleted.", "ಸರಿ, ಅಳಿಸಿಲ್ಲ."))
            }
            words.any { it in YES_WORDS } -> {
                val num = number
                scope.launch {
                    repo.deleteVehicle(num)
                    number = ""
                    step = LiveStep.MAIN
                    say(tr("$num deleted.", "$num ಅಳಿಸಲಾಗಿದೆ."))
                }
            }
            else -> say(tr("Say yes or no.", "ಹೌದು ಅಥವಾ ಇಲ್ಲ ಎಂದು ಹೇಳಿ."))
        }
    }

    // ----- know more, counts, star lists -----

    private fun tyreLine(t: TyreEntity): String {
        if (t.position == SPARE && t.present == false) {
            return tr("Spare: none", "ಸ್ಪೇರ್: ಇಲ್ಲ")
        }
        val cond = when (t.condition) {
            CONDITION_NEW -> tr("New", "ಹೊಸ")
            CONDITION_RESOLE -> tr("Resole", "ರೀಸೋಲ್")
            else -> tr("not entered", "ನಮೂದಿಸಿಲ್ಲ")
        }
        val rating = t.rating?.let { tr("$it star", "$it ನಕ್ಷತ್ರ") } ?: ""
        return "${t.position}: $cond $rating".trim()
    }

    private fun showInfo(num: String) {
        scope.launch {
            val tyres = repo.snapshotForExport().firstOrNull { it.first == num }?.second.orEmpty()
            val lines = tyres.map { tyreLine(it) }
            step = LiveStep.MAIN
            sayDetail(num + ". " + lines.joinToString(". "), num + "\n" + lines.joinToString("\n"))
        }
    }

    private fun showCounts() {
        scope.launch {
            val all = repo.snapshotForExport().flatMap { it.second }
                .filter { !(it.position == SPARE && it.present != true) }
            val one = all.count { it.rating == 1 }
            val two = all.count { it.rating == 2 }
            val three = all.count { it.rating == 3 }
            val unrated = all.count { it.rating == null }
            step = LiveStep.MAIN
            say(tr(
                "1 star: $one. 2 star: $two. 3 star: $three. Not rated: $unrated.",
                "1 ನಕ್ಷತ್ರ: $one. 2 ನಕ್ಷತ್ರ: $two. 3 ನಕ್ಷತ್ರ: $three. ರೇಟಿಂಗ್ ಇಲ್ಲ: $unrated."
            ))
        }
    }

    /** Each vehicle with the tyre positions of that rating. First ten are spoken, all are shown. */
    private fun showStars(stars: Int) {
        scope.launch {
            val rows = repo.snapshotForExport().mapNotNull { (vehicle, tyres) ->
                val found = tyres
                    .filter { it.rating == stars && (it.position != SPARE || it.present == true) }
                    .map { it.position }
                if (found.isEmpty()) null else vehicle to found
            }
            step = LiveStep.MAIN
            if (rows.isEmpty()) {
                say(tr("No $stars star vehicles.", "$stars ನಕ್ಷತ್ರದ ವಾಹನಗಳಿಲ್ಲ."))
            } else {
                val lines = rows.map { (v, positions) -> "$v: ${positions.joinToString(", ")}" }
                val spoken = tr(
                    "${rows.size} vehicles with $stars star. First ten: ${lines.take(10).joinToString(". ")}",
                    "$stars ನಕ್ಷತ್ರದ ${rows.size} ವಾಹನಗಳು. ಮೊದಲ ಹತ್ತು: ${lines.take(10).joinToString(". ")}"
                )
                sayDetail(spoken, lines.joinToString("\n"))
            }
        }
    }

    // ----- history: vehicle, tyre, then complete or a period -----

    private fun handleHistoryPosition(text: String) {
        val pos = positionFrom(text)
        if (pos == null) {
            say(tr(
                "Say OF, NF, ORO, ORI, NRO, NRI, or spare.",
                "OF, NF, ORO, ORI, NRO, NRI, ಅಥವಾ ಸ್ಪೇರ್ ಎಂದು ಹೇಳಿ."
            ))
            return
        }
        historyTyre = pos
        if (historyRangeKnown) {
            showHistory(historyDays)
        } else {
            step = LiveStep.H_RANGE
            say(tr(
                "Complete history, or one day, two days, one week, one month, or one year?",
                "ಪೂರ್ಣ ಇತಿಹಾಸ, ಅಥವಾ ಒಂದು ದಿನ, ಎರಡು ದಿನ, ಒಂದು ವಾರ, ಒಂದು ತಿಂಗಳು, ಒಂದು ವರ್ಷ?"
            ))
        }
    }

    private fun handleHistoryRange(low: String) {
        val (found, days) = rangeFrom(low)
        if (!found) {
            say(tr(
                "Say complete, or one day, two days, one week, one month, or one year.",
                "ಪೂರ್ಣ, ಅಥವಾ ಒಂದು ದಿನ, ಎರಡು ದಿನ, ಒಂದು ವಾರ, ಒಂದು ತಿಂಗಳು, ಒಂದು ವರ್ಷ ಎಂದು ಹೇಳಿ."
            ))
        } else {
            showHistory(days)
        }
    }

    private fun showHistory(days: Int?) {
        val num = historyVehicle
        val pos = historyTyre
        val since = days?.let { System.currentTimeMillis() - it * 86_400_000L }
        scope.launch {
            val rows = repo.tyreHistory(num, pos, since)
            val fmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.ENGLISH)
            val header = tr("History of $pos on $num", "$num ನ $pos ಇತಿಹಾಸ")
            val body = if (rows.isEmpty()) {
                tr("No changes found.", "ಯಾವುದೇ ಬದಲಾವಣೆ ಕಂಡುಬಂದಿಲ್ಲ.")
            } else {
                rows.joinToString("\n") { c ->
                    "${fmt.format(Date(c.timestamp))}  ${c.field}: ${c.oldValue ?: "-"} -> ${c.newValue ?: "-"}"
                }
            }
            val spoken = tr("${rows.size} changes for $pos on $num.", "$num ನ $pos ಗೆ ${rows.size} ಬದಲಾವಣೆಗಳು.")
            step = LiveStep.MAIN
            sayDetail(spoken, "$header\n$body")
        }
    }

    // ----- add vehicle -----

    private fun askNumber() {
        step = LiveStep.ASK_NUMBER
        say(tr(
            "Okay, let's add a vehicle. Please say the vehicle number.",
            "ಸರಿ, ವಾಹನ ಸೇರಿಸೋಣ. ದಯವಿಟ್ಟು ವಾಹನ ಸಂಖ್ಯೆ ಹೇಳಿ."
        ))
    }

    private fun askTyres() {
        step = LiveStep.ASK_TYRES
        say(tr("Okay, let's say the tyre conditions.", "ಸರಿ, ಟೈರ್ ಸ್ಥಿತಿಗಳನ್ನು ಹೇಳೋಣ."))
    }

    /** "add vehicle" or "add vehicle F3334". If the number is in the sentence it is used. */
    private fun startAdd(low: String) {
        val rest = wordsOf(low).filter { it !in ADD_FILLER }.joinToString(" ")
        val num = if (rest.isBlank()) null else parseVehicleNumber(rest)
        if (num == null) {
            askNumber()
        } else {
            handleNumber(num)
        }
    }

    private fun handleNumber(num: String?) {
        if (num == null) {
            step = LiveStep.ASK_NUMBER
            say(tr(
                "I could not catch the number. Please say it again: letter first, then digits.",
                "ಸಂಖ್ಯೆ ಸರಿಯಾಗಿ ಕೇಳಿಸಲಿಲ್ಲ. ಮತ್ತೆ ಹೇಳಿ: ಮೊದಲು ಅಕ್ಷರ, ನಂತರ ಅಂಕಿಗಳು."
            ))
            return
        }
        scope.launch {
            if (repo.vehicleExists(num)) {
                step = LiveStep.ASK_NUMBER
                say(tr(
                    "$num already exists. Please say a different number.",
                    "$num ಈಗಾಗಲೇ ಇದೆ. ಬೇರೆ ಸಂಖ್ಯೆ ಹೇಳಿ."
                ))
            } else {
                number = num
                tyres.clear()
                askTyres()
            }
        }
    }

    private fun handleTyres(text: String, low: String) {
        val undo = listOf("undo", "go back", "back", "ಹಿಂದೆ")
        val clear = listOf("clear", "restart", "start over", "reset")
        when {
            clear.any { low.contains(it) } -> { tyres.clear(); return }
            undo.any { low.contains(it) } -> {
                if (tyres.isNotEmpty()) tyres.removeAt(tyres.size - 1)
                return
            }
        }
        for (code in parseTyreCodes(text)) {
            if (tyres.size >= 7) break
            if (code == null) {
                // "no spare" is only valid once the six main tyres are in.
                if (tyres.size == 6) tyres.add(LiveTyre(null, null, false))
            } else {
                tyres.add(code)
            }
        }
        if (tyres.size == 7) review()
    }

    private fun review() {
        step = LiveStep.REVIEW
        say(tr(
            "Please check the entries on the screen.",
            "ದಯವಿಟ್ಟು ಪರದೆಯ ಮೇಲಿನ ನಮೂದುಗಳನ್ನು ಪರಿಶೀಲಿಸಿ."
        ))
    }

    fun reEnter() {
        if (step != LiveStep.REVIEW || saving) return
        tyres.clear()
        askTyres()
    }

    fun confirmAll() {
        if (step != LiveStep.REVIEW || saving || tyres.size != 7) return
        saving = true
        val num = number
        val list = tyres.toList()
        scope.launch {
            val result = repo.addVehicle(num)
            if (result is AddResult.Added) {
                list.forEachIndexed { i, t ->
                    val position = TYRE_POSITIONS[i]
                    if (position == SPARE) repo.setSparePresent(num, t.present)
                    if (t.present) {
                        t.condition?.let { repo.setCondition(num, position, it) }
                        t.rating?.let { repo.setRating(num, position, it) }
                    }
                }
                number = ""
                tyres.clear()
                step = LiveStep.MAIN
                saving = false
                say(tr(
                    "Thank you for adding the vehicle. I am here to help you add a vehicle anytime.",
                    "ವಾಹನ ಸೇರಿಸಿದ್ದಕ್ಕೆ ಧನ್ಯವಾದಗಳು. ವಾಹನ ಸೇರಿಸಲು ನಾನು ಯಾವಾಗಲೂ ಇಲ್ಲಿದ್ದೇನೆ."
                ))
            } else {
                saving = false
                tyres.clear()
                number = ""
                transcript.add(false to tr("$num already exists.", "$num ಈಗಾಗಲೇ ಇದೆ."))
                askNumber()
            }
        }
    }

    // ----- the cross button -----

    fun requestStop() {
        if (!adding || paused || saving) return
        interrupt()
        paused = true
    }

    /** "Stay": carry on from where the conversation was. */
    fun stay() {
        paused = false
        when (step) {
            LiveStep.ASK_NUMBER -> askNumber()
            LiveStep.ASK_TYRES -> say(tr(
                "Okay, let's continue with the tyre conditions.",
                "ಸರಿ, ಟೈರ್ ಸ್ಥಿತಿಗಳನ್ನು ಮುಂದುವರಿಸೋಣ."
            ))
            LiveStep.REVIEW -> say(tr(
                "Please check the entries on the screen.",
                "ದಯವಿಟ್ಟು ಪರದೆಯ ಮೇಲಿನ ನಮೂದುಗಳನ್ನು ಪರಿಶೀಲಿಸಿ."
            ))
            else -> resumeListening()
        }
    }

    /** "Stop": nothing was saved yet, so just drop the entries and go back to the main question. */
    fun stopAdding() {
        paused = false
        interrupt()
        number = ""
        tyres.clear()
        step = LiveStep.MAIN
        say(tr(
            "Okay, cancelled. How may I help you now?",
            "ಸರಿ, ರದ್ದುಮಾಡಲಾಗಿದೆ. ನಾನು ನಿಮಗೆ ಹೇಗೆ ಸಹಾಯ ಮಾಡಬಲ್ಲೆ ಈಗ?"
        ))
    }
}

// ----- Voice out: Indian English accent, or Kannada -----

/** Speaks text and tells us when it has finished. */
class LiveSpeaker(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var counter = 0
    private var currentId = ""
    private var onDone: (() -> Unit)? = null
    private var pendingText: String? = null
    private var pendingKannada = false
    private var pendingDone: (() -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        val text = pendingText
        val done = pendingDone
        pendingText = null
        pendingDone = null
        if (text != null && done != null) {
            if (ready) speakNow(text, pendingKannada, done) else done()
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { finish(utteranceId) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { finish(utteranceId) }
            override fun onError(utteranceId: String?, errorCode: Int) { finish(utteranceId) }
        })
    }

    private fun finish(id: String?) {
        main.post {
            if (id == currentId) {
                val cb = onDone
                onDone = null
                cb?.invoke()
            }
        }
    }

    fun say(text: String, kannada: Boolean, done: () -> Unit) {
        if (!ready) {
            pendingText = text
            pendingKannada = kannada
            pendingDone = done
            return
        }
        speakNow(text, kannada, done)
    }

    private fun speakNow(text: String, kannada: Boolean, done: () -> Unit) {
        val locale = if (kannada) Locale("kn", "IN") else Locale("en", "IN")
        val result = tts.setLanguage(locale)
        if (result < 0) {
            if (kannada) {
                // No Kannada voice on this phone: the text is still on the screen.
                main.post { done() }
                return
            }
            tts.setLanguage(Locale.US)
        }
        counter++
        currentId = "live$counter"
        onDone = done
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, currentId)
    }

    fun stop() {
        onDone = null
        currentId = ""
        tts.stop()
    }

    fun shutdown() {
        onDone = null
        tts.stop()
        tts.shutdown()
    }
}

// ----- Voice in: one open microphone that restarts itself -----

/**
 * Keeps listening. When one listening turn ends (a sentence, or silence) the next one starts at
 * once, so the mic never "closes". The phone's own start/stop beeps are muted while it listens.
 */
class LiveListener(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null

    var onText: (String) -> Unit = {}
    var onPartial: (String) -> Unit = {}
    var onUnavailable: () -> Unit = {}
    var onFail: (Int) -> Unit = {}
    var onState: (Boolean) -> Unit = {}

    private var wanted = false
    private var kannada = false
    private var running = false
    private var gen = 0
    private var hardErrors = 0
    private var muted = false

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                if (!wanted) return
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onPartial(text)
            }

            override fun onError(error: Int) {
                if (!wanted) return
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // Silence is normal. Open the next turn straight away.
                        hardErrors = 0
                        onPartial("")
                        restart(40L)
                    }
                    else -> {
                        hardErrors++
                        if (hardErrors >= 6) {
                            fail(error)
                        } else {
                            recognizer?.cancel()
                            val busy = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                                error == SpeechRecognizer.ERROR_CLIENT
                            restart(if (busy) 400L else 900L)
                        }
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                if (!wanted) return
                hardErrors = 0
                val g = gen
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onText(text)
                // If the assistant started speaking, it stopped us and gen changed. Otherwise carry on.
                if (g == gen && wanted) restart(40L)
            }
        })
    }

    /** Mutes the phone's listening beeps. Always paired with an unmute before the assistant speaks. */
    @Suppress("DEPRECATION")
    private fun mute(on: Boolean) {
        if (on == muted) return
        muted = on
        val streams = intArrayOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM
        )
        for (s in streams) {
            try {
                audio.setStreamMute(s, on)
            } catch (e: Exception) {
                // Some phones do not allow muting every stream. The rest still works.
            }
        }
    }

    private fun restart(delayMs: Long) {
        val g = gen
        running = false
        main.postDelayed({
            if (g == gen && wanted) beginTurn()
        }, delayMs)
    }

    private fun beginTurn() {
        val rec = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (kannada) "kn-IN" else "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        running = true
        rec.cancel()
        rec.startListening(intent)
    }

    private fun fail(code: Int) {
        wanted = false
        running = false
        gen++
        mute(false)
        onState(false)
        recognizer?.cancel()
        onFail(code)
    }

    fun start(kannadaNow: Boolean) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (recognizer == null || !granted) {
            onUnavailable()
            return
        }
        if (wanted && running && kannada == kannadaNow) return   // already listening
        kannada = kannadaNow
        wanted = true
        hardErrors = 0
        gen++
        onState(true)
        mute(true)
        beginTurn()
    }

    fun stop() {
        wanted = false
        running = false
        gen++
        mute(false)
        onState(false)
        recognizer?.cancel()
    }

    fun destroy() {
        stop()
        recognizer?.destroy()
    }
}

// ----- Screen -----

@Composable
fun LiveScreen(
    repo: FleetRepository,
    onOpenVehicle: (String) -> Unit,
    onExportPdf: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speaker = remember { LiveSpeaker(context) }
    val listener = remember { LiveListener(context) }
    val engine = remember(repo) {
        LiveEngine(
            repo = repo,
            scope = scope,
            speak = { text, kannada, done -> speaker.say(text, kannada, done) },
            startListening = { kannada -> listener.start(kannada) },
            stopListening = { listener.stop() },
            stopSpeaking = { speaker.stop() },
            openVehicle = onOpenVehicle,
            exportPdf = onExportPdf
        )
    }

    // Leaving the app (Home, another app) releases the mic and un-mutes the phone's sounds;
    // coming back starts listening again.
    val lifecycleOwner = remember(context) {
        var c: Context = context
        while (c is ContextWrapper && c !is LifecycleOwner) c = c.baseContext
        c as? LifecycleOwner
    }
    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> engine.onBackground()
                Lifecycle.Event.ON_START -> engine.onForeground()
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    // The screen stays on while Live is open, so the open mic is not cut off by the screen lock.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(engine) {
        listener.onText = { engine.onHeard(it) }
        listener.onPartial = { engine.onPartial(it) }
        listener.onUnavailable = { engine.onUnavailable() }
        listener.onFail = { engine.onFail(it) }
        listener.onState = { engine.listening = it }
        onDispose {
            speaker.shutdown()
            listener.destroy()
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Start either way. Without the microphone the user can type.
        engine.begin()
    }
    LaunchedEffect(engine) {
        if (engine.step == LiveStep.START) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) engine.begin() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Back while adding asks the same question as the cross button.
    BackHandler(enabled = engine.adding) { engine.requestStop() }

    var typed by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(engine.transcript.size) {
        if (engine.transcript.isNotEmpty()) listState.animateScrollToItem(engine.transcript.size - 1)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "🎙 Live / ಲೈವ್",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (engine.adding) {
                IconButton(onClick = { engine.requestStop() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop adding")
                }
            }
        }

        val status = when {
            engine.notice.isNotEmpty() -> engine.notice
            engine.speaking -> engine.tr("🔊 Speaking…", "🔊 ಮಾತನಾಡುತ್ತಿದೆ…")
            engine.paused -> ""
            engine.micOn && engine.listening -> engine.tr("🎤 Listening…", "🎤 ಕೇಳುತ್ತಿದೆ…")
            !engine.micOn -> engine.tr("⏸ Mic is paused", "⏸ ಮೈಕ್ ನಿಲ್ಲಿಸಲಾಗಿದೆ")
            else -> ""
        }
        if (status.isNotEmpty()) Text(status, fontSize = 18.sp)
        if (engine.heard.isNotEmpty()) {
            Text("“${engine.heard}”", fontSize = 18.sp, fontStyle = FontStyle.Italic)
        }

        if (engine.step == LiveStep.ASK_TYRES || engine.step == LiveStep.REVIEW) {
            TyrePanel(engine)
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(engine.transcript) { entry ->
                Text(
                    text = (if (entry.first) "You: " else "") + entry.second,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        if (engine.step == LiveStep.REVIEW) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LiveButton(Modifier.weight(2f), engine.tr("✔ All correct", "✔ ಎಲ್ಲವೂ ಸರಿ")) {
                    engine.confirmAll()
                }
                LiveButton(Modifier.weight(1f), engine.tr("Re-enter", "ಮತ್ತೆ"), primary = false) {
                    engine.reEnter()
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    label = { Text(engine.tr("Or type here", "ಅಥವಾ ಇಲ್ಲಿ ಟೈಪ್ ಮಾಡಿ")) }
                )
                LiveButton(Modifier, engine.tr("Send", "ಕಳುಹಿಸಿ"), primary = false) {
                    val text = typed
                    typed = ""
                    engine.onHeard(text)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LiveButton(
                    Modifier.weight(1f),
                    if (engine.micOn) engine.tr("🎤 Mic on", "🎤 ಮೈಕ್ ಆನ್") else engine.tr("⏸ Mic paused", "⏸ ಮೈಕ್ ನಿಲ್ಲಿಸಿದೆ"),
                    primary = engine.micOn
                ) { engine.micToggle() }
                if (engine.speaking) {
                    LiveButton(Modifier.weight(1f), engine.tr("⏹ Stop talking", "⏹ ಮಾತು ನಿಲ್ಲಿಸಿ"), primary = false) {
                        engine.interruptSpeech()
                    }
                }
            }
        }
    }

    if (engine.paused) {
        AlertDialog(
            onDismissRequest = { engine.stay() },
            title = { Text(engine.tr("Stop adding this vehicle?", "ಈ ವಾಹನ ಸೇರಿಸುವುದನ್ನು ನಿಲ್ಲಿಸುವುದೇ?")) },
            text = { Text(engine.tr("Nothing has been saved yet.", "ಇನ್ನೂ ಏನನ್ನೂ ಉಳಿಸಿಲ್ಲ.")) },
            confirmButton = {
                TextButton(onClick = { engine.stay() }) {
                    Text(engine.tr("Stay", "ಇಲ್ಲೇ ಇರಿ"), fontSize = 18.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { engine.stopAdding() }) {
                    Text(engine.tr("Stop", "ನಿಲ್ಲಿಸಿ"), fontSize = 18.sp)
                }
            }
        )
    }
}

/** The vehicle number and the seven tyre slots, filled in as the user speaks. */
@Composable
private fun TyrePanel(engine: LiveEngine) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            engine.tr("Vehicle", "ವಾಹನ") + ": " + engine.number,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        TYRE_POSITIONS.forEachIndexed { i, position ->
            val tyre = engine.tyres.getOrNull(i)
            val label = when {
                tyre == null ->
                    if (i == engine.tyres.size && engine.step == LiveStep.ASK_TYRES) "…" else "—"
                !tyre.present -> engine.tr("No spare", "ಸ್ಪೇರ್ ಇಲ್ಲ")
                else -> (if (tyre.condition == CONDITION_NEW) "N" else "R") + "${tyre.rating ?: ""}"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ratingColor(tyre?.rating) ?: Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(position, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                Text(label, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun LiveButton(
    modifier: Modifier,
    text: String,
    primary: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        colors = if (primary) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    ) {
        Text(text, fontSize = 20.sp)
    }
}
