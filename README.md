yeah,

I’ve been using this app since my HTC One days — it has always been a joy to watch and play with.

After updating to **Android 15**, the app unfortunately stopped working. While looking for a solution, I found the project here on GitHub, so I decided to **fork it and bring it up to date** with a recent **Gradle version and JDK**.

## Changes in this fork

This fork is a complete architectural overhaul of the original project, moving from a legacy Rend   erScript-based simulation to a modern, high-performance GLES 3.1 Compute Shader pipeline.

### Core Architectural Improvements
- **GLES 3.1 & Compute Shader Migration**: Physics calculations (position, velocity, attraction)
   are now performed 100% on the GPU.
- **SSBO (Shader Storage Buffer Objects)**: Particle data resides in GPU memory. We utilize **Zer
   o-Copy Rendering**, meaning particle data never leaves the GPU during the render loop.
- **Ping-Pong (Double) Buffering**: Enables the Compute and Render passes to work in parallel, ma
   ximizing GPU utilization.
- **Memory & Bandwidth Optimization**:
- **Packed Layout**: Uses a compact binary format for particle states to minimize memory band
   width pressure.
- **Color LUT**: Replaced expensive runtime HSV-to-RGB math with a fast 1D Gradient Texture (
   Lookup Table).
- **GPU-Accelerated Initialization**: Particles are reset and distributed directly on the GPU usi
   ng custom hash-based noise.
- **Sim-Speed Normalization**: Added a "Constant Speed" mode that accounts for variable frame tim
   es (Delta Time), ensuring smooth movement regardless of FPS.
### Performance & UI Tools
- **Massive Scalability**: Smoothly handles **1,000,000+ particles** on modern mobile hardware (a
    ~10x-25x boost over the original RenderScript version).
- **Enhanced Settings UI**: Real-time synchronization between SeekBars and numeric inputs for fin
   e-tuned control over Attraction and Drag coefficients.
- **Compute Tuning**: Exposed **Workgroup Size** settings to allow manual optimization for differ
   ent GPU architectures (Adreno, Mali, etc.).
### Modernized Build System
- Updated to **Gradle 8.x** and **AGP 8.x**.

### Other
- Increased the default particle count from **50.000 to 1.000.000**
- Changed the default color tone to a **more vivid look
- from ~ 30fps 1m particle to ~110fps on SD8Gen2, even on 7m prticle >30 fps

Many thanks to **nfaralli** for creating this beautiful app in the first place 🙌  
Below you can find the original README.

# Welcome to Particle Flow!

This simple app displays particles moving thanks to some attraction points that you can move by
touching them. By default the app displays 50,000 particles and has a maximum of 5 attraction
points (you can change these parameters in the Renderer if you want. You'll have to recompile and
install your own APK though). The particles color represents their speed: blue for slow particles,
red for fast particles.

If you want a simple example on how to use OpenGL ES and RenderScript, then this app may be just
what you need (after checking out the Android tutorials).
I initially wanted to learn how to use RenderScript and found [this code]
(https://code.google.com/p/renderscript-examples/wiki/Gravity).
However most of the APIs were deprecated and I ended up rewriting it almost completely, adding some
new features on the way (e.g. several attraction points and colors depending on the particle speed).

The following are general guidelines to compile and install an app on your device (assuming this is
a gradle project).  Because you don't have the keystore you cannot sign the release version of the
app and you must use `app-debug.apk` (which is signed with a debug key). You cannot install an APK
which is not signed on your device.

## Compiling the code

### Terminal

First, you need to have an environment variable JAVA_HOME pointing to your java SDK and another one
ANDROID_HOME pointing to your Android SDK (you can also create a `local.properties` file containing
`sdk.dir=<PATH TO YOUR ANDROID SDK>` in the root directory of the project. Then just do:

`$ ./gradlew build`

This will generate the APK under `./app/build/outputs/apk/`.

If for some reasons you don't want to use the gradle wrapper (`gradlew`), or if the wrapper is
missing, you can download gradle manually ([www.gradle.org](www.gradle.org)) and make sure the bin
directory is in your PATH environment variable. Then run `$ gradle build` (the APKs will also be
generated in `./app/build/outputs/apks/`).

### Android Studio

You can either create the new project with "Check out project from Version Control" and use the
github URL for this project, or if you already downloaded the code you can use "Import project
(Eclipse ADT, Gradle, etc.)".

When your project is created, you just need to build it, plug your device and start the app.

If you just want the APK, you'll still need to run the app once, or at least click on the Run icon
until you get the 'Choose Device' dialog (you don't even need to have a real device connected, you
can then click cancel). Just building the project is not enough to create the APKs on Android
Studio. Again, the APKs are located under `./app/build/outputs/apk/`.

## Installing the APK

To install the APK on your device, you can either start the app using Android Studio (it will
compile and install the APK automatically), or you can use a terminal, go to the APK directory and
do

`$ adb install app-debug.apk`

(assuming you have only one device connected to your computer).
