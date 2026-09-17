package me.corvino.aeronauticsdiscovery.mixin.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.commands.SharedSuggestionProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class PinWandCommandSuggestionsMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private EditBox input;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    private void updateUsageInfo() {
    }

    @Unique
    private static final String AERONAUTICSDISCOVERY$FUNCTION_WRAP = "function ";

    @Inject(method = "updateCommandInfo", at = @At("TAIL"), require = 0)
    private void aeronauticsdiscovery$completePinwandInner(CallbackInfo ci) {
        if (this.minecraft.player == null) return;
        if (this.minecraft.player.connection == null) return;
        String line = this.input.getValue();
        int tailStart = aeronauticsdiscovery$tailStart(line, "command");
        String wrap = null;
        if (tailStart < 0) {
            tailStart = aeronauticsdiscovery$tailStart(line, "function");
            if (tailStart >= 0) wrap = AERONAUTICSDISCOVERY$FUNCTION_WRAP;
        }
        if (tailStart < 0) return;
        int cursor = this.input.getCursorPosition();
        if (cursor < tailStart) return;
        String tail = line.substring(tailStart);
        if (tail.startsWith("/")) {
            tail = tail.substring(1);
            tailStart += 1;
            if (cursor < tailStart) return;
        }
        int offset = tailStart - (wrap == null ? 0 : wrap.length());
        if (offset < 0) return;
        String inner = wrap == null ? tail : wrap + tail;
        try {
            CommandDispatcher<SharedSuggestionProvider> dispatcher = this.minecraft.player.connection.getCommands();
            if (dispatcher == null) return;
            ParseResults<SharedSuggestionProvider> parse = dispatcher.parse(
                    new StringReader(inner), this.minecraft.player.connection.getSuggestionsProvider());
            CompletableFuture<Suggestions> future = dispatcher.getCompletionSuggestions(parse, cursor - offset)
                    .thenApply(suggestions -> aeronauticsdiscovery$shift(suggestions, offset));
            this.pendingSuggestions = future;
            future.thenRun(() -> {
                // Only the latest request may touch vanilla state: stale async completion must never overwrite newer suggestions
                if (this.pendingSuggestions != future) return;
                try {
                    this.updateUsageInfo();
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    @Unique
    private static Suggestions aeronauticsdiscovery$shift(Suggestions suggestions, int offset) {
        // On any anomaly produce NO suggestions rather than wrongly-ranged ones
        StringRange range = suggestions.getRange();
        if (range.getStart() + offset < 0 || range.getEnd() + offset < 0) {
            return new Suggestions(range, List.of());
        }
        List<Suggestion> shifted = new ArrayList<>(suggestions.getList().size());
        for (Suggestion suggestion : suggestions.getList()) {
            StringRange r = suggestion.getRange();
            if (r.getStart() + offset < 0 || r.getEnd() + offset < 0) {
                return new Suggestions(range, List.of());
            }
            shifted.add(new Suggestion(
                    StringRange.between(r.getStart() + offset, r.getEnd() + offset),
                    suggestion.getText(),
                    suggestion.getTooltip()));
        }
        return new Suggestions(StringRange.between(range.getStart() + offset, range.getEnd() + offset), shifted);
    }

    @Unique
    private static int aeronauticsdiscovery$tailStart(String line, String key) {
        if (!line.startsWith("/")) return -1;
        int i = aeronauticsdiscovery$skipWord(line, 1, "pinwand");
        if (i < 0) return -1;
        i = aeronauticsdiscovery$skipSpaces(line, i);
        if (i < 0) return -1;
        i = aeronauticsdiscovery$skipWord(line, i, "set");
        if (i < 0) return -1;
        i = aeronauticsdiscovery$skipSpaces(line, i);
        if (i < 0) return -1;
        i = aeronauticsdiscovery$skipWord(line, i, key);
        if (i < 0) return -1;
        if (i == line.length()) return i;
        if (!Character.isWhitespace(line.charAt(i))) return -1;
        return aeronauticsdiscovery$skipSpaces(line, i);
    }

    @Unique
    private static int aeronauticsdiscovery$skipWord(String line, int from, String word) {
        int end = from + word.length();
        if (end > line.length()) return -1;
        if (!line.regionMatches(from, word, 0, word.length())) return -1;
        if (end < line.length() && !Character.isWhitespace(line.charAt(end))) return -1;
        return end;
    }

    @Unique
    private static int aeronauticsdiscovery$skipSpaces(String line, int from) {
        int i = from;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        return i == from ? -1 : i;
    }
}
