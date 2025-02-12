package com.example.objectdetection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.example.objectdetection.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    private var imageUri:Uri?=null
    private val pickImageLauncher=registerForActivityResult(ActivityResultContracts.GetContent()){ uri->
        uri?.let {
            imageUri=it
            binding.ivPost.setImageURI(it)
            analyzeImageFromGallery()
        }
    }
    private val cameraLauncher=registerForActivityResult(ActivityResultContracts.TakePicture()){ success->
        if (success){
            imageUri?.let { uri->
                val bitmap=BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                binding.ivPost.setImageBitmap(bitmap)
                analyzeImageFromCamera(bitmap)
            }
        }
    }
    private lateinit var objectDetector:ObjectDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPickImage.setOnClickListener(){
            pickImageLauncher.launch("image/*")
        }
        binding.btnStartCamera.setOnClickListener(){
            var filename=createImageFile()
            filename?.also {
                imageUri=FileProvider.getUriForFile(this,"${applicationContext.packageName}.fileprovider",it)
                cameraLauncher.launch(imageUri)
            }
        }

        val options=ObjectDetectorOptions.Builder()
            .enableClassification()
            .enableMultipleObjects()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
        objectDetector=ObjectDetection.getClient(options)

        checkPermissions()
    }

    private fun analyzeImageFromGallery(){
        imageUri?.let {
            val inputStream = contentResolver.openInputStream(it)
            if (inputStream!=null){
                val originalBitmap=BitmapFactory.decodeStream(inputStream)
                val mutableBitmap=originalBitmap.copy(Bitmap.Config.ARGB_8888,true)
                val canvas=Canvas(mutableBitmap)
                val paint= Paint()
                paint.color=Color.RED
                paint.style=Paint.Style.STROKE
                paint.strokeWidth=5f

                val image=InputImage.fromFilePath(this,it)
                objectDetector.process(image)
                    .addOnSuccessListener { detectedObjects->
                        for (detectedObject in detectedObjects){
                            val bounds=detectedObject.boundingBox
                            canvas.drawRect(bounds,paint)

                            val labels=detectedObject.labels
                            for (label in labels){
                                Log.d("ObjectDetection", "Label: ${label.text}, Confidence: ${label.confidence}")
                            }
                        }
                        binding.ivBoundingBox.setImageBitmap(mutableBitmap)
                        binding.ivBoundingBox.visibility=View.VISIBLE
                    }
                    .addOnFailureListener { e ->
                        Log.e("ObjectDetection", "Error: ${e.message}")
                    }
            }
            else{
                Log.e("ObjectDetection", "Failed to open input stream for URI: $it")
            }
        }
    }

    private fun analyzeImageFromCamera(originalBitmap: Bitmap) {
        val fixedBitmap = fixOrientation(originalBitmap, imageUri!!)
        val mutableBitmap = fixedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint()
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f

        val image = InputImage.fromBitmap(fixedBitmap, 0)
        objectDetector.process(image)
            .addOnSuccessListener { detectedObjects ->
                for (detectedObject in detectedObjects) {
                    val bounds = detectedObject.boundingBox
                    canvas.drawRect(bounds, paint)

                    // Tampilkan label klasifikasi jika ada
                    val labels = detectedObject.labels
                    for (label in labels) {
                        Log.d("ObjectDetection", "Label: ${label.text}, Confidence: ${label.confidence}")
                    }
                }
                binding.ivBoundingBox.setImageBitmap(mutableBitmap)
                binding.ivBoundingBox.visibility = View.VISIBLE
            }
            .addOnFailureListener { e ->
                Log.e("ObjectDetection", "Error: ${e.message}")
            }
    }

    private fun fixOrientation(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val exif = inputStream?.let { ExifInterface(it) }
            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }

            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: IOException) {
            Log.e("ObjectDetection", "Failed to fix orientation: ${e.message}")
            bitmap
        }
    }

    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_$timeStamp"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return try {
            File.createTempFile(imageFileName, ".jpg", storageDir)
        } catch (e: Exception) {
            Log.e("ObjectDetection", "Failed to create image file: ${e.message}")
            null
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            val REQUEST_CAMERA_PERMISSION=1
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val REQUEST_CAMERA_PERMISSION=1
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d("ObjectDetection", "Permissions granted")
            } else {
                Log.e("ObjectDetection", "Permission denied")
            }
        }
    }

}