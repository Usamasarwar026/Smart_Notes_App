package com.example.smartnotes

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Check if user is logged in
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is logged in, go to Notes
            startActivity(Intent(this, NotesActivity::class.java))
        } else {
            // User not logged in, go to Login
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}