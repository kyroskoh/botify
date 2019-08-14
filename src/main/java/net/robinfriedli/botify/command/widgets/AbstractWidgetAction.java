package net.robinfriedli.botify.command.widgets;

import javax.annotation.Nullable;

import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.Botify;

/**
 * Abstract class to implement for any action that may be added to widget. Maps the action to the given emoji that will
 * be added to the widget and handles required permissions.
 */
public abstract class AbstractWidgetAction {

    @Nullable
    private final String requiredPermission;
    private final String emojiUnicode;
    private final boolean resetRequired;

    protected AbstractWidgetAction(@Nullable String requiredPermission, String emojiUnicode) {
        this(requiredPermission, emojiUnicode, false);
    }

    protected AbstractWidgetAction(@Nullable String requiredPermission, String emojiUnicode, boolean resetRequired) {
        this.requiredPermission = requiredPermission;
        this.emojiUnicode = emojiUnicode;
        this.resetRequired = resetRequired;
    }

    public void run(GuildMessageReactionAddEvent event) throws Exception {
        if (requiredPermission != null) {
            Botify.get().getSecurityManager().ensurePermission(requiredPermission, event.getMember());
        }
        handleReaction(event);
    }

    protected abstract void handleReaction(GuildMessageReactionAddEvent event) throws Exception;

    public String getEmojiUnicode() {
        return emojiUnicode;
    }

    public boolean isResetRequired() {
        return resetRequired;
    }
}
