package io.github.skitknasctrl.tetraescape

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.skitknasctrl.tetraescape.engine.GameState
import io.github.skitknasctrl.tetraescape.engine.Generator
import io.github.skitknasctrl.tetraescape.engine.Level
import io.github.skitknasctrl.tetraescape.engine.Status
import io.github.skitknasctrl.tetraescape.ui.GameView
import java.util.concurrent.Executors
import kotlin.random.Random

class MainActivity : AppCompatActivity(), GameView.Listener {

    private lateinit var gameView: GameView
    private lateinit var movesView: TextView
    private lateinit var parView: TextView
    private lateinit var levelView: TextView
    private lateinit var keysView: TextView
    private lateinit var prefs: SharedPreferences

    private val executor = Executors.newSingleThreadExecutor()
    private var level: Level? = null
    private var difficulty = 1
    private var finished = false
    private var replayShown = false
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Android 15 draws apps edge to edge: keep our content clear of the status bar and navigation bar.
        val root = findViewById<View>(R.id.root)
        val basePad = root.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(basePad + bars.left, basePad + bars.top, basePad + bars.right, basePad + bars.bottom)
            insets
        }
        gameView = findViewById(R.id.game)
        movesView = findViewById(R.id.moves)
        parView = findViewById(R.id.par)
        levelView = findViewById(R.id.level)
        keysView = findViewById(R.id.keys)
        gameView.listener = this

        prefs = getSharedPreferences("tetra", MODE_PRIVATE)
        difficulty = prefs.getInt("difficulty", 1)
        gameView.showPreview = prefs.getBoolean("preview", true)
        findViewById<View>(R.id.newLevel).setOnClickListener { newLevel(randomSeed()) }
        findViewById<View>(R.id.retry).setOnClickListener { level?.let { startLevel(it) } }
        findViewById<View>(R.id.help).setOnClickListener { showHelp() }
        findViewById<View>(R.id.settings).setOnClickListener { showSettings() }
        levelView.setOnClickListener { askForSeed() }

        val seed = savedInstanceState?.getLong("seed") ?: randomSeed()
        newLevel(seed)
        if (!prefs.getBoolean("helpShown2", false)) {
            prefs.edit().putBoolean("helpShown2", true).apply()
            showHelp()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        level?.let { outState.putLong("seed", it.seed) }
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
        executor.shutdownNow()
    }

    private fun randomSeed(): Long = Random.nextLong(1, 1_000_000)

    private fun newLevel(seed: Long) {
        val d = difficulty
        levelView.text = getString(R.string.generating)
        executor.execute {
            val lvl = Generator.generate(seed, d)
            runOnUiThread { if (!isDestroyed) startLevel(lvl) }
        }
    }

    private fun startLevel(lvl: Level) {
        dialog?.dismiss()
        level = lvl
        finished = false
        replayShown = false
        gameView.setLevel(lvl)
        levelView.text = getString(R.string.level_seed, lvl.seed, difficultyName(lvl.difficulty))
        parView.text = lvl.par.toString()
        updateHud(gameView.state!!)
    }

    private fun difficultyName(d: Int): String = getString(
        when (d) { 2 -> R.string.difficulty_medium; 3 -> R.string.difficulty_hard; else -> R.string.difficulty_easy }
    )

    private fun updateHud(st: GameState) {
        movesView.text = st.moves.toString()
        keysView.text = getString(R.string.keys_value, st.keys, st.level.keyCount)
    }

    // ---- GameView.Listener ----

    override fun onMoved(state: GameState) {
        updateHud(state)
        if (finished) return
        if (state.status == Status.WON) {
            finished = true
            val lvl = state.level
            if (gameView.isAutoPlaying || replayShown) {
                replayShown = false
                showEnd(getString(R.string.solution_title), getString(R.string.solution_done, state.moves), won = false)
                return
            }
            val msg = when {
                state.moves <= lvl.par -> getString(R.string.won_perfect, state.moves)
                else -> getString(R.string.won_message, state.moves, lvl.par)
            }
            showEnd(getString(R.string.won_title), msg, won = true)
        }
    }

    private fun showEnd(title: String, message: String, won: Boolean) {
        val lvl = level ?: return
        val info = getString(R.string.level_info, lvl.seed, difficultyName(lvl.difficulty))
        val b = AlertDialog.Builder(this).setTitle(title).setMessage(message + "\n\n" + info).setCancelable(true)
        if (won) {
            b.setPositiveButton(R.string.next_level) { _, _ -> newLevel(randomSeed()) }
            b.setNeutralButton(R.string.share) { _, _ ->
                val st = gameView.state
                val text = getString(R.string.share_text, lvl.seed, difficultyName(lvl.difficulty), st?.moves ?: 0, lvl.par)
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                }, null))
            }
            b.setNegativeButton(R.string.retry) { _, _ -> startLevel(lvl) }
        } else {
            b.setPositiveButton(R.string.retry) { _, _ -> startLevel(lvl) }
            b.setNegativeButton(R.string.new_level) { _, _ -> newLevel(randomSeed()) }
        }
        dialog = b.show()
    }

    private fun showSolution() {
        val lvl = level ?: return
        dialog?.dismiss()
        startLevel(lvl)
        replayShown = true
        levelView.text = getString(R.string.solution_playing)
        gameView.playSolution()
    }

    private fun showSettings() {
        val dp = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), 0)
        }
        box.addView(TextView(this).apply { text = getString(R.string.difficulty); setTextColor(getColor(R.color.ink_soft)) })
        val group = RadioGroup(this)
        val ids = IntArray(3)
        for (d in 1..3) {
            val rb = RadioButton(this).apply { id = View.generateViewId(); text = difficultyName(d) }
            ids[d - 1] = rb.id
            group.addView(rb)
        }
        group.check(ids[difficulty - 1])
        box.addView(group)
        val preview = CheckBox(this).apply {
            text = getString(R.string.preview_option)
            isChecked = gameView.showPreview
            setPadding(paddingLeft, (12 * dp).toInt(), paddingRight, 0)
        }
        box.addView(preview)
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newDifficulty = ids.indexOf(group.checkedRadioButtonId) + 1
                gameView.showPreview = preview.isChecked
                prefs.edit().putBoolean("preview", preview.isChecked).apply()
                gameView.invalidate()
                if (newDifficulty in 1..3 && newDifficulty != difficulty) {
                    difficulty = newDifficulty
                    prefs.edit().putInt("difficulty", difficulty).apply()
                    newLevel(randomSeed())
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showHelp() {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.rules_title)
            .setMessage(R.string.rules_text)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.show_solution) { _, _ -> showSolution() }
            .show()
    }

    private fun askForSeed() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.enter_seed_hint)
            level?.let { setText(it.seed.toString()) }
        }
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.enter_seed_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                input.text.toString().toLongOrNull()?.let { newLevel(it) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
