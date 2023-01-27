package com.github.kardzhaliyski;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateV2 {

    private static final Pattern TEXT_SPLIT_PATTERN = Pattern.compile("\\$\\{(\\w+)(?:\\.(\\w+))?}");
    private static final Pattern FOREACH_SPLIT_PATTERN = Pattern.compile("\\s*(\\w+):\\s*\\$\\{(\\w+)(?:\\.(\\w+))?}");
    private static final String TEXT_ATTRIBUTE_NAME = "t:text";
    private static final String FOREACH_ATTRIBUTE_NAME = "t:each";
    private static final String IF_ATTRIBUTE_NAME = "t:if";
    Document doc;

    public TemplateV2(String path) throws IOException {
        doc = Jsoup.parse(Path.of(path).toFile());
    }

    public void render(TemplateContext ctx, PrintWriter out) throws NoSuchFieldException, IllegalAccessException {
        Node root = doc;
        processElement(root, ctx, out);
        out.flush();
    }

    private void processElement(Node node, TemplateContext ctx, PrintWriter out) throws NoSuchFieldException, IllegalAccessException {
        String ifAttr = node.attr(IF_ATTRIBUTE_NAME);
        if (!ifAttr.isEmpty() && !isConditionTrue(ifAttr, ctx))
            return;

        String forEachAttr = node.attr(FOREACH_ATTRIBUTE_NAME);
        if (!forEachAttr.isEmpty()) {
            processForEachAttribute(node, ctx, out, forEachAttr);
        } else {
            renderNode(node, ctx, out);
        }
    }

    private void printOpeningTag(Node node, PrintWriter out) {
        out.print("<");
        out.print(node.nodeName());

        for (Attribute attribute : node.attributes()) {
            String name = attribute.getKey();
            if (name.equals(IF_ATTRIBUTE_NAME) ||
                    name.equals(FOREACH_ATTRIBUTE_NAME) ||
                    name.equals(TEXT_ATTRIBUTE_NAME)) {
                continue;
            }

            String value = attribute.getValue();
            out.printf(" %s=%s", name, value);
        }

        out.println(">");
    }

    private void printClosingTag(Node node, PrintWriter out) {
        out.printf("</%s>%n", node.nodeName());
    }

    private void processForEachAttribute(Node node, TemplateContext ctx, PrintWriter out, String forEachAttr) throws NoSuchFieldException, IllegalAccessException {
        Matcher matcher = FOREACH_SPLIT_PATTERN.matcher(forEachAttr);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid attribute value: " + forEachAttr);
        }

        Collection<Object> collection = extractCollection(ctx, matcher);
        String variableName = matcher.group(1);
        Object oldValue = ctx.get(variableName);
        for (Object o : collection) {
            ctx.put(variableName, o);
            renderNode(node, ctx, out);
        }

        if (oldValue == null) {
            ctx.remove(variableName);
        } else {
            ctx.put(variableName, oldValue);
        }
    }

    private void renderNode(Node node, TemplateContext ctx, PrintWriter out) throws NoSuchFieldException, IllegalAccessException {
        printOpeningTag(node, out);
        String textAttr = node.attr(TEXT_ATTRIBUTE_NAME);
        if (!textAttr.isEmpty()) {
            String textValue = getPropertyValue(ctx, textAttr).toString();
            out.println(textValue);
            printClosingTag(node, out);
            return;
        }

        for (Node childNode : node.childNodes()) {
            if (childNode instanceof Element) {
                processElement(childNode, ctx, out);
                continue;
            }

            String str = childNode.toString();
            if (str.isBlank()) {
                continue;
            }

            out.println(str.trim());
        }

        printClosingTag(node, out);
    }

    private boolean isConditionTrue(String attrValue, TemplateContext ctx) throws NoSuchFieldException, IllegalAccessException {
        if (attrValue.equalsIgnoreCase("false")) {
            return false;
        } else if (attrValue.equalsIgnoreCase("true")) {
            return true;
        }

        Object valueObject = attrValue;
        try {
            valueObject = getPropertyValue(ctx, attrValue);
        } catch (IllegalArgumentException ignored) {
        }

        if (valueObject == null) {
            return false;
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

    private Object getPropertyValue(TemplateContext ctx, String attrValue) throws NoSuchFieldException, IllegalAccessException {
        Matcher matcher = TEXT_SPLIT_PATTERN.matcher(attrValue);
        if (!matcher.matches()) {
            return attrValue;
        }

        String key = matcher.group(1);
        Object valueObject = ctx.get(key);
        if (valueObject == null) {
            throw new IllegalStateException("No property with name in context: " + key);
        }

        for (int i = 2; i <= matcher.groupCount(); i++) {
            String fieldName = matcher.group(i);
            if (fieldName == null) {
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

        return getCollection(valueObject);
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
}
