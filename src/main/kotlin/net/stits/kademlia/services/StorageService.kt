package net.stits.kademlia.services

import org.springframework.stereotype.Service
import java.math.BigInteger


@Service
class StorageService {
    private val storage = hashMapOf<BigInteger, Any>()

    fun getStorage(): Map<BigInteger, Any> {
        return storage
    }

    fun put(id: BigInteger, data: Any): Boolean {
        if (storage.containsKey(id)) return false

        storage[id] = data
        return true
    }

    fun get(id: BigInteger): Any? {
        return storage[id]
    }

    fun remove(id: BigInteger): Boolean {
        if (!storage.containsKey(id)) return false

        storage.remove(id)
        return true
    }

    fun update(id: BigInteger, data: Any): Boolean {
        if (!storage.containsKey(id)) return false

        storage[id] = data
        return true
    }
}