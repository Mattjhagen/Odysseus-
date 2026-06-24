package site.finchwire.odysseus

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object ServerRouter {
    private const val REMOTE = "https://finchwire.site"
    private const val LOCAL  = "http://192.168.1.73:7000"
    private const val LOCAL_PREFIX = "192.168.1."

    fun urlFlow(context: Context): Flow<Pair<String, Boolean>> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun currentUrl(): Pair<String, Boolean> {
            // User requested remote to be the default instead of auto-switching to local
            val onLocal = false 
            return Pair(REMOTE, onLocal)
        }

        trySend(currentUrl())

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network)        { trySend(currentUrl()) }
            override fun onLost(network: Network)             { trySend(currentUrl()) }
            override fun onCapabilitiesChanged(
                network: Network, caps: NetworkCapabilities
            )                                                 { trySend(currentUrl()) }
        }

        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(req, cb)
        awaitClose { cm.unregisterNetworkCallback(cb) }
    }.distinctUntilChanged()

    private fun isOnLocalNetwork(context: Context): Boolean {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wm.connectionInfo ?: return false
        val ip = info.ipAddress
        if (ip == 0) return false
        // Convert int IP to dotted string (little-endian)
        val dot = "%d.%d.%d.%d".format(
            ip and 0xff,
            (ip shr 8) and 0xff,
            (ip shr 16) and 0xff,
            (ip shr 24) and 0xff
        )
        return dot.startsWith(LOCAL_PREFIX)
    }
}
