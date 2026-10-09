package com.bustyre.tracker.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bustyre.tracker.data.CONDITION_NEW
import com.bustyre.tracker.data.CONDITION_RESOLE
import com.bustyre.tracker.data.FleetRepository
import com.bustyre.tracker.data.SPARE
import com.bustyre.tracker.data.TYRE_POSITIONS
import com.bustyre.tracker.data.TyreEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Lang { EN, KN }

/** Assistant options. Words are what the assistant listens for, in English and Kannada. */
enum class Option(val en: String, val kn: String, val words: List<String>) {
    EDIT("Edit vehicle", "ವಾಹನ ತಿದ್ದಿ", listOf("edit", "ತಿದ್ದ", "ಬದಲಿಸ")),
    DELETE("Delete vehicle", "ವಾಹನ ಅಳಿಸಿ", listOf("delete", "remove", "ಅಳಿಸ")),
    KNOW("Know more", "ವಿವರ ತಿಳಿಯಿರಿ", listOf("know", "detail", "info", "ವಿವರ", "ತಿಳಿ")),
    STAR1("1 star vehicles", "1 ನಕ್ಷತ್ರ ವಾಹನಗಳು", listOf("1 star", "one star", "ಒಂದು ನಕ್ಷತ್ರ", "1 ನಕ್ಷತ್ರ")),
    STAR2("2 star vehicles", "2 ನಕ್ಷತ್ರ ವಾಹನಗಳು", listOf("2 star", "two star", "ಎರಡು ನಕ್ಷತ್ರ", "2 ನಕ್ಷತ್ರ")),
    STAR3("3 star vehicles", "3 ನಕ್ಷತ್ರ ವಾಹನಗಳು", listOf("3 star", "three star", "ಮೂರು ನಕ್ಷತ್ರ", "3 ನಕ್ಷತ್ರ")),
    COUNT("Count ratings", "ರೇಟಿಂಗ್ ಎಣಿಸಿ", listOf("count", "ಎಣಿ")),
    HISTORY("Change history", "ಬದಲಾವಣೆ ಇತಿಹಾಸ", listOf("history", "changes", "ಇತಿಹಾಸ", "ಬದಲಾವಣೆ")),
    EXPORT("Export PDF", "PDF ರಫ್ತು", listOf("export", "pdf", "ರಫ್ತು"))
}

/** Add vehicle lives only in Live (voice), so it is not an option here. */
val ASSISTANT_OPTIONS: List<Option> = Option.entries.toList()

/**
 * The assistant's conversation logic. It reads and writes the same data the rest of the app uses.
 * Replies are shown in the transcript and spoken aloud in the chosen language.
 */
class AssistantEngine(
    private val repo: FleetRepository,
    private val scope: CoroutineScope,
    private val say: (String, Boolean) -> Unit,
    private val openVehicle: (String) -> Unit,
    private val exportPdf: () -> Unit
) {
    enum class Step { MENU, NUMBER, CONFIRM_DELETE, H_VEHICLE, H_POSITION, H_RANGE, H_TIMELINE }

    var lang by mutableStateOf(Lang.EN)
    var step by mutableStateOf(Step.MENU)
    /** The option buttons. Hidden while an option is in use; the Menu button brings them back. */
    var showMenu by mutableStateOf(true)
    val transcript = mutableStateListOf<Pair<Boolean, String>>()   // true = you, false = assistant

    private var option: Option? = null
    private var number = ""
    private var tyre = ""

    fun tr(en: String, kn: String): String = if (lang == Lang.KN) kn else en

    fun reply(text: String) {
        transcript.add(false to text)
        say(text, lang == Lang.KN)
    }

    /** Speaks a short version but shows the full text (for lists and history). */
    fun replyDetail(spoken: String, full: String) {
        transcript.add(false to full)
        say(spoken, lang == Lang.KN)
    }

    fun start() = reply(tr("Hello. What do you want to do?", "ನಮಸ್ಕಾರ. ನೀವು ಏನು ಮಾಡಲು ಬಯಸುತ್ತೀರಿ?"))

    fun cancel() {
        resetFlow()
        showMenu = true
        reply(tr("Cancelled.", "ರದ್ದುಮಾಡಲಾಗಿದೆ."))
    }

    fun showOptions() {
        resetFlow()
        showMenu = true
        reply(tr("Choose an option.", "ಒಂದು ಆಯ್ಕೆ ಮಾಡಿ."))
    }

    /** Clears the current flow. Does not bring the option buttons back: that is done by Cancel or Menu. */
    private fun resetFlow() {
        option = null
        number = ""
        tyre = ""
        step = Step.MENU
    }

    /** Every typed or spoken sentence comes through here. */
    fun onUser(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        transcript.add(true to text)
        val low = text.lowercase()
        if (Regex("\\b(cancel|back)\\b").containsMatchIn(low) || text.contains("ರದ್ದು")) {
            cancel()
            return
        }
        when (step) {
            Step.MENU -> {
                val picked = matchOption(text)
                if (picked != null) {
                    pick(picked)
                } else {
                    reply(tr("Say a number from 1 to 9, or the option name. To add a vehicle, use Live.",
                        "1 ರಿಂದ 9 ರವರೆಗಿನ ಸಂಖ್ಯೆ ಅಥವಾ ಆಯ್ಕೆಯ ಹೆಸರು ಹೇಳಿ. ವಾಹನ ಸೇರಿಸಲು ಲೈವ್ ಬಳಸಿ."))
                }
            }
            Step.NUMBER -> handleNumber(text)
            Step.CONFIRM_DELETE -> handleConfirm(low)
            Step.H_VEHICLE -> handleHistoryVehicle(text)
            Step.H_POSITION -> handleHistoryPosition(text)
            Step.H_RANGE -> handleHistoryRange(low)
            Step.H_TIMELINE -> handleHistoryTimeline(low)
        }
    }

    /** Picks the option whose longest matching word appears in the sentence, or a menu number. */
    private fun matchOption(text: String): Option? {
        val t = text.lowercase()
        var best: Option? = null
        var bestLen = 0
        for (opt in ASSISTANT_OPTIONS) {
            for (w in opt.words) {
                if (t.contains(w) && w.length > bestLen) {
                    best = opt
                    bestLen = w.length
                }
            }
        }
        if (best != null) return best
        val n = t.trim().toIntOrNull() ?: return null
        return ASSISTANT_OPTIONS.getOrNull(n - 1)
    }

    fun pick(choice: Option) {
        showMenu = false
        when (choice) {
            Option.STAR1 -> showStars(1)
            Option.STAR2 -> showStars(2)
            Option.STAR3 -> showStars(3)
            Option.COUNT -> showCounts()
            Option.HISTORY -> {
                step = Step.H_VEHICLE
                reply(tr("Which vehicle number?", "ಯಾವ ವಾಹನ ಸಂಖ್ಯೆ?"))
            }
            Option.EXPORT -> {
                reply(tr("Exporting PDF.", "PDF ರಫ್ತು ಮಾಡಲಾಗುತ್ತಿದೆ."))
                exportPdf()
                resetFlow()
            }
            else -> {
                option = choice
                step = Step.NUMBER
                reply(tr("Enter the vehicle number.", "ವಾಹನ ಸಂಖ್ಯೆ ನಮೂದಿಸಿ."))
            }
        }
    }

    private fun handleNumber(text: String) {
        val num = repo.normalise(text)
        val current = option
        scope.launch {
            val exists = repo.vehicleExists(num)
            val notFound = tr("Vehicle not found. Enter another number, or say cancel.",
                "ವಾಹನ ಸಿಗಲಿಲ್ಲ. ಬೇರೆ ಸಂಖ್ಯೆ ನಮೂದಿಸಿ, ಅಥವಾ ರದ್ದು ಎಂದು ಹೇಳಿ.")
            when (current) {
                Option.EDIT -> {
                    if (!exists) {
                        reply(notFound)
                    } else {
                        resetFlow()
                        reply(tr("Opening $num.", "$num ತೆರೆಯಲಾಗುತ್ತಿದೆ."))
                        // Let the spoken line finish before this screen is closed.
                        delay(1500)
                        openVehicle(num)
                    }
                }
                Option.KNOW -> {
                    if (!exists) {
                        reply(notFound)
                    } else {
                        val lines = tyreLines(num)
                        resetFlow()
                        replyDetail(lines.joinToString(". "), lines.joinToString("\n"))
                    }
                }
                Option.DELETE -> {
                    if (!exists) {
                        reply(notFound)
                    } else {
                        number = num
                        step = Step.CONFIRM_DELETE
                        reply(tr("Delete $num? Say yes or no.", "$num ಅಳಿಸಬೇಕೇ? ಹೌದು ಅಥವಾ ಇಲ್ಲ ಎಂದು ಹೇಳಿ."))
                    }
                }
                else -> cancel()
            }
        }
    }

    /** "no" is also heard as "number", so both count as No. */
    private fun handleConfirm(low: String) {
        val yes = low.startsWith("y") || low.contains("ಹೌದು")
        val no = low.startsWith("n") || low.contains("ಇಲ್ಲ")
        when {
            yes -> {
                val num = number
                scope.launch {
                    repo.deleteVehicle(num)
                    resetFlow()
                    reply(tr("$num deleted.", "$num ಅಳಿಸಲಾಗಿದೆ."))
                }
            }
            no -> cancel()
            else -> reply(tr("Say yes or no.", "ಹೌದು ಅಥವಾ ಇಲ್ಲ ಎಂದು ಹೇಳಿ."))
        }
    }

    // ---------- History: vehicle, then tyre, then complete or timeline ----------

    private fun handleHistoryVehicle(text: String) {
        val num = repo.normalise(text)
        scope.launch {
            if (!repo.vehicleExists(num)) {
                reply(tr("Vehicle not found. Enter another number, or say cancel.",
                    "ವಾಹನ ಸಿಗಲಿಲ್ಲ. ಬೇರೆ ಸಂಖ್ಯೆ ನಮೂದಿಸಿ, ಅಥವಾ ರದ್ದು ಎಂದು ಹೇಳಿ."))
            } else {
                number = num
                step = Step.H_POSITION
                reply(tr("Which tyre? OF, NF, ORO, ORI, NRO, NRI, or SPARE.",
                    "ಯಾವ ಟೈರ್? OF, NF, ORO, ORI, NRO, NRI, ಅಥವಾ SPARE."))
            }
        }
    }

    private fun handleHistoryPosition(text: String) {
        val pos = text.uppercase().trim().let { up ->
            TYRE_POSITIONS.firstOrNull { it == up } ?: if (up.contains("SPARE") || up.contains("ಸ್ಪೇರ್")) SPARE else null
        }
        if (pos == null) {
            reply(tr("Say OF, NF, ORO, ORI, NRO, NRI, or SPARE.",
                "OF, NF, ORO, ORI, NRO, NRI, ಅಥವಾ SPARE ಎಂದು ಹೇಳಿ."))
            return
        }
        tyre = pos
        step = Step.H_RANGE
        reply(tr("Complete history, or timeline?", "ಪೂರ್ಣ ಇತಿಹಾಸ ಅಥವಾ ಸಮಯ ರೇಖೆ?"))
    }

    private fun handleHistoryRange(low: String) {
        when {
            low.contains("complete") || low.contains("full") || low.contains("all") || low.contains("ಪೂರ್ಣ") -> {
                showHistory(null)
            }
            low.contains("timeline") || low.contains("time") || low.contains("ಸಮಯ") -> {
                step = Step.H_TIMELINE
                reply(tr("Which period? One day, two days, one week, one month, or one year.",
                    "ಯಾವ ಅವಧಿ? ಒಂದು ದಿನ, ಎರಡು ದಿನ, ಒಂದು ವಾರ, ಒಂದು ತಿಂಗಳು, ಅಥವಾ ಒಂದು ವರ್ಷ."))
            }
            else -> reply(tr("Say complete, or timeline.", "ಪೂರ್ಣ ಅಥವಾ ಸಮಯ ರೇಖೆ ಎಂದು ಹೇಳಿ."))
        }
    }

    private fun handleHistoryTimeline(low: String) {
        val days: Int? = when {
            low.contains("year") || low.contains("ವರ್ಷ") -> 365
            low.contains("month") || low.contains("ತಿಂಗಳ") -> 30
            low.contains("week") || low.contains("ವಾರ") -> 7
            low.contains("two") || low.contains("2") || low.contains("ಎರಡು") -> 2
            low.contains("one") || low.contains("1") || low.contains("day") || low.contains("ದಿನ") -> 1
            else -> null
        }
        if (days == null) {
            reply(tr("Say one day, two days, one week, one month, or one year.",
                "ಒಂದು ದಿನ, ಎರಡು ದಿನ, ಒಂದು ವಾರ, ಒಂದು ತಿಂಗಳು, ಅಥವಾ ಒಂದು ವರ್ಷ ಎಂದು ಹೇಳಿ."))
            return
        }
        showHistory(days)
    }

    /** Shows every change of the chosen tyre. days = null means no time limit. */
    private fun showHistory(days: Int?) {
        val num = number
        val pos = tyre
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
            resetFlow()
            replyDetail(spoken, "$header\n$body")
        }
    }

    // ---------- Lists and counts ----------

    private fun showStars(stars: Int) {
        scope.launch {
            val nums = repo.snapshotForExport()
                .filter { (_, tyres) ->
                    tyres.any { it.rating == stars && (it.position != SPARE || it.present == true) }
                }
                .map { it.first }
            resetFlow()
            if (nums.isEmpty()) {
                val none = tr("No $stars star vehicles.", "$stars ನಕ್ಷತ್ರದ ವಾಹನಗಳಿಲ್ಲ.")
                replyDetail(none, none)
            } else {
                val spoken = tr(
                    "${nums.size} vehicles with $stars star. First ten: ${nums.take(10).joinToString(", ")}",
                    "$stars ನಕ್ಷತ್ರದ ${nums.size} ವಾಹನಗಳು. ಮೊದಲ ಹತ್ತು: ${nums.take(10).joinToString(", ")}"
                )
                replyDetail(spoken, nums.joinToString(", "))
            }
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
            val text = tr(
                "1 star: $one. 2 star: $two. 3 star: $three. Not rated: $unrated.",
                "1 ನಕ್ಷತ್ರ: $one. 2 ನಕ್ಷತ್ರ: $two. 3 ನಕ್ಷತ್ರ: $three. ರೇಟಿಂಗ್ ಇಲ್ಲ: $unrated."
            )
            resetFlow()
            replyDetail(text, text)
        }
    }

    private suspend fun tyreLines(num: String): List<String> {
        val tyres = repo.snapshotForExport().firstOrNull { it.first == num }?.second.orEmpty()
        return tyres.map { tyreLine(it) }
    }

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
}

/** Speaks replies. Waits for the speech engine to start, and skips Kannada if the phone has no Kannada voice. */
class Speaker(context: Context) {
    private var ready = false
    private var pendingText: String? = null
    private var pendingKannada = false

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        val waiting = pendingText
        if (ready && waiting != null) speakNow(waiting, pendingKannada)
        pendingText = null
    }

    fun say(text: String, kannada: Boolean) {
        if (!ready) {
            pendingText = text
            pendingKannada = kannada
            return
        }
        speakNow(text, kannada)
    }

    private fun speakNow(text: String, kannada: Boolean) {
        val locale = if (kannada) Locale("kn", "IN") else Locale.US
        val result = tts.setLanguage(locale)
        if (kannada && result < 0) return   // no Kannada voice on this phone: text is still shown
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bt")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}

@Composable
fun AssistantScreen(
    repo: FleetRepository,
    onOpenVehicle: (String) -> Unit,
    onExportPdf: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }

    val engine = remember(repo) {
        AssistantEngine(
            repo = repo,
            scope = scope,
            say = { text, kannada -> speaker.say(text, kannada) },
            openVehicle = onOpenVehicle,
            exportPdf = onExportPdf
        )
    }
    LaunchedEffect(Unit) {
        if (engine.transcript.isEmpty()) engine.start()
    }

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(Unit) { onDispose { recognizer?.destroy() } }

    fun listen() {
        val rec = recognizer
        if (rec == null) {
            engine.reply(engine.tr("Speech input is not available on this phone.",
                "ಈ ಫೋನ್‌ನಲ್ಲಿ ಮಾತು ಗುರುತಿಸುವಿಕೆ ಲಭ್ಯವಿಲ್ಲ."))
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (engine.lang == Lang.KN) "kn-IN" else "en-IN")
        }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                engine.reply(engine.tr("I could not hear you. Try again.", "ಕೇಳಿಸಲಿಲ್ಲ. ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ."))
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) {
                    engine.reply(engine.tr("I could not hear you. Try again.", "ಕೇಳಿಸಲಿಲ್ಲ. ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ."))
                } else {
                    engine.onUser(text)
                }
            }
        })
        rec.startListening(intent)
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            listen()
        } else {
            engine.reply(engine.tr("Microphone permission is needed to speak.",
                "ಮಾತನಾಡಲು ಮೈಕ್ರೋಫೋನ್ ಅನುಮತಿ ಬೇಕು."))
        }
    }

    fun onMic() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) listen() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    var typed by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            BigButton(Modifier.weight(1f), "English", selected = engine.lang == Lang.EN) {
                engine.lang = Lang.EN
            }
            BigButton(Modifier.weight(1f), "ಕನ್ನಡ", selected = engine.lang == Lang.KN) {
                engine.lang = Lang.KN
            }
        }

        // The option buttons go away while an option is in use, so the page shows only the replies.
        if (engine.showMenu) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ASSISTANT_OPTIONS.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { opt ->
                            BigButton(Modifier.weight(1f), if (engine.lang == Lang.KN) opt.kn else opt.en) {
                                engine.pick(opt)
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(engine.transcript) { entry ->
                val fromUser = entry.first
                val text = entry.second
                Text(
                    text = (if (fromUser) "You: " else "") + text,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontSize = 22.sp),
            label = { Text(engine.tr("Type here", "ಇಲ್ಲಿ ಟೈಪ್ ಮಾಡಿ")) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BigButton(Modifier.weight(1f), engine.tr("Send", "ಕಳುಹಿಸಿ")) {
                val text = typed
                typed = ""
                engine.onUser(text)
            }
            BigButton(Modifier.weight(1f), engine.tr("Speak", "ಮಾತನಾಡಿ")) { onMic() }
            if (engine.showMenu) {
                BigButton(Modifier.weight(1f), engine.tr("Cancel", "ರದ್ದು")) { engine.cancel() }
            } else {
                BigButton(Modifier.weight(1f), engine.tr("Menu", "ಮೆನು")) { engine.showOptions() }
            }
        }
    }
}

@Composable
private fun BigButton(
    modifier: Modifier,
    text: String,
    selected: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        colors = if (selected) {
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
