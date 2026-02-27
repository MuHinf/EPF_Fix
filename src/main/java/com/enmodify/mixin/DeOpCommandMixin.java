package com.enmodify.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.commands.DeOpCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Collection;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.LEVEL_ADMINS;

@Mixin(DeOpCommands.class)
public class DeOpCommandMixin {

    @Overwrite
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("deop")
            .requires(net.minecraft.commands.Commands.hasPermission(LEVEL_ADMINS))
            .then(argument("targets", GameProfileArgument.gameProfile())
                .executes(ctx -> execute(ctx))));
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        PlayerList playerList = server.getPlayerList();
        Collection<NameAndId> targets = GameProfileArgument.getGameProfiles(context, "targets");

        // 获取执行者的权限等级
        int sourceLevel = getSourceLevel(source, playerList);

        int count = 0;
        for (NameAndId target : targets) {
            // 获取目标当前的 OP 条目
            ServerOpListEntry entry = (ServerOpListEntry) playerList.getOps().get(target);
            
            if (entry != null) {
                int targetLevel = entry.permissions().level().id();

                // 核心越权检查：不能撤销比自己等级高的人
                if (targetLevel > sourceLevel) {
                    source.sendFailure(Component.literal("无法撤销等级高于你的管理员: " + target.name()));
                    continue;
                }

                playerList.getOps().remove(target);
                count++;
                
                source.sendSuccess(() -> Component.literal("已撤销 " + target.name() + " 的管理员权限"), true);
                
                // 刷新玩家指令表
                ServerPlayer targetPlayer = playerList.getPlayer(target.id());
                if (targetPlayer != null) {
                    server.getCommands().sendCommands(targetPlayer);
                }
            } else {
                source.sendFailure(Component.literal(target.name() + " 本来就不是管理员"));
            }
        }

        if (count == 0 && !targets.isEmpty()) {
            throw new SimpleCommandExceptionType(Component.literal("没有玩家被撤销权限")).create();
        }

        return count;
    }

    // 复用之前的获取等级逻辑
    private static int getSourceLevel(CommandSourceStack source, PlayerList playerList) {
        if (source.getEntity() instanceof ServerPlayer player) {
            ServerOpListEntry entry = (ServerOpListEntry) playerList.getOps().get(player.nameAndId());
            return (entry != null) ? entry.permissions().level().id() : 0;
        }
        return 4; // 控制台默认为最高等级
    }
}