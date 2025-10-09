package com.sirim.scanner.data.network

import okhttp3.CertificatePinner as OkHttpCertificatePinner
import okhttp3.OkHttpClient

object CertificatePinner {
    fun getOkHttpClient(): OkHttpClient {
        val certificatePinner = OkHttpCertificatePinner.Builder()
            .add("api.sirim.my", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            .build()

        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .build()
    }
}
