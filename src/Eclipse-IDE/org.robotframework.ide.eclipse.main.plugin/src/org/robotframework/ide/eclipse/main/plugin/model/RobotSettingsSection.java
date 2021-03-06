/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.model;

import static com.google.common.collect.Lists.newArrayList;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.rf.ide.core.testdata.model.AKeywordBaseSetting;
import org.rf.ide.core.testdata.model.AModelElement;
import org.rf.ide.core.testdata.model.ATags;
import org.rf.ide.core.testdata.model.RobotExpressions;
import org.rf.ide.core.testdata.model.presenter.update.SettingTableModelUpdater;
import org.rf.ide.core.testdata.model.table.SettingTable;
import org.rf.ide.core.testdata.model.table.setting.AImported;
import org.rf.ide.core.testdata.model.table.setting.DefaultTags;
import org.rf.ide.core.testdata.model.table.setting.ForceTags;
import org.rf.ide.core.testdata.model.table.setting.LibraryAlias;
import org.rf.ide.core.testdata.model.table.setting.LibraryImport;
import org.rf.ide.core.testdata.model.table.setting.Metadata;
import org.rf.ide.core.testdata.model.table.setting.ResourceImport;
import org.rf.ide.core.testdata.model.table.setting.SuiteDocumentation;
import org.rf.ide.core.testdata.model.table.setting.SuiteSetup;
import org.rf.ide.core.testdata.model.table.setting.SuiteTeardown;
import org.rf.ide.core.testdata.model.table.setting.TestSetup;
import org.rf.ide.core.testdata.model.table.setting.TestTeardown;
import org.rf.ide.core.testdata.model.table.setting.TestTemplate;
import org.rf.ide.core.testdata.model.table.setting.TestTimeout;
import org.rf.ide.core.testdata.model.table.setting.VariablesImport;
import org.robotframework.ide.eclipse.main.plugin.model.RobotSetting.SettingsGroup;

import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;

public class RobotSettingsSection extends RobotSuiteFileSection implements IRobotCodeHoldingElement {

    public static final String SECTION_NAME = "Settings";

    private final SettingTableModelUpdater settingTableModelUpdater;

    RobotSettingsSection(final RobotSuiteFile parent, final SettingTable settingTable) {
        super(parent, SECTION_NAME, settingTable);
        settingTableModelUpdater = new SettingTableModelUpdater();
    }

    @Override
    public void link() {
        final SettingTable settingsTable = getLinkedElement();

        for (final Metadata metadataSetting : settingsTable.getMetadatas()) {
            elements.add(new RobotSetting(this, SettingsGroup.METADATA, metadataSetting));
        }
        for (final AImported importSetting : settingsTable.getImports()) {
            SettingsGroup group = SettingsGroup.NO_GROUP;
            if (importSetting instanceof LibraryImport) {
                group = SettingsGroup.LIBRARIES;
            } else if (importSetting instanceof ResourceImport) {
                group = SettingsGroup.RESOURCES;
            } else if (importSetting instanceof VariablesImport) {
                group = SettingsGroup.VARIABLES;
            }
            elements.add(new RobotSetting(this, group, importSetting));
        }
        final Optional<SuiteDocumentation> documentationSetting = settingsTable.documentation();
        if (documentationSetting.isPresent()) {
            elements.add(new RobotSetting(this, documentationSetting.get()));
        }
        for (final AKeywordBaseSetting<?> keywordSetting : getKeywordBasedSettings(settingsTable)) {
            elements.add(new RobotSetting(this, keywordSetting));
        }
        for (final ATags<?> tagSetting : getTagsSettings(settingsTable)) {
            elements.add(new RobotSetting(this, tagSetting));
        }
        for (final TestTemplate templateSetting : settingsTable.getTestTemplates()) {
            elements.add(new RobotSetting(this, templateSetting));
        }
        final Optional<TestTimeout> timeoutSetting = settingsTable.testTimeout();
        if (timeoutSetting.isPresent()) {
            elements.add(new RobotSetting(this, timeoutSetting.get()));
        }
        Collections.sort(elements, new Comparator<RobotFileInternalElement>() {

            @Override
            public int compare(final RobotFileInternalElement o1, final RobotFileInternalElement o2) {
                final RobotSetting s1 = (RobotSetting) o1;
                final RobotSetting s2 = (RobotSetting) o2;
                return Integer.compare(s1.getDefinitionPosition().getOffset(), s2.getDefinitionPosition().getOffset());
            }
        });
    }

    @Override
    public SettingTable getLinkedElement() {
        return (SettingTable) super.getLinkedElement();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<RobotKeywordCall> getChildren() {
        return (List<RobotKeywordCall>) super.getChildren();
    }

    public RobotSetting createSetting(final String name, final String comment, final String... args) {
        final List<String> settingArgs = newArrayList(args);

        final AModelElement<?> newModelElement = settingTableModelUpdater.create(getLinkedElement(), -1, name, comment,
                settingArgs);

        final RobotSetting setting = newSetting(name, newModelElement);
        elements.add(setting);

        return setting;
    }

    public RobotSetting insertSetting(final RobotKeywordCall call, final int allSettingsElementsIndex) {

        final RobotSetting setting = (RobotSetting) call;
        final int tableIndex = countRowsOfGroupUpTo(setting.getGroup(), allSettingsElementsIndex);
        settingTableModelUpdater.insert(getLinkedElement(), tableIndex, setting.getLinkedElement());

        if (allSettingsElementsIndex >= 0 && allSettingsElementsIndex <= elements.size()) {
            call.setParent(this);
            elements.add(allSettingsElementsIndex, setting);
        }

        return setting;
    }

    private RobotSetting newSetting(final String name, final AModelElement<?> newModelElement) {
        RobotSetting setting;
        if (name.equals(SettingsGroup.METADATA.getName())) {
            setting = new RobotSetting(this, SettingsGroup.METADATA, newModelElement);
        } else if (name.equals(SettingsGroup.LIBRARIES.getName())) {
            setting = new RobotSetting(this, SettingsGroup.LIBRARIES, newModelElement);
        } else if (name.equals(SettingsGroup.RESOURCES.getName())) {
            setting = new RobotSetting(this, SettingsGroup.RESOURCES, newModelElement);
        } else if (name.equals(SettingsGroup.VARIABLES.getName())) {
            setting = new RobotSetting(this, SettingsGroup.VARIABLES, newModelElement);
        } else {
            setting = new RobotSetting(this, SettingsGroup.NO_GROUP, newModelElement);
        }
        return setting;
    }

    @Override
    public void removeChild(final RobotKeywordCall child) {
        getChildren().remove(child);
        new SettingTableModelUpdater().remove(getLinkedElement(), child.getLinkedElement());
    }

    public List<RobotKeywordCall> getMetadataSettings() {
        return getSettingsFromGroup(SettingsGroup.METADATA);
    }

    public List<RobotKeywordCall> getGeneralSettings() {
        return getSettingsFromGroup(SettingsGroup.NO_GROUP);
    }

    public List<RobotKeywordCall> getResourcesSettings() {
        return getSettingsFromGroup(SettingsGroup.RESOURCES);
    }

    public List<RobotKeywordCall> getVariablesSettings() {
        return getSettingsFromGroup(SettingsGroup.VARIABLES);
    }

    public List<RobotKeywordCall> getLibrariesSettings() {
        return getSettingsFromGroup(SettingsGroup.LIBRARIES);
    }

    public List<RobotKeywordCall> getImportSettings() {
        return newArrayList(Iterables.filter(getChildren(), new Predicate<RobotKeywordCall>() {

            @Override
            public boolean apply(final RobotKeywordCall element) {
                return ((RobotSetting) element).isImportSetting();
            }
        }));
    }

    private List<RobotKeywordCall> getSettingsFromGroup(final SettingsGroup group) {
        return newArrayList(Iterables.filter(getChildren(), new Predicate<RobotKeywordCall>() {

            @Override
            public boolean apply(final RobotKeywordCall element) {
                return (((RobotSetting) element).getGroup() == group);
            }
        }));
    }

    public synchronized RobotSetting getSetting(final String name) {
        for (final RobotKeywordCall setting : getChildren()) {
            if (name.equals(setting.getName())) {
                return (RobotSetting) setting;
            }
        }
        return null;
    }

    public List<String> getResourcesPaths() {
        final List<RobotKeywordCall> resources = getResourcesSettings();
        final List<String> paths = newArrayList();
        for (final RobotElement element : resources) {
            final RobotSetting setting = (RobotSetting) element;
            final List<String> args = setting.getArguments();
            if (!args.isEmpty()) {
                final String escapedPath = RobotExpressions.unescapeSpaces(args.get(0));
                paths.add(escapedPath);
            }
        }
        return paths;
    }

    public List<String> getVariablesPaths() {
        final List<RobotKeywordCall> variables = getVariablesSettings();
        final List<String> paths = newArrayList();
        for (final RobotElement element : variables) {
            final RobotSetting setting = (RobotSetting) element;
            final List<String> args = setting.getArguments();
            if (!args.isEmpty()) {
                final String escapedPath = RobotExpressions.unescapeSpaces(args.get(0));
                paths.add(escapedPath);
            }
        }
        return paths;
    }

    public SetMultimap<String, String> getLibrariesPathsOrNamesWithAliases() {
        final List<RobotKeywordCall> libraries = getLibrariesSettings();
        final SetMultimap<String, String> toImport = HashMultimap.create();
        for (final RobotKeywordCall element : libraries) {
            final RobotSetting setting = (RobotSetting) element;
            final List<String> args = setting.getArguments();
            if (!args.isEmpty()) {
                final String escapedNameOrPath = RobotExpressions.unescapeSpaces(args.get(0));
                toImport.put(escapedNameOrPath, extractLibraryAlias(setting));
            }
        }
        return toImport;
    }

    private String extractLibraryAlias(final RobotSetting setting) {
        final LibraryAlias libAlias = ((LibraryImport) setting.getLinkedElement()).getAlias();
        return libAlias.isPresent() ? libAlias.getLibraryAlias().getText() : "";
    }

    private static List<? extends AKeywordBaseSetting<?>> getKeywordBasedSettings(final SettingTable settingTable) {
        final List<AKeywordBaseSetting<?>> elements = newArrayList();
        final Optional<SuiteSetup> suiteSetup = settingTable.suiteSetup();
        if (suiteSetup.isPresent()) {
            elements.add(suiteSetup.get());
        }
        final Optional<SuiteTeardown> suiteTeardown = settingTable.suiteTeardown();
        if (suiteTeardown.isPresent()) {
            elements.add(suiteTeardown.get());
        }
        final Optional<TestSetup> testSetup = settingTable.testSetup();
        if (testSetup.isPresent()) {
            elements.add(testSetup.get());
        }
        final Optional<TestTeardown> testTeardown = settingTable.testTeardown();
        if (testTeardown.isPresent()) {
            elements.add(testTeardown.get());
        }
        return elements;
    }

    private static List<? extends ATags<?>> getTagsSettings(final SettingTable settingTable) {
        final List<ATags<?>> elements = newArrayList();
        final Optional<ForceTags> forceTags = settingTable.forceTags();
        if (forceTags.isPresent()) {
            elements.add(forceTags.get());
        }
        final Optional<DefaultTags> defaultTags = settingTable.defaultTags();
        if (defaultTags.isPresent()) {
            elements.add(defaultTags.get());
        }
        return elements;
    }

    private int countRowsOfGroupUpTo(final SettingsGroup group, final int toIndex) {
        int index = 0;
        int count = 0;
        for (final RobotKeywordCall call : getChildren()) {
            if (index >= toIndex) {
                break;
            }
            final String name = call.getName();
            if ((SettingsGroup.getImportsGroupsSet().contains(group)
                    && (name.equals(SettingsGroup.LIBRARIES.getName()) || name.equals(SettingsGroup.RESOURCES.getName())
                            || name.equals(SettingsGroup.VARIABLES.getName())))
                    || name.equals(group.getName())) {
                count++;
            }
            index++;
        }
        return count;
    }
}
