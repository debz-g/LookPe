package dev.redfox.lookpe.ui

import dev.redfox.obsidian.theme.ObsidianAccent
import dev.redfox.obsidian.theme.ObsidianNeutrals
import dev.redfox.obsidian.theme.obsidianColors

/** Emerald for money in. Shared by the app and the widget; res/values/colors.xml mirrors the window/icon shades. */
val LookPeAccent = ObsidianAccent.Emerald
val LookPeNeutrals = ObsidianNeutrals.Dark

/** Tokens outside composition (the Glance widget). */
val LookPeColors = obsidianColors(LookPeAccent, LookPeNeutrals)
