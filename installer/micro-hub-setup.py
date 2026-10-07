#!/usr/bin/env python3
"""Linux setup window for Micro Hub.

Follows the Windows installer: same plugin list, same RuneLite folders,
and the same install, update, and uninstall rules.
"""

from __future__ import annotations

import hashlib
import os
import re
import shutil
import subprocess
import sys
import time
import webbrowser
import zipfile
from pathlib import Path

BG = "#0c0c0c"
CARD = "#161616"
EDGE = "#2e2e2e"
TEXT = "#e8e8e8"
MUTED = "#9a9a9a"
ACCENT = "#ffff00"
ACCENT_DARK = "#d6d600"
INSTALLED = "#34d399"
LOG_BG = "#080808"
LOG_FG = "#d4d4d8"
TRACK_OFF = "#3a3a3a"
WARN_BG = "#202000"
PILL_INSTALLED_BG = "#103024"
PILL_UPDATE_BG = "#303000"
PILL_OFF_BG = "#2c2d34"

RUN_SCRIPT_URI = (
    "https://raw.githubusercontent.com/Opra-Dev-Team/Micro-Hub/dev/installer/run.sh"
)
TEAM_NAME = "Opra Dev Team"
TEAM_GITHUB = "https://github.com/Opra-Dev-Team"
MICROBOT_URL = "https://microbot.cloud/"
SHORTCUT_NAME = "Micro Hub.desktop"

LEGACY = {
    "BankSorterPlugin.jar": "OpiesBankSorterPlugin.jar",
    "SandBuyerPlugin.jar": "OpiesSandBuyerPlugin.jar",
    "MoltenGlassPlugin.jar": "OpiesMoltenGlassPlugin.jar",
    "EclipseRedPlugin.jar": "OpiesEclipseRedPlugin.jar",
    "FlaxPickerPlugin.jar": "OpiesFlaxPickerPlugin.jar",
    "OpraMotherlodePlugin.jar": "MotherloadMinePlugin.jar",
}

BLURBS = {
    "BankSorterPlugin.jar": "Sorts the bank into an iron 8-tab layout.",
    "SandBuyerPlugin.jar": "Buys sand and soda ash in Catherby, then hops.",
    "EclipseRedPlugin.jar": "Collects Eclipse red at the Hunter Guild.",
    "MoltenGlassPlugin.jar": "Smelts molten glass at the Edgeville furnace.",
    "FlaxPickerPlugin.jar": "Picks flax at Nemus Retreat and can spin it.",
    "OpraMotherlodePlugin.jar": "Mines paydirt in the Motherlode Mine.",
    "OpraHouseThievingPlugin.jar": "Pickpockets wealthy citizens and thieves houses in Varlamore.",
}

VERSION_RE = re.compile(r"\d+\.\d+\.\d+")
WINDOW_W = 760
WINDOW_H = 620


def plugin_label(jar_name: str) -> str:
    name = Path(jar_name).stem
    if name.startswith("Opies"):
        name = name[5:]
    if name.endswith("Plugin"):
        name = name[:-6]
    spaced = re.sub(r"(?<!^)([A-Z])", r" \1", name)
    return spaced.strip()


class Library:
    def __init__(self, repo_root: Path, home: Path | None = None):
        self.repo_root = repo_root
        self.dist_dir = repo_root / "dist"
        self.library_file = self.dist_dir / "library.txt"
        self.home = home or Path.home()
        self.skip_client_check = False
        self.hash_cache: dict[tuple, str] = {}
        self.source_meta_cache: dict[str, dict] = {}
        self.jar_version_cache: dict[tuple, list[str]] = {}

    @property
    def plugin_dirs(self) -> list[Path]:
        return [
            self.home / ".runelite" / "microbot-plugins",
            self.home / ".runelite" / "sideloaded-plugins",
        ]

    @property
    def install_dir(self) -> Path:
        return self.plugin_dirs[0]

    def jar_names(self) -> list[str]:
        if not self.library_file.is_file():
            return []
        names: list[str] = []
        seen: set[str] = set()
        for line in self.library_file.read_text(encoding="utf-8").splitlines():
            name = line.strip()
            if not name or name.startswith("#"):
                continue
            if "/" in name or "\\" in name or ".." in name:
                raise ValueError(f"library.txt has an unsafe jar name: {name}")
            if name not in seen:
                seen.add(name)
                names.append(name)
        return names

    def installed_jar_paths(self, jar_name: str) -> list[Path]:
        stem = Path(jar_name).stem
        paths: list[Path] = []
        for directory in self.plugin_dirs:
            if not directory.is_dir():
                continue
            try:
                entries = list(directory.iterdir())
            except OSError:
                continue
            for file in entries:
                if not file.is_file():
                    continue
                if file.name == jar_name or file.name.startswith(stem + "-"):
                    paths.append(file)
        return paths

    def file_hash(self, path: Path) -> str:
        item = path.stat()
        key = (str(path), item.st_size, item.st_mtime_ns)
        cached = self.hash_cache.get(key)
        if cached is not None:
            return cached
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        self.hash_cache[key] = digest
        return digest

    def source_meta(self, jar_name: str) -> dict:
        cached = self.source_meta_cache.get(jar_name)
        if cached is not None:
            return cached
        meta = {"version": None, "min_client": None}
        stem = Path(jar_name).stem
        src = self.repo_root / "src"
        if src.is_dir():
            match = next(src.rglob(stem + ".java"), None)
            if match is not None:
                text = match.read_text(encoding="utf-8", errors="replace")
                version = re.search(
                    r'public static final String version = "([^"]+)"', text
                )
                if version:
                    meta["version"] = version.group(1)
                minimum = re.search(r'minClientVersion = "([^"]+)"', text)
                if minimum:
                    meta["min_client"] = minimum.group(1)
        self.source_meta_cache[jar_name] = meta
        return meta

    def jar_version_strings(self, jar_path: Path) -> list[str]:
        try:
            item = jar_path.stat()
        except OSError:
            return []
        key = (str(jar_path), item.st_size, item.st_mtime_ns)
        cached = self.jar_version_cache.get(key)
        if cached is not None:
            return list(cached)
        found: list[str] = []
        try:
            with zipfile.ZipFile(jar_path) as archive:
                for entry in archive.infolist():
                    name = entry.filename
                    if not name.endswith("Plugin.class") or "$" in name:
                        continue
                    text = archive.read(entry).decode("latin-1", errors="replace")
                    for value in VERSION_RE.findall(text):
                        if value not in found:
                            found.append(value)
        except Exception:
            self.jar_version_cache[key] = []
            return []
        self.jar_version_cache[key] = found
        return list(found)

    def jar_plugin_version(self, jar_path: Path | None, jar_name: str) -> str | None:
        if jar_path is None or not jar_path.is_file():
            return None
        minimum = self.source_meta(jar_name)["min_client"]
        for value in self.jar_version_strings(jar_path):
            if minimum and value == minimum:
                continue
            return value
        return None

    def available_version(self, jar_name: str) -> str | None:
        return self.jar_plugin_version(self.dist_dir / jar_name, jar_name)

    def installed_version(self, jar_name: str) -> str | None:
        for path in self.installed_jar_paths(jar_name):
            version = self.jar_plugin_version(path, jar_name)
            if version:
                return version
        return None

    def needs_update(self, jar_name: str) -> bool:
        source = self.dist_dir / jar_name
        if not source.is_file():
            return False
        installed = self.installed_jar_paths(jar_name)
        if not installed:
            return False
        try:
            source_hash = self.file_hash(source)
            for path in installed:
                if self.file_hash(path) != source_hash:
                    return True
        except OSError:
            return True
        return len(installed) > 1

    def legacy_name(self, jar_name: str) -> str | None:
        return LEGACY.get(jar_name)

    def legacy_installed(self, jar_name: str) -> bool:
        legacy = self.legacy_name(jar_name)
        if not legacy:
            return False
        return len(self.installed_jar_paths(legacy)) > 0

    def outdated(self) -> list[str]:
        return [
            name
            for name in self.jar_names()
            if self.needs_update(name) or self.legacy_installed(name)
        ]

    def any_installed(self) -> bool:
        return any(self.installed_jar_paths(name) for name in self.jar_names())

    def client_running(self) -> bool:
        if self.skip_client_check:
            return False
        proc = Path("/proc")
        if not proc.is_dir():
            return False
        for entry in proc.iterdir():
            if not entry.name.isdigit():
                continue
            try:
                comm = (entry / "comm").read_text(encoding="utf-8", errors="replace").strip()
                raw = (entry / "cmdline").read_bytes().replace(b"\x00", b" ")
            except OSError:
                continue
            if comm in ("RuneLite", "Microbot"):
                return True
            command = raw.decode("utf-8", errors="replace")
            exe = Path(command.split(" ", 1)[0]).name if command.strip() else ""
            names = {comm.lower(), exe.lower()}
            java = bool(names & {"java", "javaw", "java.exe", "javaw.exe"})
            if java and re.search(r"microbot-", command, re.I) and re.search(r"\.jar", command, re.I):
                return True
        return False

    def remove_jars(self, jar_names: list[str], on_step=None) -> list[Path]:
        removed: list[Path] = []
        total = len(jar_names)
        for index, jar_name in enumerate(jar_names, start=1):
            targets = [jar_name]
            legacy = self.legacy_name(jar_name)
            if legacy:
                targets.append(legacy)
            for target in targets:
                for path in self.installed_jar_paths(target):
                    path.unlink()
                    if path.exists():
                        raise RuntimeError(
                            f"Could not delete {path}. Close the client and try again."
                        )
                    removed.append(path)
            if on_step is not None:
                on_step(index, total, jar_name)
        return removed

    def install(self, jar_names: list[str], on_step=None) -> tuple[list[Path], list[Path]]:
        if self.client_running():
            raise RuntimeError(
                "Close the game client first so the old plugin files can be deleted."
            )
        if not jar_names:
            raise RuntimeError("Select at least one plugin.")
        missing = [
            name for name in jar_names if not (self.dist_dir / name).is_file()
        ]
        if missing:
            raise RuntimeError("Missing jar(s) in dist: " + ", ".join(missing))
        removed = self.remove_jars(jar_names)
        self.install_dir.mkdir(parents=True, exist_ok=True)
        installed: list[Path] = []
        total = len(jar_names)
        for index, jar_name in enumerate(jar_names, start=1):
            source = self.dist_dir / jar_name
            dest = self.install_dir / jar_name
            shutil.copyfile(source, dest)
            installed.append(dest)
            if on_step is not None:
                on_step(index, total, jar_name)
        return removed, installed

    def uninstall(self, jar_names: list[str], on_step=None) -> list[Path]:
        if self.client_running():
            raise RuntimeError(
                "Close the game client first so the plugin files can be deleted."
            )
        if not jar_names:
            raise RuntimeError("Select at least one plugin.")
        return self.remove_jars(jar_names, on_step)

    def launcher_dir(self) -> Path:
        return self.home / ".local" / "share" / "OpraMicroHub"

    def desktop_dir(self) -> Path:
        raw = os.environ.get("XDG_DESKTOP_DIR")
        if raw:
            return Path(os.path.expandvars(raw)).expanduser()
        config = self.home / ".config" / "user-dirs.dirs"
        if config.is_file():
            for line in config.read_text(encoding="utf-8").splitlines():
                text = line.strip()
                if text.startswith("XDG_DESKTOP_DIR="):
                    value = text.split("=", 1)[1].strip().strip('"')
                    value = value.replace("$HOME", str(self.home))
                    return Path(os.path.expandvars(value))
        return self.home / "Desktop"

    def shortcut_path(self) -> Path:
        return self.desktop_dir() / SHORTCUT_NAME

    def shortcut_exists(self) -> bool:
        return self.shortcut_path().is_file()

    def install_shortcut_icon(self) -> Path | None:
        source = self.repo_root / "installer" / "logo.png"
        if not source.is_file():
            return None
        folder = self.launcher_dir()
        folder.mkdir(parents=True, exist_ok=True)
        dest = folder / "logo.png"
        shutil.copyfile(source, dest)
        return dest

    def add_shortcut(self) -> Path:
        folder = self.launcher_dir()
        folder.mkdir(parents=True, exist_ok=True)
        script = folder / "open-installer.sh"
        script.write_text(
            "#!/usr/bin/env bash\n" f"curl -fsSL '{RUN_SCRIPT_URI}' | bash\n",
            encoding="utf-8",
        )
        script.chmod(0o755)
        icon = self.install_shortcut_icon()
        desktop = self.desktop_dir()
        desktop.mkdir(parents=True, exist_ok=True)
        lines = [
            "[Desktop Entry]",
            "Type=Application",
            "Version=1.0",
            "Name=Micro Hub",
            "Comment=Open the Micro Hub installer",
            f"Exec={script}",
            f"Path={folder}",
            "Terminal=false",
            "Categories=Utility;",
        ]
        if icon is not None:
            lines.append(f"Icon={icon}")
        path = self.shortcut_path()
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        path.chmod(0o755)
        self._trust_desktop(path)
        return path

    def update_existing_shortcut_icon(self) -> None:
        path = self.shortcut_path()
        if not path.is_file():
            return
        icon = self.install_shortcut_icon()
        if icon is None:
            return
        lines = []
        found = False
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.startswith("Icon="):
                lines.append(f"Icon={icon}")
                found = True
            else:
                lines.append(line)
        if not found:
            lines.append(f"Icon={icon}")
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    def remove_shortcut(self) -> None:
        path = self.shortcut_path()
        if path.is_file():
            path.unlink()

    @staticmethod
    def _trust_desktop(path: Path) -> None:
        try:
            subprocess.run(
                ["gio", "set", str(path), "metadata::trusted", "true"],
                check=False,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        except OSError:
            pass


def prepare_fonts(font_dir: Path) -> None:
    conf = Path(os.environ.get("TMPDIR", "/tmp")) / f"micro-hub-fonts-{os.getpid()}.conf"
    text = (
        '<?xml version="1.0"?>\n'
        '<!DOCTYPE fontconfig SYSTEM "fonts.dtd">\n'
        "<fontconfig>\n"
        '  <include ignore_missing="yes">/etc/fonts/fonts.conf</include>\n'
        f"  <dir>{font_dir}</dir>\n"
        "</fontconfig>\n"
    )
    conf.write_text(text, encoding="utf-8")
    os.environ["FONTCONFIG_FILE"] = str(conf)


def show_library_setup(repo_root: Path) -> None:
    prepare_fonts(repo_root / "installer" / "fonts")
    import tkinter as tk
    import tkinter.font as tkfont
    import tkinter.messagebox as messagebox

    def round_rect(canvas, x1, y1, x2, y2, radius, **kwargs):
        radius = min(radius, (x2 - x1) / 2, (y2 - y1) / 2)
        points = [
            x1 + radius, y1,
            x1 + radius, y1,
            x2 - radius, y1,
            x2 - radius, y1,
            x2, y1,
            x2, y1 + radius,
            x2, y1 + radius,
            x2, y2 - radius,
            x2, y2 - radius,
            x2, y2,
            x2 - radius, y2,
            x2 - radius, y2,
            x1 + radius, y2,
            x1 + radius, y2,
            x1, y2,
            x1, y2 - radius,
            x1, y2 - radius,
            x1, y1 + radius,
            x1, y1 + radius,
            x1, y1,
        ]
        return canvas.create_polygon(points, smooth=True, splinesteps=20, **kwargs)

    class PluginCard(tk.Canvas):
        def __init__(self, parent, app, jar_name, available, version, blurb):
            super().__init__(
                parent,
                height=46,
                bg=BG,
                highlightthickness=0,
                cursor="hand2",
                bd=0,
            )
            self.app = app
            self.jar_name = jar_name
            self.available = available
            self.available_version = version
            self.blurb = blurb
            self.checked = bool(available)
            self.knob = 1.0 if available else 0.0
            self.pill_text = "Not installed" if available else "Missing"
            self.pill_kind = "off" if available else "missing"
            self.has_update = False
            self.bind("<Button-1>", self.on_click)
            self.bind("<Configure>", lambda _event: self.redraw())

        def on_click(self, _event):
            if not self.available or self.app.busy:
                return
            self.checked = not self.checked
            self.app.refresh_status()

        def redraw(self):
            self.delete("all")
            width = max(self.winfo_width(), 480)
            height = 46
            round_rect(
                self, 1, 1, width - 2, height - 2, 8, fill=CARD, outline=EDGE
            )
            knob_t = min(1.0, max(0.0, self.knob))
            track_h = 16
            track_w = 34
            track_x = 12
            track_y = (height - track_h) / 2
            off = (58, 58, 58)
            on = (46, 46, 46) if not self.available else (255, 255, 0)
            color = tuple(int(off[i] + (on[i] - off[i]) * knob_t) for i in range(3))
            track = "#%02x%02x%02x" % color
            round_rect(
                self,
                track_x,
                track_y,
                track_x + track_w,
                track_y + track_h,
                8,
                fill=track,
                outline="",
            )
            knob_size = 12
            knob_x = track_x + 2 + (track_w - knob_size - 4) * knob_t
            knob_y = track_y + 2
            knob_off = (232, 232, 232)
            knob_on = (12, 12, 12)
            knob_rgb = tuple(
                int(knob_off[i] + (knob_on[i] - knob_off[i]) * knob_t) for i in range(3)
            )
            self.create_oval(
                knob_x,
                knob_y,
                knob_x + knob_size,
                knob_y + knob_size,
                fill="#%02x%02x%02x" % knob_rgb,
                outline="",
            )
            pill = self.pill_text or ""
            pill_font = self.app.pill_font
            if pill:
                text_w = pill_font.measure(pill)
                pill_h = 20
                pill_w = max(pill_h, int(text_w) + 16)
                pill_x = width - pill_w - 12
                pill_y = (height - pill_h) / 2
                if self.pill_kind == "installed":
                    pill_bg, pill_fg = PILL_INSTALLED_BG, INSTALLED
                elif self.pill_kind in ("update", "missing"):
                    pill_bg, pill_fg = PILL_UPDATE_BG, ACCENT
                else:
                    pill_bg, pill_fg = PILL_OFF_BG, MUTED
                round_rect(
                    self,
                    pill_x,
                    pill_y,
                    pill_x + pill_w,
                    pill_y + pill_h,
                    10,
                    fill=pill_bg,
                    outline="",
                )
                self.create_text(
                    pill_x + pill_w / 2,
                    pill_y + pill_h / 2,
                    text=pill,
                    fill=pill_fg,
                    font=pill_font,
                )
                text_right = pill_x - 12
            else:
                text_right = width - 16
            name_color = TEXT if self.available else MUTED
            self.create_text(
                54,
                14,
                text=plugin_label(self.jar_name),
                fill=name_color,
                font=self.app.name_font,
                anchor="w",
            )
            self.create_text(
                54,
                32,
                text=self.blurb,
                fill=MUTED,
                font=self.app.detail_font,
                anchor="w",
                width=max(80, text_right - 54),
            )

    class HubApp:
        def __init__(self):
            self.library = Library(repo_root)
            self.busy = False
            self.details_open = False
            self.warning_visible = False
            self.progress_visible = False
            self.progress_shown = 0.0
            self.update_wait_noted = False
            self.pending_update_applied = False
            self.cards: list[PluginCard] = []
            self._motion = None
            self._poll = None
            self.install_enabled = False

            self.root = tk.Tk()
            self.root.title("Micro Hub")
            self.root.configure(bg=BG)
            self.root.resizable(False, False)
            self.root.geometry(f"{WINDOW_W}x{WINDOW_H}")
            families = set(tkfont.families())
            for name in ("Segoe UI", "Ubuntu", "DejaVu Sans", "Sans"):
                if name in families:
                    self.ui_family = name
                    break
            else:
                self.ui_family = "TkDefaultFont"
            if "Jersey 10" in families:
                self.title_font = tkfont.Font(family="Jersey 10", size=-30)
            else:
                self.title_font = tkfont.Font(
                    family=self.ui_family, size=-22, weight="bold"
                )
            self.ui_font = tkfont.Font(family=self.ui_family, size=9)
            self.ui_bold = tkfont.Font(family=self.ui_family, size=9, weight="bold")
            self.name_font = tkfont.Font(family=self.ui_family, size=10, weight="bold")
            self.detail_font = tkfont.Font(family=self.ui_family, size=8)
            self.pill_font = tkfont.Font(family=self.ui_family, size=8, weight="bold")
            self.button_font = tkfont.Font(family=self.ui_family, size=10, weight="bold")

            logo_path = repo_root / "installer" / "logo.png"
            self._images = []
            if logo_path.is_file():
                image = tk.PhotoImage(file=str(logo_path))
                self._images.append(image)
                self.root.iconphoto(True, image)
                factor = max(1, round(max(image.width(), image.height()) / 44))
                self.logo_image = image.subsample(factor, factor) if factor > 1 else image
                self._images.append(self.logo_image)
            else:
                self.logo_image = None

            self.header = tk.Frame(self.root, bg=BG, height=96, width=WINDOW_W)
            if self.logo_image is not None:
                mark = tk.Label(self.header, image=self.logo_image, bg=BG, bd=0)
                mark.place(x=24, y=18)
            title = tk.Label(
                self.header,
                text="Micro Hub",
                font=self.title_font,
                fg=ACCENT,
                bg=BG,
            )
            title.place(x=78, y=8)
            subtitle = tk.Label(
                self.header,
                text="Turn plugins on to install them. Updates apply here when the client closes.",
                font=self.ui_font,
                fg=MUTED,
                bg=BG,
                anchor="w",
            )
            subtitle.place(x=80, y=46)
            tk.Label(
                self.header,
                text=TEAM_NAME,
                font=self.ui_bold,
                fg=MUTED,
                bg=BG,
            ).place(x=80, y=70)
            self.github = tk.Label(
                self.header,
                text="GitHub",
                font=self.ui_bold,
                fg=ACCENT,
                bg=BG,
                cursor="hand2",
            )
            self.github.place(x=200, y=70)
            self.github.bind("<Button-1>", lambda _e: self.open_link(TEAM_GITHUB))
            self.microbot = tk.Label(
                self.header,
                text="Get Microbot",
                font=self.ui_bold,
                fg=ACCENT,
                bg=BG,
                cursor="hand2",
            )
            self.microbot.bind("<Button-1>", lambda _e: self.open_link(MICROBOT_URL))

            self.warning = tk.Label(
                self.root,
                text="The game client is open. Install, uninstall, and updates wait until it closes.",
                font=self.ui_font,
                fg=ACCENT,
                bg=WARN_BG,
                anchor="w",
                padx=28,
            )
            self.select_all = self._link("Select all", TEXT, self.select_all_cards)
            self.clear_link = self._link("Clear", MUTED, self.clear_cards)
            self.list_wrap = tk.Frame(self.root, bg=BG)
            self.list_canvas = tk.Canvas(
                self.list_wrap, bg=BG, highlightthickness=0, bd=0
            )
            self.list_scroll = tk.Scrollbar(
                self.list_wrap,
                command=self.list_canvas.yview,
                bg=BG,
                troughcolor=CARD,
                activebackground=EDGE,
                bd=0,
                highlightthickness=0,
            )
            self.list_canvas.configure(yscrollcommand=self.list_scroll.set)
            self.cards_frame = tk.Frame(self.list_canvas, bg=BG)
            self.cards_window = self.list_canvas.create_window(
                (0, 0), window=self.cards_frame, anchor="nw"
            )
            self.cards_frame.bind("<Configure>", self._sync_scroll)
            self.list_canvas.bind("<Configure>", self._stretch_cards)
            self.list_canvas.bind("<Button-4>", lambda _e: self.list_canvas.yview_scroll(-3, "units"))
            self.list_canvas.bind("<Button-5>", lambda _e: self.list_canvas.yview_scroll(3, "units"))
            self.list_canvas.pack(side="left", fill="both", expand=True)
            self.list_scroll.pack(side="right", fill="y")

            self.status = tk.Label(
                self.root,
                text="Ready",
                font=self.ui_font,
                fg=MUTED,
                bg=BG,
                anchor="w",
            )
            self.details = self._link("Details", TEXT, self.toggle_details)
            self.progress = tk.Canvas(
                self.root, height=8, bg=BG, highlightthickness=0, bd=0
            )
            self.progress.bind("<Configure>", lambda _e: self.draw_progress())
            self.log = tk.Text(
                self.root,
                font=self.ui_font,
                bg=LOG_BG,
                fg=LOG_FG,
                insertbackground=LOG_FG,
                relief="flat",
                highlightthickness=0,
                bd=0,
                padx=8,
                pady=6,
                wrap="word",
            )
            self.log.configure(state="disabled")
            self.shortcut = self._link("", MUTED, self.toggle_shortcut)
            self.update_all = self._button(
                "Update all", CARD, ACCENT, self.on_update_all, width=120
            )
            self.update_all.configure(highlightthickness=1, highlightbackground=ACCENT)
            self.uninstall = self._link("Uninstall", MUTED, self.on_uninstall)
            self.close_link = self._link("Close", MUTED, self.close)
            self.install_button = self._button(
                "Install selected", ACCENT, "#000000", self.on_install, width=168
            )

            self.root.protocol("WM_DELETE_WINDOW", self.close)
            self.root.bind("<Button-4>", self._wheel)
            self.root.bind("<Button-5>", self._wheel)
            self.root.after(0, self.on_shown)

        def _link(self, text, color, command):
            label = tk.Label(
                self.root,
                text=text,
                font=self.ui_bold,
                fg=color,
                bg=BG,
                cursor="hand2",
            )
            label.bind("<Button-1>", lambda _e: command())
            return label

        def _button(self, text, bg, fg, command, width):
            label = tk.Label(
                self.root,
                text=text,
                font=self.button_font,
                fg=fg,
                bg=bg,
                cursor="hand2",
                width=width,
            )
            # width in text units is not pixels. Force a pixel width in layout.
            label.configure(width=0)
            label._pixel_width = width
            label._bg = bg
            label._fg = fg
            label.bind("<Button-1>", lambda _e: command())
            label.bind("<Enter>", lambda _e, widget=label: self._hover(widget, True))
            label.bind("<Leave>", lambda _e, widget=label: self._hover(widget, False))
            return label

        def _hover(self, widget, entering):
            if widget is self.install_button and not self.install_enabled:
                return
            if entering and widget is self.install_button:
                widget.configure(bg="#ffff8c")
            elif entering and widget is self.update_all:
                widget.configure(bg="#2c2e36")
            else:
                widget.configure(bg=widget._bg)

        def _sync_scroll(self, _event=None):
            self.list_canvas.configure(scrollregion=self.list_canvas.bbox("all"))

        def _stretch_cards(self, event):
            self.list_canvas.itemconfigure(self.cards_window, width=event.width)
            for card in self.cards:
                card.configure(width=max(200, event.width - 16))
                card.redraw()

        def _wheel(self, event):
            widget = self.root.winfo_containing(event.x_root, event.y_root)
            current = widget
            while current is not None:
                if current in (self.list_canvas, self.cards_frame) or isinstance(current, PluginCard):
                    delta = -3 if event.num == 4 else 3
                    self.list_canvas.yview_scroll(delta, "units")
                    return
                current = getattr(current, "master", None)

        def open_link(self, uri: str):
            webbrowser.open(uri)

        def write_log(self, message: str):
            stamp = time.strftime("%H:%M:%S")
            self.log.configure(state="normal")
            self.log.insert("end", f"[{stamp}]  {message}\n")
            self.log.see("end")
            self.log.configure(state="disabled")

        def set_status(self, message: str, kind: str):
            colors = {
                "ok": INSTALLED,
                "wait": ACCENT,
                "error": ACCENT,
                "busy": TEXT,
            }
            self.status.configure(text=message, fg=colors.get(kind, MUTED))

        def layout(self):
            width = WINDOW_W
            height = WINDOW_H
            header_h = 96
            banner_h = 40 if self.warning_visible else 0
            toolbar_h = 32
            status_h = 28
            progress_h = 14 if self.progress_visible else 0
            log_h = 100 if self.details_open else 0
            footer_h = 64
            self.header.place(x=0, y=0, width=width, height=header_h)
            self.root.update_idletasks()
            self.microbot.place(
                x=self.github.winfo_x() + self.github.winfo_reqwidth() + 16, y=70
            )
            if banner_h:
                self.warning.place(x=0, y=header_h, width=width, height=banner_h)
            else:
                self.warning.place_forget()
            y = header_h + banner_h + 8
            self.select_all.place(x=28, y=y + 2)
            self.clear_link.place(x=108, y=y + 2)
            list_top = y + toolbar_h
            list_bottom = height - footer_h - status_h - progress_h - log_h - 4
            list_h = max(120, list_bottom - list_top)
            self.list_wrap.place(x=16, y=list_top, width=width - 32, height=list_h)
            status_y = list_top + list_h + 8
            self.status.place(x=28, y=status_y, width=width - 150, height=22)
            self.details.place(x=width - 108, y=status_y)
            if progress_h:
                self.progress.place(x=28, y=status_y + 22, width=width - 56, height=8)
                self.draw_progress()
            else:
                self.progress.place_forget()
            if log_h:
                self.log.place(
                    x=28,
                    y=status_y + status_h + progress_h,
                    width=width - 56,
                    height=log_h,
                )
            else:
                self.log.place_forget()
            btn_y = height - 52
            self.shortcut.place(x=28, y=btn_y + 10)
            install_w = self.install_button._pixel_width
            self.install_button.place(
                x=width - 28 - install_w, y=btn_y, width=install_w, height=40
            )
            self.root.update_idletasks()
            close_w = max(48, self.close_link.winfo_reqwidth())
            close_x = self.install_button.winfo_x() - close_w - 20
            self.close_link.place(x=close_x, y=btn_y + 10)
            uninstall_w = max(64, self.uninstall.winfo_reqwidth())
            uninstall_x = close_x - uninstall_w - 18
            self.uninstall.place(x=uninstall_x, y=btn_y + 10)
            update_w = self.update_all._pixel_width
            self.update_all.place(
                x=uninstall_x - update_w - 16, y=btn_y, width=update_w, height=40
            )

        def draw_progress(self):
            canvas = self.progress
            canvas.delete("all")
            width = max(1, canvas.winfo_width())
            height = max(1, canvas.winfo_height())
            round_rect(canvas, 0, 0, width - 1, height - 1, 3, fill=CARD, outline="")
            shown = min(1.0, max(0.0, self.progress_shown))
            fill_w = int(width * shown)
            if fill_w > 2:
                round_rect(canvas, 0, 0, fill_w, max(1, height - 1), 3, fill=ACCENT, outline="")

        def show_progress(self, steps: int):
            self.progress_shown = 0.08
            self.progress_visible = True
            self.layout()

        def advance_progress(self, target: float):
            self.progress_shown = min(1.0, max(0.0, target))
            self.progress_visible = True
            if self.progress.winfo_ismapped():
                self.draw_progress()
            self.root.update_idletasks()

        def complete_progress(self):
            self.advance_progress(1)
            self.progress_visible = False
            self.layout()

        def hide_progress(self):
            self.progress_visible = False
            self.progress_shown = 0
            self.layout()

        def build_cards(self):
            for child in self.cards_frame.winfo_children():
                child.destroy()
            self.cards = []
            names = self.library.jar_names()
            if not names:
                empty = tk.Label(
                    self.cards_frame,
                    text=f"No plugins listed.\n{self.library.library_file}",
                    font=self.ui_font,
                    fg=MUTED,
                    bg=BG,
                    justify="left",
                )
                empty.pack(anchor="w", padx=12, pady=12)
                return
            for jar_name in names:
                source = self.library.dist_dir / jar_name
                available = source.is_file()
                card = PluginCard(
                    self.cards_frame,
                    self,
                    jar_name,
                    available,
                    self.library.available_version(jar_name),
                    BLURBS.get(jar_name, "Library plugin"),
                )
                card.pack(fill="x", padx=8, pady=(4, 2))
                card.bind("<Button-4>", lambda e: self.list_canvas.yview_scroll(-3, "units"))
                card.bind("<Button-5>", lambda e: self.list_canvas.yview_scroll(3, "units"))
                self.cards.append(card)
            self.root.update_idletasks()
            for card in self.cards:
                card.configure(width=max(200, self.list_canvas.winfo_width() - 16))
                card.redraw()

        def refresh_status(self):
            checked = []
            for card in self.cards:
                jar_name = card.jar_name
                version = card.available_version
                if not card.available:
                    card.pill_text = "Missing"
                    card.pill_kind = "missing"
                    card.has_update = False
                elif self.library.needs_update(jar_name) or self.library.legacy_installed(jar_name):
                    card.has_update = True
                    card.pill_text = f"Update {version}" if version else "Update"
                    card.pill_kind = "update"
                elif self.library.installed_jar_paths(jar_name):
                    installed = self.library.installed_version(jar_name)
                    card.pill_text = f"Installed {installed}" if installed else "Installed"
                    card.pill_kind = "installed"
                    card.has_update = False
                else:
                    card.pill_text = "Not installed"
                    card.pill_kind = "off"
                    card.has_update = False
                card.redraw()
                if card.checked and card.available:
                    checked.append(jar_name)
            self.install_enabled = len(checked) > 0
            if self.install_enabled:
                self.install_button.configure(bg=ACCENT, fg="#000000", cursor="hand2")
                self.install_button._bg = ACCENT
            else:
                self.install_button.configure(bg=EDGE, fg=MUTED, cursor="arrow")
                self.install_button._bg = EDGE
            if checked:
                self.uninstall.configure(fg=MUTED, cursor="hand2")
            else:
                self.uninstall.configure(fg="#5c5c5c", cursor="arrow")
            running = self.library.client_running()
            if self.warning_visible != running:
                self.warning_visible = running
                self.layout()

        def checked_names(self) -> list[str]:
            return [
                card.jar_name
                for card in self.cards
                if card.checked and card.available
            ]

        def select_all_cards(self):
            for card in self.cards:
                if card.available:
                    card.checked = True
            self.refresh_status()

        def clear_cards(self):
            for card in self.cards:
                card.checked = False
            self.refresh_status()

        def toggle_details(self):
            self.details_open = not self.details_open
            self.details.configure(text="Hide" if self.details_open else "Details")
            self.layout()

        def update_shortcut_link(self):
            if self.library.shortcut_exists():
                self.shortcut.configure(text="Remove desktop shortcut", fg=MUTED)
            else:
                self.shortcut.configure(text="Add desktop shortcut", fg=ACCENT)

        def toggle_shortcut(self):
            try:
                if self.library.shortcut_exists():
                    self.library.remove_shortcut()
                    self.write_log("Removed the desktop shortcut.")
                else:
                    self.library.add_shortcut()
                    self.write_log("Added a desktop shortcut. It opens the latest installer.")
                if self.library.shortcut_exists():
                    self.set_status("Desktop shortcut added.", "ok")
                else:
                    self.set_status("Desktop shortcut removed.", "ok")
            except Exception as exc:
                self.write_log(str(exc))
                self.set_status(str(exc), "error")
            self.update_shortcut_link()
            self.layout()

        def on_install(self):
            if self.busy or not self.install_enabled:
                return
            self.refresh_status()
            names = self.checked_names()
            if not names:
                return
            self.install_plugins(names)

        def on_update_all(self):
            if self.busy:
                return
            self.refresh_status()
            outdated = self.library.outdated()
            if not outdated:
                if self.library.any_installed():
                    self.set_status("All plugins are up to date.", "ok")
                    self.write_log("All plugins are up to date.")
                else:
                    self.set_status("No plugins are installed yet.", "idle")
                    self.write_log(
                        "No plugins are installed yet. Turn some on and use Install selected."
                    )
                return
            self.install_plugins(outdated)

        def on_uninstall(self):
            if self.busy:
                return
            self.refresh_status()
            if self.library.client_running():
                self.warning_visible = True
                self.layout()
                self.set_status(
                    "Close the game client first. Uninstall will wait until it closes.",
                    "wait",
                )
                self.write_log("Uninstall is waiting for the game client to close.")
                return
            names = self.checked_names()
            present = [name for name in names if self.library.installed_jar_paths(name)]
            if not present:
                self.write_log("None of the selected plugins are installed.")
                self.set_status("None of the selected plugins are installed.", "idle")
                return
            self.busy = True
            try:
                self.show_progress(len(names))

                def on_step(step, total, jar_name):
                    self.set_status(f"Removing {plugin_label(jar_name)}", "busy")
                    self.advance_progress(step / max(1, total))

                removed = self.library.uninstall(names, on_step)
                for path in removed:
                    self.write_log(f"Uninstalled: {path}")
                self.write_log("Other plugins were left in place.")
                self.complete_progress()
                self.set_status(
                    f"Uninstalled {len(removed)} file(s). Other plugins were left in place.",
                    "ok",
                )
            except Exception as exc:
                self.hide_progress()
                self.write_log(str(exc))
                self.set_status(str(exc), "error")
            finally:
                self.busy = False
                self.refresh_status()

        def install_plugins(self, names: list[str]) -> bool:
            if not names:
                return False
            if self.library.client_running():
                self.warning_visible = True
                self.layout()
                self.set_status(
                    "Close the game client first. Install will wait until it closes.",
                    "wait",
                )
                self.write_log("Install is waiting for the game client to close.")
                return False
            self.busy = True
            try:
                self.show_progress(len(names))

                def on_step(step, total, jar_name):
                    self.set_status(f"Installing {plugin_label(jar_name)}", "busy")
                    self.advance_progress(step / max(1, total))

                removed, installed = self.library.install(names, on_step)
                for path in removed:
                    self.write_log(f"Removed old copy: {path}")
                for path in installed:
                    self.write_log(f"Installed: {path}")
                self.write_log("Restart the client, then enable the plugins you want.")
                self.complete_progress()
                self.set_status(
                    f"Installed {len(installed)}. Restart the client before using them.",
                    "ok",
                )
                return True
            except Exception as exc:
                self.hide_progress()
                self.write_log(str(exc))
                self.set_status(str(exc), "error")
                return False
            finally:
                self.busy = False
                self.refresh_status()

        def automatic_updates(self):
            if self.pending_update_applied or self.busy:
                return
            outdated = self.library.outdated()
            if not outdated:
                self.update_wait_noted = False
                return
            labels = [plugin_label(name) for name in outdated]
            list_text = ", ".join(labels)
            if self.library.client_running():
                if not self.update_wait_noted:
                    self.update_wait_noted = True
                    if len(labels) == 1:
                        message = (
                            f"{list_text} has an update. It will install when the client closes."
                        )
                    else:
                        message = (
                            f"Updates ready for {list_text}. They will install when the client closes."
                        )
                    self.write_log(message)
                    self.set_status(message, "wait")
                return
            self.update_wait_noted = False
            self.pending_update_applied = True
            self.busy = True
            try:
                self.show_progress(len(outdated))

                def on_step(step, total, jar_name):
                    self.set_status(f"Updating {plugin_label(jar_name)}", "busy")
                    self.advance_progress(step / max(1, total))

                removed, installed = self.library.install(outdated, on_step)
                for path in removed:
                    self.write_log(f"Removed old copy: {path}")
                for path in installed:
                    self.write_log(f"Updated: {path}")
                if len(labels) == 1:
                    done = f"{list_text} was updated. Restart the client before using it."
                else:
                    done = (
                        f"Updated {len(labels)} plugins. Restart the client before using them."
                    )
                self.write_log(done)
                self.complete_progress()
                self.set_status(done, "ok")
            except Exception as exc:
                self.pending_update_applied = False
                self.hide_progress()
                self.write_log(str(exc))
                self.set_status(str(exc), "error")
            finally:
                self.busy = False
                self.refresh_status()

        def animate(self):
            for card in self.cards:
                target = 1.0 if card.checked else 0.0
                current = card.knob
                if abs(current - target) > 0.015:
                    nxt = current + (target - current) * 0.28
                    if abs(nxt - target) < 0.02:
                        nxt = target
                    card.knob = nxt
                    card.redraw()
            self._motion = self.root.after(16, self.animate)

        def poll(self):
            if not self.busy:
                self.refresh_status()
                self.automatic_updates()
            self._poll = self.root.after(1500, self.poll)

        def on_shown(self):
            self.center()
            self.layout()
            self.build_cards()
            self.refresh_status()
            if self.library.shortcut_exists():
                self.library.update_existing_shortcut_icon()
            self.update_shortcut_link()
            self.write_log("Turn on the plugins you want. Turned off plugins are left alone.")
            if self.library.shortcut_exists():
                self.write_log("Desktop shortcut is on the desktop.")
            else:
                self.write_log("Add a desktop shortcut if you want to open this installer later.")
            self.set_status("Turn on the plugins you want.", "idle")
            self.automatic_updates()
            self.poll()
            self.animate()

        def center(self):
            self.root.update_idletasks()
            screen_w = self.root.winfo_screenwidth()
            screen_h = self.root.winfo_screenheight()
            x = max(0, (screen_w - WINDOW_W) // 2)
            y = max(0, (screen_h - WINDOW_H) // 2)
            self.root.geometry(f"{WINDOW_W}x{WINDOW_H}+{x}+{y}")

        def close(self):
            if self._motion is not None:
                self.root.after_cancel(self._motion)
            if self._poll is not None:
                self.root.after_cancel(self._poll)
            self.root.destroy()

    try:
        app = HubApp()
    except Exception as exc:
        root = tk.Tk()
        root.withdraw()
        messagebox.showerror("Micro Hub setup failed", str(exc))
        root.destroy()
        raise SystemExit(1)
    app.root.mainloop()


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    try:
        show_library_setup(repo_root)
    except Exception as exc:
        sys.stderr.write(f"Micro Hub setup failed: {exc}\n")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
