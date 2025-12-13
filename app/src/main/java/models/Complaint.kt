package com.example.citypulse.models

// This data class acts as a blueprint for every complaint
data class Complaint(
    var id: String = "",           // Unique ID for the complaint
    var userId: String = "",       // User ID of the person reporting
    var userPhone: String = "",    // Phone number for contact
    var title: String = "",        // Short title (e.g., "Pothole")
    var description: String = "",  // Full details
    var address: String = "",      // Location address
    var status: String = "Pending", // Status: Pending, Approved, or Declined
    var timestamp: Long = 0        // Time the complaint was created
)