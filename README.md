<p align="center">
  <img src="installer/logo.png" alt="Micro Hub" width="180">
</p>

# Micro Hub
Plugins with a setup window that installs or removes the set you choose. Built by [Opra Dev Team](https://github.com/Opra-Dev-Team).

Each plugin is its own jar. The client loads them from your local plugin folder the next time it starts. The jars in `dist/` are already built, so installing does not require a JDK.

| Plugin | Version | Minimum client | Jar |
| --- | --- | --- | --- |
| [Opra] Bank Sorter | 1.1.9 | 2.0.7 | `dist/BankSorterPlugin.jar` |
| [Opra] Sand Buyer | 1.0.3 | 1.9.6 | `dist/SandBuyerPlugin.jar` |
| [Opra] Molten Glass | 1.0.2 | 1.9.6 | `dist/MoltenGlassPlugin.jar` |
| [Opra] Eclipse Red | 1.0.9 | 1.9.6 | `dist/EclipseRedPlugin.jar` |
| [Opra] Flax Picker | 1.0.2 | 1.9.6 | `dist/FlaxPickerPlugin.jar` |
| [Opra] Motherlode Mine | 1.2.4 | 1.9.8 | `dist/OpraMotherlodePlugin.jar` |
| [Opra] House Thieving | 1.0.0 | 2.0.7 | `dist/OpraHouseThievingPlugin.jar` |

OP is lime and RA is red. That `[Opra]` prefix is how these plugins show up in the client plugin list.

```powershell
irm https://raw.githubusercontent.com/Opra-Dev-Team/Micro-Hub/dev/installer/run.ps1 | iex
```

```bash
curl -fsSL https://raw.githubusercontent.com/Opra-Dev-Team/Micro-Hub/dev/installer/run.sh | bash
```

## Setup window

The window lists each plugin and whether it is already installed. Check the ones you want, then use **Install selected**. Close the game client first so the jars can be replaced. When it finishes, restart the client and enable the plugins you want.


https://github.com/user-attachments/assets/99107c52-b892-42f8-843a-22dd0e524a36


[Windows screenshot](installer/screenshot.png)

[Linux screenshot](installer/screenshot-linux.png)

## Motherlode Mine Plugin (Demo)

https://github.com/user-attachments/assets/951d9a29-78f1-4084-9ef8-dffe4e7ebccd

---

**Disclaimer:** AI was used in the making of this project.
