package net.robinfriedli.botify.listeners;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.boot.Shutdownable;
import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.exceptions.handlers.LoggingExceptionHandler;
import org.springframework.stereotype.Component;

/**
 * Listener responsible for listening for VoiceChannel events; currently used for the auto pause feature
 */
@Component
public class VoiceChannelListener extends ListenerAdapter implements Shutdownable {

    private final AudioManager audioManager;
    private final ExecutorService executorService;
    private final HibernateComponent hibernateComponent;

    public VoiceChannelListener(AudioManager audioManager, HibernateComponent hibernateComponent) {
        this.audioManager = audioManager;
        executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setUncaughtExceptionHandler(new LoggingExceptionHandler());
            return thread;
        });
        this.hibernateComponent = hibernateComponent;
        register();
    }

    @Override
    public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
        if (!event.getMember().getUser().isBot()) {
            executorService.execute(() -> {
                VoiceChannel channel = event.getChannelLeft();
                Guild guild = event.getGuild();
                AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
                if (channel.equals(playback.getVoiceChannel())
                    && noOtherMembersLeft(channel, guild)) {
                    if (isAutoPauseEnabled(guild)) {
                        playback.pause();
                        playback.leaveChannel();
                    } else {
                        playback.setAloneSince(LocalDateTime.now());
                    }
                }
            });
        }
    }

    @Override
    public void onGuildVoiceJoin(@Nonnull GuildVoiceJoinEvent event) {
        if (!event.getMember().getUser().isBot()) {
            executorService.execute(() -> {
                Guild guild = event.getGuild();
                AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
                if (event.getChannelJoined().equals(playback.getVoiceChannel())) {
                    playback.setAloneSince(null);
                }
            });
        }
    }

    private boolean noOtherMembersLeft(VoiceChannel channel, Guild guild) {
        return channel.getMembers().stream()
            .allMatch(member -> member.equals(guild.getSelfMember()) || member.getUser().isBot());
    }

    private boolean isAutoPauseEnabled(Guild guild) {
        return hibernateComponent.invokeWithSession(session -> {
            GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
            GuildManager guildManager = Botify.get().getGuildManager();
            GuildSpecification specification = guildManager.getContextForGuild(guild).getSpecification(session);
            AbstractGuildProperty enableAutoPauseProperty = guildPropertyManager.getProperty("enableAutoPause");
            if (enableAutoPauseProperty != null) {
                return (boolean) enableAutoPauseProperty.get(specification);
            }

            return true;
        });
    }

    @Override
    public void shutdown(int delayMs) {
        executorService.shutdown();
    }
}
