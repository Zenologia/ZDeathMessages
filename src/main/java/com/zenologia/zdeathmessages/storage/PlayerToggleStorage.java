package com.zenologia.zdeathmessages.storage;

import java.io.Closeable;
import java.util.Optional;
import java.util.UUID;

public interface PlayerToggleStorage extends Closeable {
    Optional<Boolean> loadToggle(UUID playerId) throws Exception;
    void saveToggle(UUID playerId, boolean showCustom) throws Exception;

    @Override
    void close();
}
