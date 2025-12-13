package com.example.citypulse

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.citypulse.databinding.ActivityComplaintStatusBinding
import com.example.citypulse.models.Complaint
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ComplaintStatusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComplaintStatusBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val myComplaintsList = mutableListOf<Complaint>()
    private lateinit var adapter: MyComplaintsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComplaintStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Adapter with delete callback
        adapter = MyComplaintsAdapter(myComplaintsList) { complaint ->
            confirmDelete(complaint)
        }
        binding.rvMyComplaints.adapter = adapter

        fetchMyComplaints()
    }

    private fun fetchMyComplaints() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val appId = "citypulse_v1"

        db.collection("artifacts").document(appId)
            .collection("public").document("data")
            .collection("complaints")
            .whereEqualTo("userId", user.uid)
            .addSnapshotListener { value, error ->
                binding.progressBar.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (value != null && !value.isEmpty) {
                    binding.tvEmptyState.visibility = View.GONE
                    myComplaintsList.clear()
                    for (doc in value) {
                        val complaint = doc.toObject(Complaint::class.java)
                        myComplaintsList.add(complaint)
                    }
                    myComplaintsList.sortByDescending { it.timestamp }
                    adapter.notifyDataSetChanged()
                } else {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    myComplaintsList.clear()
                    adapter.notifyDataSetChanged()
                }
            }
    }

    private fun confirmDelete(complaint: Complaint) {
        AlertDialog.Builder(this)
            .setTitle("Delete Complaint")
            .setMessage("Are you sure you want to delete this complaint? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteComplaint(complaint) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteComplaint(complaint: Complaint) {
        val appId = "citypulse_v1"
        db.collection("artifacts").document(appId)
            .collection("public").document("data")
            .collection("complaints").document(complaint.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Complaint deleted successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- ADAPTER ---
    class MyComplaintsAdapter(
        private val complaints: List<Complaint>,
        private val onDeleteClick: (Complaint) -> Unit
    ) : RecyclerView.Adapter<MyComplaintsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvStatus: TextView = view.findViewById(R.id.tvStatus)
            val tvDescription: TextView = view.findViewById(R.id.tvDescription)
            val tvAddress: TextView = view.findViewById(R.id.tvAddress)
            val tvDate: TextView = view.findViewById(R.id.tvDate)
            val viewStatusColor: View = view.findViewById(R.id.viewStatusColor)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_user_complaint, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val complaint = complaints[position]

            holder.tvTitle.text = complaint.title
            holder.tvDescription.text = complaint.description
            holder.tvAddress.text = complaint.address
            holder.tvStatus.text = complaint.status

            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            holder.tvDate.text = sdf.format(Date(complaint.timestamp))

            // Only allow deleting Pending complaints to preserve history of actions
            // (You can remove this check if you want to allow deleting everything)
            if (complaint.status == "Pending") {
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnDelete.setOnClickListener { onDeleteClick(complaint) }
            } else {
                holder.btnDelete.visibility = View.GONE
            }

            when (complaint.status) {
                "Approved" -> {
                    holder.tvStatus.setTextColor(Color.parseColor("#2E7D32"))
                    holder.viewStatusColor.setBackgroundColor(Color.parseColor("#2E7D32"))
                }
                "Declined" -> {
                    holder.tvStatus.setTextColor(Color.parseColor("#C62828"))
                    holder.viewStatusColor.setBackgroundColor(Color.parseColor("#C62828"))
                }
                else -> {
                    holder.tvStatus.setTextColor(Color.parseColor("#F9A825"))
                    holder.viewStatusColor.setBackgroundColor(Color.parseColor("#F9A825"))
                }
            }
        }

        override fun getItemCount() = complaints.size
    }
}