package id.reinhart1010.altia.scanner

import android.util.Log
import id.reinhart1010.altia.Device
import id.reinhart1010.altia.Network
import kotlinx.coroutines.*
import java.net.Inet4Address

class PingScanner(
    val network: Network,
    val ipGuesses: Map<Inet4Address, Int> = mapOf(),
    val onUpdate: (ScanResult) -> Unit
) {

    suspend fun pingIpAddresses(): List<ScanResult> =
        withContext(Dispatchers.IO) {
            network.enumerateAddresses()
                .sortedByDescending {
                    // most often seen first, then all other addresses
                    ipGuesses[it] ?: 0
                }
                .chunked(10)
                .map { ipAddresses ->
                    async {
                        ipAddresses.map { ipAddress ->
                            val isReachable = ipAddress.isReachable(1000)
                            val result =
                                ScanResult(ipAddress, isReachable, 1.0 / network.networkSize)
                            onUpdate(result)
                            result
                        }
                    }
                }
                .toList()
                .awaitAll()
                .flatten()
        }

    data class ScanResult(
        val ipAddress: Inet4Address,
        val isReachable: Boolean,
        val progressIncrease: Double
    )
}

