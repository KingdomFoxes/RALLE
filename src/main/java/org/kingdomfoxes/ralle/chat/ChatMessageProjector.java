package org.kingdomfoxes.ralle.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ChatMessageProjector {
    private ChatMessageProjector() {}

    public static List<ProjectedMessage> project(
            List<GuiMessage> newestFirst,
            boolean compactChat,
            boolean stackEmptyLines,
            int compactWindowTicks
    ) {
        if (compactWindowTicks < 0) throw new IllegalArgumentException("Compact window cannot be negative");

        var chronologicalGroups = new ArrayList<MessageGroup>();
        for (int messageIndex = newestFirst.size() - 1; messageIndex >= 0; messageIndex--) {
            var message = newestFirst.get(messageIndex);
            var blank = message.content().getString().isBlank();

            if (blank && stackEmptyLines) {
                if (!chronologicalGroups.isEmpty() && chronologicalGroups.getLast().blank()) {
                    chronologicalGroups.set(
                            chronologicalGroups.size() - 1,
                            MessageGroup.single(message, true)
                    );
                } else {
                    chronologicalGroups.add(MessageGroup.single(message, true));
                }
                continue;
            }

            if (compactChat) {
                int duplicateIndex = findDuplicate(chronologicalGroups, message, compactWindowTicks);
                if (duplicateIndex >= 0) {
                    var duplicate = chronologicalGroups.remove(duplicateIndex);
                    chronologicalGroups.add(duplicate.repeatAt(message.addedTime()));
                    continue;
                }
            }

            chronologicalGroups.add(MessageGroup.single(message, blank));
        }

        var projected = new ArrayList<ProjectedMessage>(chronologicalGroups.size());
        for (var group : chronologicalGroups) {
            projected.add(group.project());
        }
        Collections.reverse(projected);
        return List.copyOf(projected);
    }

    private static int findDuplicate(List<MessageGroup> groups, GuiMessage message, int compactWindowTicks) {
        for (int index = groups.size() - 1; index >= 0; index--) {
            var group = groups.get(index);
            if (message.addedTime() - group.latestAddedTime() > compactWindowTicks) break;
            if (group.matches(message)) return index;
        }
        return -1;
    }

    public record ProjectedMessage(int addedTime, Component content, GuiMessageTag tag) {}

    private record MessageGroup(
            int latestAddedTime,
            Component content,
            GuiMessageTag tag,
            int repetitions,
            boolean blank
    ) {
        static MessageGroup single(GuiMessage message, boolean blank) {
            return new MessageGroup(message.addedTime(), message.content(), message.tag(), 1, blank);
        }

        boolean matches(GuiMessage message) {
            return content.equals(message.content()) && Objects.equals(tag, message.tag());
        }

        MessageGroup repeatAt(int addedTime) {
            return new MessageGroup(addedTime, content, tag, repetitions + 1, blank);
        }

        ProjectedMessage project() {
            if (repetitions == 1) return new ProjectedMessage(latestAddedTime, content, tag);

            var displayed = Component.empty()
                    .append(content)
                    .append(Component.literal(" (" + repetitions + ")").withStyle(ChatFormatting.GRAY));
            return new ProjectedMessage(latestAddedTime, displayed, tag);
        }
    }
}
