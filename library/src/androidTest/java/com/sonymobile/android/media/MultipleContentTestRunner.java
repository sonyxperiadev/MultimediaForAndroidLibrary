/*
 * Copyright (C) 2015 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.sonymobile.android.media;

import com.sonymobile.android.media.annotations.Content;
import com.sonymobile.android.media.annotations.MetaInfo;
import com.sonymobile.android.media.annotations.Protocol;

import android.test.InstrumentationTestRunner;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.StringTokenizer;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class MultipleContentTestRunner extends InstrumentationTestRunner {

    @Override
    public TestSuite getAllTests() {
        TestContentProvider provider = new TestContentProvider(null);

        ArrayList<TestContent> testContents = provider.getFilteredTestItems(true, true);

        TestSuite masterSuite = new TestSuite();

        for (TestContent content : testContents) {
            TestSuite contentSuite = filterTests(ApiTestCase.class, content);
            Enumeration<Test> tests = contentSuite.tests();

            while (tests.hasMoreElements()) {
                ApiTestCase test = (ApiTestCase)tests.nextElement();
                test.setTestContent(content);
            }

            masterSuite.addTest(contentSuite);
        }

        return masterSuite;
    }

    @Override
    public ClassLoader getLoader() {
        return MultipleContentTestRunner.class.getClassLoader();
    }

    private TestSuite filterTests(Class<? extends TestCase> testClazz, TestContent content) {
        TestSuite contentSuite = new TestSuite();
        for (Method method : testClazz.getDeclaredMethods()) {
            if (isPublicTestMethod(method) && addTest(method, content)) {
                contentSuite.addTest(TestSuite.createTest(testClazz, method.getName()));
            }
        }
        return contentSuite;
    }

    private static boolean isPublicTestMethod(Method method) {
        return method.getName().startsWith("test") && Modifier.isPublic(method.getModifiers());
    }

    private boolean addTest(AnnotatedElement element, TestContent content) {
        if (element.isAnnotationPresent(Content.class) &&
                element.isAnnotationPresent(Protocol.class)) {

            String protocolType = content.getProtocolType();
            String contentType = content.getContentType();

            String annotatedProtocolTypes = element.getAnnotation(Protocol.class).types();
            String annotatedContentTypes = element.getAnnotation(Content.class).types();
            String annotatedMetaInfoFields = null;
            if (element.isAnnotationPresent(MetaInfo.class)) {
                annotatedMetaInfoFields = element.getAnnotation(MetaInfo.class).fields();
            }

            if (annotatedProtocolTypes != null && annotatedContentTypes != null &&
                    protocolType != null && contentType != null) {

                if (annotatedProtocolTypes.contains(protocolType) && annotatedContentTypes
                        .contains(contentType)) {

                    if (annotatedMetaInfoFields != null && annotatedMetaInfoFields.length() > 0) {
                        StringTokenizer tokenizer =
                                new StringTokenizer(annotatedMetaInfoFields, "&&");
                        while (tokenizer.hasMoreTokens()) {
                            if (!hasMetaInfoField(tokenizer.nextToken(), content)) {
                                return false;
                            }
                        }
                    }

                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasMetaInfoField(String field, TestContent content) {

        if (field.equals(TestContentProvider.KEY_WIDTH)) {
            return content.getWidth() != -1;
        } else if (field.equals(TestContentProvider.KEY_HEIGHT)) {
            return content.getHeight() != -1;
        } else if (field.equals(TestContentProvider.KEY_DURATION)) {
            return content.getDuration() > -1;
        } else if (field.equals(TestContentProvider.KEY_FRAMERATE)) {
            return content.getFramerate() != -1;
        } else if (field.equals(TestContentProvider.KEY_MAX_I_FRAME_INTERVAL)) {
            return content.getMaxIFrameInterval() != -1;
        } else if (field.equals(TestContentProvider.KEY_TRACK_COUNT)) {
            return content.getTrackCount() != -1;
        } else if (field.equals(TestContentProvider.KEY_MIME_TYPE)) {
            return content.getMimeType() != null;
        } else if (field.equals(TestContentProvider.KEY_TRACK_MIME_TYPE_VIDEO)) {
            return content.getTrackMimeTypeVideo() != null;
        } else if (field.equals(TestContentProvider.KEY_TRACK_MIME_TYPE_AUDIO)) {
            return content.getTrackMimeTypeAudio() != null;
        } else if (field.equals(TestContentProvider.KEY_METADATA_TITLE)) {
            return content.getMetaDataValue(TestContentProvider.KEY_METADATA_TITLE) != null;
        } else if (field.equals(TestContentProvider.KEY_METADATA_ALBUM)) {
            return content.getMetaDataValue(TestContentProvider.KEY_METADATA_ALBUM) != null;
        } else if (field.equals(TestContentProvider.KEY_METADATA_ARTIST)) {
            return content.getMetaDataValue(TestContentProvider.KEY_METADATA_ARTIST) != null;
        } else if (field.equals(TestContentProvider.KEY_METADATA_ALBUMARTIST)) {
            return content.getMetaDataValue(TestContentProvider.KEY_METADATA_ALBUMARTIST) != null;
        } else if (field.equals(TestContentProvider.KEY_METADATA_GENRE)) {
            return content.getMetaDataValue(TestContentProvider.KEY_METADATA_GENRE) != null;
        } else if (field.equals(TestContentProvider.KEY_METADATA_TRACKNUMBER)) {
            return content.getMetaDataValue(TestContentProvider.KEY_METADATA_TRACKNUMBER) != null;
        } else if (field.equals(TestContentProvider.KEY_METADATA_COMPILATION)) {
            return content.getMetaDataValue(TestContentProvider.KEY_METADATA_COMPILATION) != null;
        } else if (field.equals(TestContentProvider.KEY_METADATA_AUTHOR)) {
            return content.getMetaDataValue(TestContentProvider.KEY_METADATA_AUTHOR) != null;
        } else if (field.equals(TestContentProvider.KEY_METADATA_WRITER)) {
            return content.getMetaDataValue(TestContentProvider.KEY_METADATA_WRITER) != null;
        } else if (field.equals(TestContentProvider.KEY_METADATA_DISCNUMBER)) {
            return content.getMetaDataValue(TestContentProvider.KEY_METADATA_DISCNUMBER) != null;
        } else if (field.equals(TestContentProvider.KEY_METADATA_YEAR)) {
            return content.getMetaDataValue(TestContentProvider.KEY_METADATA_YEAR) != null;
        } else if (field.equals(TestContentProvider.KEY_HEADER_OFFSET)) {
            return content.getOffset() != -1;
        }

        return false;
    }
}
