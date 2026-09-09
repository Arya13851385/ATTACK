package com.localnet.emergency.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zero-configuration discovery of the on-premise alert broker using Android's
 * built-in Network Service Discovery (NSD), which implements mDNS/DNS-SD.
 *
 * The broker advertises itself once on the LAN as:
 *   service type: "_emergencyalert._tcp."
 *   service name: any human-readable name, e.g. "ChemZTech-AlertBroker"
 *
 * No public internet, no Google Play Services, and no manual IP entry is
 * required for the common case; a manual host:port override is still exposed
 * in the UI as a fallback for networks that block multicast.
 */
data class DiscoveredBroker(val host: String, val port: Int, val serviceName: String)

@Singleton
class NsdDiscoveryManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "NsdDiscoveryManager"
        const val SERVICE_TYPE = "_emergencyalert._tcp."
    }

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    /**
     * Emits every broker found on the subnet. Multicast traffic on Android
     * requires holding a MulticastLock for the duration of discovery.
     */
    fun discoverBrokers() = callbackFlow<DiscoveredBroker> {
        val multicastLock = wifiManager.createMulticastLock("emergencyAlertDiscovery").apply {
            setReferenceCounted(true)
            acquire()
        }

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                trySend(DiscoveredBroker(host, serviceInfo.port, serviceInfo.serviceName))
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "Discovery started for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("emergencyalert")) {
                    try {
                        @Suppress("DEPRECATION")
                        nsdManager.resolveService(serviceInfo, resolveListener)
                    } catch (e: IllegalArgumentException) {
                        Log.w(TAG, "resolveService rejected: ${e.message}")
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Broker lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped for $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: $errorCode")
                close(IllegalStateException("NSD start discovery failed: $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            close(e)
        }

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (_: Exception) {
                // Discovery may already be stopped; safe to ignore.
            }
            if (multicastLock.isHeld) multicastLock.release()
        }
    }.distinctUntilChanged()
}
