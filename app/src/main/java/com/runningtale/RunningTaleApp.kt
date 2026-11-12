package com.runningtale

import android.app.Application

class RunningTaleApp : Application() {
    /**
     * StoryMediaService가 살아 있는 동안만 존재. 서비스가 만들 때 넣고 종료할 때 비움.
     * (AppGraph.stop()이 코루틴 스코프를 닫으므로 서비스가 다시 만들어지면 새로 만들어야 함)
     */
    var graph: AppGraph? = null
}
