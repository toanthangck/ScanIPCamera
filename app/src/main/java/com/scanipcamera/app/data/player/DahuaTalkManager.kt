package com.scanipcamera.app.data.player

import android.util.Log
import com.company.NetSDK.CB_pfAudioDataCallBack
import com.company.NetSDK.EM_USEDEV_MODE
import com.company.NetSDK.INetSDK
import com.company.NetSDK.NET_SPEAK_PARAM
import com.company.NetSDK.NET_TALK_TRANSFER_PARAM
import com.company.NetSDK.SDKDEV_TALKDECODE_INFO
import com.company.NetSDK.SDK_TALK_CODING_TYPE

/**
 * Handles Dahua 2-Way Audio Talk (Phone mic -> Camera/NVR speaker & Camera mic -> Phone speaker)
 */
class DahuaTalkManager {
    companion object {
        private const val TAG = "DahuaTalkManager"
    }

    private var talkHandle: Long = 0L
    private var isRecording = false

    val isTalking: Boolean get() = talkHandle != 0L

    fun startTalk(loginHandle: Long, channel: Int = 0): Boolean {
        if (loginHandle == 0L) return false
        stopTalk()

        return try {
            val decodeInfo = SDKDEV_TALKDECODE_INFO().apply {
                encodeType = SDK_TALK_CODING_TYPE.SDK_TALK_PCM
                dwSampleRate = 8000
                nAudioBit = 16
                nPacketPeriod = 25
            }
            INetSDK.SetDeviceMode(loginHandle, EM_USEDEV_MODE.SDK_TALK_ENCODE_TYPE, decodeInfo)

            val transferParam = NET_TALK_TRANSFER_PARAM().apply {
                bTransfer = channel > 0
            }
            INetSDK.SetDeviceMode(loginHandle, EM_USEDEV_MODE.SDK_TALK_TRANSFER_MODE, transferParam)

            val speakParam = NET_SPEAK_PARAM().apply {
                nMode = 0
                nEnableWait = 0
                bEnableRender = 1
                nSpeakerChannel = channel
            }
            INetSDK.SetDeviceMode(loginHandle, EM_USEDEV_MODE.SDK_TALK_SPEAK_PARAM, speakParam)

            val callback = CB_pfAudioDataCallBack { lTalkHandle, pDataBuf, byAudioFlag ->
                if (lTalkHandle == talkHandle && pDataBuf != null) {
                    if (byAudioFlag.toInt() == 1) {
                        // Received audio from device -> decode to phone speaker
                        INetSDK.AudioDec(pDataBuf)
                    } else if (byAudioFlag.toInt() == 0) {
                        // Mic audio from phone -> send to camera/NVR speaker
                        INetSDK.TalkSendData(lTalkHandle, pDataBuf)
                    }
                }
            }

            talkHandle = INetSDK.StartTalkEx(loginHandle, callback)
            if (talkHandle == 0L) {
                Log.e(TAG, "StartTalkEx failed: ${INetSDK.GetLastError()}")
                return false
            }

            val okRecord = INetSDK.RecordStartEx(loginHandle)
            if (!okRecord) {
                Log.e(TAG, "RecordStartEx failed, stopping talk")
                INetSDK.StopTalkEx(talkHandle)
                talkHandle = 0L
                return false
            }

            isRecording = true
            Log.d(TAG, "Two-way talk started successfully on channel $channel")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Error starting talk", t)
            stopTalk()
            false
        }
    }

    fun stopTalk() {
        if (isRecording) {
            try {
                INetSDK.RecordStop()
            } catch (t: Throwable) {
                Log.w(TAG, "RecordStop error: ${t.message}")
            }
            isRecording = false
        }
        if (talkHandle != 0L) {
            try {
                INetSDK.StopTalkEx(talkHandle)
            } catch (t: Throwable) {
                Log.w(TAG, "StopTalkEx error: ${t.message}")
            }
            talkHandle = 0L
        }
    }
}
