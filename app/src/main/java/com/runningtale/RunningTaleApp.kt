package com.runningtale

import android.app.Application

class RunningTaleApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
