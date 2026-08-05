package com.example.extractor

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

class DownloaderImpl private constructor(
    private val client: OkHttpClient
) : Downloader() {

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder().url(url)

        headers.forEach { (headerName, headerValues) ->
            headerValues.forEach { headerValue ->
                requestBuilder.addHeader(headerName, headerValue)
            }
        }

        if (headers["User-Agent"].isNullOrEmpty()) {
            requestBuilder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            )
        }

        val body = if (dataToSend != null && (httpMethod == "POST" || httpMethod == "PUT")) {
            dataToSend.toRequestBody()
        } else null

        when (httpMethod) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(body ?: "".toByteArray().toRequestBody())
            "HEAD" -> requestBuilder.head()
            else -> requestBuilder.method(httpMethod, body)
        }

        val okResponse = client.newCall(requestBuilder.build()).execute()

        val responseCode = okResponse.code
        if (responseCode == 429) {
            okResponse.close()
            throw ReCaptchaException("reCAPTCHA requested by YouTube", url)
        }

        val responseBody = okResponse.body?.string() ?: ""
        val responseHeaders = mutableMapOf<String, List<String>>()
        okResponse.headers.names().forEach { name ->
            responseHeaders[name] = okResponse.headers.values(name)
        }

        val latestUrl = okResponse.request.url.toString()
        okResponse.close()

        return Response(responseCode, okResponse.message, responseHeaders, responseBody, latestUrl)
    }

    companion object {
        @Volatile
        private var instance: DownloaderImpl? = null

        fun getInstance(client: OkHttpClient = createDefaultClient()): DownloaderImpl {
            return instance ?: synchronized(this) {
                instance ?: DownloaderImpl(client).also { instance = it }
            }
        }

        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }
    }
}
