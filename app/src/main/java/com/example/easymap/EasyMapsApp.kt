package com.example.easymap

import android.app.Application
import com.example.easymap.util.NotificationHelper
import org.osmdroid.config.Configuration
import java.io.File

/** Configuração global: cache do mapa OSM (sem pedir permissões de armazenamento) e canais de notificação. */
class EasyMapsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val osmConfig = Configuration.getInstance()
        osmConfig.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        osmConfig.userAgentValue = "EasyMaps/1.0 (projeto academico PWDAM - UPS; Android)"
        osmConfig.osmdroidBasePath = File(cacheDir, "osmdroid")
        osmConfig.osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        NotificationHelper.createChannels(this)
    }
}
