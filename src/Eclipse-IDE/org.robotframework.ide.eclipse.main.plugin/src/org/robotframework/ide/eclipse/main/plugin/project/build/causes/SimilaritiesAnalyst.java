/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.project.build.causes;

import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.rf.ide.core.project.RobotProjectConfig.ReferencedVariableFile;
import org.rf.ide.core.testdata.text.read.recognizer.RobotToken;
import org.robotframework.ide.eclipse.main.plugin.RedPlugin;
import org.robotframework.ide.eclipse.main.plugin.model.RobotFileInternalElement;
import org.robotframework.ide.eclipse.main.plugin.model.RobotKeywordDefinition;
import org.robotframework.ide.eclipse.main.plugin.model.RobotProject;
import org.robotframework.ide.eclipse.main.plugin.model.RobotSuiteFile;
import org.robotframework.ide.eclipse.main.plugin.model.RobotVariable;
import org.robotframework.ide.eclipse.main.plugin.model.locators.ContinueDecision;
import org.robotframework.ide.eclipse.main.plugin.model.locators.KeywordDefinitionLocator;
import org.robotframework.ide.eclipse.main.plugin.model.locators.KeywordDefinitionLocator.KeywordDetector;
import org.robotframework.ide.eclipse.main.plugin.model.locators.VariableDefinitionLocator;
import org.robotframework.ide.eclipse.main.plugin.model.locators.VariableDefinitionLocator.VariableDetector;
import org.robotframework.ide.eclipse.main.plugin.project.library.KeywordSpecification;
import org.robotframework.ide.eclipse.main.plugin.project.library.LibrarySpecification;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

/**
 * @author Michal Anglart
 */
class SimilaritiesAnalyst {

    private static final int DEFAULT_LIMIT = 5;
    private static final int DEFAULT_MAXIMUM_DISTANCE = 2;


    private final int limit;

    private final int maximumDistance;

    private final SimilarityWithLevenshteinDistance similaritiesAlgorithm;

    SimilaritiesAnalyst() {
        this(DEFAULT_LIMIT, DEFAULT_MAXIMUM_DISTANCE);
    }

    SimilaritiesAnalyst(final int limit, final int maximumDistance) {
        this.limit = limit;
        this.maximumDistance = maximumDistance;
        this.similaritiesAlgorithm = new SimilarityWithLevenshteinDistance();
    }


    Collection<String> provideSimilarLibraries(final IFile suiteFile, final String libraryName) {
        final RobotProject robotProject = RedPlugin.getModelManager().createProject(suiteFile.getProject());
        final Collection<String> allLibs = newArrayList(
                transform(robotProject.getLibrariesSpecifications(), new Function<LibrarySpecification, String>() {

                    @Override
                    public String apply(final LibrarySpecification lib) {
                        return lib.getName();
                    }
                }));
        return limit(similaritiesAlgorithm.onlyWordsWithinDistance(allLibs, libraryName, maximumDistance));
    }

    Collection<String> provideSimilarAccessibleKeywords(final IFile suiteFile, final String keywordName) {
        final Collection<String> allNames = getAccessibleKeywords(suiteFile);
        return limit(similaritiesAlgorithm.onlyWordsWithinDistance(allNames, keywordName, maximumDistance));
    }

    private Collection<String> getAccessibleKeywords(final IFile suiteFile) {
        final Set<String> names = new LinkedHashSet<>();
        new KeywordDefinitionLocator(suiteFile).locateKeywordDefinition(new KeywordDetector() {

            @Override
            public ContinueDecision libraryKeywordDetected(final LibrarySpecification libSpec,
                    final KeywordSpecification kwSpec, final Set<String> libraryAlias,
                    final RobotSuiteFile exposingFile) {
                names.add(kwSpec.getName());
                return ContinueDecision.CONTINUE;
            }

            @Override
            public ContinueDecision keywordDetected(final RobotSuiteFile file, final RobotKeywordDefinition keyword) {
                names.add(keyword.getName());
                return ContinueDecision.CONTINUE;
            }
        });
        return names;
    }

    Collection<String> provideSimilarAccessibleVariables(final IFile suiteFile, final int offset,
            final String varName) {
        final Collection<String> allNames = getAccessibleVariables(suiteFile, offset);
        return limit(similaritiesAlgorithm.onlyWordsWithinDistance(allNames, varName, maximumDistance));
    }

    private Collection<String> getAccessibleVariables(final IFile suiteFile, final int offset) {
        final Set<String> names = new LinkedHashSet<>();
        new VariableDefinitionLocator(suiteFile).locateVariableDefinitionWithLocalScope(new VariableDetector() {

            @Override
            public ContinueDecision variableDetected(final RobotVariable variable) {
                names.add(variable.getName());
                return ContinueDecision.CONTINUE;
            }

            @Override
            public ContinueDecision varFileVariableDetected(final ReferencedVariableFile file,
                    final String variableName, final Object value) {
                names.add(variableName);
                return ContinueDecision.CONTINUE;
            }

            @Override
            public ContinueDecision localVariableDetected(final RobotFileInternalElement element,
                    final RobotToken variable) {
                names.add(variable.getText());
                return ContinueDecision.CONTINUE;
            }

            @Override
            public ContinueDecision globalVariableDetected(final String name, final Object value) {
                names.add(name);
                return ContinueDecision.CONTINUE;
            }
        }, offset);
        return names;
    }

    private <T> Collection<T> limit(final Collection<T> elements) {
        return newArrayList(Iterables.limit(elements, limit));
    }
}
