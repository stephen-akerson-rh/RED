/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.testdata.model.presenter.update.testcases;

import java.util.List;

import org.rf.ide.core.testdata.model.AModelElement;
import org.rf.ide.core.testdata.model.ModelType;
import org.rf.ide.core.testdata.model.presenter.update.ITestCaseTableElementOperation;
import org.rf.ide.core.testdata.model.table.testcases.TestCase;
import org.rf.ide.core.testdata.model.table.testcases.TestCaseUnknownSettings;
import org.rf.ide.core.testdata.text.read.IRobotTokenType;
import org.rf.ide.core.testdata.text.read.recognizer.RobotTokenType;


/**
 * @author Michal Anglart
 *
 */
public class TestCaseUnkownModelOperation implements ITestCaseTableElementOperation {

    @Override
    public boolean isApplicable(final ModelType elementType) {
        return elementType == ModelType.TEST_CASE_SETTING_UNKNOWN;
    }

    @Override
    public boolean isApplicable(final IRobotTokenType elementType) {
        return elementType == RobotTokenType.TEST_CASE_SETTING_UNKNOWN_DECLARATION;
    }

    @Override
    public AModelElement<TestCase> create(final TestCase testCase, final List<String> args, final String comment) {
        final TestCaseUnknownSettings unknown = testCase.newUnknownSettings();
        if (!args.isEmpty()) {
            for (int i = 1; i < args.size(); i++) {
                unknown.addArgument(args.get(i));
            }
        }
        if (comment != null && !comment.isEmpty()) {
            unknown.setComment(comment);
        }
        return unknown;
    }

    @Override
    public void update(final AModelElement<TestCase> modelElement, final int index, final String value) {
        final TestCaseUnknownSettings unknown = (TestCaseUnknownSettings) modelElement;

        if (value != null) {
            unknown.addArgument(index, value);
        } else {
            unknown.removeElementToken(index);
        }
    }
}