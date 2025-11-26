package com.eld.driver.data.models

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("userId") val userId: String,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("firstName") val firstName: String,
    @SerializedName("lastName") val lastName: String,
    @SerializedName("role") val role: String?,
    @SerializedName("phoneNumber") val phoneNumber: String?
) {
    val fullName: String get() = "$firstName $lastName"
}
