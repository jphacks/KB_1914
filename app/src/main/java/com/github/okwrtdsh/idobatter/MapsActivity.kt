package com.github.okwrtdsh.idobatter

import android.content.Context
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.amazonaws.amplify.generated.graphql.ListCoordinatesQuery
import com.amazonaws.mobile.config.AWSConfiguration
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.apollographql.apollo.GraphQLCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random


class MyItem(position: LatLng, title: String, snippet: String) : ClusterItem {
    private val mPosition = position
    private val mTitle = title
    private val mSnippet = snippet

    override fun getSnippet() = mSnippet
    override fun getPosition() = mPosition
    override fun getTitle() = mTitle
}

class MyItemClusterRenderer(context: Context, map: GoogleMap, manager: ClusterManager<MyItem>) :
    DefaultClusterRenderer<MyItem>(context, map, manager) {
    override fun shouldRenderAsCluster(cluster: Cluster<MyItem>?): Boolean {
        return cluster?.size ?: 0 >= 5
    }
}

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var currentMarker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mClusterManager: ClusterManager<MyItem>
    private lateinit var targetUUID: String
    private lateinit var mAWSAppSyncClient: AWSAppSyncClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        targetUUID = intent.getStringExtra("UUID")
        Log.d("############", targetUUID)

        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mAWSAppSyncClient = AWSAppSyncClient.builder()
            .context(applicationContext)
            .awsConfiguration(AWSConfiguration(applicationContext))
            .build()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mClusterManager = ClusterManager<MyItem>(this, mMap).apply {
            renderer = MyItemClusterRenderer(this@MapsActivity, mMap, this)
        }
        mMap.apply {
            setOnCameraIdleListener(mClusterManager)
            setOnMarkerClickListener(mClusterManager)
            isMyLocationEnabled = true
            uiSettings.apply {
                isScrollGesturesEnabled = true
                isZoomControlsEnabled = true
                isZoomGesturesEnabled = true
                isRotateGesturesEnabled = true
                isZoomGesturesEnabled = true
                isMapToolbarEnabled = true
                isTiltGesturesEnabled = true
                isCompassEnabled = true
                isMyLocationButtonEnabled = true
            }
        }

        // Add a marker in OsakaUniv and move the camera
        var latlng = LatLng(34.822014, 135.524468)

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    latlng = LatLng(location.latitude, location.longitude)
                    setCurrentMarker(latlng)
                }
            }
        setCurrentMarker(latlng)
        runQuery()

//        val rnd = Random
//        (1..100).map {
//            val ll = LatLng(
//                rnd.nextDoubleNorm(latlng.latitude, 0.5),
//                rnd.nextDoubleNorm(latlng.longitude)
//            )
//            mClusterManager.addItem(
//                MyItem(
//                    ll,
//                    "point",
//                    ll.toStr()
//                )
//            )
//        }
    }

    private fun setCurrentMarker(latlon: LatLng) {
        currentMarker?.remove()
        currentMarker = mMap.addMarker(
            MarkerOptions()
                .position(latlon)
                .title("Your Location")
                .snippet(latlon.toStr())
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latlon, 14f))
    }

    fun runQuery() {
        mAWSAppSyncClient.query(
            ListCoordinatesQuery.builder()
                .apply {
                    limit(10000)
                }
                .build() // TODO: filtering
        )
            .responseFetcher(AppSyncResponseFetchers.CACHE_AND_NETWORK)
            .enqueue(coordinatesCallback)
    }

    private val coordinatesCallback = object : GraphQLCall.Callback<ListCoordinatesQuery.Data>() {
        override fun onResponse(response: Response<ListCoordinatesQuery.Data>) {
            response.data()?.listCoordinates()?.items()?.map {
                if (it.uuid() == targetUUID) {
                    Log.d(">>>", "uuid: ${it.uuid()}")
                    val ll = LatLng(it.lat(), it.lng())
                    mClusterManager.addItem(
                        MyItem(
                            ll,
                            SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Date(it.time().toLong())),
                            ll.toStr()
                        )
                    )
                }
            }
        }

        override fun onFailure(e: ApolloException) {
            Log.e("ERROR", e.toString())
        }
    }
}

fun LatLng.toStr(): String {
    val latDeg: Int = latitude.toInt()
    val latMin: Int = ((latitude - latDeg.toDouble()) * 60.0).toInt()
    val latSec: Int = ((latitude - latDeg.toDouble() - latMin.toDouble() / 60.0) * 3600.0).toInt()
    val lngDeg: Int = longitude.toInt()
    val lngMin: Int = ((longitude - lngDeg.toDouble()) * 60.0).toInt()
    val lngSec: Int = ((longitude - lngDeg.toDouble() - lngMin.toDouble() / 60.0) * 3600.0).toInt()
    return "lat: %02d°%02d′%02d″, lng: %03d°%02d′%02d″".format(
        latDeg, latMin, latSec,
        lngDeg, lngMin, lngSec
    )
}

fun Random.nextDoubleNorm(mu: Double = 0.0, sigma: Double = 1.0): Double =
    ((1..12).map { nextDouble() }.sum() - 6.0) * sigma + mu