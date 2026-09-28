<p align="center">
  <img src="installer/opes-plugin-hut.png" alt="Opie Plugin Library" width="180">
</p>

# Opie Plugin Library
Plugins with a Windows setup window that installs or removes the set you choose.

Each plugin is its own jar. The client loads them from your local plugin folder the next time it starts. The jars in `dist/` are already built, so installing does not require a JDK.

| Plugin | Version | Minimum client | Jar |
| --- | --- | --- | --- |
| [OPIE] Bank Sorter | 2.3.2 | 2.0.7 | `dist/OpiesBankSorterPlugin.jar` |
| [OPIE] Sand Buyer | 1.0.3 | 1.9.6 | `dist/OpiesSandBuyerPlugin.jar` |
| [OPIE] Molten Glass | 1.0.2 | 1.9.6 | `dist/OpiesMoltenGlassPlugin.jar` |
| [OPIE] Eclipse Red | 1.2.4 | 1.9.6 | `dist/OpiesEclipseRedPlugin.jar` |
| [OPIE] Flax Picker | 1.0.1 | 1.9.6 | `dist/OpiesFlaxPickerPlugin.jar` |

The red `[OPIE]` prefix is how these plugins show up in the client plugin list.

```powershell
irm https://raw.githubusercontent.com/opesoid/opies-plugin-hut/dev/installer/run.ps1 | iex
```

