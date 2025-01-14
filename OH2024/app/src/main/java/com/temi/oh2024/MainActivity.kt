package com.temi.oh2024

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.collection.longSetOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.robotemi.sdk.navigation.model.SpeedLevel
import com.temi.oh2024.ui.theme.OH2024Theme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// This is used to record all screens that are currently present in the app
enum class Screen() {
    Home,
    GeneralQuestions,
    DirectionsAndLocations_Main,
    DirectionsAndLocations_Collections,
    DirectionsAndLocations_Facilities,
    DirectionsAndLocations_Spaces
}

@Composable
fun Gif(imageId: Int) {
    // Determine the image resource based on shouldPlayGif
    val gifEnabledLoader = ImageLoader.Builder(LocalContext.current)
        .components {
            if (SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()

    AsyncImage(
        model = imageId, // Resource or URL of the image/GIF
        contentDescription = "Animated GIF",
        imageLoader = gifEnabledLoader,
        modifier = Modifier
            .fillMaxSize() // Fill the whole screen
            .pointerInput(Unit) {
            },
        contentScale = ContentScale.Crop // Crop to fit the entire screen
    )
}

@Composable
fun GifWithFadeIn(imageId: Int) {
    // Create an animation state to control visibility
    var isVisible by remember { mutableStateOf(false) }

    // Trigger the fade-in animation with a delay
    LaunchedEffect(Unit) {
        delay(2000) // Add a 500ms delay before starting the fade-in
        isVisible = true
    }

    // Build the custom ImageLoader for GIFs
    val gifEnabledLoader = ImageLoader.Builder(LocalContext.current)
        .components {
            if (SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()

    // Animated visibility with fade-in effect
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 1000)), // 1000ms fade-in
        modifier = Modifier.fillMaxSize() // Ensure it fills the screen
    ) {
        AsyncImage(
            model = imageId, // Resource or URL of the image/GIF
            contentDescription = "Animated GIF",
            imageLoader = gifEnabledLoader,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {},
            contentScale = ContentScale.Crop // Crop to fit the entire screen
        )
    }
}

@Composable
fun RefreshExit(viewModel: MainViewModel, delay: Long = 1000) {
    LaunchedEffect(Unit) {
        delay(delay) // Wait for timeout
        viewModel.setIsExitingScreen(false)
        viewModel.setShowImageFlag(false)
    }
}

// Add sound effects here
// Naming convention is the type of audio follow my description
val buttonSoundEffect_main = R.raw.soundeffect_buttonsound
val buttonSoundEffect_secondary = R.raw.soundeffect_buttonsound1
val buttonSoundEffect_tertiary = R.raw.soundeffect_buttonsound2

val music_wait = R.raw.music_wait

val gif_thinking = R.drawable.thinking

@AndroidEntryPoint
@RequiresApi(Build.VERSION_CODES.R)
class MainActivity : ComponentActivity() {
    private var timerJob: Job? = null // Coroutine Job for the timer
    private var lastInteractionTime: Long =
        System.currentTimeMillis() // Track last interaction time
    private val timeoutPeriod: Long = 3000000 // Timeout period in milliseconds
    private var sampleCols: Int = 0
    private var sampleData: List<Int> = listOf(0)

    // Initializing the viewModel
    private lateinit var usbManagerHandler: UsbManagerHandler

    private val openDirectoryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Pass URI to the UsbManagerHandler to create the file
                Log.i("USB!", "sent: ${sampleCols}, ${sampleData}")
                usbManagerHandler.createTextFileOnUsb(uri, "library_map_data.txt", "Columns: $sampleCols\nData: $sampleData")
            }
        }
    }

    private fun listUsbFiles(usbPath: String) {
        val usbDir = File(usbPath)
        if (usbDir.exists() && usbDir.isDirectory) {
            val files = usbDir.listFiles()
            files?.forEach { file ->
                Log.i("USB! Files", "File: ${file.name}")
            }
        } else {
            Log.e("USB! Files", "USB directory does not exist or is not a directory.")
        }
    }

    private fun usbStuff(col: Int, data: List<Int>) {
        usbManagerHandler = UsbManagerHandler(this)
        sampleCols = col
        sampleData = data
        usbManagerHandler.initializeUsbManager(sampleCols, sampleData)
        usbManagerHandler.listUsbDevices()
        val usbPath = usbManagerHandler.getMountedUsbPath()
        if (usbPath != null) {
            Log.i("USB! Path", "USB is mounted at: $usbPath")
        } else {
            Log.e("USB! Path", "No mounted USB path found.")
        }
        usbManagerHandler.getMountedExternalStorage()
        listUsbFiles(usbPath.toString())
        usbManagerHandler.openDirectoryPicker(openDirectoryLauncher)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OH2024Theme {
                // Initialize the navigation controller
                val navController = rememberNavController()
                val viewModel: MainViewModel = hiltViewModel()

//                // This line of code is used for extracting the map and adding it as a file onto a USB on the Temi
//                viewModel.getMapData()?.mapImage?.let { usbStuff(it.cols, viewModel.getMapData()!!.mapImage.data) }
//
//                Log.i("USB!", "${sampleCols}, ${sampleData}")
                val currentScreen = remember { mutableStateOf(Screen.Home.name) }

                // Used for resetting the time out event
                val isThinking by viewModel.isChatGptThinking.collectAsState()
                val isTalking by viewModel.isTalking.collectAsState()
                val isGoing by viewModel.isGoing.collectAsState()
                val isListening by viewModel.isListening.collectAsState()

                // Effect to monitor the flags and reset the timer
                LaunchedEffect(Unit) {
                    while (true) {
                        if (isThinking || isTalking || isGoing || isListening) {
                            lastInteractionTime = System.currentTimeMillis()
                        }
                        delay(1000)
                    }
                }

                // Observe current back stack entry to detect navigation changes
                LaunchedEffect(navController) {
                    navController.addOnDestinationChangedListener { _, destination, _ ->
                        currentScreen.value = destination.route ?: Screen.Home.name

                        // Start or reset timer based on the current screen
                        if (currentScreen.value == Screen.Home.name) {
                            stopTimer()
                        } else {
                            startTimer(navController)
                        }
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Navigation Host
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.name,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        // Define the screens (composable destinations)
                        composable(route = Screen.Home.name) {
                            LaunchedEffect(Unit) {
                                delay(10000) // cause a delay to prevent idle phase happening as soon as user back.
                                viewModel.setGreetMode(true)
                            }
                            viewModel.setSpeed(SpeedLevel.SLOW)
                            HomeScreen(
                                navController,
                                viewModel
                            )  // Passing viewModel here
                        }
                        composable(route = Screen.GeneralQuestions.name) {
                            viewModel.setGreetMode(false)
                            GeneralQuestionsScreen(
                                navController,
                                viewModel
                            )  // Passing viewModel here
                        }
                        composable(route = Screen.DirectionsAndLocations_Main.name) {
                            viewModel.setGreetMode(false)
                            viewModel.setSpeed(SpeedLevel.HIGH)
                            DirectionsAndLocationsScreen_Main(
                                navController,
                                viewModel
                            )  // Passing viewModel here
                        }
                        composable(route = Screen.DirectionsAndLocations_Collections.name) {
                            viewModel.setGreetMode(false)
                            viewModel.setSpeed(SpeedLevel.HIGH)
                            DirectionsAndLocationsScreen_Collections(
                                navController,
                                viewModel
                            )  // Passing viewModel here
                        }
                        composable(route = Screen.DirectionsAndLocations_Facilities.name) {
                            viewModel.setGreetMode(false)
                            viewModel.setSpeed(SpeedLevel.HIGH)
                            DirectionsAndLocationsScreen_Facilities(
                                navController,
                                viewModel
                            )  // Passing viewModel here
                        }
                        composable(route = Screen.DirectionsAndLocations_Spaces.name) {
                            viewModel.setGreetMode(false)
                            viewModel.setSpeed(SpeedLevel.HIGH)
                            DirectionsAndLocationsScreen_Spaces(
                                navController,
                                viewModel
                            )  // Passing viewModel here
                        }
                    }
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            resetTimer() // Reset the timer on any touch event
        }
        return super.dispatchTouchEvent(ev) // Ensure normal event handling
    }

    private fun startTimer(navController: NavController) {
        // Cancel any existing timer
        timerJob?.cancel()
        // Start a new timer
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastInteractionTime >= timeoutPeriod) {
                    withContext(Dispatchers.Main) {
                        navController.navigate(Screen.Home.name) {
                            popUpTo(Screen.Home.name) { inclusive = true }
                        }
                    }
                    break
                }
                Log.i("TIMEOUT!", "${currentTime - lastInteractionTime}")
                delay(1000) // Check conditions every second
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun resetTimer() {
        // Update last interaction time and restart the timer
        lastInteractionTime = System.currentTimeMillis()
    }
}

@Composable
fun HomeScreen(navController: NavController, viewModel: MainViewModel) {
    RefreshExit(viewModel)
    // -- Control UI elements --

    // Button
    val buttonHeight = 100.dp
    val buttonFillWidth = 0.8f
    val buttonSpacing = 16.dp
    //Text
    val fontSize = 48.sp

    //Password system part
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    val correctPassword = "8051" // Change this to your desired password

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.nyp), // Replace with your image resource ID
            contentDescription = "Clickable Image",
            modifier = Modifier
                .align(Alignment.TopCenter) // Position the image at the top-middle of the screen
                .padding(top = 16.dp) // Add padding from the top
                .pointerInput(Unit) { // Custom click handler without ripple effect
                    detectTapGestures {
                        viewModel.onButtonClicked()
                        if (viewModel.shouldShowPasswordPrompt()) {
                            showDialog = true
                        }
                    }
                }
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Enter Password") },
            text = {
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (password == correctPassword) {
                        viewModel.triggerEffect() // Your effect code here
                    }
                    password = ""
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                Button(onClick = { showDialog = false; password = "" }) { Text("Cancel") }
            }
        )
    }

    // Use a Column to stack the buttons vertically
    Column(
        modifier = Modifier
            .fillMaxSize() // Occupy the entire screen space
            .padding(16.dp), // Add some padding to the edges
        verticalArrangement = Arrangement.Center, // Center items vertically
        horizontalAlignment = Alignment.CenterHorizontally // Center items horizontally
    ) {
        // Hidden Password Button

        // Button 1
        Button(
            onClick = {
                navController.navigate(Screen.GeneralQuestions.name)
                viewModel.playSoundEffect(buttonSoundEffect_main)
            },
            modifier = Modifier
                .fillMaxWidth(buttonFillWidth) // Width is 80% of the screen
                .height(buttonHeight), // Increase the height of the buttons
        ) {
            Text("General Questions", fontSize = fontSize)
        }

        Spacer(modifier = Modifier.height(buttonSpacing)) // Space between buttons

        // Button 2
        Button(
            onClick = {
                navController.navigate(Screen.DirectionsAndLocations_Main.name)
                viewModel.playSoundEffect(buttonSoundEffect_main)
            },
            modifier = Modifier
                .fillMaxWidth(buttonFillWidth)
                .height(buttonHeight),
        ) {
            Text("Directions and Locations", fontSize = fontSize)
        }

        Spacer(modifier = Modifier.height(buttonSpacing)) // Space between buttons

    }

    // I messed up in the naming of this, when true it is not meant to be in idle face
    val idleFaceHome by viewModel.isIdleFaceHome.collectAsState()
    val greetMode by viewModel.isGreetMode.collectAsState()

//*******************************************************************Info Poster
    var touchPoint = remember { mutableStateOf<Offset?>(null) }
    var posterOn by remember { mutableStateOf(false) }
    var touchScreenIconOn by remember { mutableStateOf(false) }
    var posterPressed by remember { mutableStateOf(false) }
    val rippleRadius = remember { Animatable(0f) }
    val rippleOpacity = remember { Animatable(1f) }
    val imageOpacity =
        remember { Animatable(1f) }  // Add Animatable for image opacity

//*******************************************************************Poster Reset Logic
// Reset the posterOn state when idleFaceHome or greetMode changes
    LaunchedEffect(idleFaceHome, greetMode) {
        if (!idleFaceHome && greetMode) {
            delay(3000)
            posterOn = true
            posterPressed = false
            touchScreenIconOn = false
            imageOpacity.snapTo(1f) // Reset image opacity to 1
        }
    }

    LaunchedEffect(idleFaceHome, greetMode) {
        if (!(!idleFaceHome && greetMode)) {
            delay(15000)
            if (posterOn) { // Ensure posterOn is still true
                touchScreenIconOn = true
            }
        }
    }

    Log.i("Testing99", "PosterOn: ${posterOn}")
    if (posterOn) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (!posterPressed) {
                            touchPoint.value = offset // Update the touch point state
                            viewModel.playSoundEffect(R.raw.soundeffect_bubble_pop)
                            posterPressed = true
                        }
                    }
                }
        ) {
            // Launch animation when touchPoint changes
            LaunchedEffect(touchPoint.value) {
                touchPoint.value?.let {
                    // Reset ripple animation states
                    rippleRadius.snapTo(0f)
                    rippleOpacity.snapTo(1f)
                    imageOpacity.snapTo(1f) // Reset image opacity to 1

                    // Animate ripple and image opacity
                    rippleRadius.animateTo(
                        targetValue = 3000f,
                        animationSpec = tween(durationMillis = 500)
                    )

                    touchScreenIconOn =  false

                    imageOpacity.animateTo(
                        targetValue = 0f, // Fade out the image
                        animationSpec = tween(durationMillis = 100)
                    )

                    rippleOpacity.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 500)
                    )

                    // After the ripple completes, hide the image by setting posterOn to false
                    posterOn = false
                    touchPoint.value = null
                }
            }

            // Show image with opacity controlled by imageOpacity
            Image(
                painter = painterResource(id = R.drawable.poster), // Use your drawable resource
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(imageOpacity.value) // Apply image opacity animation
            )

            if (touchScreenIconOn) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center) // Align to the center of the screen
                        .size(400.dp) // Set the size of the Box
                        .background(
                            Color.White,
                            shape = CircleShape
                        ) // Add a white circular background
                        .padding(10.dp) // Add padding to ensure the content doesn't touch the edges
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center) // Align to the center of the screen
                            .size(240.dp) // Set the size of the Box
                    ) {
                        // Display the GIF
                        Gif(R.drawable.touch_indicator)
                    }
                }
            }

            // Draw the ripple
            Canvas(modifier = Modifier.fillMaxSize()) {
                touchPoint.value?.let {
                    drawCircle(
                        color = Color.White.copy(alpha = rippleOpacity.value),
                        radius = rippleRadius.value,
                        center = it
                    )
                }
            }
        }
    }

//*******************************************************************Created Image Generator
    if (!idleFaceHome && greetMode) {
        GifWithFadeIn(R.drawable.idle)
    }

}

@Composable
fun BlackRectangleWithWhiteLine(
    height: Int,
    text: String,
    typingSpeed: Long,
    sidePadding: Dp // This is the new parameter to control side padding
) {
    Box(
        modifier = Modifier
            .fillMaxSize() // Fill the screen
            .padding(bottom = 0.dp) // No padding at the bottom
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter) // Align the box to the bottom center
                .background(Color.Black) // Black rectangle background
                .fillMaxWidth() // Fill the width
                .height(height.dp) // Set dynamic height
        ) {
            // White top line
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart) // Align to the top of the black box
                    .fillMaxWidth() // Stretch across the entire width
                    .height(4.dp) // Height of the white line
                    .background(Color.White) // White line color
            )

            // Typing text animation with flexible side padding
            TypingTextAnimation(
                text = text,
                typingSpeed = typingSpeed,
                sidePadding = sidePadding, // Pass sidePadding to TypingTextAnimation
                modifier = Modifier
                    .align(Alignment.Center) // Center the text
                    .padding(8.dp) // Optional padding around the text
            )
        }
    }
}

@Composable
fun TypingTextAnimation(
    text: String,
    typingSpeed: Long,
    sidePadding: Dp,
    modifier: Modifier = Modifier
) {
    var displayedText by remember { mutableStateOf("") }

    LaunchedEffect(text) {
        // This will make the text appear character by character
        for (i in text.indices) {
            displayedText += text[i] // Add one character at a time
            delay(typingSpeed) // Delay between each character
        }
    }

    // Display the animated text
    Column(
        modifier = modifier
            .fillMaxWidth() // Make sure the column takes up the entire width
            .padding(start = sidePadding, end = sidePadding) // Apply the side padding
            .wrapContentHeight(Alignment.CenterVertically) // Ensures it stays vertically centered even if multi-line
    ) {
        BasicText(
            text = displayedText,
            style = TextStyle(color = Color.White, fontSize = 32.sp),
            modifier = Modifier.fillMaxWidth() // Ensure the text is centered
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun GeneralQuestionsScreen(navController: NavController, viewModel: MainViewModel) {
    RefreshExit(viewModel, 100)

    // Flags
    val showImage by viewModel.showImageFlag.collectAsState()
    val showWaitSequence by viewModel.isChatGptThinking.collectAsState()

    // Add time out for the image
    val timeoutDuration = 2000L // Timeout duration (5 seconds)
    // Start a timeout event (timeoutDuration) to hide the image

    val talking by viewModel.isTalking.collectAsState()

    LaunchedEffect(showImage) {
        while (true) {
            if (showImage && !talking) {
                delay(timeoutDuration) // Wait for timeout
                viewModel.setShowImageFlag(false) // Hide the image after timeout
                break
            }
            delay(100)
        }
    }

    // Sample list of question types (replace with your actual data)
    // Note that if you do not want to use an image with a question, just leave it as null
    val questionTypes = listOf(
        Triple(
            "What is Temi?",
            "Temi is a personal robot designed for various assistive and entertainment tasks. This other sentence is here for testing a particular issues that way or may not occur.",
            null
        ),
        Triple(
            "How do I use Temi?",
            "You can use Temi by giving voice commands, using the touchscreen, or the mobile app.",
            null
        ),
        Triple(
            "What features does Temi have?",
            "Temi features include autonomous navigation, voice recognition, and video calling.",
            null
        ),
        Triple(
            "How can Temi assist me?",
            "Temi can assist you with tasks like scheduling, navigation, and connecting to smart devices.",
            null
        ),
        Triple(
            "What are Temi's limitations?",
            "Temi cannot perform heavy lifting or tasks that require physical dexterity.",
            null
        )
    )

    var textForSub = remember { mutableStateOf("If you are seeing this, it is a bug") }
    var imageToShow = remember { mutableStateOf<Int?>(R.drawable.sample_image) }

    Scaffold(
        topBar = {
            // Top App Bar with "Back to Home" button
            androidx.compose.material3.TopAppBar(
                title = { Text("General Questions") },
                actions = {
                    // Button in the top-right corner
                    Button(
                        onClick = {
                            navController.navigate(Screen.Home.name)
                            viewModel.playSoundEffect(buttonSoundEffect_main)
                            viewModel.setIsExitingScreen(true)
                        },
                        modifier = Modifier
                            .padding(end = 16.dp) // Padding for spacing
                            .height(60.dp)
                            .width(120.dp)
                    ) {
                        Text("HOME", fontSize = 24.sp)
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally // Center items horizontally
        ) {
            // This code is for implementing ChatGPT into the application...
            // as of now it is not going to be used for this application.
            /*
            // "Ask Question" Button
            Button(
                onClick = {
                    viewModel.playSoundEffect(buttonSoundEffect_main)

                    viewModel.askQuestionUi(
                        "Just be yourself, keep responses short such as two or three sentences. If the user says Tammy or Timmy they mean Temi, there is an issue with the text to speech system. Also you are answering questions for Nanyaung Polytechnic in Singapore."
                                +
                                "Here are some general questions regarding the robotics and mechatronics course:\n" +
                                "\n" +
                                "                    1. **Entry Requirements:**\n" +
                                "                    - What are the entry requirements for specific courses for GCE 'O' Level and Nitec/Higher Nitec students?\n" +
                                "\n" +
                                "                    2. **Diploma Courses:**\n" +
                                "                    - What diploma courses are offered at the polytechnic?\n" +
                                "\n" +
                                "                    3. **GCE 'O' Level Results:**\n" +
                                "                    - Can I combine different subject grades from multiple sittings of the GCE 'O' Level exams?\n" +
                                "\n" +
                                "                    4. **Direct Entry Programs:**\n" +
                                "                    - Are there direct entry programs or early admission options available for this course?\n" +
                                "\n" +
                                "                    5. **Class Size:**\n" +
                                "                    - What is the typical class size in lectures and tutorials for this course?\n" +
                                "\n" +
                                "                    6. **Specializations and Electives:**\n" +
                                "                    - Are there specializations or electives within the robotics and mechatronics course?\n" +
                                "\n" +
                                "                    7. **University Admission Preparation:**\n" +
                                "                    - How does the polytechnic prepare students for university admission?\n" +
                                "\n" +
                                "                    8. **Internship Opportunities:**\n" +
                                "                    - What are the internship opportunities for this course?\n" +
                                "\n" +
                                "                    9. **Overseas Exchange Programs:**\n" +
                                "                    - Are there overseas exchange or study programs available?\n" +
                                "\n" +
                                "                    10. **Industry Projects and Collaborations:**\n" +
                                "                    - What kind of industry projects or collaborations can students expect?\n" +
                                "\n" +
                                "                    11. **Lab and Facility Tours:**\n" +
                                "                    - Can we tour the labs and specialized facilities for this course?\n" +
                                "\n" +
                                "                    12. **Library and Study Resources:**\n" +
                                "                    - Is there access to libraries, study spaces, and support services?\n" +
                                "\n" +
                                "                    13. **Extracurricular Activities:**\n" +
                                "                    - Are there facilities for sports, music, and other extracurricular activities?\n" +
                                "\n" +
                                "                    14. **Software and Hardware Tools:**\n" +
                                "                    - What types of software or hardware tools do students have access to?\n" +
                                "\n" +
                                "                    15. **Clubs and Societies:**\n" +
                                "                    - What clubs or societies are available for students to join?\n" +
                                "\n" +
                                "                    16. **Mentorship and Peer Support:**\n" +
                                "                    - Is there a mentorship or peer support system for new students?\n" +
                                "\n" +
                                "                    17. **Mental Health and Counseling Services:**\n" +
                                "                    - How does the polytechnic support students in terms of mental health and counseling services?\n" +
                                "\n" +
                                "                    18. **Scholarships and Financial Aid:**\n" +
                                "                    - Are there scholarships, bursaries, or financial aid available for students?\n" +
                                "\n" +
                                "                    19. **Employment Prospects:**\n" +
                                "                    - What are the employment prospects for graduates from this course?\n" +
                                "\n" +
                                "                    20. **Career Services and Alumni Networks:**\n" +
                                "                    - Are there alumni networks or career services to help students after graduation?"
                    )
                },
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .padding(bottom = 16.dp, top = 70.dp) // Space below the button
                    .height(100.dp)
            ) {
                Text("Ask Question", fontSize = 48.sp)
            }
             */

            // Scrollable List of Questions
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(questionTypes) { question ->
                    // Each question as a text item
                    Text(
                        text = question.first,
                        fontSize = 35.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .wrapContentWidth(Alignment.CenterHorizontally) // Center horizontally
                            .clickable {
                                viewModel.playSoundEffect(buttonSoundEffect_secondary)
                                viewModel.speakForUi(question.second, true)
                                textForSub.value = question.second
                                viewModel.setShowImageFlag(true)
                                if (question.third != null) {
                                    imageToShow.value = question.third
                                } else imageToShow.value = null
                            },
                        // color = Color.White // Text color
                    )
                    // Optional divider between items
                    Divider(thickness = 1.dp)
                }
            }
        }
    }

    if (showImage) {
        // Display the image when showImage is true
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        // Hide image when tapped
                        viewModel.setShowImageFlag(false)
                    })
                }
        ) {
            if (imageToShow.value != null) {
                Image(
                    painter = painterResource(id = imageToShow.value!!), // Replace with your image resource
                    contentDescription = "Displayed Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f) // Adjust as needed
                )
            }

            BlackRectangleWithWhiteLine(300, textForSub.value, typingSpeed = 40, sidePadding = 40.dp) // You can change typingSpeed here for testing

        }
    }

    if (showWaitSequence) {
        Gif(gif_thinking)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "UseOfNonLambdaOffsetOverload")
@Composable
fun DirectionsAndLocationsScreen_Main(navController: NavController, viewModel: MainViewModel) {
    RefreshExit(viewModel, delay = 100)

    var point by remember { mutableIntStateOf(1) }

    // -- Control UI elements --
    // Button
    val buttonWidthAndHeight = 500.dp
    val roundedCorners = 12.dp
    //Text
    val fontSize = 30.sp

    val categories = listOf(Pair("Collections", R.drawable.sample_image), Pair("Facilities", R.drawable.sample_image), Pair("Spaces", R.drawable.sample_image))
    Scaffold(
        topBar = {
            // Top App Bar with "Back to Home" button
            androidx.compose.material3.TopAppBar(
                title = { Text("Directions/Locations") },
                actions = {
                    // Button in the top-right corner
                    Button(
                        onClick = {
                            navController.navigate(Screen.Home.name)
                            viewModel.playSoundEffect(buttonSoundEffect_main)
                            viewModel.setIsExitingScreen(true)
                        },
                        modifier = Modifier
                            .padding(end = 16.dp) // Padding for spacing
                            .height(60.dp)
                            .width(120.dp)
                    ) {
                        Text("HOME", fontSize = 24.sp)
                    }
                }
            )
        },

        content = {

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Sample data - in your case, this will come from viewModel.coordinatesDataForLocations()

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), // Padding around the whole LazyRow
                    horizontalArrangement = Arrangement.Center, // Space between items
                    verticalAlignment = Alignment.CenterVertically // Center items vertically
                ) {
                    items(categories.size) { index ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally, // Aligning content within Column
                            verticalArrangement = Arrangement.Center // Center items in the Column
                        ) {
                            // Button Text as location name or ID
                            Text(
                                text = categories[index].first.replaceFirstChar {
                                    if (it.isLowerCase()) it.titlecase(
                                        Locale.getDefault()
                                    ) else it.toString()
                                },
                                fontSize = 48.sp,  // Adjust as necessary
                                color = Color.Black,
                                modifier = Modifier.padding(bottom = 8.dp)  // Add some space below the text
                            )

                            // Button
                            Button(
                                onClick = {
                                    // Play sound effect
                                    viewModel.playSoundEffect(buttonSoundEffect_main)
                                    when (categories[index].first) {
                                        "Collections" -> {
                                            navController.navigate(Screen.DirectionsAndLocations_Collections.name)
                                        }
                                        "Facilities" -> {
                                            navController.navigate(Screen.DirectionsAndLocations_Facilities.name)
                                        }
                                        else -> {
                                            navController.navigate(Screen.DirectionsAndLocations_Spaces.name)
                                        }
                                    }

                                },
                                modifier = Modifier
                                    .width(buttonWidthAndHeight)  // Set button width
                                    .height(buttonWidthAndHeight)  // Set button height
                                    .padding(start = 50.dp, end = 50.dp, bottom = 50.dp),  // Add padding around the button itself
                                shape = RoundedCornerShape(roundedCorners),  // Adjust corner radius
                            ) {
                                // Background image inside the Box
                                Image(
                                    painter = painterResource(categories[index].second),
                                    contentDescription = "Button Image",
                                    modifier = Modifier.fillMaxSize() // Ensure the image fills the button's size
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "UseOfNonLambdaOffsetOverload")
@Composable
fun DirectionsAndLocationsScreen_Collections(navController: NavController, viewModel: MainViewModel) {
    RefreshExit(viewModel, delay = 100)
    val showImage by viewModel.showImageFlag.collectAsState()

    var point by remember { mutableIntStateOf(1) }

    val position = when (point) {
        1 -> {
            val (x, y) = Pair(0.0f, 0.0f) // Coordinates for point 1
            Offset(x.toFloat(), y.toFloat())
        }

        2 -> {
            val (x, y) = Pair(0.0f, 0.0f) // Coordinates for point 1 // Coordinates for point 2
            Offset(x.toFloat(), y.toFloat())
        }

        3 -> {
            val (x, y) = Pair(0.0f, 0.0f) // Coordinates for point 1 // Coordinates for point 3
            Offset(x.toFloat(), y.toFloat())
        }

        4 -> {
            val (x, y) = Pair(0.0f, 0.0f) // Coordinates for point 1 // Coordinates for point 4
            Offset(x.toFloat(), y.toFloat())
        }

        else -> Offset(0f, 0f) // Default position
    }

    // -- Control UI elements --
    // Button
    val buttonWidthAndHeight = 500.dp
    val roundedCorners = 12.dp
    //Text
    val fontSize = 30.sp

    // MutableState to hold the current position
    val currentNewMethodPosition = remember {
        mutableStateOf(
            viewModel.dynamicCoordinateConvert(
                viewModel.getPosition().x,
                viewModel.getPosition().y
            )
        )
    }

    // Observe changes to the position from the viewModel
    LaunchedEffect(viewModel) {
        viewModel.positionFlow.collect { newPosition ->
            val mappedPositionTwo = viewModel.dynamicCoordinateConvert(
                newPosition.x,
                newPosition.y
            )

            currentNewMethodPosition.value = mappedPositionTwo

            delay(100)
        }
    }

    var xMarker by remember {mutableFloatStateOf(0f) }
    var yMarker by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            // Top App Bar with "Back to Home" button
            androidx.compose.material3.TopAppBar(
                title = { Text("Directions/Locations") },
                actions = {
                    // Button in the top-right corner
                    Button(
                        onClick = {
                            navController.navigate(Screen.DirectionsAndLocations_Main.name)
                            viewModel.playSoundEffect(buttonSoundEffect_main)
                        },
                        modifier = Modifier
                            .padding(end = 16.dp) // Padding for spacing
                            .height(60.dp)
                            .width(120.dp)
                    ) {
                        Text("BACK", fontSize = 24.sp)
                    }

                    // Button in the top-right corner
                    Button(
                        onClick = {
                            navController.navigate(Screen.Home.name)
                            viewModel.playSoundEffect(buttonSoundEffect_main)
                            viewModel.setIsExitingScreen(true)
                        },
                        modifier = Modifier
                            .padding(end = 16.dp) // Padding for spacing
                            .height(60.dp)
                            .width(120.dp)
                    ) {
                        Text("HOME", fontSize = 24.sp)
                    }
                }
            )
        },

        content = {

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Sample data - in your case, this will come from viewModel.coordinatesDataForLocations()
                val locations = viewModel.coordinatesDataForLocations().take(4)  // First 4 locations

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), // Padding around the whole LazyRow
                    horizontalArrangement = Arrangement.spacedBy(10.dp), // Space between items
                    verticalAlignment = Alignment.CenterVertically // Center items vertically
                ) {
                    items(locations.size) { index ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally, // Aligning content within Column
                            verticalArrangement = Arrangement.Center // Center items in the Column
                        ) {
                            // Button Text as location name or ID
                            Text(
                                text = locations[index].first
                                    .split(" ") // Split the string by spaces
                                    .joinToString(" ") { it.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) } },
                                fontSize = 48.sp,  // Adjust as necessary
                                color = Color.Black,
                                modifier = Modifier.padding(bottom = 8.dp)  // Add some space below the text
                            )

                            // Button
                            Button(
                                onClick = {
                                    // Play sound effect
                                    viewModel.playSoundEffect(buttonSoundEffect_main)

                                    // Get coordinates for the button pressed
                                    val coordinates = viewModel.dynamicCoordinateConvert(
                                        locations[index].second,  // x coordinate
                                        locations[index].third   // y coordinate
                                    )
                                    // Update marker coordinates
                                    xMarker = coordinates.first
                                    yMarker = coordinates.second

                                    // Set image visibility flag
                                    viewModel.setShowImageFlag(true)

                                    // Update point based on button index or some logic
                                    point = index + 1

                                    // Query location
                                    viewModel.queryLocation(locations[index].first)
                                },
                                modifier = Modifier
                                    .width(buttonWidthAndHeight)  // Set button width
                                    .height(buttonWidthAndHeight)  // Set button height
                                    .padding(start = 50.dp, end = 50.dp, bottom = 50.dp),  // Add padding around the button itself
                                shape = RoundedCornerShape(roundedCorners),  // Adjust corner radius
                            ) {}
                        }
                    }
                }
            }
        }
    )

    if (showImage) { // Display the image and path overlay
        // State for zoom, pan, and tap gestures
        val scale = remember { mutableStateOf(1f) }
        val offsetX = remember { mutableStateOf(0f) }
        val offsetY = remember { mutableStateOf(0f) }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale.value = (scale.value * zoom).coerceIn(0.5f, 5f) // Limit zoom range
                        offsetX.value += pan.x
                        offsetY.value += pan.y
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (!viewModel.isSpeaking.value and !viewModel.isListening.value) {
                            viewModel.setShowImageFlag(false) // Close image on tap
                        }
                    }
                }
        ) {
            // Render the image with zoom and pan transformations
            Image(
                painter = BitmapPainter(viewModel.renderedMap), // Replace with your image resource
                contentDescription = "Displayed Image",
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offsetX.value,
                        translationY = offsetY.value
                    )
                    .aspectRatio(viewModel.mapScale) // Maintain aspect ratio if needed
            )

            /*
             // Render the path points
            CreatePath(
                dotSize = 8,
                spaceBetweenDots = 15,
                pathPoints = listOf(
                    Pair(125, 230),
                    Pair(115, 110),
                    Pair(70, 110),
                    Pair(60, -105)
                ),
                currentPosition = Pair(
                    currentNewMethodPosition.value.first.toInt(),
                    currentNewMethodPosition.value.second.toInt()
                ),
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY
            )
             */

            Log.i("MAP DATA", "${Pair(xMarker, yMarker)}")

            // Render the red circle with proper scaling and positioning
            Box(
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offsetX.value + (xMarker * scale.value),
                        translationY = offsetY.value + (yMarker * scale.value)
                    )
                    .size(20.dp) // Size of the circle
                    .background(Color.Red, shape = CircleShape) // Make it a red circle
            )

            // Render the pointer image with zoom and pan transformations
            Image(
                painter = painterResource(id = R.drawable.pointer), // Replace with your pointer image
                contentDescription = "Pointer Image",
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offsetX.value + (currentNewMethodPosition.value.first * scale.value),
                        translationY = offsetY.value + (currentNewMethodPosition.value.second * scale.value)
                    )
                    .size(viewModel.pointerSize.dp) // Maintain consistent pointer size
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "UseOfNonLambdaOffsetOverload")
@Composable
fun DirectionsAndLocationsScreen_Facilities(navController: NavController, viewModel: MainViewModel) {
    RefreshExit(viewModel, delay = 100)
    val showImage by viewModel.showImageFlag.collectAsState()

    var point by remember { mutableIntStateOf(1) }

    val position = when (point) {
        1 -> {
            val (x, y) = Pair(0.0f, 0.0f) // Coordinates for point 1
            Offset(x.toFloat(), y.toFloat())
        }

        2 -> {
            val (x, y) = Pair(0.0f, 0.0f) // Coordinates for point 1 // Coordinates for point 2
            Offset(x.toFloat(), y.toFloat())
        }

        3 -> {
            val (x, y) = Pair(0.0f, 0.0f) // Coordinates for point 1 // Coordinates for point 3
            Offset(x.toFloat(), y.toFloat())
        }

        4 -> {
            val (x, y) = Pair(0.0f, 0.0f) // Coordinates for point 1 // Coordinates for point 4
            Offset(x.toFloat(), y.toFloat())
        }

        else -> Offset(0f, 0f) // Default position
    }

    // -- Control UI elements --
    // Button
    val buttonWidthAndHeight = 500.dp
    val roundedCorners = 12.dp
    //Text
    val fontSize = 30.sp

    // MutableState to hold the current position
    val currentNewMethodPosition = remember {
        mutableStateOf(
            viewModel.dynamicCoordinateConvert(
                viewModel.getPosition().x,
                viewModel.getPosition().y
            )
        )
    }

    // Observe changes to the position from the viewModel
    LaunchedEffect(viewModel) {
        viewModel.positionFlow.collect { newPosition ->
            val mappedPositionTwo = viewModel.dynamicCoordinateConvert(
                newPosition.x,
                newPosition.y
            )

            currentNewMethodPosition.value = mappedPositionTwo

            delay(100)
        }
    }

    var xMarker by remember {mutableFloatStateOf(0f) }
    var yMarker by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            // Top App Bar with "Back to Home" button
            androidx.compose.material3.TopAppBar(
                title = { Text("Directions/Locations") },
                actions = {
                    // Button in the top-right corner
                    Button(
                        onClick = {
                            navController.navigate(Screen.DirectionsAndLocations_Main.name)
                            viewModel.playSoundEffect(buttonSoundEffect_main)
                        },
                        modifier = Modifier
                            .padding(end = 16.dp) // Padding for spacing
                            .height(60.dp)
                            .width(120.dp)
                    ) {
                        Text("BACK", fontSize = 24.sp)
                    }

                    // Button in the top-right corner
                    Button(
                        onClick = {
                            navController.navigate(Screen.Home.name)
                            viewModel.playSoundEffect(buttonSoundEffect_main)
                            viewModel.setIsExitingScreen(true)
                        },
                        modifier = Modifier
                            .padding(end = 16.dp) // Padding for spacing
                            .height(60.dp)
                            .width(120.dp)
                    ) {
                        Text("HOME", fontSize = 24.sp)
                    }
                }
            )
        },

        content = {

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Sample data - in your case, this will come from viewModel.coordinatesDataForLocations()
                val locations = viewModel.coordinatesDataForLocations().slice(4..8)  // Next 5 locations

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), // Padding around the whole LazyRow
                    horizontalArrangement = Arrangement.spacedBy(10.dp), // Space between items
                    verticalAlignment = Alignment.CenterVertically // Center items vertically
                ) {
                    items(locations.size) { index ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally, // Aligning content within Column
                            verticalArrangement = Arrangement.Center // Center items in the Column
                        ) {
                            // Button Text as location name or ID
                            Text(
                                text = locations[index].first
                                    .split(" ") // Split the string by spaces
                                    .joinToString(" ") { it.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) } },
                                fontSize = 48.sp,  // Adjust as necessary
                                color = Color.Black,
                                modifier = Modifier.padding(bottom = 8.dp)  // Add some space below the text
                            )

                            // Button
                            Button(
                                onClick = {
                                    // Play sound effect
                                    viewModel.playSoundEffect(buttonSoundEffect_main)

                                    // Get coordinates for the button pressed
                                    val coordinates = viewModel.dynamicCoordinateConvert(
                                        locations[index].second,  // x coordinate
                                        locations[index].third   // y coordinate
                                    )
                                    // Update marker coordinates
                                    xMarker = coordinates.first
                                    yMarker = coordinates.second

                                    // Set image visibility flag
                                    viewModel.setShowImageFlag(true)

                                    // Update point based on button index or some logic
                                    point = index + 1

                                    // Query location
                                    viewModel.queryLocation(locations[index].first)
                                },
                                modifier = Modifier
                                    .width(buttonWidthAndHeight)  // Set button width
                                    .height(buttonWidthAndHeight)  // Set button height
                                    .padding(start = 50.dp, end = 50.dp, bottom = 50.dp),  // Add padding around the button itself
                                shape = RoundedCornerShape(roundedCorners),  // Adjust corner radius
                            ) {}
                        }
                    }
                }
            }
        }
    )

    if (showImage) { // Display the image and path overlay
        // State for zoom, pan, and tap gestures
        val scale = remember { mutableStateOf(1f) }
        val offsetX = remember { mutableStateOf(0f) }
        val offsetY = remember { mutableStateOf(0f) }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale.value = (scale.value * zoom).coerceIn(0.5f, 5f) // Limit zoom range
                        offsetX.value += pan.x
                        offsetY.value += pan.y
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (!viewModel.isSpeaking.value and !viewModel.isListening.value) {
                            viewModel.setShowImageFlag(false) // Close image on tap
                        }
                    }
                }
        ) {
            // Render the image with zoom and pan transformations
            Image(
                painter = BitmapPainter(viewModel.renderedMap), // Replace with your image resource
                contentDescription = "Displayed Image",
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offsetX.value,
                        translationY = offsetY.value
                    )
                    .aspectRatio(viewModel.mapScale) // Maintain aspect ratio if needed
            )

            /*
             // Render the path points
            CreatePath(
                dotSize = 8,
                spaceBetweenDots = 15,
                pathPoints = listOf(
                    Pair(125, 230),
                    Pair(115, 110),
                    Pair(70, 110),
                    Pair(60, -105)
                ),
                currentPosition = Pair(
                    currentNewMethodPosition.value.first.toInt(),
                    currentNewMethodPosition.value.second.toInt()
                ),
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY
            )
             */

            Log.i("MAP DATA", "${Pair(xMarker, yMarker)}")

            // Render the red circle with proper scaling and positioning
            Box(
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offsetX.value + (xMarker * scale.value),
                        translationY = offsetY.value + (yMarker * scale.value)
                    )
                    .size(20.dp) // Size of the circle
                    .background(Color.Red, shape = CircleShape) // Make it a red circle
            )

            // Render the pointer image with zoom and pan transformations
            Image(
                painter = painterResource(id = R.drawable.pointer), // Replace with your pointer image
                contentDescription = "Pointer Image",
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offsetX.value + (currentNewMethodPosition.value.first * scale.value),
                        translationY = offsetY.value + (currentNewMethodPosition.value.second * scale.value)
                    )
                    .size(viewModel.pointerSize.dp) // Maintain consistent pointer size
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "UseOfNonLambdaOffsetOverload")
@Composable
fun DirectionsAndLocationsScreen_Spaces(navController: NavController, viewModel: MainViewModel) {
    RefreshExit(viewModel, delay = 100)
    val showImage by viewModel.showImageFlag.collectAsState()

    var point by remember { mutableIntStateOf(1) }

    val position = when (point) {
        1 -> {
            val (x, y) = Pair(0.0f, 0.0f) // Coordinates for point 1
            Offset(x.toFloat(), y.toFloat())
        }

        2 -> {
            val (x, y) = Pair(0.0f, 0.0f) // Coordinates for point 1 // Coordinates for point 2
            Offset(x.toFloat(), y.toFloat())
        }

        3 -> {
            val (x, y) = Pair(0.0f, 0.0f) // Coordinates for point 1 // Coordinates for point 3
            Offset(x.toFloat(), y.toFloat())
        }

        4 -> {
            val (x, y) = Pair(0.0f, 0.0f) // Coordinates for point 1 // Coordinates for point 4
            Offset(x.toFloat(), y.toFloat())
        }

        else -> Offset(0f, 0f) // Default position
    }

    // -- Control UI elements --
    // Button
    val buttonWidthAndHeight = 500.dp
    val roundedCorners = 12.dp
    //Text
    val fontSize = 30.sp

    // MutableState to hold the current position
    val currentNewMethodPosition = remember {
        mutableStateOf(
            viewModel.dynamicCoordinateConvert(
                viewModel.getPosition().x,
                viewModel.getPosition().y
            )
        )
    }

    // Observe changes to the position from the viewModel
    LaunchedEffect(viewModel) {
        viewModel.positionFlow.collect { newPosition ->
            val mappedPositionTwo = viewModel.dynamicCoordinateConvert(
                newPosition.x,
                newPosition.y
            )

            currentNewMethodPosition.value = mappedPositionTwo

            delay(100)
        }
    }

    var xMarker by remember {mutableFloatStateOf(0f) }
    var yMarker by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            // Top App Bar with "Back to Home" button
            androidx.compose.material3.TopAppBar(
                title = { Text("Directions/Locations") },
                actions = {
                    // Button in the top-right corner
                    Button(
                        onClick = {
                            navController.navigate(Screen.DirectionsAndLocations_Main.name)
                            viewModel.playSoundEffect(buttonSoundEffect_main)
                        },
                        modifier = Modifier
                            .padding(end = 16.dp) // Padding for spacing
                            .height(60.dp)
                            .width(120.dp)
                    ) {
                        Text("BACK", fontSize = 24.sp)
                    }

                    // Button in the top-right corner
                    Button(
                        onClick = {
                            navController.navigate(Screen.Home.name)
                            viewModel.playSoundEffect(buttonSoundEffect_main)
                            viewModel.setIsExitingScreen(true)
                        },
                        modifier = Modifier
                            .padding(end = 16.dp) // Padding for spacing
                            .height(60.dp)
                            .width(120.dp)
                    ) {
                        Text("HOME", fontSize = 24.sp)
                    }
                }
            )
        },

        content = {

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Sample data - in your case, this will come from viewModel.coordinatesDataForLocations()
                val locations = viewModel.coordinatesDataForLocations().takeLast(4)

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), // Padding around the whole LazyRow
                    horizontalArrangement = Arrangement.spacedBy(10.dp), // Space between items
                    verticalAlignment = Alignment.CenterVertically // Center items vertically
                ) {
                    items(locations.size) { index ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally, // Aligning content within Column
                            verticalArrangement = Arrangement.Center // Center items in the Column
                        ) {
                            // Button Text as location name or ID
                            Text(
                                text = locations[index].first
                                    .split(" ") // Split the string by spaces
                                    .joinToString(" ") { it.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) } },
                                fontSize = 48.sp,  // Adjust as necessary
                                color = Color.Black,
                                modifier = Modifier.padding(bottom = 8.dp)  // Add some space below the text
                            )

                            // Button
                            Button(
                                onClick = {
                                    // Play sound effect
                                    viewModel.playSoundEffect(buttonSoundEffect_main)

                                    // Get coordinates for the button pressed
                                    val coordinates = viewModel.dynamicCoordinateConvert(
                                        locations[index].second,  // x coordinate
                                        locations[index].third   // y coordinate
                                    )
                                    // Update marker coordinates
                                    xMarker = coordinates.first
                                    yMarker = coordinates.second

                                    // Set image visibility flag
                                    viewModel.setShowImageFlag(true)

                                    // Update point based on button index or some logic
                                    point = index + 1

                                    // Query location
                                    viewModel.queryLocation(locations[index].first)
                                },
                                modifier = Modifier
                                    .width(buttonWidthAndHeight)  // Set button width
                                    .height(buttonWidthAndHeight)  // Set button height
                                    .padding(start = 50.dp, end = 50.dp, bottom = 50.dp),  // Add padding around the button itself
                                shape = RoundedCornerShape(roundedCorners),  // Adjust corner radius
                            ) {}
                        }
                    }
                }
            }
        }
    )

    if (showImage) { // Display the image and path overlay
        // State for zoom, pan, and tap gestures
        val scale = remember { mutableStateOf(1f) }
        val offsetX = remember { mutableStateOf(0f) }
        val offsetY = remember { mutableStateOf(0f) }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale.value = (scale.value * zoom).coerceIn(0.5f, 5f) // Limit zoom range
                        offsetX.value += pan.x
                        offsetY.value += pan.y
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (!viewModel.isSpeaking.value and !viewModel.isListening.value) {
                            viewModel.setShowImageFlag(false) // Close image on tap
                        }
                    }
                }
        ) {
            // Render the image with zoom and pan transformations
            Image(
                painter = BitmapPainter(viewModel.renderedMap), // Replace with your image resource
                contentDescription = "Displayed Image",
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offsetX.value,
                        translationY = offsetY.value
                    )
                    .aspectRatio(viewModel.mapScale) // Maintain aspect ratio if needed
            )

            /*
             // Render the path points
            CreatePath(
                dotSize = 8,
                spaceBetweenDots = 15,
                pathPoints = listOf(
                    Pair(125, 230),
                    Pair(115, 110),
                    Pair(70, 110),
                    Pair(60, -105)
                ),
                currentPosition = Pair(
                    currentNewMethodPosition.value.first.toInt(),
                    currentNewMethodPosition.value.second.toInt()
                ),
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY
            )
             */

            Log.i("MAP DATA", "${Pair(xMarker, yMarker)}")

            // Render the red circle with proper scaling and positioning
            Box(
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offsetX.value + (xMarker * scale.value),
                        translationY = offsetY.value + (yMarker * scale.value)
                    )
                    .size(20.dp) // Size of the circle
                    .background(Color.Red, shape = CircleShape) // Make it a red circle
            )

            // Render the pointer image with zoom and pan transformations
            Image(
                painter = painterResource(id = R.drawable.pointer), // Replace with your pointer image
                contentDescription = "Pointer Image",
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offsetX.value + (currentNewMethodPosition.value.first * scale.value),
                        translationY = offsetY.value + (currentNewMethodPosition.value.second * scale.value)
                    )
                    .size(viewModel.pointerSize.dp) // Maintain consistent pointer size
            )
        }
    }
}