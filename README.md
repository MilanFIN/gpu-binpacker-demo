# 3D Binpacker Demo

![demo packing result](https://raw.githubusercontent.com/MilanFIN/GA-binpacker/refs/heads/main/images/3dbin.png)

A demonstration application for using the [gpu-binpacker](https://github.com/MilanFIN/gpu-binpacker) library.


## Running the Demo

To run the demo locally, use the included Gradle wrapper:
```bash
./gradlew run
```

By default, the demo is configured to fetch the latest `gpu-binpacker` library artifact via JitPack. 

### Local Library Development
If you are modifying the library locally and want to test it with this demo, you can update the gradle files to link to your local source:
1. In `settings.gradle`, uncomment the local source path.
2. In `build.gradle`, comment out the JitPack implementation line and uncomment `implementation project(':localLib')`.
