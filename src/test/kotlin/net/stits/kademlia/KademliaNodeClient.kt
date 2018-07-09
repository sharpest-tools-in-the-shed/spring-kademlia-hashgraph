package net.stits.kademlia

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.stits.kademlia.data.KAddress
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity


class KademliaNodeClient(private val restTemplate: TestRestTemplate, private val baseUrl: String) {
    private val mapper = ObjectMapper().registerModule(KotlinModule())

    fun getAddress(): KAddress {
        val addressResponse = doGet("http://$baseUrl/address/me")!!

        return mapper.readValue(addressResponse)
    }

    fun getAddressBook(): List<KAddress> {
        val addressBookResponse = doGet("http://$baseUrl/address/list")!!

        return mapper.readValue(addressBookResponse)
    }

    fun getStorage(): Map<String, Any> {
        val getStorageResponse = doGet("http://$baseUrl/storage/list")!!

        return mapper.readValue(getStorageResponse)
    }

    fun storeString(value: String) = doPost("http://$baseUrl/storage/put", hashMapOf("value" to value))
    fun getFromStorage(id: String) = doGet("http://$baseUrl/storage/$id")
    fun ping(id: String) = doGet("http://$baseUrl/ping/$id")
    fun bootstrap(host: String, port: Int) = doPost("http://$baseUrl/bootstrap", hashMapOf("host" to host, "port" to port))

    private fun createHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers.contentType = MediaType.APPLICATION_JSON

        return headers
    }

    private fun doGet(url: String): String? {
        val response = handleResponseException(this.restTemplate.getForEntity(url, String::class.java))

        return response.body
    }

    private fun doPost(url: String, params: Map<String, Any>): String? {
        val requestJson = mapper.writeValueAsString(params)
        val entity = HttpEntity(requestJson, createHeaders())

        val response = handleResponseException(this.restTemplate.postForEntity(url, entity, String::class.java))

        return response.body
    }

    private fun handleResponseException(response: ResponseEntity<String>): ResponseEntity<String> {
        if (response.statusCodeValue == 500)
            throw RuntimeException(response.body)

        return response
    }
}