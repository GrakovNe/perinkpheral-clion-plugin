<p align="center"><img src="src/main/resources/META-INF/pluginIcon.svg" width="96" alt="perINKpheral"></p>

# perINKpheral for CLion

Development tools for the [perINKpheral](https://github.com/GrakovNe/perinkpheral) desk display.
Status: preliminary, version 0.3.0.

## Functions

| Function | Where | Description |
|---|---|---|
| Toolchain setup | Tools, perINKpheral, Set Up Toolchain | Downloads the SDK checkout and wasi-sdk 34 into `~/.perinkpheral`. Paths that are set and valid are kept. |
| Settings | Settings, Tools, perINKpheral | SDK checkout, wasi-sdk, Python 3, device address. |
| New application | Tools, perINKpheral, New Application | Creates an application folder from the SDK sample. |
| Application project | Opening an application folder | A folder with `manifest.json` and C sources opens as a CMake project. Without a CMakeLists.txt the plugin offers to write one; it reads the plugin's settings from `~/.perinkpheral/toolchain.cmake`, so no environment variables are needed. A run configuration is created and selected. |
| Upload to Device | Tools, perINKpheral; the context menu of a file | Builds the application, installs it on the device and restarts the device into it. |
| Run | Run configuration "perINKpheral Application" | Builds the application, uploads it and restarts the device into it. Offered from the context menu of any file of an application folder. Compiler messages are links. |
| Simulator | Run configuration "<name> (simulator)" | The application runs on this computer in the firmware's own interpreter, host API and kit: the frame is the device's. The device window shows it while it runs, with Background, Foreground, the three intents, Alarm 0 and +15 min. The network is the computer's; a mocks file replaces answers. macOS: the simulator is in the plugin; Linux: built from the SDK checkout (a C compiler and libcurl). |
| Device | Tool window "perINKpheral" | Live screen, the four buttons (Shift+click: long press), a screenshot (PNG file and clipboard), status, log. |
| Manifest | `manifest.json` of an application folder | Validation, completion and descriptions by JSON schema. |
| Firmware over network | Tools, perINKpheral, Upload Firmware over Network | Sends an image to the device. |
| Firmware over USB | Tools, perINKpheral, Upload Firmware over USB | Writes an image with esptool. esptool is installed into `~/.perinkpheral/venv` on first use. |

## Requirements

- CLion 2026.2 or later.
- Python 3 and git on `PATH`.
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
