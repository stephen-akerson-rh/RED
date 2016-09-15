/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.tableeditor;

import java.util.ArrayDeque;
import java.util.Deque;

import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.ui.PlatformUI;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.EditorCommand.CommandExecutionException;

public class RobotEditorCommandsStack {
    
    private static final int COMMANDS_STACK_MAX_SIZE = 100;

    private final Deque<EditorCommand> _executedCommands = new ArrayDeque<>();

    private final Deque<EditorCommand> _toRedoCommands = new ArrayDeque<>();

    public void execute(final EditorCommand command) throws CommandExecutionException {
        final IEclipseContext context = ((IEclipseContext) PlatformUI.getWorkbench().getService(IEclipseContext.class))
                .getActiveLeaf();
        ContextInjectionFactory.inject(command, context);
        command.execute();
        
        if(_executedCommands.size() > COMMANDS_STACK_MAX_SIZE) {
            _executedCommands.removeLast();
        }

        _executedCommands.push(command);
        clear(_toRedoCommands);
    }

    public boolean isUndoPossible() {
        return !_executedCommands.isEmpty();
    }

    public void undo() {
        if (isUndoPossible()) {
            final EditorCommand commandToUndo = _executedCommands.pop();
            executeUndoCommand(commandToUndo.getUndoCommand(), _toRedoCommands);
        }
    }

    public boolean isRedoPossible() {
        return !_toRedoCommands.isEmpty();
    }

    public void redo() {
        if (isRedoPossible()) {
            final EditorCommand commandToRedo = _toRedoCommands.pop();
            executeUndoCommand(commandToRedo.getUndoCommand(), _executedCommands);
        }
    }

    public void clear() {
        clear(_toRedoCommands);
        clear(_executedCommands);
    }

    private void clear(final Deque<EditorCommand> stackToClear) {
        while (!stackToClear.isEmpty()) {
            final IEclipseContext context = ((IEclipseContext) PlatformUI.getWorkbench()
                    .getService(IEclipseContext.class)).getActiveLeaf();
            final EditorCommand command = stackToClear.pop();
            ContextInjectionFactory.uninject(command, context);
        }
    }

    private void executeUndoCommand(final EditorCommand command, final Deque<EditorCommand> commandsDestinationStack) {
        command.execute();
        commandsDestinationStack.push(command);
    }
}
