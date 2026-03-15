package com.example.homework2sentiment

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.homework2sentiment.repository.SentimentApiException
import com.example.homework2sentiment.repository.SentimentRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val repository = SentimentRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etInput = findViewById<TextInputEditText>(R.id.etInput)
        val btnAnalyze = findViewById<MaterialButton>(R.id.btnAnalyze)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvLoading = findViewById<TextView>(R.id.tvLoading)
        val resultCard = findViewById<MaterialCardView>(R.id.resultCard)
        val chipLabel = findViewById<Chip>(R.id.chipLabel)
        val tvLabel = findViewById<TextView>(R.id.tvLabel)
        val tvConfidence = findViewById<TextView>(R.id.tvConfidence)
        val tvReason = findViewById<TextView>(R.id.tvReason)

        btnAnalyze.setOnClickListener {
            val input = etInput.text?.toString()?.trim().orEmpty()

            if (input.isEmpty()) {
                Toast.makeText(this, "Vui long nhap noi dung", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (BuildConfig.GEMINI_API_KEY.isBlank()) {
                Toast.makeText(this, "Thieu GEMINI_API_KEY trong local.properties", Toast.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    progressBar.visibility = ProgressBar.VISIBLE
                    tvLoading.visibility = TextView.VISIBLE
                    btnAnalyze.isEnabled = false

                    val result = repository.classify(BuildConfig.GEMINI_API_KEY, input)
                    val normalizedLabel = result.label.lowercase().trim()
                    tvLabel.text = normalizedLabel.uppercase()
                    chipLabel.text = normalizedLabel.uppercase()
                    tvConfidence.text = String.format("%.2f", result.confidence)
                    tvReason.text = result.reason

                    applySentimentStyle(normalizedLabel, chipLabel)

                    resultCard.alpha = 0f
                    resultCard.translationY = 20f
                    resultCard.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(280)
                        .start()
                } catch (e: SentimentApiException) {
                    Toast.makeText(
                        this@MainActivity,
                        e.message,
                        Toast.LENGTH_LONG
                    ).show()
                    tvReason.text = e.message
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Loi khong xac dinh: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    progressBar.visibility = ProgressBar.GONE
                    tvLoading.visibility = TextView.GONE
                    btnAnalyze.isEnabled = true
                }
            }
        }
    }

    private fun applySentimentStyle(label: String, chipLabel: Chip) {
        val (bgColor, fgColor) = when (label) {
            "positive" -> R.color.positive_bg to R.color.positive_fg
            "negative" -> R.color.negative_bg to R.color.negative_fg
            else -> R.color.neutral_bg to R.color.neutral_fg
        }

        chipLabel.chipBackgroundColor =
            ContextCompat.getColorStateList(this, bgColor)
        chipLabel.setTextColor(ContextCompat.getColor(this, fgColor))
    }
}
