# Multimedia for Android Library Readme

# Description
Multimedia for Android Library contains a application level media player. It provides a familiar and easy to use way to playback MPEG-DASH streaming content.

# Support
Multimedia for Android Library support:
  * Containers: MPEG4 and PIFF 1.1 and 1.3.
  * Codecs: H264, H265 (if availble) and AAC.
  * Subtitles: SMPTE-TT and GRAP
  * DRM: Marlin and PlayReady (if availble)
  * MPEG-DASH: DASH264, DASH265 and Iso On-Demand
  
# Developer Guide
The included Demo Application show the Library in full use. However the Library is very easy to use and should be very familiar to anyone that has worked with Android MediaPlayer. Below is a very short example.
'''
import com.sonymobile.android.media.MediaPlayer;
...
MediaPlayer mMediaPlayer = new MediaPlayer();
mMediaPlayer.setDataSource(<Path to source>);
mMediaPlayer.setDisplay(<SurfaceHolder to render on>);
mMediaPlayer.prepare();
mMediaPlayer.play();
'''

## Android Studio
The project contain a Android Studio project for both the library and the demo application.
1. Istall Android Studio and setup the SDK
2. Select to import the project into Android Studio

## Gradle
The project can be built using Gradle, you can import it as a project dependency.

If you want Multimedia for Android Library as a jar run
'''
./gradlew jarRelease
'''
Include the built jar in your project.
