<p align="center"><img src="src/main/resources/META-INF/pluginIcon.svg" width="96" alt="perINKpheral"></p>

# perINKpheral for CLion

CLion plugin for developing [perINKpheral](https://perinkpheral.dev) applications.
Includes toolchain setup, application builds, device access and a simulator.

[Plugin documentation](https://perinkpheral.dev/sdk#clion) -
[Developer documentation](https://perinkpheral.dev/sdk).
Application guide (PDF):
[English](https://github.com/GrakovNe/perinkpheral/releases/latest/download/perinkpheral-app-guide.pdf),
[Russian](https://github.com/GrakovNe/perinkpheral/releases/latest/download/perinkpheral-app-guide.ru.pdf).

## Requirements

CLion 2026.2 or later. Set Up Toolchain downloads the SDK and wasi-sdk;
git must be on `PATH`. Device access over the network requires firmware
with `APP_DEV_CONSOLE` enabled. The bundled simulator supports macOS and Linux.

## Build

Requires Java 21 and a sibling `../perinkpheral` checkout for `tools/core`.
`-PperinkpheralPath=<checkout>` selects another checkout;
`-PclionPath=<installation>` selects an installed CLion.

```sh
./gradlew buildPlugin test
```

The plugin archive is written to `build/distributions/`.
Installation: Settings - Plugins - Install Plugin from Disk.

CI builds and tests every push. Each successful push to `main` releases the plugin archive.

## Repository

- `src/main/kotlin/org/perinkpheral/clion/` - IDE actions, settings, run configurations and device tools.
- `src/main/resources/simulator/` - bundled simulator binaries.
- `src/main/resources/schemas/` - application manifest schema.
- `gradle.properties` - plugin version and CLion compatibility.

## License

[MIT](LICENSE).
