package com.example.smartnotes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class NotesAdapter(
    private val notesList: List<Note>,
    private val onEditClick: (Note) -> Unit,
    private val onDeleteClick: (Note) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.noteTitleTextView)
        val descriptionTextView: TextView = itemView.findViewById(R.id.noteDescriptionTextView)
        val timestampTextView: TextView = itemView.findViewById(R.id.noteTimestampTextView)
        val editButton: ImageButton = itemView.findViewById(R.id.editNoteButton)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deleteNoteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notesList[position]

        holder.titleTextView.text = note.title
        holder.descriptionTextView.text = note.description
        holder.timestampTextView.text = formatTimestamp(note.timestamp)

        // Card click - Show full note in dialog
        holder.itemView.setOnClickListener {
            showNoteDialog(holder.itemView.context, note)
        }

        holder.editButton.setOnClickListener {
            onEditClick(note)
        }

        holder.deleteButton.setOnClickListener {
            onDeleteClick(note)
        }
    }

    override fun getItemCount(): Int = notesList.size

    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000} minutes ago"
            diff < 86400000 -> "${diff / 3600000} hours ago"
            diff < 604800000 -> "${diff / 86400000} days ago"
            else -> {
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    private fun showNoteDialog(context: android.content.Context, note: Note) {
        AlertDialog.Builder(context)
            .setTitle(note.title)
            .setMessage(note.description)
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Edit") { _, _ ->
                onEditClick(note)
            }
            .create()
            .show()
    }
}