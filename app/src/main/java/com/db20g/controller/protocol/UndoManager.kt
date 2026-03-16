package com.db20g.controller.protocol

/**
 * Undo/redo manager for channel edits using the Command pattern.
 * Stores snapshots of the entire channel list for simplicity and reliability.
 */
class UndoManager(private val maxHistory: Int = 50) {

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()

    data class Snapshot(
        val channels: List<RadioChannel>,
        val description: String
    )

    /**
     * Save the current state before making changes.
     * Call this BEFORE modifying channels.
     */
    fun saveState(channels: List<RadioChannel>, description: String) {
        undoStack.addLast(Snapshot(channels.map { it.copy() }, description))
        if (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        // Clear redo stack on new edit
        redoStack.clear()
    }

    /**
     * Undo the last change. Returns the previous channel list, or null if nothing to undo.
     */
    fun undo(currentChannels: List<RadioChannel>): Snapshot? {
        if (undoStack.isEmpty()) return null
        val previous = undoStack.removeLast()
        // Save current state for redo
        redoStack.addLast(Snapshot(currentChannels.map { it.copy() }, previous.description))
        return previous
    }

    /**
     * Redo the last undone change. Returns the restored channel list, or null if nothing to redo.
     */
    fun redo(currentChannels: List<RadioChannel>): Snapshot? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeLast()
        // Save current state for undo
        undoStack.addLast(Snapshot(currentChannels.map { it.copy() }, next.description))
        return next
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    val undoDescription: String? get() = undoStack.lastOrNull()?.description
    val redoDescription: String? get() = redoStack.lastOrNull()?.description

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
