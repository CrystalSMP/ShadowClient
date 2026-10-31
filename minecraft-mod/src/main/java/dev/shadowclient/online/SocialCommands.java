package dev.shadowclient.online;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Chat-Befehle (nur im Client, gehen nicht an den Server):
 * /sc status [Text] · /sc dm <Name> <Text> · /sc invite <Name> · /sc join · /sc party invite <Name> · /sc party leave · /sc party
 */
public final class SocialCommands {
    private SocialCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(literal("sc")
                .executes(c -> help(c))
                .then(literal("status")
                        .executes(c -> { SocialService.setStatus("", r -> reply(r)); return 1; })
                        .then(argument("text", StringArgumentType.greedyString())
                                .executes(c -> { SocialService.setStatus(StringArgumentType.getString(c, "text"), r -> reply(r)); return 1; })))
                .then(literal("dm").then(argument("name", StringArgumentType.word())
                        .then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                            FriendsService.Friend f = friend(c);
                            if (f != null) SocialService.sendDm(f.uuid(), StringArgumentType.getString(c, "text"),
                                    r -> reply(r != null ? r : "✉ an " + f.name() + " gesendet"));
                            return 1;
                        }))))
                .then(literal("invite").then(argument("name", StringArgumentType.word()).executes(c -> {
                    FriendsService.Friend f = friend(c);
                    if (f != null) SocialService.invite(f.uuid(), r -> reply(r));
                    return 1;
                })))
                .then(literal("join").executes(c -> { reply(SocialService.acceptLast()); return 1; }))
                .then(literal("party")
                        .executes(c -> { showParty(); return 1; })
                        .then(literal("leave").executes(c -> { SocialService.partyLeave(r -> reply(r)); return 1; }))
                        .then(literal("invite").then(argument("name", StringArgumentType.word()).executes(c -> {
                            FriendsService.Friend f = friend(c);
                            if (f != null) SocialService.partyInvite(f.uuid(), r -> reply(r));
                            return 1;
                        }))))));
    }

    private static FriendsService.Friend friend(CommandContext<FabricClientCommandSource> c) {
        String name = StringArgumentType.getString(c, "name");
        FriendsService.Friend f = FriendsService.byName(name);
        if (f == null) reply(name + " ist nicht in deiner Freundesliste (Freunde-Modul an?)");
        return f;
    }

    private static int help(CommandContext<FabricClientCommandSource> c) {
        reply("/sc status <Text> · /sc dm <Name> <Text> · /sc invite <Name> · /sc join · /sc party [invite <Name> | leave]");
        return 1;
    }

    private static void showParty() {
        SocialService.Party p = SocialService.party();
        if (p == null) { reply("Du bist in keiner Party. /sc party invite <Name> erstellt eine."); return; }
        StringBuilder sb = new StringBuilder("Party: ");
        for (SocialService.Member m : p.members()) {
            sb.append(m.name()).append(m.uuid().equals(p.leader()) ? "★" : "").append(m.online() ? "" : " (offline)").append(", ");
        }
        reply(sb.substring(0, sb.length() - 2));
    }

    private static void reply(String msg) {
        if (msg == null) return;
        FriendsService.chat(Text.literal(msg).formatted(Formatting.GRAY));
    }
}
