# Android architecture

## Modules (current implementation)

```
app/
  MainActivity.kt                # lifecycle, native CameraX UI, capture and MediaStore
  camera/ManualCamera.kt         # manual Camera2Interop capability checks
  film/FilmPreset.kt             # original recipe definitions
  film/FilmProcessor.kt          # deterministic CPU offline processing
```

## Data flow (M1)

CameraX Preview -> PreviewView (indicative tint overlay)

CameraX ImageCapture -> cache JPEG -> EXIF-oriented bitmap -> FilmProcessor -> Android MediaStore

All image bytes remain on the device. Temporary capture JPEG is deleted on processing completion.

## Roadmap target architecture

```
Camera2/CameraX source (YUV/RAW)
    -> Shared film engine (color response, grain, halation, sensor character)
       -> OpenGL ES GPU preview
       -> photo still output (tiled high-res)
       -> VideoCapture MediaCodec / custom Surface -> MP4 with audio
       -> Panorama stitched 16-bit intermediate -> film pipeline -> JPG
```

**Do not claim film consistency between preview/video/photo until the GPU pipeline is implemented.**

## Hardware support policy

Features requiring physical capabilities (AE off, manual ISO, exposure time, RAW, logical/physical multi-camera) must be gated by Camera2 characteristics. Aperture cannot be physically controlled on most fixed-aperture phones. Film emulation parameters are separate from hardware capture parameters.

## Security and privacy

No Internet permission, authentication, analytics, or backend. The app saves only files the user explicitly captures.

## APK releases

`android-debug.yml` builds untrusted-for-production debug artifacts for testing. Before general distribution, implement a secure, reproducible release-signing pipeline without storing a keystore in the repository.
