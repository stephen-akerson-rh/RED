/*
 * Copyright 2017 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.launch.local;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.rf.ide.core.execution.TestsMode;
import org.rf.ide.core.execution.server.AgentConnectionServer;
import org.rf.ide.core.execution.server.AgentServerKeepAlive;
import org.rf.ide.core.execution.server.AgentServerTestsStarter;
import org.rf.ide.core.executor.RobotRuntimeEnvironment;
import org.rf.ide.core.executor.RunCommandLineCallBuilder.RunCommandLine;
import org.robotframework.ide.eclipse.main.plugin.debug.model.RobotDebugTarget;
import org.robotframework.ide.eclipse.main.plugin.launch.AgentConnectionServerJob;
import org.robotframework.ide.eclipse.main.plugin.launch.DebugExecutionEventsListener;
import org.robotframework.ide.eclipse.main.plugin.launch.ExecutionTrackerForExecutionView;
import org.robotframework.ide.eclipse.main.plugin.launch.IRobotProcess;
import org.robotframework.ide.eclipse.main.plugin.launch.MessagesTrackerForLogView;
import org.robotframework.ide.eclipse.main.plugin.launch.RobotConsoleFacade;
import org.robotframework.ide.eclipse.main.plugin.launch.RobotConsolePatternsListener;
import org.robotframework.ide.eclipse.main.plugin.launch.RobotEventBroker;
import org.robotframework.ide.eclipse.main.plugin.launch.RobotLaunchInMode;
import org.robotframework.ide.eclipse.main.plugin.model.RobotProject;

class RobotLaunchInDebugMode extends RobotLaunchInMode {

    private final RobotEventBroker robotEventBroker;

    public RobotLaunchInDebugMode(final RobotEventBroker robotEventBroker) {
        this.robotEventBroker = robotEventBroker;
    }

    @Override
    protected Process launchAndAttachToProcess(final RobotLaunchConfiguration robotConfig, final ILaunch launch,
            final IProgressMonitor monitor) throws CoreException, IOException {

        final RobotProject robotProject = robotConfig.getRobotProject();
        final RobotRuntimeEnvironment runtimeEnvironment = getRobotRuntimeEnvironment(robotProject);

        final RunCommandLine cmdLine = prepareCommandLine(robotConfig);
        if (cmdLine.getPort() < 0) {
            throw newCoreException("Unable to find free port");
        }

        final String host = "127.0.0.1";
        final int port = cmdLine.getPort();

        final AgentServerKeepAlive keepAliveListener = new AgentServerKeepAlive();
        final AgentServerTestsStarter testsStarter = new AgentServerTestsStarter(TestsMode.DEBUG);

        final RobotDebugTarget debugTarget = new RobotDebugTarget("Robot Test at " + host + ":" + port, launch);

        try {
            AgentConnectionServerJob.setupServerAt(host, port)
                    .withConnectionTimeout(AgentConnectionServer.CLIENT_CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                    .agentEventsListenedBy(keepAliveListener)
                    .agentEventsListenedBy(testsStarter)
                    .agentEventsListenedBy(
                            new DebugExecutionEventsListener(debugTarget, robotConfig.getResourcesUnderDebug()))
                    .agentEventsListenedBy(new MessagesTrackerForLogView())
                    .agentEventsListenedBy(new ExecutionTrackerForExecutionView(robotEventBroker))
                    .start()
                    .waitForServer();

            final String processLabel = robotConfig.createConsoleDescription(runtimeEnvironment);
            final String version = robotConfig.createExecutorVersion(runtimeEnvironment);

            final Process process = execProcess(cmdLine, robotConfig);
            final IRobotProcess robotProcess = (IRobotProcess) DebugPlugin.newProcess(launch, process, processLabel);

            final RobotConsoleFacade redConsole = robotProcess.provideConsoleFacade(processLabel);
            redConsole.addHyperlinksSupport(new RobotConsolePatternsListener(robotProject));
            redConsole.writeLine("Command: " + cmdLine.show());
            redConsole.writeLine("Suite Executor: " + version);

            debugTarget.connectWith(robotProcess);

            testsStarter.allowClientTestsStart();
            return process;
        } catch (final InterruptedException e) {
            throw newCoreException("Interrupted when waiting for remote connection server", e);
        }
    }
}