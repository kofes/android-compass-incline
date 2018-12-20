package io.milkpp.kofes.myapplication

import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import com.google.android.gms.ads.*
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.math.cos
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var sensorManager: SensorManager
    private lateinit var sensorEventListener: SensorEventListener

    private enum class StateView(val value: Int) {
        SHOW_NOTHING(0),
        SHOW_COMPASS(1),
        SHOW_INCLINE(2)
    }

    private var stateView: StateView = StateView.SHOW_NOTHING

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(R.layout.activity_main)

        MobileAds.initialize(this, getString(R.string.admob_app_id))
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        bottom_orientation.max = 360
        bottom_orientation.min = 0
        left_orientation.max = 360
        left_orientation.min = 0

        sensorEventListener = object: SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                    System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                    updateOrientationAngles()
                } else if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                    updateOrientationAngles()
                }
            }

            private var currentOrientation: Float = 0f
            private var timestamp: Long = System.currentTimeMillis()
            private var timeDelta: Long = 200 // ms
            private var accumulatedXAngle: Double = 0.0
            private var accumulatedYAngle: Double = 0.0
            private var accumulatedZAngle: Double = 0.0
            private var statisticsCounter: Long = 0 // 1 for timeDelta
            private var counter: Long = 0

            fun updateOrientationAngles() {
                // Update rotation matrix, which is needed to update orientation angles.
                SensorManager.getRotationMatrix(
                    rotationMatrix,
                    null,
                    accelerometerReading,
                    magnetometerReading
                )
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                if ((System.currentTimeMillis() - timestamp) > timeDelta) {
                    //
                    if (statisticsCounter >= 1000 / timeDelta) {
                        val params = Bundle()
                        params.putString(FirebaseAnalytics.Param.ITEM_ID, "main_id")
                        params.putString(FirebaseAnalytics.Param.ITEM_NAME, "orientation")
                        params.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "orientation")
                        params.putString("orientation", "${Math.round((accumulatedXAngle / counter) / Math.PI * 360)}")
                        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, params)
                        statisticsCounter = 0
                    } else
                        ++statisticsCounter
                    //
                    val checker = sqrt(cos(accumulatedYAngle / counter) * cos(accumulatedYAngle / counter)
                                + cos(accumulatedZAngle / counter) * cos(accumulatedZAngle / counter))
                    val zAngle = (accumulatedZAngle / counter / 2 / Math.PI) * 360 + 180
                    val yAngle = (accumulatedYAngle / counter / Math.PI) * 360 + 180
                    if (checker > 1.41) {
                        text_view.setBackgroundColor(getColor(R.color.colorAccent))
                        text_view.text = "horizontal"
                    } else {
                        text_view.setBackgroundColor(getColor(R.color.colorPrimaryDark))
                        text_view.text = "verical"
                    }
                    bottom_orientation.setProgress(zAngle.toInt(), true)
                    left_orientation.setProgress(yAngle.toInt(), true)
                    val angle: Float = ((accumulatedXAngle / counter) / Math.PI * 360).toFloat()
                    val rotateAnimation = RotateAnimation(
                        currentOrientation,
                        -angle,
                        Animation.RELATIVE_TO_SELF,
                        0.5f,
                        Animation.RELATIVE_TO_SELF,
                        0.5f)
                    rotateAnimation.duration = timeDelta
                    rotateAnimation.fillAfter = true
                    if (compass_view.visibility == View.VISIBLE)
                        compass_view.startAnimation(rotateAnimation)
                    currentOrientation = -angle
                    accumulatedXAngle = 0.0
                    accumulatedYAngle = 0.0
                    accumulatedZAngle = 0.0
                    counter = 0
                    timestamp = System.currentTimeMillis()
                } else {
                    ++counter
                    accumulatedXAngle += orientationAngles[0]
                    accumulatedYAngle += orientationAngles[1]
                    accumulatedZAngle += orientationAngles[2]
                }
            }
        }
    }

    private var adCounter: Long = 0
    private var adDeltaCounter: Long = 3

    override fun onResume() {
        super.onResume()
        //
        bottom_orientation.max = 360
        bottom_orientation.min = 0
        left_orientation.max = 360
        left_orientation.min = 0
        //
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this.sensorEventListener,
                accelerometer,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                this.sensorEventListener,
                magneticField,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
        //
        val adView = InterstitialAd(this)
        adView.adUnitId = getString(R.string.admob_interstitial_id)
        adView.loadAd(AdRequest.Builder().build())
        adView.adListener = object : AdListener() {
            override fun onAdClosed() {
                super.onAdClosed()
                adView.loadAd(AdRequest.Builder().build())
            }
        }

        float_button.setOnClickListener {
            if (adCounter >= adDeltaCounter && adView.isLoaded) {
                adView.show()
                adCounter = 0
            } else
                ++adCounter
            when (stateView) {
                StateView.SHOW_NOTHING -> {
                    compass_view.visibility = View.VISIBLE
                    stateView = StateView.SHOW_COMPASS
                }
                StateView.SHOW_COMPASS -> {
                    compass_view.visibility = View.INVISIBLE
                    bottom_orientation.visibility = View.VISIBLE
                    left_orientation.visibility = View.VISIBLE
                    stateView = StateView.SHOW_INCLINE
                }
                StateView.SHOW_INCLINE -> {
                    bottom_orientation.visibility = View.INVISIBLE
                    left_orientation.visibility = View.INVISIBLE
                    compass_view.visibility = View.VISIBLE
                    stateView = StateView.SHOW_COMPASS
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Don't receive any more updates from either sensor.
        sensorManager.unregisterListener(this.sensorEventListener)
    }
}
