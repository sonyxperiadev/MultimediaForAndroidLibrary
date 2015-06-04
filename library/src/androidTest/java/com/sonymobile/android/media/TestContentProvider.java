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
    public static final String KEY_CONTENT = "TestContent";

    public static final String KEY_CONTENT_URI = "ContentURI";

    public static final String KEY_WIDTH = "Width";

    public static final String KEY_HEIGHT = "Height";

    public static final String KEY_ROTATION = "Rotation";

    public static final String KEY_DURATION = "Duration";

    public static final String KEY_FRAMERATE = "Framerate";

    public static final String KEY_BITRATE = "BitRate";

    public static final String KEY_SUBTITLE_DATA_LENGTH = "SubtitleDataLength";

    public static final String KEY_SUBTITLE_LENGTH_INTERVAL = "SubtitleLengthInterval";

    public static final String KEY_TRACK_COUNT = "TrackCount";

    public static final String KEY_SUBTITLE_TRACK = "SubtitleTrack";

    public static final String KEY_ID = "Id";

    public static final String KEY_MAX_I_FRAME_INTERVAL = "maxIFrameInterval";

    public static final String KEY_HEADER_OFFSET = "Offset";

    public static final String KEY_FILE_LENGTH = "Length";

    public static final String KEY_TRACK_MIME_TYPE_VIDEO = "TrackMimeTypeVideo";

    public static final String KEY_TRACK_MIME_TYPE_AUDIO = "TrackMimeTypeAudio";

    public static final String KEY_MIME_TYPE = "MimeType";

    public static final String KEY_CONTENT_TYPE = "Content";

    public static final String KEY_PROTOCOL_TYPE = "Protocol";

    public static final String KEY_METADATA_TITLE = "Title";

    public static final String KEY_METADATA_ALBUM = "Album";

    public static final String KEY_METADATA_ARTIST = "Artist";

    public static final String KEY_METADATA_ALBUMARTIST = "AlbumArtist";

    public static final String KEY_METADATA_GENRE = "Genre";

    public static final String KEY_METADATA_TRACKNUMBER = "TrackNumber";

    public static final String KEY_METADATA_COMPILATION = "Compilation";

    public static final String KEY_METADATA_AUTHOR = "Author";

    public static final String KEY_METADATA_COMPOSER = "Composer";

    public static final String KEY_METADATA_NUMBERTRACKS = "NumberTracks";

    public static final String KEY_METADATA_WRITER = "Writer";

    public static final String KEY_METADATA_DISCNUMBER = "DiscNumber";

    public static final String KEY_METADATA_YEAR = "Year";

    public static final String KEY_METADATA_ALBUMART = "AlbumArt";

    public static final String KEY_METADATA_SONY_MOBILE_CAMERA_CONTENT_FLAG = "CameraContent";

    private static final String PATH_TO_TESTFILE = Environment.getExternalStorageDirectory()
            .getAbsolutePath() + "/testcontent.xml";

    private static final String TAG = "TestContentProvider";

    private final ArrayList<TestContent> mContents;

    private final Context mContext;

    public TestContentProvider(Context context) {
        mContents = new ArrayList<>();
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

    public ArrayList<TestContent> getFilteredTestItems(boolean protocolTypeSet,
            boolean contentTypeSet) {

        ArrayList<TestContent> filteredList = new ArrayList<>();

        for (TestContent content : mContents) {
            String protocolType = content.getProtocolType();
            String contentType = content.getContentType();

            if (protocolTypeSet && contentTypeSet) {
                if (protocolType != null && contentType != null) {
                    filteredList.add(content);
                }
            } else if (protocolTypeSet) {
                if (protocolType != null) {
                    filteredList.add(content);
                }
            } else if (contentTypeSet) {
                if (contentType != null) {
                    filteredList.add(content);
                }
            } else {
                filteredList.add(content);
            }
        }

        return filteredList;
    }

    private String getMediaStoreUri(String idName) {

        if (!idName.isEmpty() && mContext != null) {
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
                if (KEY_CONTENT.equals(tagName)) { // On <TestContent> tag
                    TestContent obj = new TestContent();
                    while (parser.next() != XmlPullParser.END_TAG ||
                            !KEY_CONTENT.equals(parser.getName())) {
                        // While not on the </TestContent> tag
                        if (KEY_CONTENT_URI.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next(); // Step to the text content
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setContentUri(parser.getText());
                            }
                        } // done with filename
                        else if (KEY_HEIGHT.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setHeight(Integer.parseInt(parser.getText()));
                            }
                        } // done with height
                        else if (KEY_WIDTH.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setWidth(Integer.parseInt(parser.getText()));
                            }
                        } // done with width
                        else if (KEY_ROTATION.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setRotation(Integer.parseInt(parser.getText()));
                            }
                        } // done with rotation
                        else if (KEY_DURATION.equals(parser.getName()) &&
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
                        else if (KEY_FRAMERATE.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setFramerate(Float.parseFloat(parser.getText()));
                            }
                        } // done with framerate
                        else if (KEY_BITRATE.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setBitrate(Integer.parseInt(parser.getText()));
                            }
                        } // done with bitrate
                        else if (KEY_ID.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setId(parser.getText());
                            }
                        }// done with id
                        else if (KEY_MAX_I_FRAME_INTERVAL.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMaxIFrameInterval(Integer.parseInt(parser.getText()));
                            }
                        }// done with seekpoint
                        else if (KEY_SUBTITLE_DATA_LENGTH.equals(parser.getName())
                                && parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setSubtitleDataLength(Integer.parseInt(parser.getText()));
                            }
                        } // done with subtitledata
                        else if (KEY_SUBTITLE_LENGTH_INTERVAL.equals(parser.getName())
                                && parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setSubtitleLengthInterval(Integer.parseInt(parser.getText()));
                            }
                        } // done with subtitlelengthinterval
                        else if (KEY_SUBTITLE_TRACK.equals(parser.getName())
                                && parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setSubtitleTrack(Integer.parseInt(parser.getText()));
                            }
                        } // done with subtitletrack
                        else if (KEY_HEADER_OFFSET.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setOffset(Integer.parseInt(parser.getText()));
                            }
                        }// done with offset
                        else if (KEY_FILE_LENGTH.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setLength(Long.parseLong(parser.getText()));
                            }
                        }// done with length
                        else if (KEY_TRACK_MIME_TYPE_AUDIO.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setTrackMimeTypeAudio(parser.getText());
                            }
                        } // done with trackmimetypeaudio
                        else if (KEY_MIME_TYPE.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMimeType(parser.getText());
                            }
                        } // done with mimetype
                        else if (KEY_TRACK_COUNT.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setTrackCount(Integer.parseInt(parser.getText()));
                            }
                        } // done with trackcount
                        else if (KEY_TRACK_MIME_TYPE_VIDEO.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setTrackMimeTypeVideo(parser.getText());
                            }
                        } // done with trackmimetypevideo
                        else if (KEY_METADATA_TITLE.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_TITLE, parser.getText());
                            }
                        } // done with KEY_TITLE
                        else if (KEY_METADATA_ALBUM.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_ALBUM, parser.getText());
                            }
                        } // done with KEY_ALBUM
                        else if (KEY_METADATA_ARTIST.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_ARTIST, parser.getText());
                            }
                        } // done with KEY_ARTIST
                        else if (KEY_METADATA_ALBUMARTIST.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_ALBUM_ARTIST, parser.getText());
                            }
                        } // done with KEY_ALBUM_ARTIST
                        else if (KEY_METADATA_GENRE.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_GENRE, parser.getText());
                            }
                        } // done with KEY_GENRE
                        else if (KEY_METADATA_TRACKNUMBER.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_TRACK_NUMBER, parser.getText());
                            }
                        } // done with KEY_TRACK_NUMBER
                        else if (KEY_METADATA_COMPILATION.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_COMPILATION, parser.getText());
                            }
                        } // done with KEY_COMPILATION
                        else if (KEY_METADATA_AUTHOR.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_AUTHOR, parser.getText());
                            }
                        } // done with KEY_AUTHOR
                        else if (KEY_METADATA_COMPOSER.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_COMPOSER, parser.getText());
                            }
                        } // done with KEY_COMPOSER
                        else if (KEY_METADATA_WRITER.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_WRITER, parser.getText());
                            }
                        } // done with KEY_WRITER
                        else if (KEY_METADATA_DISCNUMBER.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_DISC_NUMBER, parser.getText());
                            }
                        } // done with KEY_DISC_NUMBER
                        else if (KEY_METADATA_YEAR.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_YEAR, parser.getText());
                            }
                        } // done with KEY_YEAR
                        else if (KEY_METADATA_SONY_MOBILE_CAMERA_CONTENT_FLAG.equals(
                                parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setMetaDataValue(MetaData.KEY_IS_CAMERA_CONTENT,
                                        parser.getText());
                            }
                        } // done with KEY_YEAR
                        else if (KEY_CONTENT_TYPE.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setContentType(parser.getText());
                            }
                        } // done with content type
                        else if (KEY_PROTOCOL_TYPE.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                obj.setProtocolType(parser.getText());
                            }
                        } // done with protocol type
                        else if (KEY_METADATA_ALBUMART.equals(parser.getName()) &&
                                parser.getEventType() == XmlPullParser.START_TAG) {
                            parser.next();
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                String text = parser.getText();
                                int widthIndex = text.indexOf('w');
                                int heightIndex = text.indexOf('h');
                                if (text.startsWith("s") && widthIndex > -1 && heightIndex > -1) {
                                    obj.setAlbumArtSize(Integer.parseInt(text.substring(1,
                                            widthIndex)));
                                    obj.setAlbumArtWidth(Integer.parseInt(text.substring
                                            (widthIndex + 1, heightIndex)));
                                    obj.setAlbumArtHeight(Integer.parseInt(text.substring
                                            (heightIndex + 1)));
                                } else {
                                    Log.d(TAG, "No valid albumart data provided: \"" +
                                            text + "\". Use format s[0-9]+w[0-9]+h[0-9]+");
                                }
                            }
                        } // done with album art
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
