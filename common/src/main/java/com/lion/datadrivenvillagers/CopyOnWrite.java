package com.lion.datadrivenvillagers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The three registries are written on one thread (mod init, then the server thread on reload) and
/// read from others: the render thread asks for textures and hats in single player, world generation
/// asks for structure templates on its workers. Nothing here is ever mutated in place; a write copies,
/// changes the copy and hands back an unmodifiable view, which the registry publishes through a
/// `volatile` field. A reader sees the old map or the new one, never a half-built one, and the
/// insertion order that `ordered()` reports survives.
public final class CopyOnWrite {

    private CopyOnWrite() {
    }

    public static <K, V> Map<K, V> with(Map<K, V> map, K key, V value) {
        Map<K, V> copy = new LinkedHashMap<>(map);
        copy.put(key, value);
        return Collections.unmodifiableMap(copy);
    }

    public static <K, V> Map<K, V> without(Map<K, V> map, K key) {
        if (!map.containsKey(key)) {
            return map;
        }
        Map<K, V> copy = new LinkedHashMap<>(map);
        copy.remove(key);
        return Collections.unmodifiableMap(copy);
    }

    public static <T> List<T> plus(List<T> list, T value) {
        List<T> copy = new ArrayList<>(list);
        copy.add(value);
        return Collections.unmodifiableList(copy);
    }
}
