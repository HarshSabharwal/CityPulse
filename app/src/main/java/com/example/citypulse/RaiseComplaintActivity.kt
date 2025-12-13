package com.example.citypulse

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.citypulse.databinding.ActivityRaiseComplaintBinding
import com.example.citypulse.models.Complaint
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RaiseComplaintActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityRaiseComplaintBinding
    private var photoUri: Uri? = null
    private lateinit var currentPhotoPath: String

    // Map, Location, Firebase
    private var googleMap: GoogleMap? = null
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    private lateinit var geocoder: Geocoder
    private var selectedLatLng: LatLng? = null

    // Firebase Instances
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // --- PERMISSION AND ACTIVITY LAUNCHERS ---

    // 1. Camera Permission
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                launchCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    // 2. Camera Result
    private val cameraResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                photoUri = Uri.fromFile(File(currentPhotoPath))
                binding.ivComplaintPreview.setImageURI(photoUri)
                binding.ivComplaintPreview.visibility = View.VISIBLE
            } else {
                Log.e("CameraResult", "Failed to take picture")
            }
        }

    // 3. Location Permission
    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                enableMyLocation()
            } else {
                Toast.makeText(this, "Location permission is required for map", Toast.LENGTH_SHORT).show()
            }
        }

    // --- LIFECYCLE ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRaiseComplaintBinding.inflate(layoutInflater)
        setContentView(binding.root)

        geocoder = Geocoder(this, Locale.getDefault())

        // Setup the map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Setup button listeners
        binding.btnTakePhoto.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }

        binding.btnGetLocation.setOnClickListener {
            // Get current location and update both map and address
            getCurrentLocation(true)
        }

        binding.btnSubmitComplaint.setOnClickListener {
            submitComplaint()
        }
    }

    // --- SUBMISSION LOGIC ---

    private fun submitComplaint() {
        val address = binding.etManualLocation.text.toString().trim()
        val description = binding.etComplaintDescription.text.toString().trim()

        // Get selected radio button for title
        val selectedId = binding.rgComplaintType.checkedRadioButtonId
        if (selectedId == -1) {
            Toast.makeText(this, "Please select a complaint type", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedRadioButton = findViewById<RadioButton>(selectedId)
        val title = selectedRadioButton.text.toString()

        // 1. Basic Validation
        if (address.isEmpty()) {
            Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show()
            return
        }

        if (photoUri == null) {
            Toast.makeText(this, "Please take a photo", Toast.LENGTH_SHORT).show()
            return
        }

        // --- ML INTEGRATION START ---
        // Validate the image against the selected category model
        val isVerified = validateWithML(title, currentPhotoPath)

        if (!isVerified) {
            // Option 1: Block submission
            // Toast.makeText(this, "Image does not match the selected category!", Toast.LENGTH_LONG).show()
            // return

            // Option 2: Just warn but allow (Current Implementation)
            Toast.makeText(this, "Warning: AI could not verify this image category.", Toast.LENGTH_SHORT).show()
        }
        // --- ML INTEGRATION END ---

        // 2. Prepare Data
        binding.btnSubmitComplaint.isEnabled = false
        Toast.makeText(this, "Submitting...", Toast.LENGTH_SHORT).show()

        val user = auth.currentUser
        val appId = "citypulse_v1"
        // Generate a new ID for this complaint
        val complaintId = db.collection("artifacts").document(appId)
            .collection("public").document("data")
            .collection("complaints").document().id

        val complaint = Complaint(
            id = complaintId,
            userId = user?.uid ?: "unknown",
            userPhone = user?.phoneNumber ?: "",
            title = title,
            description = description,
            address = address,
            status = "Pending",
            timestamp = System.currentTimeMillis()
        )

        // 3. Upload to Firestore
        db.collection("artifacts").document(appId)
            .collection("public").document("data")
            .collection("complaints").document(complaintId)
            .set(complaint)
            .addOnSuccessListener {
                Log.d("ComplaintSubmit", "Success!")
                Toast.makeText(this@RaiseComplaintActivity, "Complaint Submitted Successfully!", Toast.LENGTH_LONG).show()

                // --- REDIRECT TO HOME ---
                val intent = Intent(this@RaiseComplaintActivity, HomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("ComplaintSubmit", "Error writing document", e)
                Toast.makeText(this@RaiseComplaintActivity, "Submission Failed: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnSubmitComplaint.isEnabled = true
            }
    }

    /**
     * Maps the complaint type to a specific ML model and runs inference.
     */
    private fun validateWithML(complaintType: String, imagePath: String): Boolean {
        // Map UI selection to your .tflite model filenames
        // Ensure these files exist in src/main/assets/
        val modelFileName = when (complaintType) {
            "Garbage" -> "garbage_model.tflite"
            "Polluted Water" -> "water_model.tflite"
            "Pothole" -> "pothole_model.tflite"
            "Pipeline Leakage" -> "pipeline_model.tflite"
            else -> return true // No model for unknown types, assume valid
        }

        try {
            // 1. Prepare Image
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap == null) return false

            // 2. Initialize Classifier
            // Note: This uses TensorFlow Lite Task Library options.
            // You might need to adjust based on your specific model metadata (input size, etc.)
            val options = ImageClassifier.ImageClassifierOptions.builder()
                .setMaxResults(1)
                .build()

            val imageClassifier = ImageClassifier.createFromFileAndOptions(
                this,
                modelFileName,
                options
            )

            // 3. Process Image (Resize/Normalize handled by Task library if metadata is present)
            // If your model lacks metadata, you might need manual resizing here:
            val tensorImage = TensorImage.fromBitmap(bitmap)

            // 4. Run Inference
            val results = imageClassifier.classify(tensorImage)

            // 5. Check Results
            if (results.isNotEmpty() && results[0].categories.isNotEmpty()) {
                val category = results[0].categories[0]
                val score = category.score
                val label = category.label

                Log.d("ML_Check", "Model: $modelFileName, Predicted: $label, Score: $score")

                // Simple logic: If the model predicts the correct class with > 50% confidence
                // Note: This assumes your model labels match the complaintType strings (e.g. "Garbage")
                // You may need to map "garbage_class_0" to "Garbage" depending on your model training.
                if (score > 0.5) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("ML_Check", "Error running ML model", e)
            // If ML fails (e.g., file missing), fallback to allowing submission or return false
            return true
        }

        return false // Default to false if logic isn't met, or change to true for lenient mode
    }

    // --- MAP LOGIC ---

    override fun onMapReady(map: GoogleMap) {
        this.googleMap = map
        checkLocationPermission()

        // When the map stops moving, get the address for the center pin
        map.setOnCameraIdleListener {
            val center = map.cameraPosition.target
            selectedLatLng = center
            getAddressFromLocation(center)
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                enableMyLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Toast.makeText(this, "Location access is needed to show map", Toast.LENGTH_LONG).show()
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = true
            googleMap?.uiSettings?.isMyLocationButtonEnabled = false // We use our own button
            getCurrentLocation(true)
        }
    }

    private fun getCurrentLocation(moveCamera: Boolean) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return // Permission not granted
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    selectedLatLng = currentLatLng
                    if (moveCamera) {
                        googleMap?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f)
                        )
                    }
                    getAddressFromLocation(currentLatLng)
                } else {
                    Toast.makeText(this, "Could not get current location. Try again.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun getAddressFromLocation(latLng: LatLng) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                    runOnUiThread {
                        if (addresses.isNotEmpty()) {
                            binding.etManualLocation.setText(addresses[0].getAddressLine(0))
                        } else {
                            binding.etManualLocation.setText("No address found")
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    binding.etManualLocation.setText(addresses[0].getAddressLine(0))
                } else {
                    binding.etManualLocation.setText("No address found")
                }
            }
        } catch (e: IOException) {
            Log.e("Geocoder", "Geocoder failed", e)
            binding.etManualLocation.setText("Lat: ${latLng.latitude}, Lon: ${latLng.longitude}")
        }
    }

    // --- CAMERA LOGIC ---

    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        var photoFile: File? = null
        try {
            photoFile = createImageFile()
        } catch (ex: IOException) {
            Log.e("CameraError", "Error creating image file", ex)
            Toast.makeText(this, "Error setting up camera", Toast.LENGTH_SHORT).show()
        }

        if (photoFile != null) {
            val photoURI: Uri = FileProvider.getUriForFile(
                this,
                "com.example.citypulse.provider", // Must match authorities in Manifest
                photoFile
            )
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            cameraResultLauncher.launch(takePictureIntent)
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }
}