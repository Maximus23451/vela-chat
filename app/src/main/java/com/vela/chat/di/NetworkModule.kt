package com.vela.chat.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.vela.chat.BuildConfig
import com.vela.chat.data.AppJson
import com.vela.chat.data.net.TofuTrustManager
import com.vela.chat.data.net.TrustedCertStore
import com.vela.chat.data.remote.OpenAiApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * [OkHttpClient] with no logging interceptor at all — for calls whose URL query string
 * can carry a secret (some web-search providers accept the API key only as a query
 * param, e.g. SerpAPI/Google PSE). The main client's `HttpLoggingInterceptor` logs the
 * full request line at `BASIC` in debug builds, which would otherwise put those keys in
 * Logcat; this client sidesteps the question of which providers support header auth
 * instead by never logging URLs for this traffic, for any provider.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NoLogOkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(trustedCertStore: TrustedCertStore): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val trustManager = TofuTrustManager(trustedCertStore)
        val sslSocketFactory = SSLContext.getInstance("TLS")
            .apply { init(null, arrayOf(trustManager), SecureRandom()) }
            .socketFactory
        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustManager)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // No read timeout: token streams can be arbitrarily long-lived.
            .readTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    @NoLogOkHttpClient
    fun provideNoLogOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            // Real URLs are supplied per-call via @Url; this is just a required base.
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(AppJson.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenAiApi(retrofit: Retrofit): OpenAiApi = retrofit.create(OpenAiApi::class.java)
}
