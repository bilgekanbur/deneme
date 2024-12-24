package com.example.pianosense

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.os.CountDownTimer
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.DialogFragment

class CountdownDialogFragment(private val onCountdownFinished: () -> Unit) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // countdown_dialog.xml dosyasını bağla
        return inflater.inflate(R.layout.fragment_countdown_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val countdownTextView: TextView = view.findViewById(R.id.countdownTextView)

        // Geri sayım başlat
        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdownTextView.text = "${millisUntilFinished / 1000}"
            }

            override fun onFinish() {
                dismiss() // Dialog kapanır
                onCountdownFinished() // Kayıt başlar
            }
        }.start()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
