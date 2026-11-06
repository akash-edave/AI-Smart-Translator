package com.example.aismarttranslator

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.net.Uri
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.ui.graphics.asImageBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mlkit.nl.translate.*
import com.google.mlkit.common.model.DownloadConditions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

private val Bg = Color(0xFF0B1020)
private val CardBg = Color(0xFF171F35)
private val Purple = Color(0xFF8B5CF6)
private val Cyan = Color(0xFF22D3EE)
private val White = Color.White
private val Gray = Color(0xFF9CA3AF)
private val Green = Color(0xFF22C55E)

data class Language(
    val name: String,
    val code: String
)

data class TranslationItem(
    val original: String,
    val translated: String,
    val from: String,
    val to: String
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Purple,
                    background = Bg,
                    surface = CardBg
                )
            ) {
                TranslatorApp()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslatorApp() {

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val languages = remember {
        listOf(
            Language("English", TranslateLanguage.ENGLISH),
            Language("Marathi", TranslateLanguage.MARATHI),
            Language("Hindi", TranslateLanguage.HINDI),
            Language("Gujarati", TranslateLanguage.GUJARATI),
            Language("Bengali", TranslateLanguage.BENGALI),
            Language("Tamil", TranslateLanguage.TAMIL),
            Language("Telugu", TranslateLanguage.TELUGU),
            Language("Kannada", TranslateLanguage.KANNADA),
            Language("French", TranslateLanguage.FRENCH),
            Language("German", TranslateLanguage.GERMAN),
            Language("Spanish", TranslateLanguage.SPANISH),
            Language("Japanese", TranslateLanguage.JAPANESE),
            Language("Arabic", TranslateLanguage.ARABIC),
            Language("Portuguese", TranslateLanguage.PORTUGUESE),
            Language("Russian", TranslateLanguage.RUSSIAN)
        )
    }

    var sourceLanguage by remember {
        mutableStateOf(languages[0])
    }

    var targetLanguage by remember {
        mutableStateOf(languages[1])
    }

    var inputText by remember {
        mutableStateOf("")
    }

    var translatedText by remember {
        mutableStateOf("")
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var listening by remember {
        mutableStateOf(false)
    }

    var status by remember {
        mutableStateOf("Ready to translate")
    }

    // Camera and Gallery OCR

    var ocrLoading by remember {
        mutableStateOf(false)
    }

    val textRecognizer = remember {
        TextRecognition.getClient(
            DevanagariTextRecognizerOptions.Builder().build()
        )
    }

    DisposableEffect(textRecognizer) {
        onDispose {
            textRecognizer.close()
        }
    }

    fun recognizeText(image: InputImage) {

        ocrLoading = true
        status = "Reading text from image..."

        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->

                val extractedText = visionText.text

                if (extractedText.isNotBlank()) {
                    inputText = extractedText
                    translatedText = ""
                    status = "Text extracted successfully!"
                } else {
                    status = "No text found in image"
                }

                ocrLoading = false
            }
            .addOnFailureListener { exception ->

                ocrLoading = false
                status = "OCR failed"

                Toast.makeText(
                    context,
                    exception.localizedMessage ?: "Unable to read image",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    val galleryLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->

            uri?.let {
                try {
                    val image = InputImage.fromFilePath(context, it)
                    recognizeText(image)
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Unable to open image",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }


    var history by remember {
        mutableStateOf(emptyList<TranslationItem>())
    }

    var showHistory by remember {
        mutableStateOf(false)
    }

    var speechRecognizer by remember {
        mutableStateOf<SpeechRecognizer?>(null)
    }

    


    val translator = remember(
        sourceLanguage.code,
        targetLanguage.code
    ) {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage.code)
                .setTargetLanguage(targetLanguage.code)
                .build()
        )
    }

    DisposableEffect(translator) {
        onDispose {
            translator.close()
        }
    }

    val tts = remember {
        TextToSpeech(context) {}
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
            tts.stop()
            tts.shutdown()
        }
    }

    // Voice Input Function

    fun startVoiceRecognition() {

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(
                context,
                "Speech recognition is not available on this device",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {

            speechRecognizer?.destroy()

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

            speechRecognizer = recognizer

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.forLanguageTag(
                        sourceLanguage.code
                    ).toLanguageTag()
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    sourceLanguage.code
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )

                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Speak in ${sourceLanguage.name}"
                )
            }

            recognizer.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        listening = true
                        status = "Listening... Speak now"
                    }

                    override fun onBeginningOfSpeech() {
                        status = "Recognizing your voice..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        status = "Processing voice..."
                    }

                    override fun onError(error: Int) {

                        listening = false
                        status = "Voice recognition failed"

                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO ->
                                "Audio recording error"

                            SpeechRecognizer.ERROR_NETWORK ->
                                "Network error"

                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Network timeout"

                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "No speech detected. Try again"

                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "Speech timeout. Please speak again"

                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Microphone permission required"

                            else ->
                                "Please try again"
                        }

                        Toast.makeText(
                            context,
                            message,
                            Toast.LENGTH_SHORT
                        ).show()

                        recognizer.destroy()

                        if (speechRecognizer === recognizer) {
                            speechRecognizer = null
                        }
                    }

                    override fun onResults(results: Bundle?) {

                        listening = false

                        val matches = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        if (!matches.isNullOrEmpty()) {

                            inputText = matches[0]

                            translatedText = ""

                            status = "Voice converted to text successfully!"

                        } else {

                            status = "No voice detected"
                        }

                        recognizer.destroy()

                        if (speechRecognizer === recognizer) {
                            speechRecognizer = null
                        }
                    }

                    override fun onPartialResults(
                        partialResults: Bundle?
                    ) {
                    }

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {
                    }
                }
            )

            recognizer.startListening(intent)

        } catch (e: Exception) {

            listening = false

            status = "Unable to start microphone"

            Toast.makeText(
                context,
                e.localizedMessage ?: "Voice input error",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Microphone Permission

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startVoiceRecognition()
            } else {

                Toast.makeText(
                    context,
                    "Microphone permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    fun requestMicrophonePermission() {

        val permission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        )

        if (permission == PackageManager.PERMISSION_GRANTED) {

            startVoiceRecognition()

        } else {

            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    fun stopVoiceRecognition() {

        speechRecognizer?.stopListening()
        listening = false
        status = "Voice input stopped"
    }

    fun swapLanguages() {

        val oldSource = sourceLanguage

        sourceLanguage = targetLanguage
        targetLanguage = oldSource

        val oldInput = inputText

        inputText = translatedText
        translatedText = oldInput

        status = "Languages swapped"
    }

    fun translateText() {

        if (inputText.isBlank()) {

            Toast.makeText(
                context,
                "Please enter some text",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (sourceLanguage.code == targetLanguage.code) {

            Toast.makeText(
                context,
                "Please select different languages",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        scope.launch {

            loading = true
            translatedText = ""
            status = "Downloading language model..."

            try {

                val conditions = DownloadConditions.Builder()
                    .build()

                translator.downloadModelIfNeeded(conditions).await()

                status = "Translating..."

                val result = translator.translate(inputText).await()

                translatedText = result

                status = "Translation completed successfully!"

                history = listOf(
                    TranslationItem(
                        original = inputText,
                        translated = result,
                        from = sourceLanguage.name,
                        to = targetLanguage.name
                    )
                ) + history

            } catch (e: Exception) {

                status = "Translation failed: ${e.localizedMessage}"

                Toast.makeText(
                    context,
                    status,
                    Toast.LENGTH_LONG
                ).show()

            } finally {

                loading = false
            }
        }
    }

    val keyboardVisible = WindowInsets.isImeVisible

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Bg
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {

            Spacer(Modifier.height(15.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Column {

                    Text(
                        "AI SMART",
                        color = White,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Text(
                        "TRANSLATOR",
                        color = Cyan,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "🌍",
                    fontSize = 38.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Your Personal Language Assistant",
                color = Gray,
                fontSize = 13.sp
            )

            Spacer(
                modifier = Modifier.height(
                    if (keyboardVisible) 5.dp else 15.dp
                )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                LanguageDropdown(
                    language = sourceLanguage,
                    languages = languages,
                    modifier = Modifier.weight(1f)
                ) {
                    sourceLanguage = it
                    translatedText = ""
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = { swapLanguages() }
                ) {

                    Text(
                        "⇄",
                        color = Cyan,
                        fontSize = 30.sp
                    )
                }

                Spacer(Modifier.width(8.dp))

                LanguageDropdown(
                    language = targetLanguage,
                    languages = languages,
                    modifier = Modifier.weight(1f)
                ) {
                    targetLanguage = it
                    translatedText = ""
                }
            }

            Spacer(
                modifier = Modifier.height(
                    if (keyboardVisible) 5.dp else 12.dp
                )
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = CardBg
                )
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {

                    Text(
                        "ENTER TEXT",
                        color = Cyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(15.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {

                        BasicTextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                            },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 17.sp,
                                lineHeight = 25.sp
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Cyan),
                            decorationBox = { innerTextField ->

                                Box {

                                    if (inputText.isEmpty()) {
                                        Text(
                                            "Type or speak something to translate...",
                                            color = Gray,
                                            fontSize = 15.sp
                                        )
                                    }

                                    innerTextField()
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {

                        Text(
                            "${inputText.length} characters",
                            color = Gray,
                            fontSize = 12.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {


// Gallery Button

                            Button(
                                onClick = {
                                    galleryLauncher.launch("image/*")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF059669)
                                ),
                                contentPadding = PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 5.dp
                                )
                            ) {
                                Text(
                                    "🖼️",
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(Modifier.width(6.dp))

                            // Microphone Button

                            Button(
                                onClick = {

                                    if (listening) {
                                        stopVoiceRecognition()
                                    } else {
                                        requestMicrophonePermission()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (listening) {
                                        Color.Red
                                    } else {
                                        Purple
                                    }
                                ),
                                contentPadding = PaddingValues(
                                    horizontal = 12.dp,
                                    vertical = 5.dp
                                )
                            ) {

                                Text(
                                    text = if (listening) "■ STOP" else "🎤 MIC",
                                    color = if (listening) Color.Red else Cyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        if (listening) {
                                            stopVoiceRecognition()
                                        } else {
                                            requestMicrophonePermission()
                                        }
                                    }
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Text(
                                "CLEAR",
                                color = Cyan,
                                modifier = Modifier.clickable {

                                    inputText = ""
                                    translatedText = ""

                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(15.dp))

            Button(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    translateText()
                },
                enabled = !loading && !ocrLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Purple
                )
            ) {

                if (loading) {

                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = White,
                        strokeWidth = 2.dp
                    )

                    Spacer(Modifier.width(10.dp))

                    Text("PLEASE WAIT...")

                } else {

                    Text(
                        "⚡ TRANSLATE NOW",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                status,
                color = if (loading || listening) Cyan else Gray,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(15.dp))

            if (!keyboardVisible) {

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF202B45)
                    )
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {

                            Text(
                                "TRANSLATION",
                                color = Cyan,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "✓",
                                color = Green,
                                fontSize = 20.sp
                            )
                        }

                        Spacer(Modifier.height(15.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {

                            Text(
                                translatedText.ifEmpty {
                                    "Your translated text will appear here..."
                                },
                                color = if (translatedText.isEmpty()) Gray else White,
                                fontSize = 17.sp
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {

                            OutlinedButton(
                                onClick = {

                                    if (translatedText.isNotEmpty()) {

                                        val clipboard =
                                            context.getSystemService(
                                                Context.CLIPBOARD_SERVICE
                                            ) as ClipboardManager

                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText(
                                                "Translation",
                                                translatedText
                                            )
                                        )

                                        Toast.makeText(
                                            context,
                                            "Copied!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {

                                Text("📋 COPY")
                            }

                            OutlinedButton(
                                onClick = {

                                    if (translatedText.isNotEmpty()) {

                                        val locale = Locale.forLanguageTag(
                                            targetLanguage.code
                                        )

                                        val result = tts.setLanguage(locale)

                                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                                            result == TextToSpeech.LANG_NOT_SUPPORTED
                                        ) {

                                            Toast.makeText(
                                                context,
                                                "Voice language not supported",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                        } else {

                                            tts.speak(
                                                translatedText,
                                                TextToSpeech.QUEUE_FLUSH,
                                                null,
                                                "translation"
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {

                                Text("🔊 SPEAK")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        showHistory = !showHistory
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text(
                        if (showHistory) "HIDE HISTORY"
                        else "🕘 TRANSLATION HISTORY"
                    )
                }

                if (showHistory) {

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 150.dp)
                    ) {

                        items(history) { item ->

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = CardBg
                                )
                            ) {

                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {

                                    Text(
                                        "${item.from} → ${item.to}",
                                        color = Cyan,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        item.original,
                                        color = White
                                    )

                                    Text(
                                        item.translated,
                                        color = Green
                                    )

                                    TextButton(
                                        onClick = {

                                            inputText = item.original
                                            translatedText = item.translated

                                            sourceLanguage = languages.first {
                                                it.name == item.from
                                            }

                                            targetLanguage = languages.first {
                                                it.name == item.to
                                            }
                                        }
                                    ) {

                                        Text("USE AGAIN")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageDropdown(
    language: Language,
    languages: List<Language>,
    modifier: Modifier = Modifier,
    onSelect: (Language) -> Unit
) {

    var expanded by remember {
        mutableStateOf(false)
    }

    Box(modifier = modifier) {

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    expanded = true
                },
            colors = CardDefaults.cardColors(
                containerColor = CardBg
            ),
            shape = RoundedCornerShape(12.dp)
        ) {

            Text(
                "${language.name} ▼",
                color = White,
                modifier = Modifier.padding(12.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {

            languages.forEach { item ->

                DropdownMenuItem(
                    text = {
                        Text(item.name)
                    },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    }
                )
            }
        }
    }
}