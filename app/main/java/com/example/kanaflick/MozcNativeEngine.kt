package com.example.kanaflick

import android.content.Context
import android.util.Log
import com.google.android.apps.inputmethod.libs.mozc.session.MozcJni
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Command
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.KeyEvent
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand
import java.io.File
import java.io.IOException

class MozcNativeEngine(context: Context) {
    private val appContext = context.applicationContext
    private var sessionId: Long? = null
    private var initializationAttempted = false
    private var initialized = false

    @Synchronized
    fun candidates(reading: String): List<String>? {
        if (reading.isBlank() || !initialize()) return null
        return try {
            val id = sessionId ?: createSession().also { sessionId = it }
            sendCommand(id, SessionCommand.CommandType.REVERT)
            var offset = 0
            while (offset < reading.length) {
                val codePoint = reading.codePointAt(offset)
                val keyString = String(Character.toChars(codePoint))
                sendKey(
                    id,
                    KeyEvent.newBuilder()
                        .setKeyString(keyString)
                        .setInputStyle(KeyEvent.InputStyle.AS_IS)
                        .build()
                )
                offset += Character.charCount(codePoint)
            }
            val output = sendKey(id, KeyEvent.newBuilder().setSpecialKey(KeyEvent.SpecialKey.SPACE).build())
            buildList {
                if (output.hasCandidateWindow()) {
                    output.candidateWindow.candidateList.forEach { candidate ->
                        if (candidate.value.isNotBlank()) add(candidate.value)
                    }
                }
                if (output.hasPreedit()) {
                    output.preedit.segmentList.forEach { segment ->
                        if (segment.value.isNotBlank()) add(segment.value)
                    }
                }
            }.distinct()
        } catch (error: UnsatisfiedLinkError) {
            Log.e(TAG, "Mozc JNI command failed", error)
            initialized = false
            sessionId = null
            null
        } catch (error: IOException) {
            Log.e(TAG, "Mozc returned an invalid command response", error)
            sessionId = null
            null
        }
    }

    @Synchronized
    private fun initialize(): Boolean {
        if (initializationAttempted) return initialized
        initializationAttempted = true
        return try {
            val dataFile = File(appContext.filesDir, DATA_FILE_NAME)
            if (!dataFile.isFile || dataFile.length() == 0L) {
                appContext.assets.open(DATA_FILE_NAME).use { input ->
                    dataFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val profile = File(appContext.filesDir, "mozc-profile")
            if (!profile.isDirectory && !profile.mkdirs()) {
                throw IOException("Could not create Mozc profile directory")
            }
            System.loadLibrary("mozc")
            initialized = MozcJni.initialize() &&
                MozcJni.onPostLoad(profile.absolutePath, dataFile.absolutePath)
            if (!initialized) Log.e(TAG, "Mozc native engine initialization failed")
            initialized
        } catch (error: IOException) {
            Log.e(TAG, "Mozc engine data is unavailable", error)
            false
        } catch (error: UnsatisfiedLinkError) {
            Log.e(TAG, "Mozc native library is unavailable", error)
            false
        }
    }

    private fun createSession(): Long {
        val input = Input.newBuilder().setType(Input.CommandType.CREATE_SESSION).build()
        val output = evaluate(input)
        if (!output.hasId()) throw IOException("Mozc did not return a session id")
        return output.id
    }

    private fun sendCommand(id: Long, type: SessionCommand.CommandType) {
        val sessionCommand = SessionCommand.newBuilder().setType(type).build()
        val input = Input.newBuilder()
            .setType(Input.CommandType.SEND_COMMAND)
            .setId(id)
            .setCommand(sessionCommand)
            .build()
        evaluate(input)
    }

    private fun sendKey(id: Long, key: KeyEvent): ProtoCommands.Output {
        val input = Input.newBuilder()
            .setType(Input.CommandType.SEND_KEY)
            .setId(id)
            .setKey(key)
            .build()
        return evaluate(input)
    }

    private fun evaluate(input: Input): ProtoCommands.Output {
        val response = MozcJni.evalCommand(Command.newBuilder().setInput(input).build().toByteArray())
        val command = Command.parseFrom(response)
        if (!command.hasOutput()) throw IOException("Mozc returned no command output")
        val output = command.output
        if (output.hasErrorCode() && output.errorCode != ProtoCommands.Output.ErrorCode.SESSION_SUCCESS) {
            throw IOException("Mozc session command failed: ${output.errorCode}")
        }
        return output
    }

    private companion object {
        const val DATA_FILE_NAME = "mozc.data"
        const val TAG = "MozcNativeEngine"
    }
}
