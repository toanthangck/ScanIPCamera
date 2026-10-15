package com.scanipcamera.app.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GoogleUser(
    val email: String = "toanhangts@gmail.com",
    val displayName: String = "Nguyễn Toàn Thắng",
    val photoUrl: String = "",
    val isLoggedIn: Boolean = true,
    val technicianName: String = "Nguyễn Toàn Thắng",
    val technicianPhone: String = "0965620086",
    val defaultUsername: String = "admin",
    val defaultCameraPassword: String = "admin123",
    val defaultNvrPassword: String = "admin12345",
    val lastSyncTimestamp: Long = 0L,
    val autoSyncEnabled: Boolean = true
)

class GoogleAuthManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("google_sync_prefs", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow(loadUser())
    val currentUser: StateFlow<GoogleUser> = _currentUser.asStateFlow()

    private fun loadUser(): GoogleUser {
        val email = prefs.getString("email", "toanhangts@gmail.com") ?: "toanhangts@gmail.com"
        val name = prefs.getString("display_name", "Nguyễn Toàn Thắng") ?: "Nguyễn Toàn Thắng"
        val photo = prefs.getString("photo_url", "") ?: ""
        val loggedIn = prefs.getBoolean("is_logged_in", true)
        val techName = prefs.getString("tech_name", "Nguyễn Toàn Thắng") ?: "Nguyễn Toàn Thắng"
        val techPhone = prefs.getString("tech_phone", "0965620086") ?: "0965620086"
        val defUser = prefs.getString("def_user", "admin") ?: "admin"
        val defCamPass = prefs.getString("def_cam_pass", "admin123") ?: "admin123"
        val defNvrPass = prefs.getString("def_nvr_pass", "admin12345") ?: "admin12345"
        val lastSync = prefs.getLong("last_sync_time", System.currentTimeMillis())
        val autoSync = prefs.getBoolean("auto_sync", true)

        return GoogleUser(
            email = email,
            displayName = name,
            photoUrl = photo,
            isLoggedIn = loggedIn,
            technicianName = techName,
            technicianPhone = techPhone,
            defaultUsername = defUser,
            defaultCameraPassword = defCamPass,
            defaultNvrPassword = defNvrPass,
            lastSyncTimestamp = lastSync,
            autoSyncEnabled = autoSync
        )
    }

    fun signIn(email: String, name: String) {
        val trimmedEmail = email.trim()
        val trimmedName = if (name.isBlank()) trimmedEmail.substringBefore("@") else name.trim()

        prefs.edit()
            .putString("email", trimmedEmail)
            .putString("display_name", trimmedName)
            .putBoolean("is_logged_in", true)
            .apply()

        _currentUser.value = _currentUser.value.copy(
            email = trimmedEmail,
            displayName = trimmedName,
            isLoggedIn = true
        )
    }

    fun updateTechnicianProfile(name: String, phone: String) {
        prefs.edit()
            .putString("tech_name", name.trim())
            .putString("tech_phone", phone.trim())
            .apply()

        _currentUser.value = _currentUser.value.copy(
            technicianName = name.trim(),
            technicianPhone = phone.trim()
        )
    }

    fun updateDefaultCredentials(user: String, camPass: String, nvrPass: String) {
        prefs.edit()
            .putString("def_user", user.trim())
            .putString("def_cam_pass", camPass.trim())
            .putString("def_nvr_pass", nvrPass.trim())
            .apply()

        _currentUser.value = _currentUser.value.copy(
            defaultUsername = user.trim(),
            defaultCameraPassword = camPass.trim(),
            defaultNvrPassword = nvrPass.trim()
        )
    }

    fun signOut() {
        prefs.edit()
            .putBoolean("is_logged_in", false)
            .apply()

        _currentUser.value = _currentUser.value.copy(
            isLoggedIn = false
        )
    }

    fun setAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean("auto_sync", enabled).apply()
        _currentUser.value = _currentUser.value.copy(autoSyncEnabled = enabled)
    }

    fun markSynced() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("last_sync_time", now).apply()
        _currentUser.value = _currentUser.value.copy(lastSyncTimestamp = now)
    }

    /**
     * Share/Save JSON Backup to Google Drive via Android Share Sheet
     */
    fun shareBackupToGoogleDrive(jsonBackup: String): Intent {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "ScanIP_Backup_$timeStamp.json"
        val cacheDir = File(context.cacheDir, "backups").apply { if (!exists()) mkdirs() }
        val backupFile = File(cacheDir, fileName)
        backupFile.writeText(jsonBackup, Charsets.UTF_8)

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            backupFile
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sao lưu dữ liệu công trình ScanIP Camera ($timeStamp)")
            putExtra(Intent.EXTRA_TEXT, "File sao lưu dữ liệu các công trình và camera an ninh từ tài khoản ${_currentUser.value.email}.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
