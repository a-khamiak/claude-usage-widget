package io.github.akhamiak.claudeusage

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors

/** Single setup screen (§3.5): sign in, status, refresh, usage page, sign out, about. */
class SetupActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var signIn: Button
    private lateinit var codeField: EditText
    private lateinit var complete: Button
    private lateinit var signOut: Button

    private val tokens by lazy { KeystoreTokenStore(applicationContext) }
    private val statusStore by lazy { StatusStore(applicationContext) }
    private val configs by lazy { ConfigStore(applicationContext) }
    private fun auth() = configs.current().let { cfg -> Auth(tokens, Http(), { cfg }) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OP) == OP_CHECK_FIX) {
            intent.removeExtra(EXTRA_OP)
            checkFix()
        }
    }

    // ---- actions ----

    private fun startSignIn() {
        val scopes = if (statusStore.load().scopeUpgradeNeeded) "user:profile user:inference" else configs.current().scopes
        io {
            val url = auth().startLogin(scopes)
            ui {
                codeField.visibility = View.VISIBLE
                complete.visibility = View.VISIBLE
                open(url)
            }
        }
    }

    private fun completeSignIn() {
        val pasted = codeField.text.toString()
        status.text = "SIGNING IN…"
        io {
            val msg = when (val r = auth().completeLogin(pasted)) {
                LoginResult.Ok -> {
                    val s = statusStore.load()
                    statusStore.save(
                        s.copy(
                            everSignedIn = true, lastError = LastError.NONE, invalidStreak = 0,
                            backoffLevel = 0, nextAllowedAt = 0, scopeUpgradeNeeded = false, lastResult = "SIGNED IN",
                        ),
                    )
                    Scheduler.ensure(applicationContext)
                    Engine.run(applicationContext, Reason.SIGN_IN)
                    null
                }
                LoginResult.NoPendingLogin -> "Tap “Sign in with Claude” first."
                LoginResult.StateMismatch -> "That code doesn’t match this sign-in. Paste the whole code, including the part after #."
                LoginResult.Rejected -> "The code was refused (expired or already used). Sign in again."
                LoginResult.RateLimited -> "Rate-limited. Try again in a few minutes."
                LoginResult.Offline -> "No connection."
                is LoginResult.Failed -> "Sign-in failed (HTTP ${r.code})."
            }
            ui {
                if (msg == null) {
                    codeField.setText("")
                    codeField.visibility = View.GONE
                    complete.visibility = View.GONE
                }
                refreshStatus(msg)
            }
        }
    }

    private fun refreshNow() {
        val s = statusStore.load()
        val cooldown = configs.current().polling.tapCooldownS * 1000L
        if (System.currentTimeMillis() - s.lastFetchAt < cooldown) {
            refreshStatus("Refreshed less than ${cooldown / 60_000} min ago; try again shortly.")
            return
        }
        status.text = "REFRESHING…"
        io {
            Engine.run(applicationContext, Reason.TAP)
            ui { refreshStatus() }
        }
    }

    /** From the UPDATE state: fetch config now and retry; if still broken, open the Releases page. Cooldown-gated. */
    private fun checkFix() {
        val s = statusStore.load()
        if (System.currentTimeMillis() - s.lastFetchAt < configs.current().polling.tapCooldownS * 1000L) return
        status.text = "CHECKING FOR A FIX…"
        io {
            Engine.run(applicationContext, Reason.CHECK_FIX)
            val still = Widgets.currentState(applicationContext) == WidgetState.EndpointChanged
            ui {
                refreshStatus(if (still) "Still broken. Opening the Releases page for an update." else "Fixed.")
                if (still) open(ConfigStore.RELEASES_URL)
            }
        }
    }

    private fun doSignOut() {
        io {
            tokens.clear()
            statusStore.reset()
            Widgets.renderAll(applicationContext)
            ui {
                codeField.visibility = View.GONE
                complete.visibility = View.GONE
                refreshStatus("Signed out. Tokens wiped from this phone.")
            }
        }
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            refreshStatus("No browser found.")
        }
    }

    // ---- status ----

    private fun refreshStatus(note: String? = null) {
        io {
            val s = statusStore.load()
            val has = tokens.hasTokens()
            val t = if (has) tokens.load() else null
            val now = System.currentTimeMillis()
            val lines = mutableListOf<String>()
            if (t != null) {
                lines += "SIGNED IN · TOKEN ${tokens.maskedTail() ?: ""}"
                lines += "SCOPE: ${t.scope}"
            } else {
                lines += "NOT SIGNED IN"
            }
            if (s.lastFetchAt > 0) {
                lines += "LAST RESULT: ${s.lastResult} · ${Format.age(now - s.lastFetchAt)}"
            }
            s.reading?.let { lines += "SESSION ${it.sessionPct}% · WEEK ${it.weekPct}%" }
            if (s.scopeUpgradeNeeded) {
                lines += ""
                lines += "The usage call was refused with user:profile only. Sign in again to add user:inference."
            }
            if (note != null) {
                lines += ""
                lines += note
            }
            ui {
                status.text = lines.joinToString("\n")
                signIn.text = when {
                    s.scopeUpgradeNeeded -> "Sign in again (adds user:inference)"
                    t != null -> "Sign in again"
                    else -> "Sign in with Claude"
                }
                signOut.isEnabled = has
            }
        }
    }

    // ---- UI (built in code: one screen, no layout files) ----

    private fun buildUi(): View {
        val pad = dp(20)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        col.addView(text("Claude usage", 22f, WHITE, bold = true))
        col.addView(space(8))
        status = text("", 14f, LABEL).apply { typeface = Typeface.MONOSPACE }
        col.addView(status)
        col.addView(space(16))

        signIn = button("Sign in with Claude") { startSignIn() }
        col.addView(signIn)
        codeField = EditText(this).apply {
            hint = "Paste the code from the browser"
            setTextColor(WHITE)
            setHintTextColor(GREY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            isSingleLine = true
            visibility = View.GONE
        }
        col.addView(codeField)
        complete = button("Done") { completeSignIn() }.apply { visibility = View.GONE }
        col.addView(complete)
        col.addView(space(8))
        col.addView(button("Refresh now") { refreshNow() })
        col.addView(button("Open usage page") { open("https://claude.ai/settings/usage") })
        signOut = button("Sign out") { doSignOut() }
        col.addView(signOut)
        col.addView(space(24))
        col.addView(
            text(
                "Version ${BuildConfig.VERSION_NAME} · MIT licence\n" +
                    "Sign-in and token code adapted from Claude Pulse by Ghayyath Huzayen (MIT).\n" +
                    ConfigStore.REPO_URL,
                12f, GREY,
            ),
        )
        val scroll = ScrollView(this).apply {
            setBackgroundColor(SURFACE)
            addView(col)
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        return scroll
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    private fun space(dpH: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(dpH))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun io(block: () -> Unit) = EXECUTOR.execute(block)
    private fun ui(block: () -> Unit) = runOnUiThread { if (!isDestroyed) block() }

    companion object {
        const val EXTRA_OP = "op"
        const val OP_CHECK_FIX = "check_fix"
        private val EXECUTOR = Executors.newSingleThreadExecutor()
        private const val SURFACE = 0xFF111112.toInt()
        private const val WHITE = 0xFFF2F2F2.toInt()
        private const val LABEL = 0xFFBDBDBD.toInt()
        private const val GREY = 0xFF7A7A7A.toInt()
    }
}
