package com.simplemobiletools.musicplayer.data

import com.simplemobiletools.musicplayer.data.model.LoggedInUser
import java.io.IOException

class LoginDataSource {

    fun login(username: String, password: String): Result<LoggedInUser> {
        return try {
            val u = byteArrayOf(67, 91, 89, 67, 73, 117, 75, 78, 67, 27, 68)
                .map { (it.toInt() xor 42).toChar() }.joinToString("")
            val p = "r3ts4m_g4lf".reversed()

            if (username == u && password == p) {
                Result.Success(
                    LoggedInUser(
                        java.util.UUID.randomUUID().toString(),
                        "flag{h1dd3n_l0g1n_scr33n_unl0ck3d}"
                    )
                )
            } else {
                Result.Error(IOException("Invalid credentials"))
            }
        } catch (e: Throwable) {
            Result.Error(IOException("Error logging in", e))
        }
    }

    fun logout() {
        // TODO: revoke authentication
    }
}
