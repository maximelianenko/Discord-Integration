package di.dilogin.controller.impl;

import java.time.Instant;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.nickuc.login.api.nLoginAPI;

import di.dicore.api.DIApi;
import di.dilogin.BukkitApplication;
import di.dilogin.controller.DILoginController;
import di.dilogin.controller.LangManager;
import di.dilogin.controller.MainController;
import di.dilogin.dao.DIUserDao;
import di.dilogin.dao.DIUserDaoSqlImpl;
import di.dilogin.entity.DIUser;
import di.dilogin.minecraft.bukkit.BukkitUtil;
import di.dilogin.minecraft.bukkit.event.custom.DILoginEvent;
import di.dilogin.minecraft.bukkit.ext.authme.AuthmeHook;
import di.dilogin.minecraft.cache.TmpCache;
import di.dilogin.minecraft.cache.UserBlockedCache;
import di.internal.utils.Util;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

/**
 * DILogin plugin control.
 */
public class DILoginControllerBukkitImpl implements DILoginController {

    /**
     * Starts the implementation of the class that gets data from the users.
     */
    private static DIUserDao userDao;

    /**
     * Get the main plugin api.
     */
    private static final DIApi api = MainController.getDIApi();

    public DILoginControllerBukkitImpl(){
            DILoginControllerBukkitImpl.userDao = new DIUserDaoSqlImpl();
    }

    @Override
    public DIUserDao getDIUserDao() {
        return userDao;
    }

    @Override
    public EmbedBuilder getEmbedBase() {
        EmbedBuilder embedBuilder = new EmbedBuilder().setColor(
                Util.hex2Rgb(api.getInternalController().getConfigManager().getString("discord_embed_color")));
        if (api.getInternalController().getConfigManager().getBoolean("discord_embed_server_image")) {
            Optional<Guild> optGuild = Optional.ofNullable(api.getCoreController().getDiscordApi().get()
                    .getGuildById(api.getCoreController().getConfigManager().getLong("discord_server_id")));
            if (optGuild.isPresent()) {
                String url = optGuild.get().getIconUrl();
                if (url != null)
                    embedBuilder.setThumbnail(url);
            }
        }
        if (api.getInternalController().getConfigManager().getBoolean("discord_embed_timestamp"))
            embedBuilder.setTimestamp(Instant.now());
        return embedBuilder;
    }

    @Override
    public boolean isSessionEnabled() {
        return api.getInternalController().getConfigManager().getBoolean("sessions");
    }

    @Override
    public boolean isSyncroRolEnabled() {
        return api.getInternalController().getConfigManager().getBoolean("syncro_rol_enable");
    }

    @Override
    public boolean isSyncronizeOptionEnabled() {
        return api.getInternalController().getConfigManager().getBoolean("syncro_enable");
    }

    @Override
    public boolean isAuthmeEnabled() {
        return BukkitApplication.getPlugin().getServer().getPluginManager().isPluginEnabled("AuthMe");
    }

    @Override
    public boolean isNLoginEnabled() {
        return BukkitApplication.getPlugin().getServer().getPluginManager().isPluginEnabled("nLogin");
    }

    @Override
    public boolean isLuckPermsEnabled() {
        return BukkitApplication.getPlugin().getServer().getPluginManager().isPluginEnabled("LuckPerms")
                && api.getInternalController().getConfigManager().getBoolean("syncro_rol_enable");
    }

    @Override
    public void loginUser(String playerName, User user) {
        Optional<Player> optionalPlayer = BukkitUtil.getUserPlayerByName(playerName);

        if (!optionalPlayer.isPresent())
            return;

        Player player = optionalPlayer.get();

        if (user != null && isSyncronizeOptionEnabled()) {
            syncUserName(player, user);
        }

        if (isAuthmeEnabled()) {
            AuthmeHook.login(player);
        } else if (isNLoginEnabled()) {
            nLoginAPI.getApi().forceLogin(player.getName());
        } else {
            Bukkit.getScheduler().runTask(BukkitApplication.getPlugin(),
                    () -> Bukkit.getPluginManager().callEvent(new DILoginEvent(new DIUser(playerName, Optional.of(user)))));
            UserBlockedCache.remove(player.getName());
            player.sendMessage(LangManager.getString("login_success"));
        }
        TmpCache.removeLogin(player.getName());
    }

    @Override
    public void kickPlayer(String playerName, String message) {
        Optional<Player> optionalPlayer = BukkitUtil.getUserPlayerByName(playerName);

        if (!optionalPlayer.isPresent())
            return;

        Runnable task = () -> optionalPlayer.get().kickPlayer(message);
        Bukkit.getScheduler().runTask(BukkitApplication.getPlugin(), task);
    }

    /**
     * Syncro player's name.
     *
     * @param player Minecraft player.
     */
    private void syncUserName(Player player, User user) {
        Optional<DIUser> optDIUser = userDao.get(player.getName());

        if (!optDIUser.isPresent())
            return;

        JDA jda = api.getCoreController().getDiscordApi().get();
        Guild guild = api.getCoreController().getGuild().get();

        Member member = guild.retrieveMember(user, true).complete();
        Member bot = guild.retrieveMember(jda.getSelfUser(), true).complete();

        if (bot.canInteract(member)) {
            member.modifyNickname(player.getName()).queue();
        } else {
            api.getInternalController().getLogger()
                    .info("Cannot change the nickname of " + player.getName() + ". Insufficient permissions.");
        }
    }

}
