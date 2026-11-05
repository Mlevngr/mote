# Markwon's registry uses CorePlugin.class.isAssignableFrom(plugin.getClass()) to
# put the core plugin first. Merging skin plugins into CorePlugin reverses that
# order and lets the core span factory overwrite custom heading colors.
-keep,allowobfuscation class io.noties.markwon.core.CorePlugin { *; }
