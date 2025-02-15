package slw.nightrunning

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.support.v4.app.ActivityCompat.requestPermissions
import android.support.v4.content.ContextCompat.checkSelfPermission
import android.support.v4.content.res.ResourcesCompat.getDrawable
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.telephony.SmsManager
import android.view.View
import android.widget.Toast
import com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLngZoom
import kotlinx.android.synthetic.main.activity_main.*
import slw.nightrunning.LogActivity.Companion.EXTRA_FILENAME
import java.util.*


class MainActivity : AppCompatActivity() {

    // lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initBaiduMapStyle()
        setContentView(R.layout.activity_main)
        mapView.onCreate(this, savedInstanceState)

        mapView.map.uiSettings.setAllGesturesEnabled(false)
        mapView.showZoomControls(false)
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        logListButton.setOnClickListener {
            startActivity(Intent(this, LogListActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        setBaiduMapStyleByUiMode()
        startServiceWithPermissionRequest()
        updateEmergencyButton(getSettingsPreferences())
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle?, outPersistentState: PersistableBundle?) {
        super.onSaveInstanceState(outState, outPersistentState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        resetBaiduMapStyle()
        stopService()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }


    // service & binder

    private val binder: MainServiceBinder? get() = serviceConnection.binder

    private val serviceConnection = object : ServiceConnection {

        var binder: MainServiceBinder? = null

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            this.binder = service as MainServiceBinder
            service.apply {
                onStateUpdated = {
                    if (isRunning) startTimer()
                    else stopTimer()
                    updateInfoText()
                    updateControlButton()
                }
                onStepCountUpdated = {
                    updateInfoText()
                }
                onLocationUpdated = {
                    updateMapView()
                    updateInfoText()
                }
                hideNotification()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder?.run {
                onStateUpdated = null
                stopTimer()
                onStepCountUpdated = null
                onLocationUpdated = null
                if (isRunning) showNotification()
            }
            binder = null
        }

    }

    private fun startService(): Boolean {
        if (checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
            startService(Intent(this, MainService::class.java))
            bindService(Intent(this, MainService::class.java), serviceConnection, 0)
            return true
        }
        return false
    }

    private fun stopService() {
        val isRunning = binder?.isRunning ?: false
        serviceConnection.onServiceDisconnected(null)
        unbindService(serviceConnection)
        if (!isRunning) stopService(Intent(this, MainService::class.java))
    }

    private fun startServiceWithPermissionRequest(): Boolean {
        val succeed = startService()
        if (!succeed) requestPermissions(this, arrayOf(ACCESS_FINE_LOCATION), 0)
        return succeed
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0) {
            if (!startService()) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.location_not_granted_title))
                    .setMessage(getString(R.string.location_not_granted_message))
                    .setPositiveButton(getString(R.string.all_right)) { _, _ -> startServiceWithPermissionRequest() }
                    .setNegativeButton(getString(R.string.no_way)) { _, _ -> finish() }
                    .show()
            }
        }
    }


    // timer

    private val timer = Timer(true)

    private var task: TimerTask? = null

    private fun startTimer() {
        task = task ?: timer.schedule(1000L) {
            infoText.post { updateInfoText() }
        }
    }

    private fun stopTimer() {
        task?.cancel()
        task = null
    }


    // views

    private fun updateControlButton() {
        val isRunning = binder?.isRunning ?: false
        if (!isRunning) {
            welcomeText.visibility = View.VISIBLE
            titleText.visibility = View.INVISIBLE
            infoText.visibility = View.INVISIBLE
            logListButton.visibility = View.VISIBLE
            controlButton.setImageDrawable(getDrawable(resources, R.drawable.ic_start, theme))
            controlButton.contentDescription = getString(R.string.start)
            controlButton.setOnClickListener {
                binder?.startRunning()
            }
        } else {
            welcomeText.visibility = View.GONE
            titleText.visibility = View.VISIBLE
            infoText.visibility = View.VISIBLE
            logListButton.visibility = View.INVISIBLE
            controlButton.setImageDrawable(getDrawable(resources, R.drawable.ic_stop, theme))
            controlButton.contentDescription = getString(R.string.stop)
            controlButton.setOnClickListener {
                stopRunningAndSaveAndShow()
            }
        }
    }

    private fun updateInfoText() = binder?.run {
        if (isRunning) {
            titleText.text = (System.currentTimeMillis() - startTime).timeSpanDescription()
            val routeLength = runningRoute.geoLength
            val liveSpeed = runningRoute.liveSpeed
            infoText.text = getString(R.string.info_run, runningStepCount, routeLength, liveSpeed)
        }
    }

    private fun updateMapView() = binder?.run {
        if (nowLocation != null) {
            mapView.visibility = View.VISIBLE
            locatingLabel.visibility = View.GONE
        } else {
            mapView.visibility = View.INVISIBLE
            locatingLabel.visibility = View.VISIBLE
        }

        val map = mapView.map
        map.clear()
        if (runningRoute.size >= 2) {
            map.addRoutePolyline(runningRoute)
            map.addStartPoint(runningRoute.first().toLatLng())
            map.addLivePoint(runningRoute.last().toLatLng())
            mapView.zoomToViewRoute(runningRoute)
        } else {
            nowLocation?.toLatLng()?.let { latLng ->
                map.addLivePoint(latLng)
                map.setMapStatus(newLatLngZoom(latLng, 18f))
            }
        }
    }

    private fun updateEmergencyButton(settingsPreferences: SharedPreferences) = settingsPreferences.run {
        if (!emergencyContactEnabled) {
            emergencyButton.visibility = View.GONE
        } else {
            emergencyButton.visibility = View.VISIBLE
            emergencyButton.setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.long_click_to_make_emergency_call),
                    Toast.LENGTH_LONG
                ).show()
            }

            val phoneNumber = emergencyPhoneNumber
            var message = emergencyMessage
            emergencyButton.setOnLongClickListener {
                binder?.nowLocation?.let {
                    message += "\n" + it.run { getString(R.string.info_gps, latitude, longitude, altitude) }
                }
                makeEmergencyCall(phoneNumber, message)
                true
            }
        }
    }


    // other actions

    private fun stopRunningAndSaveAndShow(): Boolean {
        val log = binder?.stopRunning() ?: return false
        if (log.route.size < 2) {
            Toast.makeText(this, getString(R.string.got_few_data), Toast.LENGTH_LONG)
                .show()
            return false
        }
        val filename = saveRunningLog(log) ?: return false
        val intent = Intent(this, LogActivity::class.java)
        intent.putExtra(EXTRA_FILENAME, filename)
        startActivity(intent)
        return true
    }

    private fun makeEmergencyCall(number: String, message: String) {
        try {
            SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.phone_number_incorrect), Toast.LENGTH_LONG)
                .show()
        }
    }

}
