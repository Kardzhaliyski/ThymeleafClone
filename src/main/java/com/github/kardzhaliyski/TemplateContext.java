package com.github.kardzhaliyski;

import java.util.HashMap;
import java.util.Map;

public class TemplateContext {
    Map<String, Object> map = new HashMap<>();

    public void put(String key, Object value) {
        map.put(key, value);
    }

    public Object get(String key) {
        return map.get(key);
    }
}
