package com.loic.wakeup.ui.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun NfcScanningEffect(enabled: Boolean, onTagScanned: (String) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val activity = context.findActivity()
        val adapter = NfcAdapter.getDefaultAdapter(context)
        if (enabled && activity != null && adapter != null) {
            val callback = NfcAdapter.ReaderCallback { tag ->
                val hex = tag.id.joinToString("") { "%02x".format(it) }
                onTagScanned(hex)
            }
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
            adapter.enableReaderMode(activity, callback, flags, null)
            onDispose { adapter.disableReaderMode(activity) }
        } else {
            onDispose { }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
