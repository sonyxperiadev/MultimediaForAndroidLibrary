# Multimedia for Android Library Readme

# Description
Multimedia for Android Library contains an application level media player. It provides a familiar and easy to use way to playback MPEG-DASH streaming content, HTTP and local playback.

# Contents supported
  * Containers: MPEG4, PIFF 1.1 and PIFF 1.3.
  * Codecs: H264, H265 (if supported by device) and AAC.
  * Subtitles: SMPTE-TT
  * DRM: Marlin and PlayReady (DRM support is not availble on all devices)
  * MPEG-DASH: DASH264, DASH265 (ISO On-Demand profile only)
  
# Developer Guide
The library requires API level 19 (KitKat) or above.
The included Demo Application shows the Library in full use. However the Library is very easy to use and should be very familiar to anyone that has worked with Android MediaPlayer. Below is a very short example.
```
import com.sonymobile.android.media.MediaPlayer;
...
MediaPlayer mMediaPlayer = new MediaPlayer();
mMediaPlayer.setDataSource(<Path to source>);
mMediaPlayer.setDisplay(<SurfaceHolder to render on>);
mMediaPlayer.prepare();
mMediaPlayer.play();
```

## Android Studio
The project contains an Android Studio project for both the library and the demo application.
* Install Android Studio and setup the SDK 
* Select it to import the project into Android Studio 

## Gradle
The project can be built using Gradle, you can import it as a project dependency.

If you want to use Multimedia for Android Library as a jar run
```
./gradlew jarRelease
```
Include the built jar in your project.
