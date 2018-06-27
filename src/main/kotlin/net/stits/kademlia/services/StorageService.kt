package net.stits.kademlia.services

import org.springframework.stereotype.Service
import java.math.BigInteger


@Service
class StorageService {
    private val storage = hashMapOf<BigInteger, ByteArray>()

    fun put(id: BigInteger, data: ByteArray): Boolean {
        if (storage.containsKey(id)) return false

        storage[id] = data
        return true
    }

    fun get(id: BigInteger): ByteArray? {
        return storage[id]
    }

    fun remove(id: BigInteger): Boolean {
        if (!storage.containsKey(id)) return false

        storage.remove(id)
        return true
    }

    fun update(id: BigInteger, data: ByteArray): Boolean {
        if (!storage.containsKey(id)) return false

        storage[id] = data
        return true
    }
}