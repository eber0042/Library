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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
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
    DirectionsAndLocations,
    Tours
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

                viewModel.getMapData()?.mapImage?.let { usbStuff(it.cols, viewModel.getMapData()!!.mapImage.data) }

                Log.i("USB!", "${sampleCols}, ${sampleData}")
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
                        composable(route = Screen.DirectionsAndLocations.name) {
                            viewModel.setGreetMode(false)
                            viewModel.setSpeed(SpeedLevel.HIGH)
                            DirectionsAndLocationsScreen(
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
                navController.navigate(Screen.DirectionsAndLocations.name)
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

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun GeneralQuestionsScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    RefreshExit(viewModel, 100)

    // Flags
    val showImage by viewModel.showImageFlag.collectAsState()
    val showWaitSequence by viewModel.isChatGptThinking.collectAsState()

    // Add time out for the image
    val timeoutDuration = 5000L // Timeout duration (5 seconds)
    // Start a timeout event (timeoutDuration) to hide the image
    LaunchedEffect(showImage) {
        if (showImage) {
            delay(timeoutDuration) // Wait for timeout
            viewModel.setShowImageFlag(false) // Hide the image after timeout
        }
    }

    // Sample list of question types (replace with your actual data)
    // Note that if you do not want to use an image with a question, just leave it as null
    val questionTypes = listOf(
        Triple(
            "What is Temi?",
            "Temi is a personal robot designed for various assistive and entertainment tasks. This other sentence is here for testing a particular issues that way or may not occur.",
            R.drawable.sample_image
        ),
        Triple(
            "How do I use Temi?",
            "You can use Temi by giving voice commands, using the touchscreen, or the mobile app.",
            R.drawable.sample_image
        ),
        Triple(
            "What features does Temi have?",
            "Temi features include autonomous navigation, voice recognition, and video calling.",
            R.drawable.sample_image
        ),
        Triple(
            "How can Temi assist me?",
            "Temi can assist you with tasks like scheduling, navigation, and connecting to smart devices.",
            R.drawable.sample_image
        ),
        Triple(
            "What are Temi's limitations?",
            "Temi cannot perform heavy lifting or tasks that require physical dexterity.",
            R.drawable.sample_image
        ),
        Triple("SAMPLE", "Sample dialogue for future question 1.", null),
        Triple("SAMPLE", "Sample dialogue for future question 2.", null),
        Triple("SAMPLE", "Sample dialogue for future question 3.", null),
        Triple("SAMPLE", "Sample dialogue for future question 4.", null),
        Triple("SAMPLE", "Sample dialogue for future question 5.", null),
        Triple("SAMPLE", "Sample dialogue for future question 6.", null),
        Triple("SAMPLE", "Sample dialogue for future question 7.", null),
        Triple("SAMPLE", "Sample dialogue for future question 8.", null),
        Triple("SAMPLE", "Sample dialogue for future question 9.", null),
        Triple("SAMPLE", "Sample dialogue for future question 10.", null),
        Triple("SAMPLE", "Sample dialogue for future question 11.", null),
        Triple("SAMPLE", "Sample dialogue for future question 12.", null),
        Triple("SAMPLE", "Sample dialogue for future question 13.", null),
        Triple("SAMPLE", "Sample dialogue for future question 14.", null),
        Triple("SAMPLE", "Sample dialogue for future question 15.", null),
        Triple("SAMPLE", "Sample dialogue for future question 16.", null)
    )

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
                            viewModel.playSoundEffect(buttonSoundEffect_secondary)
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
            // "Ask Question" Button
            Button(
                onClick = {
                    viewModel.playSoundEffect(buttonSoundEffect_main)
                    viewModel.askQuestionUi("Just be yourself, keep responses short. If the user says Tammy or Timmy they mean Temi, there is an issue with the text to speech system. Just ignore the issue and treat it as if the said temi")
                },
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .padding(
                        bottom = 16.dp,
                        top = 70.dp
                    ) // Space below the button
                    .height(100.dp)
            ) {
                Text("Ask Question", fontSize = 48.sp)
            }

            // Scrollable List of Questions
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(questionTypes) { question ->
                    // Each question as a text item
                    Text(
                        text = question.first,
                        fontSize = 50.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .wrapContentWidth(Alignment.CenterHorizontally) // Center horizontally
                            .clickable {
                                viewModel.playSoundEffect(
                                    buttonSoundEffect_tertiary
                                )
                                viewModel.speakForUi(question.second, true)
                                if (question.third != null) viewModel.setShowImageFlag(
                                    true
                                )
                            },
                        color = Color.Black // Text color
                    )
                    // Optional divider between items
                    Divider(
                        color = androidx.compose.ui.graphics.Color.Black,
                        thickness = 1.dp
                    )
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
            Image(
                painter = painterResource(id = R.drawable.sample_image), // Replace with your image resource
                contentDescription = "Displayed Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f) // Adjust as needed
            )
        }
    }

    if (showWaitSequence) {
        Gif(gif_thinking)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint(
    "UnusedMaterial3ScaffoldPaddingParameter",
    "UseOfNonLambdaOffsetOverload"
)
@Composable
fun DirectionsAndLocationsScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    RefreshExit(viewModel, delay = 100)

    val showImage by viewModel.showImageFlag.collectAsState()

    var point by remember { mutableIntStateOf(1) }

    val position = when (point) {
        1 -> {
            val (x, y) = Pair(0.0f, 0.0f) // Coordinates for point 1
            Offset(x.toFloat(), y.toFloat())
        }

        2 -> {
            val (x, y) = Pair(
                0.0f,
                0.0f
            ) // Coordinates for point 1 // Coordinates for point 2
            Offset(x.toFloat(), y.toFloat())
        }

        3 -> {
            val (x, y) = Pair(
                0.0f,
                0.0f
            ) // Coordinates for point 1 // Coordinates for point 3
            Offset(x.toFloat(), y.toFloat())
        }

        4 -> {
            val (x, y) = Pair(
                0.0f,
                0.0f
            ) // Coordinates for point 1 // Coordinates for point 4
            Offset(x.toFloat(), y.toFloat())
        }

        else -> Offset(0f, 0f) // Default position
    }

    // -- Control UI elements --
    // Button
    val buttonWidthAndHeight = 400.dp
    val roundedCorners = 12.dp
    //Text
    val fontSize = 48.sp

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
                            viewModel.playSoundEffect(buttonSoundEffect_secondary)
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
                // Create the two rows of buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // First row of buttons (test 1, test 2)
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                viewModel.playSoundEffect(buttonSoundEffect_main)
                                // viewModel.goToPosition(1)
                                viewModel.setShowImageFlag(true)
                                point = 1
                                viewModel.queryLocation("test point 1")
                            },
                            modifier = Modifier
                                .width(buttonWidthAndHeight)
                                .height(buttonWidthAndHeight),
                            shape = RoundedCornerShape(roundedCorners) // Adjust corner radius
                        ) {
                            Text("Test 1", fontSize = fontSize)
                        }
                        Spacer(modifier = Modifier.width(16.dp)) // Add space between buttons
                        Button(
                            onClick = {
                                viewModel.playSoundEffect(buttonSoundEffect_main)
                                // viewModel.goToPosition(2)
                                viewModel.setShowImageFlag(true)
                                point = 2
                                viewModel.queryLocation("test point 2")
                            },
                            modifier = Modifier
                                .width(buttonWidthAndHeight)
                                .height(buttonWidthAndHeight),
                            shape = RoundedCornerShape(roundedCorners) // Adjust corner radius
                        ) {
                            Text("Test 2", fontSize = fontSize)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp)) // Add space between rows

                    // Second row of buttons (test 3, test 4)
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                viewModel.playSoundEffect(buttonSoundEffect_main)// viewModel.goToPosition(3)
                                viewModel.setShowImageFlag(true)
                                point = 3
                                viewModel.queryLocation("test point 3")
                            },
                            modifier = Modifier
                                .width(buttonWidthAndHeight)
                                .height(buttonWidthAndHeight),
                            shape = RoundedCornerShape(roundedCorners) // Adjust corner radius
                        ) {
                            Text("Test 3", fontSize = fontSize)
                        }
                        Spacer(modifier = Modifier.width(16.dp)) // Add space between buttons
                        Button(
                            onClick = {
                                viewModel.playSoundEffect(buttonSoundEffect_main)// viewModel.goToPosition(4)
                                viewModel.setShowImageFlag(true)
                                point = 4
                                viewModel.queryLocation("test point 4")
                            },
                            modifier = Modifier
                                .width(buttonWidthAndHeight)
                                .height(buttonWidthAndHeight),
                            shape = RoundedCornerShape(roundedCorners) // Adjust corner radius
                        ) {
                            Text("Test 4", fontSize = fontSize)
                        }
                    }
                }
            }
        }
    )

    if (showImage) {//showImage
        // State to hold the scale and offset
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
                        scale.value = (scale.value * zoom).coerceIn(
                            0.5f,
                            5f
                        ) // Restrict zoom range
                        offsetX.value += pan.x
                        offsetY.value += pan.y
                    }
                }
        ) {
            @Composable
            fun CreatePath(
                dotSize: Int,
                spaceBetweenDots: Int,
                pathPoints: List<Pair<Int, Int>>, // List of points defining the path
                currentPosition: Pair<Int, Int>,
                scale: androidx.compose.runtime.State<Float>, // Pass scale as a parameter
                offsetX: androidx.compose.runtime.State<Float>, // Pass offsetX as a parameter
                offsetY: androidx.compose.runtime.State<Float> // Pass offsetY as a parameter
            ) {
                // List to hold point data: number, distance, coordinates (pair), and steps
                val pointData: MutableList<Triple<Int, Int, Pair<Int, Int>>> =
                    mutableListOf()
                var numberOfPoints = 0
                var currentPoint: Pair<Int, Int>

                // Loop through all points except the last one
                for (i in 0 until pathPoints.size - 1) {
                    currentPoint = pathPoints[i]

                    fun stepRatio(
                        startPoint: Pair<Int, Int>,
                        finishPoint: Pair<Int, Int>,
                        spaceBetweenDots: Int
                    ): Pair<Int, Int> {
                        val xDifference = finishPoint.first - startPoint.first
                        val yDifference = finishPoint.second - startPoint.second

                        if (xDifference == 0 && yDifference == 0) {
                            return Pair(0, 0) // No movement needed
                        } else if (xDifference == 0) {
                            return Pair(
                                0,
                                spaceBetweenDots * if (yDifference > 0) 1 else -1
                            )
                        } else if (yDifference == 0) {
                            return Pair(
                                spaceBetweenDots * if (xDifference > 0) 1 else -1,
                                0
                            )
                        }

                        val angleInRadians =
                            atan2(yDifference.toFloat(), xDifference.toFloat())
                        val x = spaceBetweenDots * cos(angleInRadians)
                        val y = spaceBetweenDots * sin(angleInRadians)

                        return Pair(x.roundToInt(), y.roundToInt())
                    }

                    // Calculate the total length of the line
                    val totalDistance = sqrt(
                        ((pathPoints[i + 1].first - pathPoints[i].first).toDouble()
                            .pow(2)) +
                                ((pathPoints[i + 1].second - pathPoints[i].second).toDouble()
                                    .pow(2))
                    )

                    // Get step increments
                    val steps = stepRatio(
                        pathPoints[i],
                        pathPoints[i + 1],
                        spaceBetweenDots
                    )
                    var cumulativeDistance = 0.0

                    // Loop through and add points until total distance is covered
                    while (cumulativeDistance < totalDistance) {
                        val xDifference =
                            (currentPoint.first - currentPosition.first).toDouble()
                        val yDifference =
                            (currentPoint.second - currentPosition.second).toDouble()
                        val distance =
                            sqrt(xDifference.pow(2) + yDifference.pow(2))
                        pointData.add(
                            Triple(
                                numberOfPoints++,
                                distance.roundToInt(),
                                Pair(currentPoint.first, currentPoint.second)
                            )
                        )

                        // Update current point
                        currentPoint = Pair(
                            currentPoint.first + steps.first,
                            currentPoint.second + steps.second
                        )

                        // Update cumulative distance
                        cumulativeDistance = sqrt(
                            ((currentPoint.first - pathPoints[i].first).toDouble()
                                .pow(2)) +
                                    ((currentPoint.second - pathPoints[i].second).toDouble()
                                        .pow(2))
                        )
                    }
                }

                // Find the index of the point with the smallest distance
                val minDistanceIndex =
                    pointData.indices.minByOrNull { pointData[it].second } ?: 0
                currentPoint = pathPoints[0]

                // Render the dots starting from the point with the smallest distance
                for (i in minDistanceIndex until numberOfPoints) {
                    val (x, y) = pointData[i].third // Destructure the Pair
                    Box(
                        modifier = Modifier
                            .size(dotSize.dp)
                            .graphicsLayer(
                                scaleX = scale.value,
                                scaleY = scale.value,
                                translationX = offsetX.value,
                                translationY = offsetY.value
                            )
                            .offset(x = x.dp, y = y.dp)
                            .background(Color.Red, shape = CircleShape)

                    )
                }
            }

//            // Render the main image with zoom and pan applied

            Image(
                painter = painterResource(R.drawable.sample_image), //BitmapPainter(viewModel.renderedMap), // Replace with your image resource
                contentDescription = "Displayed Image",
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offsetX.value,
                        translationY = offsetY.value
                    )
                    .aspectRatio(viewModel.mapScale) // Adjust as needed
            )

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

            // Pointer image remains fixed (not scaled or moved)
            Image(
                painter = painterResource(id = R.drawable.pointer), // Replace with your image resource
                contentDescription = "Pointer Image",
                modifier = Modifier
                    .size(viewModel.pointerSize.dp)
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offsetX.value,
                        translationY = offsetY.value
                    )
                    .offset(
                        x = currentNewMethodPosition.value.first.dp,
                        y = currentNewMethodPosition.value.second.dp
                    )
            )
        }
    }
}