package com.example.citypulse

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.citypulse.databinding.ActivityAuthBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var storedVerificationId: String
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken

    // ADMIN PHONE NUMBER (You can change this)
    private val ADMIN_PHONE_NUMBER = "9090909090"

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.w("AuthActivity", "onVerificationFailed", e)
            binding.progressBar.visibility = View.GONE
            Toast.makeText(applicationContext, "Verification failed: ${e.message}", Toast.LENGTH_LONG).show()
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            storedVerificationId = verificationId
            resendToken = token
            binding.progressBar.visibility = View.GONE
            binding.tilPhoneNumber.visibility = View.GONE
            binding.btnSendOtp.visibility = View.GONE
            binding.tilOtp.visibility = View.VISIBLE
            binding.btnVerifyOtp.visibility = View.VISIBLE
            Toast.makeText(applicationContext, "OTP Sent!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Check if user is already logged in
        if (auth.currentUser != null) {
            routeUser(auth.currentUser?.phoneNumber)
        }

        binding.btnSendOtp.setOnClickListener {
            val phoneNumber = binding.etPhoneNumber.text.toString().trim()
            if (phoneNumber.isNotEmpty() && phoneNumber.length == 10) {
                // If it's the specific test number, we might want to bypass real OTP in a real app,
                // but for now we'll use the standard flow.
                val fullPhoneNumber = "+91$phoneNumber"
                binding.progressBar.visibility = View.VISIBLE
                sendVerificationCode(fullPhoneNumber)
            } else {
                Toast.makeText(this, "Please enter a valid 10-digit phone number", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnVerifyOtp.setOnClickListener {
            val otp = binding.etOtp.text.toString().trim()
            if (otp.isNotEmpty() && otp.length == 6) {
                binding.progressBar.visibility = View.VISIBLE
                val credential = PhoneAuthProvider.getCredential(storedVerificationId, otp)
                signInWithPhoneAuthCredential(credential)
            } else {
                Toast.makeText(this, "Please enter the 6-digit OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendVerificationCode(phoneNumber: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                binding.progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    Toast.makeText(applicationContext, "Login Successful!", Toast.LENGTH_SHORT).show()
                    val user = task.result?.user
                    routeUser(user?.phoneNumber)
                } else {
                    Toast.makeText(applicationContext, "Login Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun routeUser(phoneNumber: String?) {
        if (phoneNumber != null && phoneNumber.endsWith(ADMIN_PHONE_NUMBER)) {
            // Go to Admin Dashboard
            val intent = Intent(this, AdminDashboardActivity::class.java)
            startActivity(intent)
        } else {
            // Go to User Home
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
        }
        finish()
    }
}