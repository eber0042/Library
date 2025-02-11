package com.temi.oh2024

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Environment
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.robotemi.sdk.Robot
import com.robotemi.sdk.SttLanguage
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener
import com.robotemi.sdk.listeners.OnDetectionDataChangedListener
import com.robotemi.sdk.listeners.OnMovementStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotLiftedListener
import com.robotemi.sdk.listeners.OnRobotDragStateChangedListener
import com.robotemi.sdk.listeners.OnTtsVisualizerWaveFormDataChangedListener
import com.robotemi.sdk.listeners.OnConversationStatusChangedListener
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.constants.CliffSensorMode
import com.robotemi.sdk.constants.Gender
import com.robotemi.sdk.constants.HardButton
import com.robotemi.sdk.constants.SensitivityLevel
import com.robotemi.sdk.listeners.OnBeWithMeStatusChangedListener
import com.robotemi.sdk.map.MapDataModel
import com.robotemi.sdk.model.DetectionData
import com.robotemi.sdk.navigation.model.Position
import com.robotemi.sdk.navigation.model.SpeedLevel
import com.robotemi.sdk.permission.Permission
import com.robotemi.sdk.voice.model.TtsVoice
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Singleton

data class TtsStatus(val status: TtsRequest.Status)
enum class DetectionStateChangedStatus(val state: Int) { // Why is it like this?
    DETECTED(state = 2),
    LOST(state = 1),
    IDLE(state = 0);

    companion object {
        fun fromState(state: Int): DetectionStateChangedStatus? = entries.find { it.state == state }
    }
}
data class DetectionDataChangedStatus( val angle: Double, val distance: Double)
enum class MovementType {
    SKID_JOY,
    TURN_BY,
    NONE
}
enum class MovementStatus {
    START,
    GOING,
    OBSTACLE_DETECTED,
    NODE_INACTIVE,
    CALCULATING,
    COMPLETE,
    ABORT
}
data class MovementStatusChangedStatus(
    val type: MovementType,   // Use the MovementType enum
    val status: MovementStatus  // Use the MovementStatus enum
)
data class Dragged(
    val state: Boolean
)
data class Lifted(
    val state: Boolean
)
data class AskResult(val result: String, val id: Long = System.currentTimeMillis())
enum class Language(val value: Int) {
    SYSTEM(0),
    EN_US(1),
    ZH_CN(2),
    ZH_HK(3),
    ZH_TW(4),
    TH_TH(5),
    HE_IL(6),
    KO_KR(7),
    JA_JP(8),
    IN_ID(9),
    ID_ID(10),
    DE_DE(11),
    FR_FR(12),
    FR_CA(13),
    PT_BR(14),
    AR_EG(15),
    AR_AE(16),
    AR_XA(17),
    RU_RU(18),
    IT_IT(19),
    PL_PL(20),
    ES_ES(21),
    CA_ES(22),
    HI_IN(23),
    ET_EE(24),
    TR_TR(25),
    EN_IN(26),
    MS_MY(27),
    VI_VN(28),
    EL_GR(29);

    companion object {
        fun fromLanguage(value: Int): Language? = Language.entries.find { it.value == value }
    }
}
data class WakeUp(
    val result: String
)
data class WaveForm(
    val result: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WaveForm

        return result.contentEquals(other.result)
    }

    override fun hashCode(): Int {
        return result.contentHashCode()
    }
}
data class ConversationStatus (
    val status: Int,
    val text: String
)
data class ConversationAttached (
    val isAttached: Boolean
)
enum class LocationState(val value:String) {
    START(value = "start"),
    CALCULATING(value = "calculating"),
    GOING(value = "going"),
    COMPLETE(value = "complete"),
    ABORT(value = "abort"),
    REPOSING(value = "reposing");

    companion object {
        fun fromLocationState(value: String): LocationState? = LocationState.entries.find { it.value == value }
    }
}
enum class BeWithMeState(val value:String) {
    ABORT(value = "abort"),
    CALCULATING(value = "calculating"),
    SEARCH(value = "search"),
    START(value = "start"),
    TRACK(value = "track"),
    OBSTACLE_DETECTED(value = "obstacle detected");

    companion object {
        fun fromBeWithMeState(value: String): BeWithMeState? = BeWithMeState.entries.find { it.value == value }
    }
}


@Module
@InstallIn(SingletonComponent::class)
object RobotModule {
    @Provides
    @Singleton
    fun provideRobotController() = RobotController()
}

class RobotController():
    OnRobotReadyListener,
    OnDetectionStateChangedListener,
    Robot.TtsListener,
    OnDetectionDataChangedListener,
    OnMovementStatusChangedListener,
    OnRobotLiftedListener,
    OnRobotDragStateChangedListener,
    Robot.AsrListener,
    Robot.WakeupWordListener,
    OnTtsVisualizerWaveFormDataChangedListener,
    OnConversationStatusChangedListener,
    Robot.ConversationViewAttachesListener,
    OnGoToLocationStatusChangedListener,
    OnBeWithMeStatusChangedListener
{
    private val robot = Robot.getInstance() //This is needed to reference the data coming from Temi

    // Setting up the Stateflows here
    private val _ttsStatus = MutableStateFlow( TtsStatus(status = TtsRequest.Status.PENDING) )
    val ttsStatus = _ttsStatus.asStateFlow()

    private val _detectionStateChangedStatus = MutableStateFlow(DetectionStateChangedStatus.IDLE)
    val detectionStateChangedStatus = _detectionStateChangedStatus.asStateFlow()

    private val _detectionDataChangedStatus = MutableStateFlow(DetectionDataChangedStatus(angle = 0.0, distance = 0.0))
    val detectionDataChangedStatus = _detectionDataChangedStatus.asStateFlow() // This can include talking state as well

    private val _movementStatusChangedStatus = MutableStateFlow(
        MovementStatusChangedStatus(
            MovementType.NONE, MovementStatus.NODE_INACTIVE
        )
    )
    val movementStatusChangedStatus = _movementStatusChangedStatus.asStateFlow() // This can include talking state as well

    private val _dragged = MutableStateFlow(Dragged(false))
    val dragged = _dragged.asStateFlow() // This can include talking state as well

    private val _lifted = MutableStateFlow(Lifted(false))
    val lifted = _lifted.asStateFlow() // This can include talking state as well

    private val _askResult = MutableStateFlow(AskResult("hzdghasdfhjasdfb"))
    val askResult = _askResult.asStateFlow()

    private val _language = MutableStateFlow(Language.SYSTEM)
    val language = _language.asStateFlow()

    private val _wakeUp = MutableStateFlow(WakeUp("56"))
    val wakeUp = _wakeUp.asStateFlow()

    private val _waveform = MutableStateFlow(WaveForm(byteArrayOf(0)))
    val waveform = _waveform.asStateFlow()

    private val _conversationStatus = MutableStateFlow(ConversationStatus(status = 0, text = "56"))
    val conversationStatus = _conversationStatus.asStateFlow()

    private val _conversationAttached = MutableStateFlow(ConversationAttached(false))
    val conversationAttached = _conversationAttached.asStateFlow()

    private val _locationState = MutableStateFlow(LocationState.ABORT)
    val locationState = _locationState.asStateFlow()

    private val _beWithMeStatus = MutableStateFlow(BeWithMeState.ABORT)
    val beWithMeState = _beWithMeStatus.asStateFlow()

    //************** map data
    private fun createPixelImageFromListAndroid(pixelValues: List<Int>, width: Int): Bitmap {
        // Calculate height from the list size and given width
        val height = (pixelValues.size + width - 1) / width

        // Create a Bitmap with the appropriate size
        val pixelSize = 1f // You can adjust this as needed

        val requiredMemory =
            (width * height * pixelSize * pixelSize * 4) // ARGB_8888 (4 bytes per pixel)

        Log.i("TESTING6", "Required Memory: ${requiredMemory}")

        val bitmap = Bitmap.createBitmap(
            width * pixelSize.toInt(),
            height * pixelSize.toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = false // Disable anti-aliasing for pixelated effect
            style = Paint.Style.FILL // Fill each "pixel" with color
        }

        // Fill the bitmap with square pixels
        for (i in pixelValues.indices) {
            val x = (i % width) * pixelSize // X position of the square
            val y = (i / width) * pixelSize // Y position of the square

            // Set the color based on the pixel value
            val color = when (pixelValues[i]) {
                -1 -> android.graphics.Color.WHITE  // Empty Space
                0 -> android.graphics.Color.LTGRAY   // Floor
                100 -> android.graphics.Color.BLACK   // Walls
                70 -> android.graphics.Color.DKGRAY   // Obstacles
                else -> android.graphics.Color.RED  // Other values are red
            }

            // Set the paint color for the square
            paint.color = color

            // Draw a square pixel
            canvas.drawRect(x, y, x + pixelSize, y + pixelSize, paint)
        }

        return bitmap
    }

    // Stuff down here is used to render the map
//    private val bitMap = createPixelImageFromListAndroid(
//        getMapData()?.mapImage!!.data,
//        getMapData()?.mapImage!!.cols
//    )
//    val renderedMap: ImageBitmap = bitMap.asImageBitmap()

    var mapScale = 1f
    val pointerSize = 30

    private val divisor = 20

    val homeBaseCoordinates = getMapData()?.locations?.find { it.layerId == "home base" }?.layerPoses

    val realPointOne =
        Pair(getMapData()?.mapInfo?.originX ?: 0.0f, getMapData()?.mapInfo?.originY
            ?: 0.0f)

    val realPointTwo = Pair(
        getMapData()?.mapInfo?.originX?.plus(getMapData()?.mapInfo?.width!! / divisor)
            ?: 0.0f,
        getMapData()?.mapInfo?.originY?.plus(getMapData()?.mapInfo?.height!! / divisor)
            ?: 0.0f
    )

    // Use this if you have to render a map
//    val xOffset = (renderedMap.width.toFloat() / 4)
//    val yOffset = (renderedMap.height.toFloat() / 4) + (pointerSize)

    val xOffset = (getMapData()?.mapImage!!.rows.toFloat() / 4)
    val yOffset = (getMapData()?.mapImage!!.cols.toFloat() / 4) + (pointerSize)

    val mapPointOne = Pair(-xOffset, -yOffset)
    val mapPointTwo = Pair(+xOffset, +yOffset)

    data class Location(
        val id: String,
        val index: Int,  // index of the location in another list
        val x: Float,    // x-coordinate of the location
        val y: Float     // y-coordinate of the location
    )

    // Use the robotController to get the map data
    private val locations = robot.getMapData()?.locations

    // Create a list to store location triplets
    private val locationTriplets = mutableListOf<Location>()

    // Function to populate the triplet list with the location data
    fun populateLocationTriplets() {
        // Add the ID tags here from the Temi center map to add new locations
        val locationIDs: MutableList<String> = mutableListOf("lifestyle collection", "management collection r", "life sciences collectionr", "design collection r", "info services", "self check machines", "library portal pcs", "cafe", "smart kiosk", "smart learning hub r", "exhibition space", "art gallery", "learn for life pod")


//        val locationIDs: MutableList<String> = mutableListOf("entrance","smart learning hub", "exhibition space", "art gallery", "learn for life pod")


        for (locationID in locationIDs) {
            // Find the index of the location by matching the layerId
            val index = locations?.indexOfFirst { it.layerId == locationID }

            // Check if the location exists
            if (index != null && index >= 0) {
                // Get the location from the list
                val location = locations?.get(index)

                // Extract the coordinates, or use 0f if they are missing
                val x = location?.layerPoses?.getOrNull(0)?.x ?: 0f
                val y = location?.layerPoses?.getOrNull(0)?.y ?: 0f

                // Add the location triplet to the list
                locationTriplets.add(Location(locationID, index, x, y))
            }
        }
    }

    init{ // This will populate the locations at the start of application
        populateLocationTriplets()
        // Log.i("Location Data", "${locationTriplets[0]}")
    }

    // Function to return the coordinates as a list of pairs
    fun coordinatesDataForLocations(): List<Triple<String, Float, Float>> {
        return locationTriplets.map { Triple(it.id, it.x, it.y) }
    }

    val mapPointOneReal = Pair(-xOffset + (pointerSize), -(yOffset - (pointerSize)))
    val mapPointTwoReal = Pair(xOffset + (pointerSize), (yOffset - (pointerSize)))
    val dynamicCoordinateConvert: (Float, Float) -> Pair<Float, Float> = { a, b ->
        convertCoordinates(
            a,
            b,
            realPointOne,
            realPointTwo,
            mapPointOneReal,
            mapPointTwoReal
        )
    }

    private fun convertCoordinates(
        realX: Float,
        realY: Float,
        realPoint1: Pair<Float?, Float?>,
        realPoint2: Pair<Float?, Float?>,
        mapPoint1: Pair<Float, Float>,
        mapPoint2: Pair<Float, Float>,
    ): Pair<Float, Float> {

        val secondPointReal = Pair(
            realPoint2.first?.minus(realPoint1.first!!) ?: 0f,
            realPoint2.second?.minus(realPoint1.second!!) ?: 0f
        )
        val secondPointMap = Pair(
            mapPoint2.first - mapPoint1.first,
            mapPoint2.second - mapPoint1.second
        )

        val scaleX =
            if (secondPointReal.first != 0f) secondPointMap.first / secondPointReal.first else 1f
        val scaleY =
            if (secondPointReal.second != 0f) secondPointMap.second / secondPointReal.second else 1f

        Log.i("Testing2", "$scaleX, $scaleY")

        val offsetX = mapPoint1.first
        val offsetY = mapPoint1.second

        val mappedX = offsetX + (realX - (realPoint1.first!!)) * scaleX
        val mappedY = offsetY + (realY - (realPoint1.second!!)) * scaleY

        return Pair(mappedX, mappedY)
    }


    //************* map data END

    init {
        robot.addOnRobotReadyListener(this)
        robot.addTtsListener(this)
        robot.addOnDetectionStateChangedListener((this))
        robot.addOnDetectionDataChangedListener(this)
        robot.addOnMovementStatusChangedListener(this)
        robot.addOnRobotLiftedListener(this)
        robot.addOnRobotDragStateChangedListener(this)
        robot.addAsrListener(this)
        robot.addWakeupWordListener(this)
        robot.addOnTtsVisualizerWaveFormDataChangedListener(this)
        robot.addOnConversationStatusChangedListener(this)
        robot.addConversationViewAttachesListener(this)
        robot.addOnGoToLocationStatusChangedListener(this)
        robot.addOnBeWithMeStatusChangedListener(this)
        robot.addOnBeWithMeStatusChangedListener(this)
    }
    //********************************* General Functions
    suspend fun speak(speech: String, buffer: Long, haveFace: Boolean = true) {
        delay(buffer)
        val request = TtsRequest.create(
            speech = speech,
            isShowOnConversationLayer = false,
            showAnimationOnly = haveFace,
            language = TtsRequest.Language.EN_US
        )

        // Need to create TtsRequest
        robot.speak(request)
        delay(buffer)
    }

    suspend fun turnBy(degree: Int, speed: Float = 1f, buffer: Long) {
        delay(buffer)
        robot.turnBy(degree, speed)
        delay(buffer)
    }

    suspend fun tiltAngle(degree: Int, speed: Float = 1f, buffer: Long) {
        delay(buffer)
        robot.tiltAngle(degree, speed)
        delay(buffer)
    }

    fun listOfLocations() {
        Log.i("INFO!", robot.locations.toString())
        Log.i("HOPE!", robot.wakeupWord)
        Log.i("HOPE!", robot.wakeupWordDisabled.toString())
    }

    fun goTo(location: String, backwards: Boolean = false) {
//        robot.tiltAngle(20)
        robot.goTo(location, noBypass = false, backwards = backwards)
//        robot.tiltAngle(20)
    }

    fun setGoToSpeed(speedLevel: SpeedLevel) {
        robot.goToSpeed = speedLevel
    }

    fun goToPosition(position: Position) {
        robot.goToPosition(position)
    }

    suspend fun skidJoy(x: Float, y: Float) {
        robot.skidJoy(x, y)
        delay(500)
    }

    fun askQuestion(question: String) {
        robot.askQuestion(question)
    }

    fun wakeUp() {
        robot.wakeup(listOf(SttLanguage.SYSTEM))
    }

    fun finishConversation() {
        robot.finishConversation()
    }

    fun getPosition(): Position {
        Log.i("Robot Position Data", "${robot.getPosition()}")
        return robot.getPosition()
    }

    fun getMapData(): MapDataModel? {
        return robot.getMapData()
    }

    // Move these outside the function to maintain state across calls
    private val numberArray = (1..5).toMutableList()
    private var currentIndex = 0
    private var previousLastChoice = -1

    suspend fun textModelChoice(state: Int, buffer: Long) {
        // Function to get the next random number in the shuffled array
        fun getRandomChoice(): Int {
            if (currentIndex >= numberArray.size) {
                numberArray.shuffle()  // Reshuffle when the array is exhausted
                currentIndex = 0

                // Ensure the first choice isn't the same as the last choice from the previous array
                if (numberArray[0] == previousLastChoice) {
                    // Find a random index to swap with the first element
                    val swapIndex = (1 until numberArray.size).random()  // Get a random index (1..4)
                    val temp = numberArray[0]
                    numberArray[0] = numberArray[swapIndex]
                    numberArray[swapIndex] = temp
                }
            }

            val choice = numberArray[currentIndex]  // Get the current choice
            currentIndex++  // Move to the next index
            previousLastChoice = choice  // Update the last choice to the current choice

            return choice
        }

        val choice = getRandomChoice()  // Get a randomized choice

        when (state) {
            0 -> { // All answers correct
                Log.d("Quiz", "Perfect")
                when (choice) {
                    1 -> speak(speech = "Oh, you got it right? You want a medal or something?", buffer)
                    2 -> speak(speech = "Congratulations! You must be so proud... of answering a quiz question.", buffer)
                    3 -> speak(speech = "Wow, you did it! Now go do something actually challenging.", buffer)
                    4 -> speak(speech = "You got it right, big deal. Let’s not get carried away.", buffer)
                    5 -> speak(speech = "Perfect score, huh? Enjoy your moment of glory, it’s not lasting long.", buffer)
                }
            }

            1 -> { // Partially correct
                Log.d("Quiz", "Partial")
                when (choice) {
                    1 -> speak(speech = "Almost there... but not quite. Story of your life, huh?", buffer)
                    2 -> speak(speech = "Half right? So close, yet so far. Keep trying, maybe you'll get it one day.", buffer)
                    3 -> speak(speech = "Some of it was right, but seriously, you can do better than that.", buffer)
                    4 -> speak(speech = "You're halfway there! But no, that doesn't count as winning.", buffer)
                    5 -> speak(speech = "Partial credit? I mean, do you want a participation trophy or what?", buffer)
                }
            }

            2 -> { // All answers wrong
                Log.d("Quiz", "Incorrect")
                when (choice) {
                    1 -> speak(speech = "Wow. How did you manage to get that wrong? Even my dog knows that one.", buffer)
                    2 -> speak(speech = "Not a single answer right? Impressive... in all the wrong ways.", buffer)
                    3 -> speak(speech = "Oh, you really went for zero, huh? Bold strategy. Let’s see how it works out.", buffer)
                    4 -> speak(speech = "All wrong? I didn’t even think that was possible with how easy these questions are. And yet, here we are.", buffer)
                    5 -> speak(speech = "You do realize that you are meant to select the correct answers, right?", buffer)
                }
            }
        }
    }

    fun detectionMode(detectionOn: Boolean) {
        robot.setDetectionModeOn(detectionOn, 2f)
        Log.i("TESTING9", "${robot.detectionModeOn}")
    }

    fun togglePowerButton(toggleOn: Boolean) {
        if (toggleOn) {
            robot.setHardButtonMode(HardButton.POWER, HardButton.Mode.DISABLED)
        } else {
            robot.setHardButtonMode(HardButton.POWER, HardButton.Mode.ENABLED)
        }
    }

    //********************************* General Data
    fun getPositionYaw(): Float
    {
        return robot.getPosition().yaw
    }

    fun volumeControl (volume: Int) {
        robot.volume = volume
    }

    fun setMainButtonMode(isEnabled: Boolean) {
        if (isEnabled){
            robot.setHardButtonMode(HardButton.MAIN, HardButton.Mode.ENABLED)
        } else {
            robot.setHardButtonMode(HardButton.MAIN, HardButton.Mode.DISABLED)
        }
    }

    fun setCliffSensorOn(sensorOn: Boolean) {
        robot.groundDepthCliffDetectionEnabled = sensorOn
        robot.frontTOFEnabled = sensorOn
        robot.backTOFEnabled = sensorOn
        if (sensorOn) {
            robot.cliffSensorMode = CliffSensorMode.HIGH_SENSITIVITY
            robot.headDepthSensitivity = SensitivityLevel.HIGH
        } else {
            robot.cliffSensorMode = CliffSensorMode.OFF
            robot.headDepthSensitivity = SensitivityLevel.LOW
        }
    }

    fun stopMovement() {
        robot.stopMovement()
    }

    fun tileAngle(degree: Int) {
        robot.tiltAngle(degree)
    }

    fun constrainBeWith() {
        robot.constraintBeWith()
    }

    fun getBatteryLevel(): Int {
        // if you get -1 that means there has been an issue
        return robot.batteryData?.level ?: -1
    }
    //********************************* Override is below
    /**
     * Called when connection with robot was established.
     *
     * @param isReady `true` when connection is open. `false` otherwise.
     */
    override fun onRobotReady(isReady: Boolean) {

        if (!isReady) return

        // robot.cliffSensorMode = CliffSensorMode.HIGH_SENSITIVITY
        setCliffSensorOn(true)

        Log.i("DEBUG!", "Cliff Enabled " + robot.groundDepthCliffDetectionEnabled.toString())
        Log.i("DEBUG!", "Cliff Enabled " + robot.cliffSensorMode.toString())

        robot.setTtsVoice(ttsVoice = TtsVoice(Gender.FEMALE, 1.1F, 4))
        robot.setDetectionModeOn(on = true, distance = 2.0f) // Set how far it can detect stuff
//        robot.requestToBeKioskApp()
        robot.setKioskModeOn(on = true)
        robot.volume = 4 // set volume to 4

        robot.setHardButtonMode(HardButton.VOLUME, HardButton.Mode.DISABLED)
        robot.setHardButtonMode(HardButton.MAIN, HardButton.Mode.DISABLED)
        robot.setHardButtonMode(HardButton.POWER, HardButton.Mode.ENABLED)
        robot.hideTopBar()

//        robot.setHardButtonMode(HardButton.VOLUME, HardButton.Mode.ENABLED)
//        robot.setHardButtonMode(HardButton.MAIN, HardButton.Mode.ENABLED)
//        robot.setHardButtonMode(HardButton.POWER, HardButton.Mode.ENABLED)
//        robot.showTopBar()

        robot.setKioskModeOn(false)
        Log.i("HOPE!", " In kiosk: ${robot.isKioskModeOn().toString()}")
        Log.i("HOPE!", " Check permission setting: ${robot.checkSelfPermission(permission = Permission.SETTINGS)}")
        Log.i("HOPE!", " Battery Data: ${robot.batteryData}")
        Log.i("HOPE!", " Hard Buttons disabled: ${robot.isHardButtonsDisabled}")
        Log.i("HOPE!", " Power Buttons Disabled: ${robot.getHardButtonMode(HardButton.POWER)}")

    }

    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
//        Log.i("onTtsStatusChanged", "status: ${ttsRequest.status}")
        _ttsStatus.update {
            TtsStatus(status = ttsRequest.status)
        }
    }

    override fun onDetectionStateChanged(state: Int) {
        _detectionStateChangedStatus.update {
//            Log.d("DetectionState", "Detection state changed: ${DetectionStateChangedStatus.fromState(state)}")
            DetectionStateChangedStatus.fromState(state = state) ?: return@update it
        }
    }

    override fun onDetectionDataChanged(detectionData: DetectionData) {
        _detectionDataChangedStatus.update {
            DetectionDataChangedStatus(angle = detectionData.angle, distance = detectionData.distance)
        }
    }

    override fun onMovementStatusChanged(type: String, status: String) {
        _movementStatusChangedStatus.update { currentStatus ->
            // Convert the type and status to their respective enums
            val movementType = when (type) {
                "skidJoy" -> MovementType.SKID_JOY
                "turnBy" -> MovementType.TURN_BY
                else -> return@update currentStatus // If the type is unknown, return the current state
            }
            val movementStatus = when (status) {
                "start" -> MovementStatus.START
                "going" -> MovementStatus.GOING
                "obstacle detected" -> MovementStatus.OBSTACLE_DETECTED
                "node inactive" -> MovementStatus.NODE_INACTIVE
                "calculating" -> MovementStatus.CALCULATING
                "complete" -> MovementStatus.COMPLETE
                "abort" -> MovementStatus.ABORT
                else -> return@update currentStatus // If the status is unknown, return the current state
            }
            // Create a new MovementStatusChangedStatus from the enums
            MovementStatusChangedStatus(movementType, movementStatus)
        }
    }

    override fun onRobotLifted(isLifted: Boolean, reason: String) {
        _lifted.update {
            Lifted(isLifted)
        }
    }

    override fun onRobotDragStateChanged(isDragged: Boolean) {
        _dragged.update {
            Dragged(isDragged)
        }
    }

    override fun onAsrResult(asrResult: String, sttLanguage: SttLanguage) {
        _askResult.update {
            AskResult(asrResult)
        }

        _language.update {
            Language.fromLanguage(value = sttLanguage.value) ?: return@update it
        }

    }

    override fun onWakeupWord(wakeupWord: String, direction: Int) {
        _wakeUp.update {
            WakeUp(wakeupWord)
        }
    }

    override fun onTtsVisualizerWaveFormDataChanged(waveForm: ByteArray) {
        _waveform.update {
            WaveForm(waveForm)
        }
    }

    override fun onConversationStatusChanged(status: Int, text: String) {
        _conversationStatus.update {
            ConversationStatus(status, text)
        }
    }

    override fun onConversationAttaches(isAttached: Boolean) {
        _conversationAttached.update {
            ConversationAttached(isAttached)
        }
    }

    override fun onGoToLocationStatusChanged(
        location: String,
        status: String,
        descriptionId: Int,
        description: String
    ) {
        _locationState.update {
            LocationState.fromLocationState(value = status) ?: return@update it
        }
    }

    override fun onBeWithMeStatusChanged(status: String) {
        _beWithMeStatus.update {
            BeWithMeState.fromBeWithMeState(value = status) ?: return@update it
        }
    }
}