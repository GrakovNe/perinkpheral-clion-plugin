<p align="center"><img src="src/main/resources/META-INF/pluginIcon.svg" width="96" alt="perINKpheral"></p>

# perINKpheral for CLion

Development tools for the [perINKpheral](https://github.com/GrakovNe/perinkpheral) desk display.
Status: preliminary, version 0.7.1. Two parts: the core - the build and its checks, the device
over the network, the simulator's network - is `tools/core` of the perinkpheral repository, the
same that `tools/perinkpheral` runs from the command line; this plugin is what ties it to the IDE.
All of it runs in the IDE's own process, with no Python.

Building the plugin needs a checkout of perinkpheral beside this one (`../perinkpheral`), or
`-PperinkpheralPath=<checkout>`: Gradle builds the core from it.

## Functions

| Function | Where | Description |
|---|---|---|
| Toolchain setup | Tools, perINKpheral, Set Up Toolchain | Downloads the SDK checkout and wasi-sdk 34 into `~/.perinkpheral`. Paths that are set and valid are kept. |
| Settings | Settings, Tools, perINKpheral | SDK checkout, wasi-sdk, device address, the simulator's language and font. |
| New application | Tools, perINKpheral, New Application | Creates an application folder from the SDK sample. |
| Application project | Opening an application folder | A folder with `manifest.json` and C sources opens as a CMake project. Without a CMakeLists.txt the plugin offers to write one; it reads the plugin's settings from `~/.perinkpheral/toolchain.cmake` and the SDK's module, `sdk/cmake/perinkpheral.cmake`: CMake compiles the code with clang of wasi-sdk and the build's flags, which the plugin writes there from the core: the module CLion compiles is the one the device gets. `strings.h` and `pictures.h` are made by the plugin into `~/.perinkpheral/generated/<name>` when the project opens and when a file of `strings` or `pictures` changes; a string the code does not use yet, or a translation that lags behind, is accepted there (the build refuses both), and when they cannot be made the ones before stay. A problem is said once, not for every file saved. A run configuration is created and selected. |
| Upload to Device | Tools, perINKpheral; the context menu of a file | Builds the application, installs it on the device and restarts the device into it. Stop cuts an upload under way. |
| Run | Run configuration "perINKpheral Application" | Builds the application - compiled with clang, checked by the rules the device applies, measured against what the device has - uploads it and restarts the device into it. Offered from the context menu of any file of an application folder. Compiler messages are links. |
| Simulator | Run configuration "<name> (simulator)" | The application runs on this computer in the firmware's own interpreter, host API and kit: the frame is the device's. The device window shows it while it runs, with Background, Foreground, the three intents, Alarm 0 and +15 min. The network is the IDE's own, the local network included; redirects, hosts and headers follow the device's rules; a mocks file replaces answers. Stop during the build keeps the simulator from starting. The simulator is in the plugin for macOS (arm64, x86_64) and Linux (x86_64, aarch64). Not on Windows yet. |
| Device | Tool window "perINKpheral" | Live screen, the four buttons (Shift+click: long press), a screenshot (PNG file and clipboard), status, log, which goes on from the beginning of the new one after a restart. |
| Manifest | `manifest.json` of an application folder | Validation, completion and descriptions by JSON schema, list settings and `each` polls included. |
| Firmware over network | Tools, perINKpheral, Upload Firmware over Network | Sends an image to the device. |
| Firmware over USB | Tools, perINKpheral, Upload Firmware over USB | Writes an image with esptool: Espressif's own build of it, a program with no Python under it, downloaded into `~/.perinkpheral` on first use. |

## Requirements

- CLion 2026.2 or later.
- git on `PATH` (Set Up Toolchain clones the SDK with it).
- A device with development firmware (`APP_DEV_CONSOLE`, the default) for Run, Device and Firmware over network.

## Firmware over USB

| Region | Action |
|---|---|
| `ota_0` | written with the image |
| `otadata` | erased; the bootloader then starts `ota_0` |
| bootloader, partition table, `nvs`, `appkv`, `appcode`, `apps`, `glyphs` | not touched |

Settings, the Wi-Fi network and installed applications are kept. Offsets are read from
`firmware/partitions.csv` of the SDK checkout.

## Build

```
./gradlew buildPlugin
```

The archive is written to `build/distributions`. Install it with Settings, Plugins, Install Plugin from Disk.

`-PclionPath=<CLion installation>` builds against an installed CLion instead of a downloaded one.

## License

MIT.

## Why the plugin does its own network

macOS lets an application into the local network by the user's permission, and programs the
application starts do not always get it: a Python started by CLion could not reach the device
while CLion could. Everything that talks to the device or to a user's server - the upload, the
firmware, the simulator's requests - is therefore done in CLion's own process.
