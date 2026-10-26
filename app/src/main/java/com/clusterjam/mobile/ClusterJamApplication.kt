package com.clusterjam.mobile

import android.app.Application
import com.clusterjam.mobile.app.di.AppContainer

class ClusterJamApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
