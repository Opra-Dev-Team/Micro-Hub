<p align="center">
  <img src="installer/micro-hub.png" alt="Micro Hub" width="180">
</p>

# Micro Hub
Plugins with a Windows setup window that installs or removes the set you choose. Built by [Opra Dev Team](https://github.com/Opra-Dev-Team).

Each plugin is its own jar. The client loads them from your local plugin folder the next time it starts. The jars in `dist/` are already built, so installing does not require a JDK.

| Plugin | Version | Minimum client | Jar |
| --- | --- | --- | --- |
| [Opra] Bank Sorter | 2.3.2 | 2.0.7 | `dist/BankSorterPlugin.jar` |
| [Opra] Sand Buyer | 1.0.3 | 1.9.6 | `dist/SandBuyerPlugin.jar` |
| [Opra] Molten Glass | 1.0.2 | 1.9.6 | `dist/MoltenGlassPlugin.jar` |
| [Opra] Eclipse Red | 1.2.4 | 1.9.6 | `dist/EclipseRedPlugin.jar` |
| [Opra] Flax Picker | 1.0.2 | 1.9.6 | `dist/FlaxPickerPlugin.jar` |
| [Opra] Motherlode Mine | 1.1.0 | 1.9.8 | `dist/OpraMotherlodePlugin.jar` |

OP is lime and RA is red. That `[Opra]` prefix is how these plugins show up in the client plugin list.

```powershell
irm https://raw.githubusercontent.com/Opra-Dev-Team/Micro-Hub/dev/installer/run.ps1 | iex
```

## Setup window

The window lists each plugin and whether it is already installed. Check the ones you want, then use **Install selected**. Close the game client first so the jars can be replaced. When it finishes, restart the client and enable the plugins you want.

<p align="center">
  <img src="installer/screenshot.png" alt="Micro Hub setup window showing the plugin list and Install selected button" width="720">
</p>

---

**Disclaimer:** AI was used in the making of this project.
