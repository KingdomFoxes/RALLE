package org.kingdomfoxes.ralle.chat.rank;

/** Player-selected presentation for Wynncraft guild-chat rank pills. */
public enum GuildRankStyle {
    TITLES("titles"),
    STARS("stars"),
    STARS_AND_TITLES("stars-and-titles");

    private final String settingValue;

    GuildRankStyle(String settingValue) {
        this.settingValue = settingValue;
    }

    public String settingValue() {
        return settingValue;
    }

    public static GuildRankStyle fromSetting(String value) {
        for (var style : values()) {
            if (style.settingValue.equals(value)) return style;
        }
        return TITLES;
    }
}
