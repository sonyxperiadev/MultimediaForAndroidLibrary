/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */
package com.sonymobile.android.media.internal;

import com.sonymobile.android.media.SurfaceViewStubActivity;
import com.sonymobile.android.media.TestContentProvider;
import com.sonymobile.android.media.Utils;

import android.test.ActivityInstrumentationTestCase2;

import java.io.IOException;

public class StateTestRunner extends
        ActivityInstrumentationTestCase2<SurfaceViewStubActivity> {

    private StateTest mStateTest;

    public StateTestRunner() throws Exception {
        super(SurfaceViewStubActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mStateTest = new StateTest(new TestContentProvider(getActivity().getApplicationContext()),
                getActivity().getApplicationContext());
        Utils.logd(this, "setUp", "Start test");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        Utils.logd(this, "tearDown", "End test");
    }

    public void testStateIDLE() throws IOException {
        mStateTest.testIDLEState();
    }

    public void testStateINITIALIZED() throws IOException {
        mStateTest.testINITIALIZEDState();
    }

    public void testStatePREPARED() throws IOException {
        mStateTest.testPREPAREDState();
    }

    public void testStatePLAYING() throws IOException {
        mStateTest.testPLAYINGState();
    }

    public void testStatePAUSED() throws IOException {
        mStateTest.testPAUSEDState();
    }

    public void testStateCOMPLETED() throws IOException {
        mStateTest.testCOMPLETEDState();
    }

    public void testStateEND() throws IOException {
        mStateTest.testENDState();
    }

    public void testStateERROR() throws IOException {
        mStateTest.testERRORState();
    }

    public void testStatePREPARING() throws IOException {
        mStateTest.testPREPARINGState();
    }
}
