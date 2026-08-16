package com.mlevngr.mote.plugin.ai

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.mlevngr.mote.plugin.ai.R

class AiSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ai_settings_root)) { view, insets ->
            val bars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val settings = AiPluginSettings(this)
        val configuration = settings.load()
        val internal = findViewById<TextInputEditText>(R.id.internal_endpoint)
        val external = findViewById<TextInputEditText>(R.id.external_endpoint)
        val model = findViewById<TextInputEditText>(R.id.model)
        val apiKey = findViewById<TextInputEditText>(R.id.api_key)
        internal.setText(configuration.internalEndpoint)
        external.setText(configuration.externalEndpoint)
        model.setText(configuration.model)
        apiKey.setText(configuration.apiKey)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<android.view.View>(R.id.save).setOnClickListener {
            settings.save(
                AiPluginConfiguration(
                    internal.text?.toString().orEmpty(),
                    external.text?.toString().orEmpty(),
                    model.text?.toString().orEmpty(),
                    apiKey.text?.toString().orEmpty()
                )
            )
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }
    }
}
