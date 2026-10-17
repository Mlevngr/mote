package com.mlevngr.inknote

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mlevngr.inknote.assets.NoteWorkspace
import com.mlevngr.inknote.library.NoteLibrary
import com.mlevngr.inknote.ui.NoteCanvasZoom
import com.mlevngr.inknote.ui.NoteCanvasZoomLayout
import kotlin.math.abs

/** Dependency-free device smoke suite. Run on an emulator/test device, not a personal note vault. */
class EditorSmokeInstrumentation : Instrumentation() {
    private lateinit var activity: EditorActivity
    private lateinit var canvas: NoteCanvasZoomLayout
    private lateinit var list: RecyclerView
    private var downTime = 0L
    private var eventTime = 0L

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val tests = listOf<Pair<String, () -> Unit>>(
            "fitWidthScrollIsStillForwarded" to ::fitWidthScroll,
            "singleFingerPanAndPointerHandoff" to ::panAndHandoff,
            "longNoteScrollAndTaskRefresh" to ::scrollAndRefresh,
            "modeSwitchUsesReadingPosition" to ::modeSwitch,
            "navigationCancelsPendingFocus" to ::cancelFocus
        )
        var failures = 0
        tests.forEachIndexed { index, (name, test) ->
            val status = Bundle().apply {
                putString("class", this@EditorSmokeInstrumentation.javaClass.name)
                putString("test", name)
                putInt("numtests", tests.size)
                putInt("current", index + 1)
            }
            sendStatus(1, status)
            try {
                openFixture()
                test()
                sendStatus(0, status)
            } catch (error: Throwable) {
                failures++
                status.putString("stack", error.stackTraceToString())
                sendStatus(-2, status)
            } finally {
                if (::activity.isInitialized) runOnMainSync { activity.finish() }
                settle()
            }
        }
        finish(if (failures == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", "\nEditor smoke: ${tests.size - failures}/${tests.size} passed\n")
            putInt("failures", failures)
        })
    }

    private fun openFixture() {
        val location = NoteLibrary.FolderLocation.Root
        val entry = NoteLibrary(targetContext).createNote(location, "Viewport smoke ${System.nanoTime()}")
        NoteWorkspace(targetContext, location, entry.name).save(
            (0 until 160).joinToString("\n") { "- [ ] Task $it" }
        )
        activity = startActivitySync(EditorActivity.intent(targetContext, location, entry.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as EditorActivity
        settle()
        runOnMainSync {
            canvas = activity.findViewById(R.id.note_canvas)
            list = activity.findViewById(R.id.note_content)
            check(list.adapter!!.itemCount >= 160) { "Fixture has not rendered" }
        }
    }

    private fun settle() {
        // Covers row rendering, 100ms preview-quality and 220ms delete debounce tasks.
        repeat(3) {
            SystemClock.sleep(150)
            waitForIdleSync()
        }
    }

    private fun transform(): NoteCanvasZoom.Transform {
        val field = NoteCanvasZoomLayout::class.java.getDeclaredField("transform")
        field.isAccessible = true
        return field.get(canvas) as NoteCanvasZoom.Transform
    }

    private fun event(action: Int, vararg pointers: Triple<Int, Float, Float>) {
        if (action == MotionEvent.ACTION_DOWN) {
            downTime = SystemClock.uptimeMillis()
            eventTime = downTime
        } else eventTime += 16
        val properties = pointers.map { (id, _, _) ->
            MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }.toTypedArray()
        val coordinates = pointers.map { (_, x, y) ->
            MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f }
        }.toTypedArray()
        val event = MotionEvent.obtain(downTime, eventTime, action, pointers.size, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        canvas.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun pinch(scale: Float) {
        val x = canvas.width / 2f
        val y = canvas.height / 2f
        event(MotionEvent.ACTION_DOWN, Triple(7, x - 40f, y))
        event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            Triple(7, x - 40f, y), Triple(12, x + 40f, y))
        event(MotionEvent.ACTION_MOVE, Triple(7, x - 40f * scale, y), Triple(12, x + 40f * scale, y))
    }

    private fun releasePinch() {
        val x = canvas.width / 2f
        val y = canvas.height / 2f
        // Lift index 0, deliberately leaving a nonzero pointer ID to exercise rebasing.
        event(MotionEvent.ACTION_POINTER_UP, Triple(7, x - 80f, y), Triple(12, x + 80f, y))
        event(MotionEvent.ACTION_UP, Triple(12, x + 80f, y))
    }

    private fun drag(dx: Float, dy: Float) {
        val x = canvas.width / 2f
        val y = canvas.height / 2f
        event(MotionEvent.ACTION_DOWN, Triple(3, x, y))
        for (step in 1..10) {
            event(MotionEvent.ACTION_MOVE, Triple(3, x + dx * step / 10, y + dy * step / 10))
        }
        event(MotionEvent.ACTION_UP, Triple(3, x + dx, y + dy))
    }

    private fun fitWidthScroll() {
        runOnMainSync { repeat(4) { drag(0f, -300f) } }
        settle()
        runOnMainSync {
            check(canvas.currentScale == 1f)
            check((list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() > 0)
        }
    }

    private fun panAndHandoff() {
        for (target in listOf(2f, 4f, 6f)) {
            runOnMainSync {
                pinch(target / canvas.currentScale)
                check(abs(canvas.currentScale - target) < 0.01f)
                val before = transform()
                val x = canvas.width / 2f
                val y = canvas.height / 2f
                event(MotionEvent.ACTION_POINTER_UP, Triple(7, x - 80f, y), Triple(12, x + 80f, y))
                check(transform() == before) { "Pointer release jumped" }
                event(MotionEvent.ACTION_MOVE, Triple(12, x + 130f, y + 40f))
                check(abs(transform().translationX - before.translationX - 50f) < 2f)
                check(abs(transform().translationY - before.translationY - 40f) < 2f)
                event(MotionEvent.ACTION_UP, Triple(12, x + 130f, y + 40f))
                val panned = transform()
                drag(-70f, -30f)
                check(abs(transform().translationX - panned.translationX + 70f) < 2f)
            }
        }
    }

    private fun scrollAndRefresh() {
        runOnMainSync { pinch(2f); releasePinch() }
        settle()
        runOnMainSync { repeat(25) { drag(0f, -canvas.height / 2f) } }
        settle()
        runOnMainSync {
            val first = (list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            check(first > 50) { "Zoomed single finger scrolling stuck at row $first" }
        }
        repeat(20) {
            var position = 0
            var top = 0
            runOnMainSync {
                val manager = list.layoutManager as LinearLayoutManager
                position = manager.findFirstVisibleItemPosition()
                top = manager.findViewByPosition(position)!!.top
                checkBox(list).performClick()
            }
            settle()
            runOnMainSync {
                val manager = list.layoutManager as LinearLayoutManager
                check(manager.findFirstVisibleItemPosition() == position) { "Task refresh changed row anchor" }
                check(abs(manager.findViewByPosition(position)!!.top - top) <= 2) { "Task refresh changed offset" }
            }
        }
        runOnMainSync {
            repeat(50) { drag(0f, -canvas.height / 2f) }
        }
        settle()
        runOnMainSync {
            check(!list.canScrollVertically(1)) { "Cannot reach end of long note" }
            repeat(50) { drag(0f, canvas.height / 2f) }
        }
        settle()
        runOnMainSync { check(!list.canScrollVertically(-1)) { "Cannot return to top" } }
    }

    private fun checkBox(view: View): CheckBox {
        if (view is CheckBox) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                runCatching { checkBox(view.getChildAt(i)) }.getOrNull()?.let { return it }
            }
        }
        error("No checkbox visible")
    }

    private fun modeSwitch() {
        runOnMainSync { list.scrollBy(0, 6000) }
        settle()
        var position = 0
        runOnMainSync {
            position = (list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            check(position > 30)
            activity.findViewById<View>(R.id.toggle_mode).performClick()
        }
        settle()
        runOnMainSync {
            val after = (list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            check(abs(after - position) < 8) { "Mode switch jumped from $position to $after" }
        }
        runOnMainSync { activity.findViewById<View>(R.id.toggle_mode).performClick() }
        settle()
        runOnMainSync {
            check((list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() > 20)
        }
    }

    private fun cancelFocus() {
        runOnMainSync {
            activity.findViewById<View>(R.id.toggle_mode).performClick()
            pinch(2f)
            releasePinch()
            repeat(20) { drag(0f, -canvas.height / 2f) }
        }
        settle()
        runOnMainSync {
            check((list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() > 40) {
                "Pending focus pulled viewport back to first line"
            }
        }
        // A background task refresh must not bring the offscreen editor back into view either.
        runOnMainSync { checkBox(list).performClick() }
        settle()
        runOnMainSync {
            check((list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() > 40)
        }
    }
}
