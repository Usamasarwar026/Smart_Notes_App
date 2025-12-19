package com.example.smartnotes

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class NotesActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var notesRecyclerView: RecyclerView
    private lateinit var addNoteButton: ExtendedFloatingActionButton
    private lateinit var logoutButton: ImageButton
    private lateinit var searchEditText: EditText
    private lateinit var totalNotesText: TextView
    private lateinit var startWritingLayout: View
    private lateinit var welcomeTextView: TextView

    private val notesList = mutableListOf<Note>()
    private val filteredNotesList = mutableListOf<Note>()
    private lateinit var notesAdapter: NotesAdapter
    private var notesListener: ListenerRegistration? = null

    private val CHANNEL_ID = "smartnotes_channel"
    private val NOTIFICATION_ID = 1004
    private var pendingLogoutNotification = false // flag for logout notification

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Initialize Views
        notesRecyclerView = findViewById(R.id.notesRecyclerView)
        addNoteButton = findViewById(R.id.addNoteButton)
        logoutButton = findViewById(R.id.logoutButton)
        searchEditText = findViewById(R.id.searchEditText)
        totalNotesText = findViewById(R.id.totalNotesText)
        startWritingLayout = findViewById(R.id.startWritingLayout)
        welcomeTextView = findViewById(R.id.welcomeTextView)

        // Set Welcome Text (username only)
        val userName = intent.getStringExtra("userEmail") ?: "User"
        welcomeTextView.text = "Welcome Back..."

        // Setup RecyclerView
        notesAdapter = NotesAdapter(
            filteredNotesList,
            onEditClick = { note -> editNote(note) },
            onDeleteClick = { note -> deleteNote(note) }
        )
        notesRecyclerView.layoutManager = LinearLayoutManager(this)
        notesRecyclerView.adapter = notesAdapter

        // Notifications
        createNotificationChannel()
        askNotificationPermission()

        // Search functionality
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterNotes(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, after: Int) {}
        })

        // Start Writing / Add Note button
        startWritingLayout.setOnClickListener { startActivity(Intent(this, AddEditNoteActivity::class.java)) }
        addNoteButton.setOnClickListener { startActivity(Intent(this, AddEditNoteActivity::class.java)) }

        // Logout button
        logoutButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes") { _, _ ->
                    notesListener?.remove()
                    auth.signOut()
                    // Check notification permission
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        pendingLogoutNotification = true
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            NOTIFICATION_ID
                        )
                    } else {
                        showNotification("Session Ended", "You have logged out securely.")
                    }

                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("No", null)
                .show()
        }

        // Load notes from Firestore
        loadNotes()
    }

    private fun loadNotes() {
        val userId = auth.currentUser?.uid ?: return
        notesListener = firestore.collection("notes")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                notesList.clear()
                snapshots?.forEach { doc ->
                    val note = doc.toObject(Note::class.java)
                    note.id = doc.id
                    notesList.add(note)
                }
                totalNotesText.text = notesList.size.toString()
                filterNotes(searchEditText.text.toString())
            }
    }

    private fun filterNotes(query: String) {
        filteredNotesList.clear()
        if (query.isEmpty()) filteredNotesList.addAll(notesList)
        else {
            val searchQuery = query.lowercase()
            filteredNotesList.addAll(notesList.filter {
                it.title.lowercase().contains(searchQuery) || it.description.lowercase().contains(searchQuery)
            })
        }
        notesAdapter.notifyDataSetChanged()
    }

    private fun editNote(note: Note) {
        val intent = Intent(this, AddEditNoteActivity::class.java)
        intent.putExtra("noteId", note.id)
        intent.putExtra("noteTitle", note.title)
        intent.putExtra("noteDescription", note.description)
        startActivity(intent)
    }

    private fun deleteNote(note: Note) {
        AlertDialog.Builder(this)
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to delete this note?")
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("notes").document(note.id)
                    .delete()
                    .addOnSuccessListener {
                        showNotification("Note Removed", "The note was deleted.")
                        Toast.makeText(this, "Note deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------- NOTIFICATION FUNCTIONS -------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart Notes Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_ID
                )
            }
        }
    }

    private fun showNotification(title: String, message: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)

        NotificationManagerCompat.from(this)
            .notify(System.currentTimeMillis().toInt(), builder.build())
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_ID) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) && pendingLogoutNotification) {
                showNotification("Session Ended", "You have logged out securely.")
                pendingLogoutNotification = false
            }
        }
    }
}
