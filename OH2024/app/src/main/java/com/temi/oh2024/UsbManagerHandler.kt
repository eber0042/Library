package com.temi.oh2024

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

class UsbManagerHandler(private val context: Context) {

    companion object {
        const val REQUEST_CODE_USB = 1001
    }

    private var cols: Int = 0
    private var data: List<Int> = listOf()

    // Initialize USB manager
    fun initializeUsbManager(cols: Int, data: List<Int>) {
        this.cols = cols // Store the columns data
        this.data = data // Store the data

        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList

        if (deviceList.isNotEmpty()) {
            for ((_, device) in deviceList) {
                Log.i("USB! Device", "Found device: ${device.deviceName}")
                requestUsbPermission(device, usbManager)
            }
        } else {
            Log.i("USB! Device", "No USB devices found")
        }
    }

    private fun requestUsbPermission(device: UsbDevice, usbManager: UsbManager) {
        if (!usbManager.hasPermission(device)) {
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED),
                PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, permissionIntent)
        } else {
            Log.i("USB! Device", "Already have permission for device: ${device.deviceName}")
        }
    }

    fun listUsbDevices(): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList

        if (deviceList.isNotEmpty()) {
            for ((_, device) in deviceList) {
                Log.i("USB! Check", "Device: ${device.deviceName}")
            }
            return true
        } else {
            Log.i("USB! Check", "No USB devices detected")
            return false
        }
    }

    fun getMountedExternalStorage(): List<String> {
        val paths = mutableListOf<String>()
        val storageDirs = context.getExternalFilesDirs(null)

        storageDirs.forEach { file ->
            if (file != null && Environment.isExternalStorageRemovable(file)) {
                paths.add(file.absolutePath)
            }
        }

        Log.i("USB! Check", "Mounted paths: $paths")
        return paths
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun getMountedUsbPath(): String? {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val volumes = storageManager.storageVolumes
            for (volume in volumes) {
                if (volume.isRemovable && volume.state == Environment.MEDIA_MOUNTED) {
                    return volume.directory?.absolutePath ?: "Unknown Path"
                }
            }
        } else {
            // For older versions
            val externalDirs = context.getExternalFilesDirs(null)
            for (file in externalDirs) {
                if (Environment.isExternalStorageRemovable(file)) {
                    return file.absolutePath
                }
            }
        }
        return null
    }







    // Open directory picker and launch for result
    fun openDirectoryPicker(launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        launcher.launch(intent)
    }

    // Create a .txt file on the USB device
    fun createTextFileOnUsb(uri: Uri?, fileName: String, content: String) {
        uri?.let {
            try {
                val resolver = context.contentResolver
                val documentFile = DocumentFile.fromTreeUri(context, uri)

                documentFile?.let { dir ->
                    val newFile = dir.createFile("text/plain", fileName)
                    newFile?.let { file ->
                        val outputStream: OutputStream? = resolver.openOutputStream(file.uri)
                        outputStream?.let {
                            val writer = BufferedWriter(OutputStreamWriter(it))
                            writer.write(content)
                            writer.close()
                        } ?: run {
                            Log.e("USB Device", "Unable to open output stream for file.")
                        }
                    } ?: run {
                        Log.e("USB Device", "Unable to create file: $fileName")
                    }
                } ?: run {
                    Log.e("USB Device", "Unable to access USB directory.")
                }
            } catch (e: Exception) {
                Log.e("USB Device", "Error creating file: ${e.message}")
            }
        } ?: run {
            Log.e("USB Device", "URI is null, cannot create file.")
        }
    }

    // Method to open the USB device and claim an interface
    fun sendDataToUsb(
        cols: Int,
        data: List<Int>,
        device: UsbDevice,
        usbManager: UsbManager
    ): Boolean {
        // Open the device
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            Log.e("USB!", "Failed to open connection to device")
            return false
        }

        // Get the first interface (usually it’s interface 0, but this can vary)
        val interfaceCount = device.interfaceCount
        if (interfaceCount == 0) {
            Log.e("USB!", "No interfaces found on the device")
            connection.close()
            return false
        }

        val usbInterface = device.getInterface(0)  // Assuming the first interface is what we need
        val endpoint =
            usbInterface.getEndpoint(0)  // Assuming the first endpoint is the one we want (adjust as necessary)

        // Claim the interface
        if (!connection.claimInterface(usbInterface, true)) {
            Log.e("USB!", "Failed to claim interface")
            connection.close()
            return false
        }

        // Prepare data to be sent (convert cols and data to a byte array)
        val byteData = prepareData(cols, data)

        // Send the data over the USB connection (using Bulk transfer as an example)
        val bytesWritten =
            connection.bulkTransfer(endpoint, byteData, byteData.size, 5000)  // 5000ms timeout
        if (bytesWritten < 0) {
            Log.e("USB!", "Failed to send data")
            connection.releaseInterface(usbInterface)
            connection.close()
            return false
        }

        Log.i("USB!", "Data sent successfully: $bytesWritten bytes")

        // Release the interface and close the connection
        connection.releaseInterface(usbInterface)
        connection.close()

        return true
    }

    // Method to convert your data to a byte array
    private fun prepareData(cols: Int, data: List<Int>): ByteArray {
        // Example conversion: you can modify this logic based on your data format
        val byteArray = ByteArray(cols * 4)  // 4 bytes per integer (adjust as needed)
        for (i in 0 until cols) {
            val value = data.getOrElse(i) { 0 } // Default to 0 if data is shorter than columns
            // Example: converting integer to 4-byte array (big-endian)
            byteArray[i * 4] = (value shr 24).toByte()
            byteArray[i * 4 + 1] = (value shr 16).toByte()
            byteArray[i * 4 + 2] = (value shr 8).toByte()
            byteArray[i * 4 + 3] = value.toByte()
        }
        return byteArray
    }

}