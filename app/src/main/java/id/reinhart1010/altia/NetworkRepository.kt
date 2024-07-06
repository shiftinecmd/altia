package id.reinhart1010.altia

import androidx.lifecycle.LiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NetworkRepository(private val networkDao: NetworkDao) {
    suspend fun getNetwork(networkId: Long): LiveData<Network> {
        withContext(Dispatchers.IO) {
            networkDao.getById(networkId)
        }
        return networkDao.getById(networkId)
    }
}