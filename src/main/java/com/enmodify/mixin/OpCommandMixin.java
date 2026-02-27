package com.enmodify.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.NameAndId; 
import net.minecraft.server.commands.OpCommand;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Collection;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.LEVEL_ADMINS; 

@Mixin(OpCommand.class)
public class OpCommandMixin {

    @Overwrite
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("op")
            .requires(net.minecraft.commands.Commands.hasPermission(LEVEL_ADMINS))
            // --- ADD 子指令 ---
            .then(literal("add")
                .then(argument("targets", GameProfileArgument.gameProfile())
                    .executes(ctx -> executeAdd(ctx, "-", false))
                    .then(argument("level", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"0", "1", "2", "3", "4", "-"}, builder))
                        .executes(ctx -> executeAdd(ctx, StringArgumentType.getString(ctx, "level"), false))
                        .then(argument("bypass", BoolArgumentType.bool())
                            .executes(ctx -> executeAdd(ctx, StringArgumentType.getString(ctx, "level"), BoolArgumentType.getBool(ctx, "bypass")))))))
            // --- MODIFY 子指令 ---
            .then(literal("modify")
                .then(argument("targets", GameProfileArgument.gameProfile())
                    .then(argument("level", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"0", "1", "2", "3", "4", "-"}, builder))
                        .executes(ctx -> executeModify(ctx, StringArgumentType.getString(ctx, "level"), null))
                        .then(argument("bypass", BoolArgumentType.bool())
                            .executes(ctx -> executeModify(ctx, StringArgumentType.getString(ctx, "level"), BoolArgumentType.getBool(ctx, "bypass")))))))
            // --- GET 子指令 ---
            .then(literal("get")
                .then(argument("targets", GameProfileArgument.gameProfile())
                    .executes(ctx -> executeGet(ctx))))
            // --- LIST 子指令 ---
            .then(literal("list")
                .executes(ctx -> executeList(ctx))));
    }

    private static int executeAdd(CommandContext<CommandSourceStack> context, String levelStr, boolean bypass) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        PlayerList playerList = server.getPlayerList();
        Collection<NameAndId> targets = GameProfileArgument.getGameProfiles(context, "targets");

        // 这里的 operatorUserPermissions().level().id() 是根据你查阅的 1.21.11 源码修正的
        int requestedLevel = "-".equals(levelStr) ? server.operatorUserPermissions().level().id() : Integer.parseInt(levelStr);
        int sourceLevel = getSourceLevel(source, playerList);

        if (requestedLevel > sourceLevel) {
            throw new SimpleCommandExceptionType(Component.literal("越权：最高只能赋予 " + sourceLevel + " 级")).create();
        }

        for (NameAndId target : targets) {
            PermissionLevel pLevel = PermissionLevel.byId(requestedLevel);
            LevelBasedPermissionSet pSet = LevelBasedPermissionSet.forLevel(pLevel);
            playerList.getOps().add(new ServerOpListEntry(target, pSet, bypass));
            
            source.sendSuccess(() -> Component.literal("已添加 " + target.name() + " (等级: " + requestedLevel + ")"), true);
            refreshPlayer(server, playerList, target);
        }
        return targets.size();
    }

    private static int executeModify(CommandContext<CommandSourceStack> context, String levelStr, Boolean bypass) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        PlayerList playerList = server.getPlayerList();
        Collection<NameAndId> targets = GameProfileArgument.getGameProfiles(context, "targets");

        int sourceLevel = getSourceLevel(source, playerList);

        for (NameAndId target : targets) {
            ServerOpListEntry oldEntry = (ServerOpListEntry) playerList.getOps().get(target);
            if (oldEntry == null) continue;

            int currentLevel = oldEntry.permissions().level().id(); 
            boolean currentBypass = oldEntry.getBypassesPlayerLimit(); // 1.21.11 映射名

            int newLevel = "-".equals(levelStr) ? currentLevel : Integer.parseInt(levelStr);
            boolean newBypass = (bypass == null) ? currentBypass : bypass;

            if (newLevel > sourceLevel || currentLevel > sourceLevel) {
                source.sendFailure(Component.literal("无法修改高于自己等级的玩家: " + target.name()));
                continue;
            }

            playerList.getOps().remove(target);
            PermissionLevel newPLevel = PermissionLevel.byId(newLevel);
            LevelBasedPermissionSet newPSet = LevelBasedPermissionSet.forLevel(newPLevel);
            playerList.getOps().add(new ServerOpListEntry(target, newPSet, newBypass));
            
            source.sendSuccess(() -> Component.literal("已修改 " + target.name() + " 为等级: " + newLevel), true);
            refreshPlayer(server, playerList, target);
        }
        return targets.size();
    }

    private static int executeGet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PlayerList playerList = context.getSource().getServer().getPlayerList();
        Collection<NameAndId> targets = GameProfileArgument.getGameProfiles(context, "targets");

        for (NameAndId target : targets) {
            ServerOpListEntry entry = (ServerOpListEntry) playerList.getOps().get(target);
            if (entry != null) {
                context.getSource().sendSuccess(() -> Component.literal(target.name() + " 等级: " + entry.permissions().level().id()), false);
            } else {
                context.getSource().sendFailure(Component.literal(target.name() + " 不是管理员"));
            }
        }
        return targets.size();
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Collection<ServerOpListEntry> opEntries = source.getServer().getPlayerList().getOps().getEntries();

        if (opEntries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("当前没有管理员"), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== 管理员列表 (" + opEntries.size() + ") ==="), false);
        for (ServerOpListEntry entry : opEntries) {
            NameAndId user = (NameAndId) entry.getUser(); // 强制转换以匹配 1.21.11
            if (user != null) {
                source.sendSuccess(() -> Component.literal(
                    "- " + user.name() + " [等级: " + entry.permissions().level().id() + "]" + 
                    (entry.getBypassesPlayerLimit() ? " (不占人数)" : "")
                ), false);
            }
        }
        return opEntries.size();
    }

    private static int getSourceLevel(CommandSourceStack source, PlayerList playerList) {
        if (source.getEntity() instanceof ServerPlayer player) {
            ServerOpListEntry entry = (ServerOpListEntry) playerList.getOps().get(player.nameAndId());
            return (entry != null) ? entry.permissions().level().id() : 0;
        }
        return 4;
    }

    private static void refreshPlayer(MinecraftServer server, PlayerList playerList, NameAndId target) {
        ServerPlayer targetPlayer = playerList.getPlayer(target.id());
        if (targetPlayer != null) {
            server.getCommands().sendCommands(targetPlayer);
        }
    }
}