package app.vidwall

import android.app.Application
import com.google.android.material.color.DynamicColors

class VidWallApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Цвета системы (Material You), как в обычных приложениях.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
