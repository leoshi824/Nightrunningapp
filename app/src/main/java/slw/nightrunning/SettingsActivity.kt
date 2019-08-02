package slw.nightrunning

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v4.app.ActivityCompat.requestPermissions
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_settings.*
import android.Manifest.permission.SEND_SMS
import android.Manifest.permission.CALL_PHONE
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.support.v4.app.ActivityCompat


class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        settingsPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
        emergencyContactEnabledCheckBox.setOnCheckedChangeListener { _, isChecked ->
            emergencyContactNumberField.isEnabled = isChecked
            emergencyContactMessageField.isEnabled = isChecked
            if (isChecked) {
                       ActivityCompat.requestPermissions(this, arrayOf(SEND_SMS,CALL_PHONE), 0)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        emergencyContactEnabledCheckBox.isChecked = settingsPreferences.getBoolean("emergencyContactEnabled", false)
        emergencyContactNumberField.setText(settingsPreferences.getString("emergencyContactNumber", ""))
        val defaultMessage = getString(R.string.default_emergency_contact_message)
        emergencyContactMessageField.setText(settingsPreferences.getString("emergencyContactMessage", defaultMessage))
    }

    override fun onStop() {
        super.onStop()
        val editor = settingsPreferences.edit()
        editor.putBoolean("emergencyContactEnabled", emergencyContactEnabledCheckBox.isChecked)
        editor.putString("emergencyContactNumber", emergencyContactNumberField.text.toString())
        editor.putString("emergencyContactMessage", emergencyContactMessageField.text.toString())
        editor.apply()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkSelfPermission(this, Manifest.permission.SEND_SMS) != PERMISSION_GRANTED ||
            checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PERMISSION_GRANTED)
            emergencyContactEnabledCheckBox.isChecked = false

    }
}
