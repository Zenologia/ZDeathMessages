package com.zenologia.zdeathmessages.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record TemplateConfig(
        List<String> messages,
        List<String> staffMessages,
        GeneralConfig.MessageFormat formatOverride
) {

    public static TemplateConfig empty() {
        return new TemplateConfig(Collections.emptyList(), Collections.emptyList(), null);
    }

    public boolean isEmpty() {
        return messages.isEmpty() && staffMessages.isEmpty();
    }

    public List<String> publicLines() {
        return messages;
    }

    public List<String> staffLinesOrPublic() {
        return staffMessages.isEmpty() ? messages : staffMessages;
    }

    public static TemplateConfig from(ConfigurationSection sec) {
        if (sec == null) return empty();

        List<String> msgs = readStringOrList(sec, "messages");
        List<String> staff = readStringOrList(sec, "staff-messages");

        String fmtStr = sec.getString("format", null);
        GeneralConfig.MessageFormat fmt = fmtStr == null ? null : GeneralConfig.MessageFormat.fromString(fmtStr);

        return new TemplateConfig(msgs, staff, fmt);
    }

    private static List<String> readStringOrList(ConfigurationSection sec, String key) {
        if (sec.isList(key)) {
            List<String> raw = sec.getStringList(key);
            return raw == null ? Collections.emptyList() : new ArrayList<>(raw);
        }
        String single = sec.getString(key, null);
        if (single == null) return Collections.emptyList();
        return new ArrayList<>(List.of(single));
    }
}
