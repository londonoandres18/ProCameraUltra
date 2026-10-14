package com.nfcstudio.app

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.*
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private var adapter: NfcAdapter? = null
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var payload: EditText
    private lateinit var historyView: TextView
    private lateinit var writeButton: Button
    private var writeArmed = false
    private val prefs by lazy { getSharedPreferences("nfc_studio", MODE_PRIVATE) }
    private val navy = 0xFF101827.toInt()
    private val panel = 0xFF192438.toInt()
    private val accent = 0xFF4F8CFF.toInt()
    private val muted = 0xFF9CAAC0.toInt()
    private val white = 0xFFF4F7FC.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = NfcAdapter.getDefaultAdapter(this)
        buildUi()
        handleIntent(intent)
        refreshStatus()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(navy) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
        }
        scroll.addView(page); setContentView(scroll)
        page.addView(TextView(this).apply {
            text = "NFC STUDIO"; textSize = 12f; setTextColor(accent)
            letterSpacing = .18f; typeface = Typeface.DEFAULT_BOLD
        })
        page.addView(TextView(this).apply {
            text = "Lector y analizador NFC"; textSize = 27f; setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(6), 0, dp(4))
        })
        page.addView(TextView(this).apply {
            text = "Identifica tecnologías, lee datos accesibles y escribe etiquetas compatibles. Sin root."
            textSize = 14f; setTextColor(muted)
        })
        status = TextView(this).apply {
            textSize = 14f; setTextColor(white); setPadding(dp(14), dp(13), dp(14), dp(13))
            setBackgroundColor(panel)
        }
        page.addView(status, margin(top = 18, bottom = 14))
        page.addView(sectionTitle("1 · Escanear"))
        page.addView(TextView(this).apply {
            text = "Activa NFC en Ajustes y acerca una tarjeta al área NFC del teléfono."
            textSize = 14f; setTextColor(muted); setPadding(0, dp(6), 0, dp(12))
        })
        page.addView(button("Preparar lectura").apply {
            setOnClickListener {
                writeArmed = false; writeButton.text = "Armar escritura"
                status.text = "Listo para leer. Acerca la tarjeta o el llavero."
            }
        })
        page.addView(sectionTitle("2 · Resultado del análisis"))
        result = TextView(this).apply {
            text = "Aún no hay una tarjeta leída."; textSize = 13f; setTextColor(white)
            setPadding(dp(14), dp(14), dp(14), dp(14)); setBackgroundColor(panel)
            typeface = Typeface.MONOSPACE
        }
        page.addView(result, margin(top = 8, bottom = 12))
        page.addView(sectionTitle("3 · Escribir una etiqueta autorizada"))
        page.addView(TextView(this).apply {
            text = "Introduce texto o URL, arma la escritura y acerca solo una etiqueta destino que tengas autorización para modificar."
            textSize = 13f; setTextColor(muted); setPadding(0, dp(6), 0, dp(10))
        })
        payload = EditText(this).apply {
            setText("https://"); textSize = 15f; setTextColor(white); setHintTextColor(muted)
            hint = "Texto o URL"; setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(panel); minLines = 2; gravity = Gravity.TOP
        }
        page.addView(payload, margin(bottom = 10))
        writeButton = button("Armar escritura")
        writeButton.setOnClickListener {
            if (payload.text.toString().isBlank()) {
                Toast.makeText(this, "Escribe un texto o una URL primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            writeArmed = !writeArmed
            writeButton.text = if (writeArmed) "Cancelar escritura" else "Armar escritura"
            status.text = if (writeArmed) "ESCRITURA ARMADA: acerca la etiqueta destino autorizada." else "Escritura cancelada."
        }
        page.addView(writeButton)
        page.addView(sectionTitle("4 · Emulación en el teléfono"))
        page.addView(TextView(this).apply {
            text = "Incluye un perfil HCE de demostración propio. No copia UID ni suplanta credenciales de acceso o transporte."
            textSize = 13f; setTextColor(muted); setPadding(0, dp(6), 0, dp(8))
        })
        page.addView(button("Comprobar configuración HCE").apply {
            setOnClickListener {
                val supported = packageManager.hasSystemFeature("android.hardware.nfc.hce")
                result.text = "Emulación HCE\nSoporte del dispositivo: ${if (supported) "disponible" else "no detectado"}\nAID de prueba: F0010203040506\nRespuesta: NFC STUDIO DEMO\n\nNo es una copia de una tarjeta física."
                status.text = "Perfil HCE de demostración consultado."
            }
        })
        page.addView(sectionTitle("Historial local"))
        historyView = TextView(this).apply {
            textSize = 12f; setTextColor(muted); setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(panel)
        }
        page.addView(historyView, margin(top = 8, bottom = 10))
        page.addView(button("Borrar historial").apply {
            setOnClickListener { prefs.edit().remove("history").apply(); historyView.text = "Sin lecturas guardadas." }
        })
        historyView.text = prefs.getString("history", "Sin lecturas guardadas.") ?: "Sin lecturas guardadas."
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action in listOf(NfcAdapter.ACTION_TAG_DISCOVERED, NfcAdapter.ACTION_TECH_DISCOVERED, NfcAdapter.ACTION_NDEF_DISCOVERED)) {
            @Suppress("DEPRECATION")
            val tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) as? Tag
            if (tag != null) inspectTag(tag)
        }
    }

    private fun inspectTag(tag: Tag) {
        val id = tag.id?.joinToString("") { "%02X".format(it) } ?: "no expuesto"
        val techs = tag.techList.map { it.substringAfterLast('.') }
        val out = StringBuilder()
        out.append("ID expuesto: ").append(id).append('\n')
        out.append("Tecnologías: ").append(techs.joinToString(", ")).append('\n')
        var ndefRead = false
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                out.append("NDEF: capacidad ").append(ndef.maxSize).append(" bytes; ")
                    .append(if (ndef.isWritable) "escribible" else "solo lectura").append('\n')
                val message = ndef.ndefMessage
                if (message != null) {
                    out.append("Registros NDEF:\n")
                    message.records.forEachIndexed { i, rec -> out.append("  ").append(i + 1).append(". ").append(describeRecord(rec)).append('\n') }
                    ndefRead = true
                } else out.append("Sin mensaje NDEF almacenado\n")
                ndef.close()
            } else out.append("No se detectó NDEF estándar; puede ser propietaria, protegida o no compatible.\n")
        } catch (e: Exception) {
            out.append("NDEF no accesible: ").append(e.message ?: e.javaClass.simpleName).append('\n')
            out.append("Puede requerir autenticación o protocolo específico.\n")
        }
        if (MifareUltralight.get(tag) != null) out.append("Familia MIFARE Ultralight detectada.\n")
        if (IsoDep.get(tag) != null) out.append("ISO-DEP disponible; no implica que se pueda clonar la credencial.\n")
        if (NfcA.get(tag) != null) out.append("Capa NFC-A detectada.\n")
        if (NfcB.get(tag) != null) out.append("Capa NFC-B detectada.\n")
        if (NfcF.get(tag) != null) out.append("Capa NFC-F detectada.\n")
        if (NfcV.get(tag) != null) out.append("Capa NFC-V detectada.\n")
        result.text = out.toString()
        status.text = if (writeArmed) "Etiqueta detectada con escritura armada." else "Lectura completada."
        addHistory("ID $id · ${techs.joinToString("/")} ${if (ndefRead) "· NDEF" else ""}")
        if (writeArmed) writeToTag(tag)
    }

    private fun writeToTag(tag: Tag) {
        val text = payload.text.toString()
        val record = if (text.startsWith("https://") || text.startsWith("http://")) NdefRecord.createUri(text)
            else NdefRecord.createTextRecord("es", text)
        val message = NdefMessage(arrayOf(record))
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) throw IllegalStateException("La etiqueta es de solo lectura.")
                if (ndef.maxSize < message.toByteArray().size) throw IllegalStateException("Capacidad insuficiente.")
                ndef.writeNdefMessage(message); ndef.close()
            } else {
                val fmt = NdefFormatable.get(tag) ?: throw IllegalStateException("La etiqueta no admite escritura NDEF estándar.")
                fmt.connect(); fmt.format(message); fmt.close()
            }
            writeArmed = false; writeButton.text = "Armar escritura"
            status.text = "Escritura completada. Retira y vuelve a acercar la etiqueta para verificar."
            Toast.makeText(this, "Etiqueta NDEF escrita", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            status.text = "No se pudo escribir: ${e.message ?: e.javaClass.simpleName}"
            Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
            writeArmed = false; writeButton.text = "Armar escritura"
        }
    }

    private fun describeRecord(record: NdefRecord): String = try {
        when {
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                val p = record.payload; val len = p[0].toInt() and 0x3F
                "Texto: " + String(p, 1 + len, p.size - 1 - len, Charsets.UTF_8)
            }
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI) -> "URI: ${record.toUri()}"
            else -> "TNF=${record.tnf}, tipo=${record.type.decodeToString()}, ${record.payload.size} bytes"
        }
    } catch (_: Exception) { "Registro binario, ${record.payload.size} bytes" }

    private fun addHistory(entry: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val old = prefs.getString("history", "") ?: ""
        val value = ("$stamp · $entry\n$old").take(3000)
        prefs.edit().putString("history", value).apply()
        historyView.text = value.ifBlank { "Sin lecturas guardadas." }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }

    override fun onResume() {
        super.onResume(); refreshStatus()
        val nfc = adapter ?: return
        if (nfc.isEnabled) {
            val pending = PendingIntent.getActivity(this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED), IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED), IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED))
            val techLists = arrayOf(arrayOf(Ndef::class.java.name), arrayOf(NdefFormatable::class.java.name), arrayOf(NfcA::class.java.name), arrayOf(IsoDep::class.java.name), arrayOf(MifareUltralight::class.java.name))
            try { nfc.enableForegroundDispatch(this, pending, filters, techLists) } catch (_: Exception) {}
        }
    }
    override fun onPause() { super.onPause(); try { adapter?.disableForegroundDispatch(this) } catch (_: Exception) {} }

    private fun refreshStatus() {
        status.text = when {
            adapter == null -> "No se detecta adaptador NFC; verifica las especificaciones del teléfono."
            adapter?.isEnabled != true -> "NFC apagado. Actívalo en Ajustes para comenzar."
            else -> "NFC disponible · Sin root · Listo para detectar etiquetas"
        }
    }
    private fun sectionTitle(s: String) = TextView(this).apply {
        text = s; textSize = 17f; setTextColor(white); typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(20), 0, dp(2))
    }
    private fun button(s: String) = Button(this).apply {
        text = s; isAllCaps = false; textSize = 14f; setTextColor(white); setBackgroundColor(accent); minHeight = dp(48)
    }
    private fun margin(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
