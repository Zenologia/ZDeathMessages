package com.zenologia.zdeathmessages.message;

import com.zenologia.zdeathmessages.config.TemplateConfig;

public record ResolvedTemplate(
        TemplateConfig template,
        TemplateSource source,
        boolean disabledWorld
) {
    public ResolvedTemplate(TemplateConfig template, TemplateSource source) {
        this(template, source, false);
    }

    public static ResolvedTemplate forWorldDisabled() {
        return new ResolvedTemplate(TemplateConfig.empty(), TemplateSource.WORLD_DISABLED, true);
    }
}
