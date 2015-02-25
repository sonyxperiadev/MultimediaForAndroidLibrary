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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.regex.Pattern;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Video.Media;
import android.text.format.Time;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class TestContentProvider {
    private static final String XML_CONTENT = "TestContent";

    private static final String XML_CONTENT_URI = "ContentURI";

    private static final String XML_WIDTH = "Width";

    private static final String XML_HEIGHT = "Height";

    private static final String XML_DURATION = "Duration";

    private static final String XML_FRAMERATE = "Framerate";

    private static final String XML_BITRATE = "BitRate";

    private static final String XML_SUBTITLE_DATA_LENGTH = "SubtitleDataLength";

    private static final String XML_SUBTITLE_LENGTH_INTERVAL = "SubtitleLengthInterval";

    private static final String XML_TRACK_COUNT = "TrackCount";

    private static final String XML_SUBTITLE_TRACK = "SubtitleTrack";

    private static final String XML_ID = "Id";

    private static final String XML_MAX_I_FRAME_INTERVAL = "maxIFrameInterval";

    private static final String XML_HEADER_OFFSET = "Offset";

    private static final String XML_FILE_LENGTH = "Length";

    private static final String XML_TRACK_MIME_TYPE_VIDEO = "TrackMimeTypeVideo";

    private static final String XML_TRACK_MIME_TYPE_AUDIO = "TrackMimeTypeAudio";

    private static final String XML_MIME_TYPE = "MimeType";

    private static final String XML_METADATA_TITLE = "Title";

    private static final String XML_METADATA_ALBUM = "Album";

    private static final String XML_METADATA_ARTIST = "Artist";

    private static final String XML_METADATA_ALBUMARTIST = "AlbumArtist";

    private static final String XML_METADATA_GENRE = "Genre";

    private static final String XML_METADATA_TRACKNUMBER = "TrackNumber";

    private static final String XML_METADATA_COMPILATION = "Compilation";

    private static final String XML_METADATA_AUTHOR = "Author";

    private static final String XML_METADATA_COMPOSER = "Composer";

    private static final String XML_METADATA_NUMBERTRACKS = "NumberTracks";

    private static final String XML_METADATA_WRITER = "Writer";

    private static final String XML_METADATA_DISCNUMBER = "DiscNumber";

    private static final String XML_METADATA_YEAR = "Year";

    private static final String PATH_TO_TESTFILE = Environment.getExternalStorageDirectory()
            .getAbsolutePath() + "/testcontent.xml";

    private static final String TAG = "TestContentProvider";

    private ArrayList<TestContent> mContents;

    private Context mContext;

    public TestContentProvider(Context context) {
        mContents = new ArrayList<TestContent>();
        mContext = context;
        loadContents();
    }

    public TestContent getTestItemById(String id) {
        int size = mContents.size();
        for (int i = 0; i < size; i++) {
            TestContent tc = mContents.get(i);
            if (id.equals(tc.getId())) {
                return tc;
            }
        }
        return null;
    }

    public ArrayList<TestContent> getAllTestItems() {
        return mContents;
    }

    private String getMediaStoreUri(String idName) {

        if (!idName.equals("")) {
            ContentResolver cr = mContext.getContentResolver();
            Cursor c = cr.query(Media.EXTERNAL_CONTENT_URI, null, Media.DISPLAY_NAME
                    + "=? COLLATE NOCASE",
                    new String[] {
                        idName
                    }, null);
            try {
                while (c.moveToNext()) {
                    int displayIndex = c.getColumnIndex(Media.DISPLAY_NAME);
                    int idIndex = c.getColumnIndex(Media._ID);

                    String displayName = c.getString(displayIndex);
                    int id = c.getInt(idIndex);

                    if (displayName.equalsIgnoreCase(idName)) {
                        // Found Uri for the requested content.
                        Uri uri = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, id);
                        return uri.toString();
                    }
                }
            } finally {
                c.close();
            }
        }
        return null;
    }

    private void loadContents() {
        InputStream in = null;
        try {
            File file = new File(PATH_TO_TESTFILE);
            in = new FileInputStream(file);
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, null);

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();
                if (XML_CONTENT.equals(tagName)) { // On <TestContent> tag
                    TestContent obj = new TestContent();
                    while (parser.next() != XmlPullParser.END_TAG ||
                            !XML_CONTENT.equals(parser.getName())) {
                        // While not on the </TestContent> tag
                        if (XML_CONTENT_URI.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next(); // Step to the text content
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setContentUri(parser.getText());
                            }
                        } // done with filename
                        else if (XML_HEIGHT.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setHeight(Integer.parseInt(parser.getText()));
                            }
                        } // done with height
                        else if (XML_WIDTH.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setWidth(Integer.parseInt(parser.getText()));
                            }
                        } // done with width
                        else if (XML_DURATION.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                Time duration = new Time();
                                String text = parser.getText();
                                // Check for format HHMMSS
                                if (text.length() == 6 && Pattern.matches("[0-9]+", text)) {
                                    String hh = text.substring(0, 2);
                                    String mm = text.substring(2, 4);
                                    String ss = text.substring(4, 6);
                                    duration.hour = Integer.parseInt(hh);
                                    duration.minute = Integer.parseInt(mm);
                                    duration.second = Integer.parseInt(ss);
                                } else {
                                    Log.d(TAG, "No valid duration provided: \"" +
                                            text + "\". Use format HHMMSS");
                                }
                                obj.setDuration(duration);
                            }
                        } // done with duration
                        else if (XML_FRAMERATE.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setFramerate(Float.parseFloat(parser.getText()));
                            }
                        } // done with framerate
                        else if (XML_BITRATE.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setBitrate(Integer.parseInt(parser.getText()));
                            }
                        } // done with bitrate
                        else if (XML_ID.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setId(parser.getText());
                            }
                        }// done with id
                        else if (XML_MAX_I_FRAME_INTERVAL.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMaxIFrameInterval(Integer.parseInt(parser.getText()));
                            }
                        }// done with seekpoint
                        else if (XML_SUBTITLE_DATA_LENGTH.equals(parser.getName())
                                && parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setSubtitleDataLength(Integer.parseInt(parser.getText()));
                            }
                        } // done with subtitledata
                        else if (XML_SUBTITLE_LENGTH_INTERVAL.equals(parser.getName())
                                && parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setSubtitleLengthInterval(Integer.parseInt(parser.getText()));
                            }
                        } // done with subtitlelengthinterval
                        else if (XML_SUBTITLE_TRACK.equals(parser.getName())
                                && parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setSubtitleTrack(Integer.parseInt(parser.getText()));
                            }
                        } // done with subtitletrack
                        else if (XML_HEADER_OFFSET.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setOffset(Integer.parseInt(parser.getText()));
                            }
                        }// done with offset
                        else if (XML_FILE_LENGTH.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setLength(Long.parseLong(parser.getText()));
                            }
                        }// done with length
                        else if (XML_TRACK_MIME_TYPE_AUDIO.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setTrackMimeTypeAudio(parser.getText());
                            }
                        } // done with trackmimetypeaudio
                        else if (XML_MIME_TYPE.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMimeType(parser.getText());
                            }
                        } // done with mimetype
                        else if (XML_TRACK_COUNT.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setTrackCount(Integer.parseInt(parser.getText()));
                            }
                        } // done with trackcount
                        else if (XML_TRACK_MIME_TYPE_VIDEO.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setTrackMimeTypeVideo(parser.getText());
                            }
                        } // done with trackmimetypevideo
                        else if (XML_METADATA_TITLE.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_TITLE, parser.getText());
                            }
                        } // done with KEY_TITLE
                        else if (XML_METADATA_ALBUM.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_ALBUM, parser.getText());
                            }
                        } // done with KEY_ALBUM
                        else if (XML_METADATA_ARTIST.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_ARTIST, parser.getText());
                            }
                        } // done with KEY_ARTIST
                        else if (XML_METADATA_ALBUMARTIST.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_ALBUM_ARTIST, parser.getText());
                            }
                        } // done with KEY_ALBUM_ARTIST
                        else if (XML_METADATA_GENRE.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_GENRE, parser.getText());
                            }
                        } // done with KEY_GENRE
                        else if (XML_METADATA_TRACKNUMBER.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_TRACK_NUMBER, parser.getText());
                            }
                        } // done with KEY_TRACK_NUMBER
                        else if (XML_METADATA_COMPILATION.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_COMPILATION, parser.getText());
                            }
                        } // done with KEY_COMPILATION
                        else if (XML_METADATA_AUTHOR.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_AUTHOR, parser.getText());
                            }
                        } // done with KEY_AUTHOR
                        else if (XML_METADATA_COMPOSER.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_COMPOSER, parser.getText());
                            }
                        } // done with KEY_COMPOSER
                        else if (XML_METADATA_WRITER.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_WRITER, parser.getText());
                            }
                        } // done with KEY_WRITER
                        else if (XML_METADATA_DISCNUMBER.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_DISC_NUMBER, parser.getText());
                            }
                        } // done with KEY_DISC_NUMBER
                        else if (XML_METADATA_YEAR.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_YEAR, parser.getText());
                            }
                        } // done with KEY_YEAR
                        else {
                            // No match for tags, do nothing
                        }
                    }
                    if (obj.getId() != null) {
                        if (obj.getId().equals(TestContent.ID_TYPE_MEDIASTORE)) {
                            obj.setContentUri(getMediaStoreUri(obj.getContentUri()));
                        }
                        mContents.add(obj);
                    }
                } // Leaving <TestContent> tag
            } // End of document
        } catch (IOException e) {
            Log.d(TAG, "Error in loadContents(). Please make sure your testcontent.xml" +
                    " is correctly formatted and located at " + PATH_TO_TESTFILE
                    + e.getMessage());
        } catch (NumberFormatException e) {
            Log.d(TAG, "Error in loadContents(). Please make sure your testcontent.xml" +
                    " is correctly formatted and located at " + PATH_TO_TESTFILE
                    + e.getMessage());
        } catch (XmlPullParserException e) {
            Log.d(TAG, "Error in loadContents(). Please make sure your testcontent.xml" +
                    " is correctly formatted and located at " + PATH_TO_TESTFILE
                    + e.getMessage());
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
            }
        }
    }
}
