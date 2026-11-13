package com.aetherwave.data.source.youtube

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewPipeOkHttpDownloader @Inject constructor(
    private val client: OkHttpClient,
) : Downloader() {
    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody(
            request.headers()["Content-Type"]?.firstOrNull()?.toMediaTypeOrNull(),
        )
        val builder = okhttp3.Request.Builder().url(request.url())
        request.headers().forEach { (name, values) -> values.forEach { builder.addHeader(name, it) } }
        builder.method(request.httpMethod(), body)
        client.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            val headers = response.headers.toMultimap()
            return Response(
                response.code,
                response.message,
                headers,
                responseBody,
                response.request.url.toString(),
            )
        }
    }

    companion object {
        fun createClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
