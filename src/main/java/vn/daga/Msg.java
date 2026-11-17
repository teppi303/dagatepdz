package vn.daga;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public final class Msg {
    private static final LegacyComponentSerializer LS = LegacyComponentSerializer.legacyAmpersand();
    public static final String PREFIX = "&8[&6ĐáGà&8] &r";

    private Msg() {}

    public static Component c(String s) {
        return LS.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static void send(CommandSender to, String msg) {
        to.sendMessage(c(PREFIX + msg));
    }

    public static void raw(CommandSender to, String msg) {
        to.sendMessage(c(msg));
    }
}
