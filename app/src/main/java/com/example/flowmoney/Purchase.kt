package com.example.flowmoney

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Purchase(
    val value: String,
    val establishment: String,
    val timestamp: String,
    val category: String
) : Parcelable
