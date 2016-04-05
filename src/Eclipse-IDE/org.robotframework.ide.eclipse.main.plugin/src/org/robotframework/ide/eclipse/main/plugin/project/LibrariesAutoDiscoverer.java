/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.project;

import static com.google.common.collect.Lists.newArrayList;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.rf.ide.core.dryrun.RobotDryRunHandler;
import org.rf.ide.core.dryrun.RobotDryRunLibraryImport;
import org.rf.ide.core.dryrun.RobotDryRunLibraryImport.DryRunLibraryImportStatus;
import org.rf.ide.core.dryrun.RobotDryRunLibraryImport.DryRunLibraryType;
import org.rf.ide.core.dryrun.RobotDryRunOutputParser;
import org.rf.ide.core.executor.ILineHandler;
import org.rf.ide.core.executor.RobotRuntimeEnvironment.RobotEnvironmentException;
import org.rf.ide.core.executor.RunCommandLineCallBuilder.RunCommandLine;
import org.robotframework.ide.eclipse.main.plugin.PathsConverter;
import org.robotframework.ide.eclipse.main.plugin.RedPlugin;
import org.robotframework.ide.eclipse.main.plugin.launch.RobotLaunchConfigurationDelegate;
import org.robotframework.ide.eclipse.main.plugin.model.RobotProject;
import org.robotframework.ide.eclipse.main.plugin.model.RobotSuiteFile;
import org.robotframework.ide.eclipse.main.plugin.project.RobotProjectConfig.ReferencedLibrary;
import org.robotframework.ide.eclipse.main.plugin.project.editor.libraries.JarStructureBuilder;
import org.robotframework.ide.eclipse.main.plugin.project.editor.libraries.JarStructureBuilder.JarClass;
import org.robotframework.ide.eclipse.main.plugin.project.editor.libraries.PythonLibStructureBuilder;
import org.robotframework.ide.eclipse.main.plugin.project.editor.libraries.PythonLibStructureBuilder.PythonClass;
import org.robotframework.red.swt.SwtThread;

import com.google.common.io.Files;

/**
 * @author mmarzec
 */
public class LibrariesAutoDiscoverer {

    private IEventBroker eventBroker;

    private RobotProject robotProject;

    private List<IResource> suiteFiles = newArrayList();

    private RobotDryRunOutputParser dryRunOutputParser;

    private RobotDryRunHandler dryRunHandler;

    private boolean isSummaryWindowEnabled;

    private static AtomicBoolean isWorkspaceJobRunning = new AtomicBoolean(false);

    public LibrariesAutoDiscoverer(final RobotProject robotProject, final Collection<IResource> suiteFiles,
            final IEventBroker eventBroker) {
        this(robotProject, suiteFiles, true);
        this.eventBroker = eventBroker;
    }

    public LibrariesAutoDiscoverer(final RobotProject robotProject, final Collection<IResource> suiteFiles,
            final boolean isSummaryWindowEnabled) {
        this.robotProject = robotProject;
        this.suiteFiles.addAll(suiteFiles);
        this.isSummaryWindowEnabled = isSummaryWindowEnabled;
        dryRunOutputParser = new RobotDryRunOutputParser(robotProject.getStandardLibraries().keySet());
        dryRunHandler = new RobotDryRunHandler();
    }

    public void start() {
        if (!isWorkspaceJobRunning.get()) {
            isWorkspaceJobRunning.set(true);

            final WorkspaceJob wsJob = new WorkspaceJob("Discovering libraries") {

                @Override
                public IStatus runInWorkspace(final IProgressMonitor monitor) throws CoreException {
                    try {
                        startDiscovering(monitor);
                        startAddingLibrariesToProjectConfiguration(monitor);
                        if (isSummaryWindowEnabled) {
                            SwtThread.syncExec(new Runnable() {

                                @Override
                                public void run() {
                                    new LibrariesAutoDiscovererWindow(getActiveShell(),
                                            dryRunOutputParser.getImportedLibraries()).open();
                                }
                            });
                        }
                    } catch (final InvocationTargetException e) {
                        MessageDialog.openError(getActiveShell(), "Discovering libraries",
                                "Problems occured during discovering libraries: " + e.getCause().getMessage());
                    } finally {
                        isWorkspaceJobRunning.set(false);
                    }

                    return Status.OK_STATUS;
                }

                @Override
                protected void canceling() {
                    isSummaryWindowEnabled = false;
                    dryRunHandler.destroyDryRunProcess();
                    this.cancel();
                }
            };
            wsJob.setUser(true);
            wsJob.schedule();
        }
    }

    private void startDiscovering(final IProgressMonitor monitor) throws InvocationTargetException {

        SubMonitor subMonitor = SubMonitor.convert(monitor);
        subMonitor.subTask("Preparing Robot dry run execution...");
        subMonitor.setWorkRemaining(3);

        final LibrariesSourcesCollector librariesSourcesCollector = collectPythonpathAndClasspathLocations();
        subMonitor.worked(1);

        final RunCommandLine dryRunCommandLine = createDryRunCommandLine(librariesSourcesCollector);
        subMonitor.worked(1);

        subMonitor.subTask("Executing Robot dry run...");
        executeDryRun(dryRunCommandLine);
        subMonitor.worked(1);

        subMonitor.done();
    }

    private LibrariesSourcesCollector collectPythonpathAndClasspathLocations() throws InvocationTargetException {
        final LibrariesSourcesCollector librariesSourcesCollector = new LibrariesSourcesCollector(robotProject);
        try {
            librariesSourcesCollector.collectPythonAndJavaLibrariesSources();
        } catch (final CoreException e) {
            throw new InvocationTargetException(e);
        }
        return librariesSourcesCollector;
    }

    private RunCommandLine createDryRunCommandLine(final LibrariesSourcesCollector librariesSourcesCollector)
            throws InvocationTargetException {
        final List<String> suiteNames = newArrayList();
        final List<String> additionalProjectsLocations = newArrayList();
        collectSuiteNamesAndProjectsLocations(suiteNames, additionalProjectsLocations);

        RunCommandLine runCommandLine = null;
        try {
            runCommandLine = dryRunHandler.buildDryRunCommand(robotProject.getRuntimeEnvironment(),
                    robotProject.getProject().getLocation().toFile(), suiteNames,
                    librariesSourcesCollector.getPythonpathLocations(),
                    librariesSourcesCollector.getClasspathLocations(), additionalProjectsLocations);
        } catch (IOException e) {
            throw new InvocationTargetException(e);
        }
        return runCommandLine;
    }

    private void collectSuiteNamesAndProjectsLocations(final List<String> suiteNames,
            final List<String> additionalProjectsLocations) {
        final List<String> resourcesPaths = newArrayList();
        for (final IResource resource : suiteFiles) {
            RobotSuiteFile suiteFile = null;
            if (resource.getType() == IResource.FILE) {
                suiteFile = RedPlugin.getModelManager().createSuiteFile((IFile) resource);
            }
            if (suiteFile != null && suiteFile.isResourceFile()) {
                final IPath resourceFilePath = PathsConverter
                        .toWorkspaceRelativeIfPossible(resource.getProjectRelativePath());
                resourcesPaths.add(resourceFilePath.toString());
            } else {
                if (resource.isLinked()) {
                    collectLinkedSuiteNamesAndProjectsLocations(resource, suiteNames, additionalProjectsLocations);
                } else {
                    suiteNames.add(RobotLaunchConfigurationDelegate.createSuiteName(resource));
                }
            }
        }
        if (!resourcesPaths.isEmpty()) {
            final File tempSuiteFileWithResources = dryRunHandler.createTempSuiteFile(resourcesPaths);
            if (tempSuiteFileWithResources != null) {
                suiteNames.add(Files.getNameWithoutExtension(tempSuiteFileWithResources.getPath()));
                additionalProjectsLocations.add(tempSuiteFileWithResources.getParent());
            }
        }
    }

    private void collectLinkedSuiteNamesAndProjectsLocations(final IResource resource, final List<String> suiteNames,
            final List<String> additionalProjectsLocations) {
        final IPath linkedFileLocation = resource.getLocation();
        if (linkedFileLocation != null) {
            final File linkedFile = linkedFileLocation.toFile();
            if (linkedFile.exists()) {
                suiteNames.add(Files.getNameWithoutExtension(linkedFile.getName()));
                final String linkedFileParentPath = linkedFile.getParent();
                if (!additionalProjectsLocations.contains(linkedFileParentPath)) {
                    additionalProjectsLocations.add(linkedFileParentPath);
                }
            }
        }
    }

    private void executeDryRun(final RunCommandLine dryRunCommandLine) throws InvocationTargetException {
        if (dryRunCommandLine != null) {
            final List<ILineHandler> dryRunOutputlisteners = newArrayList();
            dryRunOutputlisteners.add(dryRunOutputParser);
            dryRunHandler.startDryRunHandlerThread(dryRunCommandLine.getPort(), dryRunOutputlisteners);

            dryRunHandler.executeDryRunProcess(dryRunCommandLine);
        }
    }

    private void startAddingLibrariesToProjectConfiguration(final IProgressMonitor monitor) {
        final List<RobotDryRunLibraryImport> importedLibraries = filterUnknownReferencedLibraries();
        if (!importedLibraries.isEmpty()) {
            RobotProjectConfig config = robotProject.getOpenedProjectConfig();
            final boolean inEditor = config != null;
            if (config == null) {
                config = new RobotProjectConfigReader().readConfiguration(robotProject.getConfigurationFile());
            }
            final List<RobotDryRunLibraryImport> dryRunLibrariesToAdd = filterExistingReferencedLibraries(
                    importedLibraries, config);

            SubMonitor subMonitor = SubMonitor.convert(monitor);
            subMonitor.setWorkRemaining(dryRunLibrariesToAdd.size() + 1);
            final List<ReferencedLibrary> addedLibs = new ArrayList<>();
            for (final RobotDryRunLibraryImport libraryImport : dryRunLibrariesToAdd) {
                subMonitor.subTask("Adding discovered library to project configuration: " + libraryImport.getName());
                if (libraryImport.getType() == DryRunLibraryType.JAVA) {
                    addJavaLibraryToProjectConfiguration(config, libraryImport, addedLibs);
                } else {
                    addPythonLibraryToProjectConfiguration(config, libraryImport, addedLibs);
                }
                subMonitor.worked(1);
            }

            subMonitor.subTask("Updating project configuration...");
            if (!addedLibs.isEmpty()) {
                sendProjectConfigChangedEvent(addedLibs);
                if (!inEditor) {
                    new RobotProjectConfigWriter().writeConfiguration(config, robotProject);
                }
            }
            subMonitor.worked(1);
            subMonitor.done();
        }
    }

    private void sendProjectConfigChangedEvent(final List<ReferencedLibrary> addedLibs) {
        final RedProjectConfigEventData<List<ReferencedLibrary>> eventData = new RedProjectConfigEventData<>(
                robotProject.getConfigurationFile(), addedLibs);
        if (eventBroker == null) {
            eventBroker = (IEventBroker) PlatformUI.getWorkbench().getService(IEventBroker.class);
        }
        if (eventBroker != null) {
            eventBroker.send(RobotProjectConfigEvents.ROBOT_CONFIG_LIBRARIES_STRUCTURE_CHANGED, eventData);
        }
    }

    private List<RobotDryRunLibraryImport> filterUnknownReferencedLibraries() {
        final List<RobotDryRunLibraryImport> importedLibraries = newArrayList();
        for (final RobotDryRunLibraryImport dryRunLibraryImport : dryRunOutputParser.getImportedLibraries()) {
            if (dryRunLibraryImport.getType() != DryRunLibraryType.UNKNOWN) {
                importedLibraries.add(dryRunLibraryImport);
            }
        }
        return importedLibraries;
    }

    private List<RobotDryRunLibraryImport> filterExistingReferencedLibraries(
            final List<RobotDryRunLibraryImport> importedLibraries, RobotProjectConfig config) {
        final List<RobotDryRunLibraryImport> dryRunLibrariesToAdd = newArrayList();
        if (config != null) {
            final List<String> currentLibrariesNames = newArrayList();
            for (final ReferencedLibrary referencedLibrary : config.getLibraries()) {
                currentLibrariesNames.add(referencedLibrary.getName());
            }
            for (final RobotDryRunLibraryImport dryRunLibraryImport : importedLibraries) {
                if (!currentLibrariesNames.contains(dryRunLibraryImport.getName())) {
                    dryRunLibrariesToAdd.add(dryRunLibraryImport);
                } else {
                    dryRunLibraryImport.setStatusAndAdditionalInfo(DryRunLibraryImportStatus.ALREADY_EXISTING,
                            "Library '" + dryRunLibraryImport.getName()
                                    + "' already existing in project configuration.");
                }
            }
        }
        return dryRunLibrariesToAdd;
    }

    private void addPythonLibraryToProjectConfiguration(final RobotProjectConfig config,
            final RobotDryRunLibraryImport libraryImport, final List<ReferencedLibrary> addedLibs) {
        final PythonLibStructureBuilder pythonLibStructureBuilder = new PythonLibStructureBuilder(
                robotProject.getRuntimeEnvironment());
        Collection<PythonClass> pythonClasses = newArrayList();
        try {
            pythonClasses = pythonLibStructureBuilder.provideEntriesFromFile(libraryImport.getSourcePath());
        } catch (RobotEnvironmentException e) {
            libraryImport.setStatusAndAdditionalInfo(DryRunLibraryImportStatus.NOT_ADDED, e.getMessage());
            return;
        }

        final Collection<ReferencedLibrary> librariesToAdd = new ArrayList<>();
        if (pythonClasses.isEmpty()) {
            libraryImport.setStatusAndAdditionalInfo(DryRunLibraryImportStatus.NOT_ADDED,
                    "RED was unable to find classes inside '" + libraryImport.getSourcePath() + "' module.");
        } else {
            for (PythonClass pythonClass : pythonClasses) {
                if (pythonClass.getQualifiedName().equalsIgnoreCase(libraryImport.getName())) {
                    librariesToAdd.add(pythonClass.toReferencedLibrary(libraryImport.getSourcePath()));
                }
            }
            if (librariesToAdd.isEmpty()) {
                libraryImport.setStatusAndAdditionalInfo(DryRunLibraryImportStatus.NOT_ADDED,
                        "RED was unable to find class '" + libraryImport.getName() + "' inside '"
                                + libraryImport.getSourcePath() + "' module.");
            }
        }

        addReferencedLibrariesToProjectConfiguration(config, libraryImport, addedLibs, librariesToAdd);
    }

    private void addJavaLibraryToProjectConfiguration(final RobotProjectConfig config,
            final RobotDryRunLibraryImport libraryImport, final List<ReferencedLibrary> addedLibs) {
        final JarStructureBuilder jarStructureBuilder = new JarStructureBuilder(robotProject.getRuntimeEnvironment());
        List<JarClass> classesFromJar = newArrayList();
        try {
            classesFromJar = jarStructureBuilder.provideEntriesFromFile(libraryImport.getSourcePath());
        } catch (RobotEnvironmentException e) {
            libraryImport.setStatusAndAdditionalInfo(DryRunLibraryImportStatus.NOT_ADDED, e.getMessage());
            return;
        }
        final Collection<ReferencedLibrary> librariesToAdd = new ArrayList<>();
        if (classesFromJar.isEmpty()) {
            libraryImport.setStatusAndAdditionalInfo(DryRunLibraryImportStatus.NOT_ADDED,
                    "RED was unable to find classes inside '" + libraryImport.getSourcePath() + "' module.");
        } else {
            for (JarClass jarClass : classesFromJar) {
                if (jarClass.getQualifiedName().equalsIgnoreCase(libraryImport.getName())) {
                    librariesToAdd.add(jarClass.toReferencedLibrary(libraryImport.getSourcePath()));
                }
            }
            if (librariesToAdd.isEmpty()) {
                libraryImport.setStatusAndAdditionalInfo(DryRunLibraryImportStatus.NOT_ADDED,
                        "RED was unable to find class '" + libraryImport.getName() + "' inside '"
                                + libraryImport.getSourcePath() + "' module.");
            }
        }

        addReferencedLibrariesToProjectConfiguration(config, libraryImport, addedLibs, librariesToAdd);
    }

    private void addReferencedLibrariesToProjectConfiguration(final RobotProjectConfig config,
            final RobotDryRunLibraryImport libraryImport, final List<ReferencedLibrary> addedLibs,
            final Collection<ReferencedLibrary> librariesToAdd) {
        for (final ReferencedLibrary library : librariesToAdd) {
            if (config.addReferencedLibrary(library)) {
                addedLibs.add(library);
                libraryImport.setStatusAndAdditionalInfo(DryRunLibraryImportStatus.ADDED, "");
            }
        }
    }

    private static Shell getActiveShell() {
        final IWorkbench workbench = PlatformUI.getWorkbench();
        final IWorkbenchWindow workbenchWindow = workbench.getActiveWorkbenchWindow();
        return workbenchWindow != null ? workbenchWindow.getShell() : null;
    }

}