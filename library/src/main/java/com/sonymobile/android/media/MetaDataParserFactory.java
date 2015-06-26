/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
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

import java.io.IOException;

import android.os.Handler;

import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.MediaParserFactory;

/**
 * Class for creating MetaData parsers.
 */
public class MetaDataParserFactory {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "MetaDataParserFactory";

     /**
     * Static method for creating a meta data parser. User is responsible for
     * calling release() on the parser after usage.
     *
     * @param path the path to the content.
     * @param offset the offset to the content.
     * @param length the length of the content.
     * @return A MetaDataParser object.
     */
    public static MetaDataParser create(String path, Long offset, Long length) {
        try {
            MetaDataParser parser = MediaParserFactory.createParser(path, offset, length, -1,
                    null);
            releaseParser(parser);
            return parser;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Static method for creating a meta data parser. User is responsible for
     * calling release() on the parser after usage.
     *
     * @param path the path to the content.
     * @param offset the offset to the content.
     * @param length the length of the content.
     * @param maxBufferSize max buffer size for http content.
     * @return A MetaDataParser object.
     */
    public static MetaDataParser create(String path, Long offset, Long length, int maxBufferSize) {
        try {
            MetaDataParser parser = MediaParserFactory.createParser(path, offset, length,
                    maxBufferSize, null);
            releaseParser(parser);
            return parser;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Static method for creating a meta data parser. User is responsible for
     * calling release() on the parser after usage.
     *
     * @param path the path to the content.
     * @param offset the offset to the content.
     * @param length the length of the content.
     * @param maxBufferSize max buffer size for http content.
     * @param notify A handler to send source related messages to, e.g IO
     *            problems.
     * @return A MetaDataParser object.
     */
    public static MetaDataParser create(String path, Long offset, Long length, int maxBufferSize,
            Handler notify) {
        try {
            MetaDataParser parser = MediaParserFactory.createParser(path, offset, length,
                    maxBufferSize, notify);
            releaseParser(parser);
            return parser;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Static method for creating a meta data parser. User is responsible for
     * calling release() on the parser after usage.
     *
     * @param path tha path to the content.
     * @return A MetaDataParser object.
     */
    public static MetaDataParser create(String path) {
        try {
            MetaDataParser parser = MediaParserFactory.createParser(path, 0L, Long.MAX_VALUE,
                    -1, null);
            releaseParser(parser);
            return parser;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Static method for creating a meta data parser. User is responsible for
     * calling release() on the parser after usage.
     *
     * @param path the path to the content.
     * @param maxBufferSize max buffer size for http content.
     * @return A MetaDataParser object.
     */
    public static MetaDataParser create(String path, int maxBufferSize) {
        try {
            MetaDataParser parser = MediaParserFactory.createParser(path, 0L, Long.MAX_VALUE,
                    maxBufferSize, null);
            releaseParser(parser);
            return parser;
        } catch (IOException e) {
            return null;
        }
    }

    private static void releaseParser(MetaDataParser parser) {
        if (parser != null) {
            parser.release();
        }
    }
}
