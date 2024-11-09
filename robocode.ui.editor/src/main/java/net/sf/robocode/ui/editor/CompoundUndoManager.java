/*
 * Copyright (c) 2001-2023 Mathew A. Nelson and Robocode contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://robocode.sourceforge.io/license/epl-v10.html
 */
package net.sf.robocode.ui.editor;


import javax.swing.event.UndoableEditEvent;

import java.lang.reflect.Field;

import javax.swing.event.DocumentEvent.EventType;
import javax.swing.text.AbstractDocument.DefaultDocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoableEdit;


/**
 * Undo manager that compounds undo and redo edits.
 * 
 * @author Flemming N. Larsen (original)
 */
@SuppressWarnings("serial")
public class CompoundUndoManager extends UndoManagerWithActions {

	private CompoundEdit currentCompoundEdit;
	private EventType lastEventType;
	private boolean isCompoundMarkStart;

	public CompoundUndoManager() {
		super();
		reset();
	}

	@Override
	public void undoableEditHappened(UndoableEditEvent undoableEditEvent) {
		UndoableEdit edit = undoableEditEvent.getEdit();
		DefaultDocumentEvent event = getDocumentEvent(edit);

		if (event != null) {
			handleDocumentEvent(event, edit);
		}

		// Update the state of the actions
		updateUndoRedoState();
	}

	@Override
	public void discardAllEdits() {
		super.discardAllEdits();
		reset();
	}

	/**
	 * Retrieves the DefaultDocumentEvent from the UndoableEdit if it exists.
	 *
	 * @param edit the undoable edit event
	 * @return the corresponding DefaultDocumentEvent, or null if not found
	 */
	private DefaultDocumentEvent getDocumentEvent(UndoableEdit edit) {
		if (edit instanceof DefaultDocumentEvent) {
			return (DefaultDocumentEvent) edit;
		}

		try {
			return extractDocumentEventFromWrapper(edit);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Attempts to extract the DefaultDocumentEvent from an UndoableEdit wrapper via reflection.
	 *
	 * @param edit the undoable edit event
	 * @return the DefaultDocumentEvent inside the wrapper, or null if not found
	 * @throws Exception if the reflection fails
	 */
	private DefaultDocumentEvent extractDocumentEventFromWrapper(UndoableEdit edit) throws Exception {
		Class<?> clazz = Class.forName("javax.swing.text.AbstractDocument$DefaultDocumentEventUndoableWrapper");
		if (UndoableEdit.class.isAssignableFrom(clazz)) {
			Field f = clazz.getDeclaredField("dde"); // DefaultDocumentEvent
			f.setAccessible(true);
			return (DefaultDocumentEvent) f.get(edit);
		}
		return null;
	}

	/**
	 * Processes the given document event, managing compound edits based on the event type.
	 *
	 * @param event the document event
	 * @param edit the undoable edit event
	 */
	private void handleDocumentEvent(DefaultDocumentEvent event, UndoableEdit edit) {
		EventType eventType = event.getType();

		if (eventType != EventType.CHANGE) {
			boolean isEndCompoundEdit = shouldEndCompoundEdit(eventType, event);

			if (!isCompoundMarkStart) {
				if (isEndCompoundEdit || eventType != lastEventType) {
					endCurrentCompoundEdit();
				}
				lastEventType = eventType;
			}

			if (currentCompoundEdit == null) {
				newCurrentCompoundEdit();
			}
		}

		if (currentCompoundEdit != null) {
			currentCompoundEdit.addEdit(edit);
		}
	}

	/**
	 * Determines whether the current compound edit should be ended based on the event type.
	 *
	 * @param eventType the type of the event (insert, remove, change, etc.)
	 * @param event the document event
	 * @return true if the compound edit should be ended, false otherwise
	 */
	private boolean shouldEndCompoundEdit(EventType eventType, DefaultDocumentEvent event) {
		if (eventType == EventType.INSERT) {
			return containsNewLine(event);
		}
		return false;
	}

	/**
	 * Checks if the inserted text in the document contains a newline character.
	 *
	 * @param event the document event
	 * @return true if the inserted text contains a newline, false otherwise
	 */
	private boolean containsNewLine(DefaultDocumentEvent event) {
		try {
			String insertedText = event.getDocument().getText(event.getOffset(), event.getLength());
			return insertedText.contains("\n");
		} catch (BadLocationException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Ends the current compound edit, and mark the start for combining the next insertions or removals of text to be
	 * put into the same compound edit so that these combined operations acts like as one single edit.
	 *
	 * @see #markCompoundEnd()
	 */
	public void markCompoundStart() {
		endCurrentCompoundEdit();
		isCompoundMarkStart = true;
	}

	/**
	 * Ends the current compound edit so that previous edits acts like a single edit.
	 * 
	 * @see #markCompoundStart()
	 */
	public void markCompoundEnd() {
		endCurrentCompoundEdit();
		isCompoundMarkStart = false;
	}

	private void reset() {
		currentCompoundEdit = null;
		lastEventType = EventType.INSERT; // important
	}

	private void endCurrentCompoundEdit() {
		if (currentCompoundEdit != null) {
			currentCompoundEdit.end();
			currentCompoundEdit = null;
		}
	}

	private void newCurrentCompoundEdit() {
		// Set current compound edit to a new one
		currentCompoundEdit = new CompoundEdit() {
			// Make sure canUndo() and canRedo() works
			@Override
			public boolean isInProgress() {
				return false;
			}

			@Override
			public void undo() throws CannotUndoException {
				endCurrentCompoundEdit();
				super.undo();
			}
		};
		// Add the current compound edit to the internal edits
		addEdit(currentCompoundEdit);
	}
}
