package com.example.pianosense

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment

class ProgressDialogFragment(private val onTimeout: () -> Unit) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // ProgressBar tasarımı için XML dosyasını bağla
        return inflater.inflate(R.layout.fragment_progress_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 10 saniye sonra Dialog'u kapat ve onTimeout() işlemini çalıştır
        Handler(Looper.getMainLooper()).postDelayed({
            dismiss() // Dialog'u kapat
            onTimeout() // Sonuç ekranına geçişi tetikle
        }, 10000) // 10 saniye
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
