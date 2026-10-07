package com.bhanu.mynotes

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private val secretTriggerPin = "mmsungun2202"

    private lateinit var layoutNotesView: LinearLayout
    private lateinit var layoutSecretVault: View
    private lateinit var searchBarNotes: EditText
    private lateinit var containerNotesList: LinearLayout
    private lateinit var fabAddNote: FloatingActionButton
    private lateinit var vaultWebView: WebView
    private lateinit var btnQuickLock: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // View References
        layoutNotesView = findViewById(R.id.layoutNotesView)
        layoutSecretVault = findViewById(R.id.layoutSecretVault)
        searchBarNotes = findViewById(R.id.searchBarNotes)
        containerNotesList = findViewById(R.id.containerNotesList)
        fabAddNote = findViewById(R.id.fabAddNote)
        vaultWebView = findViewById(R.id.vaultWebView)
        btnQuickLock = findViewById(R.id.btnQuickLock)

        // Setup notes
        setupDefaultNotes()
        loadSavedNotes()

        // Setup Stealth Secret Trigger on Search Bar
        setupSecretSearchTrigger()

        // Setup Floating Add Note Button
        fabAddNote.setOnClickListener {
            showAddNewNoteDialog()
        }

        // Setup Quick Lock Button
        btnQuickLock.setOnClickListener {
            lockVaultToNotes()
        }

        // Setup Web Engine for Vault
        setupVaultWebView()
    }

    private fun setupSecretSearchTrigger() {
        searchBarNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString()?.trim() ?: ""
                if (input.equals(secretTriggerPin, ignoreCase = true)) {
                    // Instantly wipe search bar to leave zero trace
                    searchBarNotes.text.clear()
                    hideKeyboard()
                    openSecretVault()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun openSecretVault() {
        layoutNotesView.visibility = View.GONE
        fabAddNote.visibility = View.GONE
        layoutSecretVault.visibility = View.VISIBLE
        // Load the secret assets page
        vaultWebView.loadUrl("file:///android_asset/vault.html")
    }

    private fun lockVaultToNotes() {
        layoutSecretVault.visibility = View.GONE
        layoutNotesView.visibility = View.VISIBLE
        fabAddNote.visibility = View.VISIBLE
        vaultWebView.loadUrl("about:blank")
    }

    private fun setupVaultWebView() {
        val settings: WebSettings = vaultWebView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        vaultWebView.webViewClient = WebViewClient()
    }

    private fun setupDefaultNotes() {
        val prefs = getSharedPreferences("notes_storage", Context.MODE_PRIVATE)
        if (!prefs.contains("initialized")) {
            val editor = prefs.edit()
            editor.putString("note_1", "Project Ideas\n• Research new web frameworks\n• Clean laptop workspace & check system performance\n• Draft study sprint plans")
            editor.putString("note_2", "Daily Reminders\n• Revise physics notes in the morning\n• Check evening study focus tracker\n• Keep water bottle handy on study desk")
            editor.putBoolean("initialized", true)
            editor.apply()
        }
    }

    private fun loadSavedNotes() {
        containerNotesList.removeAllViews()
        val prefs = getSharedPreferences("notes_storage", Context.MODE_PRIVATE)
        val allNotes = prefs.all

        for ((key, value) in allNotes) {
            if (key != "initialized" && value is String) {
                addNoteCardToUI(value)
            }
        }
    }

    private fun addNoteCardToUI(content: String) {
        val card = MaterialCardView(this).apply {
            radius = 28f
            elevation = 3f
            strokeWidth = 1
            setStrokeColor(0xFFE2E8F0.toInt())
            setCardBackgroundColor(0xFFFFFFFF.toInt())
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
            layoutParams = params
        }

        val textView = TextView(this).apply {
            text = content
            textSize = 15f
            setTextColor(0xFF334155.toInt())
            setPadding(36, 32, 36, 32)
            lineHeight = 48
        }

        card.addView(textView)
        containerNotesList.addView(card)
    }

    private fun showAddNewNoteDialog() {
        val input = EditText(this).apply {
            hint = "Write your note title and thoughts..."
            setPadding(40, 40, 40, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("Create Note")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    val prefs = getSharedPreferences("notes_storage", Context.MODE_PRIVATE)
                    val newId = "note_${System.currentTimeMillis()}"
                    prefs.edit().putString(newId, text).apply()
                    addNoteCardToUI(text)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchBarNotes.windowToken, 0)
    }
}
