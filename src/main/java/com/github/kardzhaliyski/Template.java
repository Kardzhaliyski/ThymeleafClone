package com.github.kardzhaliyski;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Template {
    private static final Pattern TEXT_SPLIT_PATTERN = Pattern.compile("\\$\\{(\\w+)(?:\\.(\\w+))?}");
    private static final Pattern FOREACH_SPLIT_PATTERN = Pattern.compile("\\s*(\\w+):\\s*\\$\\{(\\w+)(?:\\.(\\w+))?}");
    private static final String TEXT_ATTRIBUTE_NAME = "t:text";
    private static final String FOREACH_ATTRIBUTE_NAME = "t:each";
    private static final String IF_ATTRIBUTE_NAME = "t:if";
    Document doc;

    public Template(String path) throws IOException {
        doc = Jsoup.parse(Path.of(path).toFile());
    }

    public void render(TemplateContext ctx, PrintWriter out) throws NoSuchFieldException, IllegalAccessException {
        Document clone = doc.clone();
        processIfStatements(clone, ctx);
        processForEach(clone, ctx);
        fillTextAttributes(clone, ctx, null, null);
        out.println(clone.toString());
        out.flush();
    }

    private void processIfStatements(Document base, TemplateContext ctx) throws NoSuchFieldException, IllegalAccessException {
        Elements elements = base.getElementsByAttribute(IF_ATTRIBUTE_NAME);
        for (Element element : elements) {
            String attrValue = element.attr(IF_ATTRIBUTE_NAME);
            if (!isConditionTrue(attrValue, ctx)) {
                element.remove();
                continue;
            }

            element.removeAttr(IF_ATTRIBUTE_NAME);
        }
    }

    private boolean isConditionTrue(String attrValue, TemplateContext ctx) throws NoSuchFieldException, IllegalAccessException {
        Object valueObject = null;
        try {
            valueObject = getPropertyValue(ctx, attrValue, null, null);
        } catch (IllegalArgumentException ignored ) {
        }

        if (valueObject == null) {
            valueObject = attrValue;
        }

        if (valueObject instanceof String value) {
            if (value.equalsIgnoreCase("false") ||
                    value.equalsIgnoreCase("off") ||
                    value.equalsIgnoreCase("no")) {
                return false;
            }
            return true;
        }

        if (valueObject instanceof Boolean value) {
            return value;
        }

        if (valueObject instanceof Number value) {
            if (value.doubleValue() <= 0) {
                return false;
            }
        }

        return true;
    }

    private void processForEach(Element base, TemplateContext ctx) throws NoSuchFieldException, IllegalAccessException {
        Elements elements = base.getElementsByAttribute(FOREACH_ATTRIBUTE_NAME);
        for (Element element : elements) {
            String attrValue = element.attr(FOREACH_ATTRIBUTE_NAME);
            Matcher matcher = FOREACH_SPLIT_PATTERN.matcher(attrValue);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid attribute value: " + attrValue);
            }

            Collection<Object> collection = extractCollection(ctx, matcher);
            element.removeAttr(FOREACH_ATTRIBUTE_NAME);
            String variableName = matcher.group(1);
            for (Object o : collection) {
                Element clone = element.clone();
                fillTextAttributes(clone, ctx, variableName, o);
                element.parent().appendChild(clone);
            }

            element.remove();
        }
    }

    private Collection<Object> extractCollection(TemplateContext ctx, Matcher matcher) throws NoSuchFieldException, IllegalAccessException {
        String key = matcher.group(2);
        Object valueObject = ctx.get(key);
        if (valueObject == null) {
            throw new IllegalStateException("No property with name: " + key);
        }

        int grpCount = matcher.groupCount();
        for (int i = 2; i < grpCount; i++) {
            String grp = matcher.group(i + 1);
            if (grp == null) {
                continue;
            }

            String fieldName = grp;
            valueObject = getFieldValue(valueObject, fieldName);
        }

        Collection<Object> collection = getCollection(valueObject);
        return collection;
    }

    private Collection<Object> getCollection(Object obj) {
        if (obj.getClass().isArray()) {
            return Arrays.asList((Object[]) obj);
        }

        if (Collection.class.isAssignableFrom(obj.getClass())) {
            return (Collection<Object>) obj;
        }

        throw new IllegalArgumentException("Object is not a collection or array: " + obj);
    }

    private void fillTextAttributes(Element e, TemplateContext ctx, String propKey, Object propValue) throws NoSuchFieldException, IllegalAccessException {
        Elements elements = e.getElementsByAttribute(TEXT_ATTRIBUTE_NAME);
        for (Element element : elements) {
            String attrValue = element.attr(TEXT_ATTRIBUTE_NAME);
            Object valueObject = getPropertyValue(ctx, attrValue, propKey, propValue);

            element.text(valueObject.toString());
            element.removeAttr(TEXT_ATTRIBUTE_NAME);
        }
    }

    private Object getPropertyValue(TemplateContext ctx, String attrValue, String propKey, Object propValue) throws NoSuchFieldException, IllegalAccessException {
        Matcher matcher = TEXT_SPLIT_PATTERN.matcher(attrValue);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid attribute value: " + attrValue);
        }

        String key = matcher.group(1);
        Object valueObject = key.equals(propKey) ? propValue : ctx.get(key);
        if (valueObject == null) {
            throw new IllegalStateException("No property with name: " + key);
        }

        for (int i = 2; i <= matcher.groupCount(); i++) {
            String fieldName = matcher.group(i);
            if(fieldName == null) {
                break;
            }

            valueObject = getFieldValue(valueObject, fieldName);
        }

        return valueObject;
    }

    private Object getFieldValue(Object instance, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Class<?> clazz = instance.getClass();
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(instance);
    }
}
