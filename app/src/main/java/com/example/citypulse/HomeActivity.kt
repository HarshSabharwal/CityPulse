package com.example.citypulse

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.citypulse.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        // Check if user is logged in
        if (user == null) {
            // User is not logged in, go back to AuthActivity
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return // Stop further execution in this method
        }

        // Display the user's phone number
        binding.tvUserPhone.text = user.phoneNumber ?: "Welcome!"

        // Set up Raise Complaint button
        binding.cardRaiseComplaint.setOnClickListener {
            val intent = Intent(this, RaiseComplaintActivity::class.java)
            startActivity(intent)
        }

        // Set up Complaint Status button
        binding.cardComplaintStatus.setOnClickListener {
            val intent = Intent(this, ComplaintStatusActivity::class.java)
            startActivity(intent)
        }

        // Set up the logout button
        binding.btnLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "Logged Out", Toast.LENGTH_SHORT).show()
            // Go back to AuthActivity
            val intent = Intent(this, AuthActivity::class.java)
            startActivity(intent)
            finish() // Finish HomeActivity
        }
    }
}