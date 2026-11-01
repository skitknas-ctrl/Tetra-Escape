package io.github.skitknasctrl.tetraescape

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.github.skitknasctrl.tetraescape.engine.GameState
import io.github.skitknasctrl.tetraescape.engine.Generator
import io.github.skitknasctrl.tetraescape.engine.Level
import io.github.skitknasctrl.tetraescape.engine.Status
import io.github.skitknasctrl.tetraescape.ui.GameView
import java.util.concurrent.Executors
import kotlin.random.Random

class MainActivity : AppCompatActivity(), GameView.Listener {

    private lateinit var gameView: GameView
    private lateinit var stepsView: TextView
    private lateinit var timeView: TextView
    private lateinit var levelView: TextView
    private lateinit var difficultyGroup: RadioGroup

    private val executor = Executors.newSingleThreadExecutor()
    private var timer: CountDownTimer? = null
    private var timeLeftMs = 0L
    private var level: Level? = null
    private var difficulty = 1
    private var finished = false
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        gameView = findViewById(R.id.game)
        stepsView = findViewById(R.id.steps)
        timeView = findViewById(R.id.time)
        levelView = findViewById(R.id.level)
        difficultyGroup = findViewById(R.id.difficulty)
        gameView.listener = this

        val prefs = getSharedPreferences("tetra", MODE_PRIVATE)
        difficulty = prefs.getInt("difficulty", 1)
        difficultyGroup.check(
            when (difficulty) { 2 -> R.id.diff2; 3 -> R.id.diff3; else -> R.id.diff1 }
        )
        difficultyGroup.setOnCheckedChangeListener { _, id ->
            difficulty = when (id) { R.id.diff2 -> 2; R.id.diff3 -> 3; else -> 1 }
            prefs.edit().putInt("difficulty", difficulty).apply()
            newLevel(randomSeed())
        }
        findViewById<Button>(R.id.newLevel).setOnClickListener { newLevel(randomSeed()) }
        findViewById<Button>(R.id.retry).setOnClickListener { level?.let { startLevel(it) } }
        findViewById<Button>(R.id.help).setOnClickListener { showHelp() }
        levelView.setOnClickListener { askForSeed() }

        val seed = savedInstanceState?.getLong("seed") ?: randomSeed()
        newLevel(seed)
        if (!prefs.getBoolean("helpShown", false)) {
            prefs.edit().putBoolean("helpShown", true).apply()
            showHelp()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        level?.let { outState.putLong("seed", it.seed) }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
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
        timer?.cancel(); timer = null
        level = lvl
        finished = false
        timeLeftMs = lvl.timeSeconds * 1000L
        gameView.setLevel(lvl)
        levelView.text = getString(R.string.level_seed, lvl.seed, difficultyName(lvl.difficulty))
        updateHud(gameView.state!!)
        timeView.text = getString(R.string.time_value, lvl.timeSeconds)
    }

    private fun difficultyName(d: Int): String = getString(
        when (d) { 2 -> R.string.difficulty_medium; 3 -> R.string.difficulty_hard; else -> R.string.difficulty_easy }
    )

    private fun updateHud(st: GameState) {
        stepsView.text = st.stepsLeft.toString()
    }

    // ---- GameView.Listener ----

    override fun onFirstMove() {
        val lvl = level ?: return
        timer?.cancel()
        timer = object : CountDownTimer(lvl.timeSeconds * 1000L, 100) {
            override fun onTick(ms: Long) {
                timeLeftMs = ms
                timeView.text = getString(R.string.time_value, (ms + 999) / 1000)
            }
            override fun onFinish() {
                timeLeftMs = 0
                timeView.text = getString(R.string.time_value, 0)
                if (!finished) {
                    finished = true
                    showEnd(getString(R.string.lost_time_title), getString(R.string.lost_message), won = false)
                }
            }
        }.start()
    }

    override fun onMoved(state: GameState) {
        updateHud(state)
        if (finished) return
        when (state.status) {
            Status.WON -> {
                finished = true
                timer?.cancel()
                val secs = (timeLeftMs / 1000).toInt()
                showEnd(getString(R.string.won_title), getString(R.string.won_message, state.moves, secs), won = true)
            }
            Status.LOST -> {
                finished = true
                timer?.cancel()
                showEnd(getString(R.string.lost_steps_title), getString(R.string.lost_message), won = false)
            }
            Status.PLAYING -> {}
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
                val text = getString(
                    R.string.share_text, lvl.seed, lvl.difficulty, st?.moves ?: 0, (timeLeftMs / 1000).toInt(),
                )
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

    private fun showHelp() {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.rules_title)
            .setMessage(R.string.rules_text)
            .setPositiveButton(android.R.string.ok, null)
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
