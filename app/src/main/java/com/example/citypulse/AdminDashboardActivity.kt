package com.example.citypulse

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.citypulse.databinding.ActivityAdminDashboardBinding
import com.example.citypulse.models.Complaint
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private val db = FirebaseFirestore.getInstance()
    private val complaintsList = mutableListOf<Complaint>()
    private lateinit var adapter: ComplaintsAdapter
    private var firestoreListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup RecyclerView
        adapter = ComplaintsAdapter(complaintsList) { complaint, newStatus ->
            updateComplaintStatus(complaint, newStatus)
        }
        binding.rvComplaints.adapter = adapter

        // Setup Button Listener to Load Data
        binding.btnShowComplaints.setOnClickListener {
            // Disable button so they don't click it twice
            binding.btnShowComplaints.isEnabled = false
            binding.btnShowComplaints.text = "Loading..."
            binding.progressBar.visibility = View.VISIBLE

            // Fetch data
            fetchComplaints()
        }

        // Setup Logout
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_logout) {
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
                true
            } else false
        }
    }

    private fun fetchComplaints() {
        // Prevent multiple listeners
        firestoreListener?.remove()

        val appId = "citypulse_v1"
        firestoreListener = db.collection("artifacts").document(appId)
            .collection("public").document("data")
            .collection("complaints")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    Toast.makeText(this, "Error fetching data: ${error.message}", Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = View.GONE
                    binding.btnShowComplaints.isEnabled = true
                    binding.btnShowComplaints.text = "Show Complaints"
                    return@addSnapshotListener
                }

                if (value != null) {
                    complaintsList.clear()
                    for (doc in value) {
                        val complaint = doc.toObject(Complaint::class.java)
                        complaintsList.add(complaint)
                    }
                    adapter.notifyDataSetChanged()

                    // Show the list and hide the loader
                    binding.rvComplaints.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.GONE

                    // Optional: Update button text or keep it disabled
                    binding.btnShowComplaints.text = "Refresh List"
                    binding.btnShowComplaints.isEnabled = true
                }
            }
    }

    private fun updateComplaintStatus(complaint: Complaint, newStatus: String) {
        val appId = "citypulse_v1"
        db.collection("artifacts").document(appId)
            .collection("public").document("data")
            .collection("complaints").document(complaint.id)
            .update("status", newStatus)
            .addOnSuccessListener {
                Toast.makeText(this, "Status updated to $newStatus", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        firestoreListener?.remove()
    }

    // INNER CLASS ADAPTER
    class ComplaintsAdapter(
        private val complaints: List<Complaint>,
        private val onActionClick: (Complaint, String) -> Unit
    ) : RecyclerView.Adapter<ComplaintsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvStatus: TextView = view.findViewById(R.id.tvStatus)
            val tvDescription: TextView = view.findViewById(R.id.tvDescription)
            val tvAddress: TextView = view.findViewById(R.id.tvAddress)
            val tvUser: TextView = view.findViewById(R.id.tvUser)
            val btnApprove: Button = view.findViewById(R.id.btnApprove)
            val btnDecline: Button = view.findViewById(R.id.btnDecline)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_complaint, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val complaint = complaints[position]
            holder.tvTitle.text = complaint.title
            holder.tvStatus.text = "Status: ${complaint.status}"
            holder.tvDescription.text = complaint.description
            holder.tvAddress.text = complaint.address
            holder.tvUser.text = "User: ${complaint.userPhone}"

            // Simple visual logic
            if (complaint.status == "Approved") {
                holder.btnApprove.isEnabled = false
                holder.btnDecline.isEnabled = true
            } else if (complaint.status == "Declined") {
                holder.btnApprove.isEnabled = true
                holder.btnDecline.isEnabled = false
            } else {
                holder.btnApprove.isEnabled = true
                holder.btnDecline.isEnabled = true
            }

            holder.btnApprove.setOnClickListener { onActionClick(complaint, "Approved") }
            holder.btnDecline.setOnClickListener { onActionClick(complaint, "Declined") }
        }

        override fun getItemCount() = complaints.size
    }
}