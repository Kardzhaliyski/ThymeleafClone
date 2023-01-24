package com.github.kardzhaliyski;

import java.util.HashMap;
import java.util.Map;

public class TemplateContext {
    Map<String, Object> map = new HashMap<>();

    public Object put(String key, Object value) {
        return map.put(key, value);
    }

    public Object get(String key) {
        return map.get(key);
    }

    public void remove(String variableName) {
        map.remove(variableName);
    }
}
