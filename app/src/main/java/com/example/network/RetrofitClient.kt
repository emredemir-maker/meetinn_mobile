package com.example.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.google.firebase.auth.FirebaseAuth

object RetrofitClient {
    private const val BASE_URL = "https://ais-pre-v4uh7jzvdk5gxyc5u37t5b-506706966583.europe-west2.run.app/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val authInterceptor = Interceptor { chain ->
        var request = chain.request()
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            try {
                val task = user.getIdToken(false)
                val token = com.google.android.gms.tasks.Tasks.await(task).token
                if (token != null) {
                    request = request.newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    val apiService: MeetInnApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MeetInnApiService::class.java)
    }
}
