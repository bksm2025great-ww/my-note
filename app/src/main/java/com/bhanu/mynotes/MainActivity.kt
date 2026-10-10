package com.bhanu.mynotes

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var mainNotesContainer: LinearLayout
    private lateinit var searchBar: EditText
    private lateinit var notesContainer: LinearLayout
    private lateinit var secretVaultWebView: WebView
    private lateinit var fabAddNote: FloatingActionButton
    private lateinit var noteEditorContainer: LinearLayout
    private lateinit var btnCancelEdit: ImageView
    private lateinit var btnSaveNote: MaterialButton
    private lateinit var editNoteTitle: EditText
    private lateinit var editNoteContent: EditText
    private lateinit var btnMenuTheme: ImageView
    private lateinit var updateBadgeDot: View

    private var editingNoteId: Long? = null
    private val notesList = mutableListOf<NoteItem>()

    // Update Tracking Variables
    private var latestApkUrl: String? = null
    private var latestVersionName: String? = null
    private var isUpdateAvailable: Boolean = false

    data class NoteItem(val id: Long, var title: String, var content: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-Edge Full Screen (Transparent Floating Status Bar)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        setContentView(R.layout.activity_main)

        // Privacy Shield: Recent Apps me screen black dikhane ke liye
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        initViews()
        setupAutofillFix()
        setupWebView()
        setupListeners()
        loadNotesFromStorage()
        renderNotes()

        // Background me GitHub Release check karna
        checkForAppUpdates()

        // Back button handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (secretVaultWebView.visibility == View.VISIBLE) {
                    lockVaultToNotes()
                } else if (noteEditorContainer.visibility == View.VISIBLE) {
                    closeEditor()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun initViews() {
        mainNotesContainer = findViewById(R.id.mainNotesContainer)
        searchBar = findViewById(R.id.searchBar)
        notesContainer = findViewById(R.id.notesContainer)
        secretVaultWebView = findViewById(R.id.secretVaultWebView)
        fabAddNote = findViewById(R.id.fabAddNote)
        noteEditorContainer = findViewById(R.id.noteEditorContainer)
        btnCancelEdit = findViewById(R.id.btnCancelEdit)
        btnSaveNote = findViewById(R.id.btnSaveNote)
        editNoteTitle = findViewById(R.id.editNoteTitle)
        editNoteContent = findViewById(R.id.editNoteContent)
        btnMenuTheme = findViewById(R.id.btnMenuTheme)
        updateBadgeDot = findViewById(R.id.updateBadgeDot)
    }

    private fun setupAutofillFix() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            searchBar.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        searchBar.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    private fun setupWebView() {
        secretVaultWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
        }

        secretVaultWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript("window.lockVaultToNotes = function() { AndroidBridge.lockVaultToNotes(); };", null)
            }
        }

        secretVaultWebView.addJavascriptInterface(object {
            @JavascriptInterface
            fun lockVaultToNotes() {
                this@MainActivity.lockVaultToNotes()
            }
        }, "AndroidBridge")
    }

    private fun setupListeners() {
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString()?.trim() ?: ""
                if (text == "mmsungun2202") {
                    openSecretVault()
                } else {
                    renderNotes(text)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        fabAddNote.setOnClickListener {
            openEditorForNote(null)
        }

        btnCancelEdit.setOnClickListener {
            closeEditor()
        }

        btnSaveNote.setOnClickListener {
            saveCurrentNote()
        }

        // Settings / Update Icon click
        btnMenuTheme.setOnClickListener {
            showSettingsUpdateDialog()
        }
    }

    // --- GitHub Release Check Logic ---
    private fun checkForAppUpdates() {
        Thread {
            try {
                val url = URL("https://api.github.com/repos/bksm2025great-ww/my-note/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 6000
                conn.readTimeout = 6000

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val json = JSONObject(response.toString())
                    val tagName = json.optString("tag_name", "")
                    val assets = json.optJSONArray("assets")

                    var downloadUrl: String? = null
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", null)
                                break
                            }
                        }
                    }

                    val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName

                    // Version comparison
                    if (downloadUrl != null && tagName.isNotEmpty() && tagName != currentVersion && !tagName.contains(currentVersion ?: "")) {
                        latestApkUrl = downloadUrl
                        latestVersionName = tagName
                        isUpdateAvailable = true

                        runOnUiThread {
                            updateBadgeDot.visibility = View.VISIBLE
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // --- Settings & Update Popup Card ---
    private fun showSettingsUpdateDialog() {
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }

        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("Settings & Updates")

        if (isUpdateAvailable && latestApkUrl != null) {
            dialogBuilder.setMessage("A new update ($latestVersionName) is ready!\n\nCurrent version: $currentVersion\nTap below to download and install automatically.")
            dialogBuilder.setPositiveButton("Update Now") { _, _ ->
                downloadAndInstallApk(latestApkUrl!!)
            }
            dialogBuilder.setNegativeButton("Later", null)
        } else {
            dialogBuilder.setMessage("Your app is up to date!\n\nCurrent version: $currentVersion")
            dialogBuilder.setPositiveButton("Check Again") { _, _ ->
                Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
                checkForAppUpdates()
            }
            dialogBuilder.setNegativeButton("Close", null)
        }

        dialogBuilder.show()
    }

    // --- Direct In-App Download and Install ---
    private fun downloadAndInstallApk(apkUrl: String) {
        Toast.makeText(this, "Downloading update in background...", Toast.LENGTH_LONG).show()

        try {
            val fileName = "my_notes_update.apk"
            val destination = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (destination.exists()) destination.delete()

            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("Downloading My Notes Update")
                setDescription("Please wait while the update downloads...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destination))
            }

            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)

            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id == downloadId) {
                        try {
                            unregisterReceiver(this)
                        } catch (e: Exception) {}

                        // Launch Native Android Package Installer
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            val apkUri = FileProvider.getUriForFile(
                                this@MainActivity,
                                "$packageName.provider",
                                destination
                            )
                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(installIntent)
                    }
                }
            }

            registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: agar permission issue ho to seedha link open karega
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))
            startActivity(browserIntent)
        }
    }

    private fun openSecretVault() {
        hideKeyboard(searchBar)
        searchBar.setText("")
        mainNotesContainer.visibility = View.GONE
        fabAddNote.visibility = View.GONE
        secretVaultWebView.visibility = View.VISIBLE
        secretVaultWebView.loadUrl("file:///android_asset/vault.html")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    }

    fun lockVaultToNotes() {
        runOnUiThread {
            secretVaultWebView.visibility = View.GONE
            mainNotesContainer.visibility = View.VISIBLE
            fabAddNote.visibility = View.VISIBLE
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        }
    }

    private fun openEditorForNote(note: NoteItem?) {
        if (note != null) {
            editingNoteId = note.id
            editNoteTitle.setText(note.title)
            editNoteContent.setText(note.content)
        } else {
            editingNoteId = null
            editNoteTitle.setText("")
            editNoteContent.setText("")
        }
        mainNotesContainer.visibility = View.GONE
        fabAddNote.visibility = View.GONE
        noteEditorContainer.visibility = View.VISIBLE
        editNoteTitle.requestFocus()
    }

    private fun closeEditor() {
        hideKeyboard(editNoteTitle)
        noteEditorContainer.visibility = View.GONE
        mainNotesContainer.visibility = View.VISIBLE
        fabAddNote.visibility = View.VISIBLE
    }

    private fun saveCurrentNote() {
        val title = editNoteTitle.text.toString().trim().ifEmpty { "Untitled Note" }
        val content = editNoteContent.text.toString().trim()

        if (editingNoteId != null) {
            val existing = notesList.find { it.id == editingNoteId }
            existing?.let {
                it.title = title
                it.content = content
            }
        } else {
            notesList.add(0, NoteItem(System.currentTimeMillis(), title, content))
        }

        saveNotesToStorage()
        closeEditor()
        renderNotes(searchBar.text.toString().trim())
    }

    private fun deleteNote(note: NoteItem) {
        notesList.removeAll { it.id == note.id }
        saveNotesToStorage()
        renderNotes(searchBar.text.toString().trim())
    }

    private fun renderNotes(query: String = "") {
        notesContainer.removeAllViews()
        val filtered = if (query.isEmpty()) notesList else notesList.filter {
            it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true)
        }

        for (note in filtered) {
            val card = MaterialCardView(this).apply {
                radius = dpToPx(16).toFloat()
                cardElevation = dpToPx(2).toFloat()
                strokeColor = Color.parseColor("#E2E8F0")
                strokeWidth = dpToPx(1)
                setCardBackgroundColor(Color.WHITE)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(12)
                }
                layoutParams = params
            }

            val cardInner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            }

            val header = RelativeLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val tvTitle = TextView(this).apply {
                text = note.title
                textSize = 17f
                setTextColor(Color.parseColor("#0F172A"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                val titleParams = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_START)
                    addRule(RelativeLayout.START_OF, 1001)
                }
                layoutParams = titleParams
            }

            val btnMenu = ImageView(this).apply {
                id = 1001
                setImageResource(android.R.drawable.ic_menu_more)
                setColorFilter(Color.parseColor("#64748B"))
                val menuParams = RelativeLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                }
                layoutParams = menuParams
                setOnClickListener { anchor ->
                    val popup = PopupMenu(this@MainActivity, anchor)
                    popup.menu.add(0, 1, 0, "Edit")
                    popup.menu.add(0, 2, 1, "Delete")
                    popup.setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            1 -> openEditorForNote(note)
                            2 -> deleteNote(note)
                        }
                        true
                    }
                    popup.show()
                }
            }

            header.addView(tvTitle)
            header.addView(btnMenu)
            cardInner.addView(header)

            val tvContent = TextView(this).apply {
                text = note.content
                textSize = 14f
                setTextColor(Color.parseColor("#475569"))
                val contentParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(0)
                    topMargin = dpToPx(8)
                }
                layoutParams = contentParams
            }
            cardInner.addView(tvContent)

            card.addView(cardInner)
            notesContainer.addView(card)
        }
    }

    private fun loadNotesFromStorage() {
        notesList.clear()
        val prefs = getSharedPreferences("my_notes_pref", Context.MODE_PRIVATE)
        val data = prefs.getString("saved_notes", null)
        if (data != null) {
            try {
                val array = JSONArray(data)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    notesList.add(
                        NoteItem(
                            obj.getLong("id"),
                            obj.getString("title"),
                            obj.getString("content")
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (notesList.isEmpty()) {
            notesList.add(
                NoteItem(
                    1L,
                    "Daily Reminders",
                    "• Revise physics notes in the morning\n• Check evening study focus tracker\n• Keep water bottle handy on study desk"
                )
            )
            notesList.add(
                NoteItem(
                    2L,
                    "Project Ideas",
                    "• Research new web frameworks\n• Clean laptop workspace & check system performance\n• Draft study sprint plans"
                )
            )
            saveNotesToStorage()
        }
    }

    private fun saveNotesToStorage() {
        val prefs = getSharedPreferences("my_notes_pref", Context.MODE_PRIVATE)
        val array = JSONArray()
        for (item in notesList) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("content", item.content)
            }
            array.put(obj)
        }
        prefs.edit().putString("saved_notes", array.toString()).apply()
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onPause() {
        super.onPause()
        if (::secretVaultWebView.isInitialized && secretVaultWebView.visibility == View.VISIBLE) {
            lockVaultToNotes()
        }
    }
}
