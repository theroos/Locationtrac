package com.example.locationtrac

import android.Manifest
import androidx.compose.ui.tooling.preview.Preview
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.locationtrac.ui.theme.LocationTracTheme
import com.google.android.gms.location.*
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val districtState = mutableStateOf("Detecting Location...")

    private val latState = mutableStateOf(22.5726) // default Kolkata
    private val longState = mutableStateOf(88.3639)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this)

        setContent {
            LocationTracTheme {
                DistrictScreen(lat = latState.value, long = longState.value, districtState.value)
            }
        }

        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = packageName
            load(this@MainActivity, getSharedPreferences("osm", MODE_PRIVATE))
        }

        // Permission check
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
        }
    }


    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationUpdates() {

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    latState.value = location.latitude
                    longState.value = location.longitude

                    val district = getDistrictName(location.latitude, location.longitude)
                    districtState.value = district
                }
            }
        }

        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    
    private fun getDistrictName(lat: Double, long: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val address = geocoder.getFromLocation(lat, long, 1)?.firstOrNull()

            address?.let {

                // Try best possible fields
                when {
                    !it.subLocality.isNullOrEmpty() ->
                        it.subLocality   // Ballygunge, Salt Lake, etc.

                    !it.locality.isNullOrEmpty() ->
                        it.locality      // Kolkata fallback

                    !it.subAdminArea.isNullOrEmpty() ->
                        it.subAdminArea  // district fallback

                    else -> "Unknown Area"
                }

            } ?: "Unknown Location"

        } catch (e: Exception) {
            "No Internet / Unable to detect district"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }
}


@Composable
fun DistrictScreen(lat: Double, long: Double, district: String) {

    Box(modifier = Modifier.fillMaxSize()){

        OpenStreetMapView(lat, long)

        Box(
            modifier = Modifier.align(Alignment.BottomCenter).
            padding(16.dp).
            fillMaxWidth(0.8f).
            background(color = androidx.compose.ui.graphics.Color.White,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "You are currently at $district area",
                fontSize = 15.sp
            )
        }

    }

}



@Preview(showBackground = true)
@Composable
fun DistrictScreenPreview() {
    DistrictScreen(lat = 22.5726, long = 88.3639, district = "Kolkata")
}


@Composable
fun OpenStreetMapView(lat: Double, long: Double) {
    AndroidView(
        factory = { context ->
            org.osmdroid.views.MapView(context).apply {
                setMultiTouchControls(true)
                controller.setZoom(15.0)
            }
        },
        update = { mapView ->

            val geoPoint = org.osmdroid.util.GeoPoint(lat, long)
            mapView.controller.animateTo(geoPoint)
            mapView.controller.setZoom(17.0)

            mapView.invalidate()

            mapView.overlays.clear()

            val marker = org.osmdroid.views.overlay.Marker(mapView)
            marker.position = geoPoint
            marker.icon = ContextCompat.getDrawable(mapView.context,R.drawable.locationpincopy)
            marker.setAnchor(
                org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
            )

            mapView.overlays.add(marker)
        },
        modifier = Modifier.fillMaxSize()
    )
}



