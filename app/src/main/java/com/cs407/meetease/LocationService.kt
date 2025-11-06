package com.cs407.meetease

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var db: FirebaseFirestore? = null
    private var userId: String? = null
    private var groupId: String? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val NOTIFICATION_CHANNEL_ID = "location_service_channel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "LocationService"
    }

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        db = FirebaseFirestore.getInstance()
        userId = Firebase.auth.currentUser?.uid
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @SuppressLint("MissingPermission")
    private fun start() {
        if (userId == null) {
            Log.e(TAG, "User not logged in, stopping service.")
            stop()
            return
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "Location service started.")

        serviceScope.launch {
            try {
                val userDoc = db!!.collection("users").document(userId!!).get().await()
                groupId = userDoc.getString("groupId")

                if (groupId == null) {
                    Log.e(TAG, "User has no group, stopping service.")
                    stop()
                    return@launch
                }

                Log.d(TAG, "Got groupId: $groupId. Starting location updates.")
                startLocationUpdates()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to get group ID", e)
                stop()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(3000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    Log.d(TAG, "LocationService: Got location: ${location.latitude}, ${location.longitude}")
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    updateLocationInFirestore(geoPoint)
                }
            }
        }

        try {
            locationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted, stopping service.", e)
            stop()
        }
    }

    private fun updateLocationInFirestore(geoPoint: GeoPoint) {
        if (userId != null && groupId != null) {
            serviceScope.launch {
                try {
                    Log.d(TAG, "LocationService: Writing to Firestore at path: groups/$groupId/members/$userId")
                    db!!.collection("groups").document(groupId!!)
                        .collection("members").document(userId!!)
                        .update("location", geoPoint)
                        .await()
                    Log.d(TAG, "LocationService: Firestore write successful.")
                } catch (e: Exception) {
                    Log.e(TAG, "LocationService: FAILED to update location in Firestore", e)
                }
            }
        }
    }

    private fun stop() {
        Log.d(TAG, "Location service stopping.")
        try {
            if (::locationCallback.isInitialized) {
                locationClient.removeLocationUpdates(locationCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }

        removeLocationFromFirestore()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeLocationFromFirestore() {
        if (userId != null && groupId != null) {
            serviceScope.launch {
                try {
                    Log.d(TAG, "LocationService: Removing location from Firestore.")
                    db!!.collection("groups").document(groupId!!)
                        .collection("members").document(userId!!)
                        .update("location", FieldValue.delete())
                        .await()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove location from Firestore", e)
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Location Sharing",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("MeetEase")
            .setContentText("Sharing your live location...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
}