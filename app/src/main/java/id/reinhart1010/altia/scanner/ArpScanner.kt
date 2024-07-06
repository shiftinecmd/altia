package id.reinhart1010.altia.scanner

import android.util.Log
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.util.regex.Pattern

data class ArpEntry(val ip: InetAddress, val hwAddress: MacAddress) {
    companion object {
        fun from(ip: String, mac: String) = ArpEntry(InetAddress.getByName(ip), MacAddress(mac))
    }
}

data class MacAddress(val address: String) {
    fun getAddress(hideMacDetail: Boolean): String {
        if (hideMacDetail) {
            return address.substring(0, "aa:bb:cc".length) + ":XX:XX:XX"
        }
        return address
    }

    val isBroadcast get() = address == "00:00:00:00:00:00"
}

private val whiteSpacePattern = Pattern.compile("\\s+")

private fun InputStream.readStreamAsTable(): Sequence<List<String>> {
    return this.bufferedReader().use { it.readText() }.lineSequence()
        .map { it.split(whiteSpacePattern) }
}


object ArpScanner {
    
    private val TAG = ArpScanner.javaClass.name

    suspend fun getFromAllSources() = withContext(Dispatchers.Default) {
        listOf(async { getArpTableFromFile() }, async { getArpTableFromIpCommand() })
            .awaitAll()
            .asSequence()
            .flatten()
            .filter { !it.hwAddress.isBroadcast }
            .associateBy { it.ip }
    }

    private suspend fun getArpTableFromFile(): Sequence<ArpEntry> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.i(TAG, "arp file checking skipped on Android 10 and above")
            emptySequence<ArpEntry>()
        } else {
            try {
                File("/proc/net/arp").inputStream().readStreamAsTable()
                    .drop(1)
                    .filter { it.size == 6 }
                    .map {
                        ArpEntry.from(it[0], it[3])
                    }
            } catch (exception: FileNotFoundException) {
                Log.e(TAG, "arp file not found $exception")
                emptySequence<ArpEntry>()
            }
        }
    }

    private suspend fun getArpTableFromIpCommand(): Sequence<ArpEntry> =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Log.i(TAG, "arp file checking skipped on Android 13 and above")
                emptySequence<ArpEntry>()
            } else {
                try {
                    val execution = Runtime.getRuntime().exec("ip neigh")
                    execution.waitFor()
                    execution.inputStream.readStreamAsTable()
                        .filter { it.size >= 5 }
                        .map {
                            ArpEntry.from(it[0], it[4])
                        }
                        .onEach { Log.d(TAG, "found entry in 'ip neigh': $it") }
                } catch (exception: IOException) {
                    Log.e(TAG, "io error when running ip neigh $exception")
                    emptySequence<ArpEntry>()
                }
            }
        }
}



