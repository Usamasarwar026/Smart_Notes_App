package com.example.smartnotes

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AddEditNoteActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var titleEditText: EditText
    private lateinit var descriptionEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var backButton: ImageButton

    private var noteId: String? = null
    private var isEditMode = false

    private val CHANNEL_ID = "smartnotes_channel"
    private val NOTIFICATION_ID = 1003

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_note)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Initialize views
        titleEditText = findViewById(R.id.titleEditText)
        descriptionEditText = findViewById(R.id.descriptionEditText)
        saveButton = findViewById(R.id.saveButton)
        backButton = findViewById(R.id.backButton)

        // Setup notifications
        createNotificationChannel()
        askNotificationPermission()

        // Check if editing existing note
        noteId = intent.getStringExtra("noteId")
        if (noteId != null) {
            isEditMode = true
            titleEditText.setText(intent.getStringExtra("noteTitle"))
            descriptionEditText.setText(intent.getStringExtra("noteDescription"))
        }

        // Save button click
        saveButton.setOnClickListener { saveNote() }

        // Back button click
        backButton.setOnClickListener { finish() }
    }

    private fun saveNote() {
        val title = titleEditText.text.toString().trim()
        val description = descriptionEditText.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show()
            return
        }

        if (description.isEmpty()) {
            Toast.makeText(this, "Please enter description", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = auth.currentUser?.uid ?: return

        val note = Note(
            id = noteId ?: "",
            title = title,
            description = description,
            timestamp = System.currentTimeMillis(),
            userId = userId
        )

        if (isEditMode && noteId != null) {
            // Update existing note
            firestore.collection("notes").document(noteId!!)
                .set(note)
                .addOnSuccessListener {
                    showNotification(
                        "Note Updated",
                        "Changes saved successfully."
                    )
                    Toast.makeText(this, "Note updated!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Create new note
            firestore.collection("notes")
                .add(note)
                .addOnSuccessListener {
                    showNotification(
                        "New Note Created",
                        "Your note has been added to your collection."
                    )
                    Toast.makeText(this, "Note saved!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
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
}
