package dev.drivedna.probe

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView

/** Requests CAR_SPEED at runtime, then starts [ProbeService]. Read-only probe. */
class ProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "DriveDNA probe running. See logcat tag $TAG."; textSize = 28f })
        val needed = arrayOf(SPEED_PERMISSION, "android.permission.POST_NOTIFICATIONS")
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) startProbe() else requestPermissions(needed.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissions.forEachIndexed { i, p ->
            Log.i(TAG, "PERM $p granted=${grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED}")
        }
        startProbe()
    }

    private fun startProbe() {
        Log.i(TAG, "PERM $SPEED_PERMISSION state=${checkSelfPermission(SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED}")
        startForegroundService(Intent(this, ProbeService::class.java))
    }

    companion object {
        const val TAG = "DDPROBE"
        const val SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
    }
}
