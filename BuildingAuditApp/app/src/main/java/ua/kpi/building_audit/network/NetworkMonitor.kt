package ua.kpi.building_audit.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkState(val isOnline: Boolean, val transport: String) {
    companion object {
        val OFFLINE = NetworkState(isOnline = false, transport = "немає")
    }
}

/** Абстракція над станом мережі: у тестах її підміняє проста реалізація з MutableStateFlow. */
interface ConnectivityStatus {
    val state: StateFlow<NetworkState>
}

class AndroidNetworkMonitor(context: Context) : ConnectivityStatus {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val _state = MutableStateFlow(read(manager.activeNetwork))
    override val state: StateFlow<NetworkState> = _state.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                _state.value = fromCapabilities(capabilities)
            }

            override fun onLost(network: Network) {
                _state.value = NetworkState.OFFLINE
            }
        })
    }

    private fun read(network: Network?): NetworkState =
        manager.getNetworkCapabilities(network)?.let(::fromCapabilities) ?: NetworkState.OFFLINE

    // NET_CAPABILITY_VALIDATED не перевіряю: емулятор без виходу в інтернет усе одно бачить локальний сервер.
    private fun fromCapabilities(capabilities: NetworkCapabilities): NetworkState {
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "мобільна мережа"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "інша"
        }
        return NetworkState(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET), transport)
    }
}
