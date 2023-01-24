package com.github.kardzhaliyski;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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
        Node root = doc.body();
        processElement(root, ctx, out);
        out.flush();
    }

    private void processElement(Node node, TemplateContext ctx, PrintWriter out) throws NoSuchFieldException, IllegalAccessException {
        String ifAttr = node.attr(IF_ATTRIBUTE_NAME);
        if (!ifAttr.isEmpty()) {
            if (processIfAttribute(node, ctx, ifAttr)) return;
        }

        String textAttr = node.attr(TEXT_ATTRIBUTE_NAME);
        if (!textAttr.isEmpty()) {
            processTextAttribute(node, ctx, textAttr);
        }

        String forEachAttr = node.attr(FOREACH_ATTRIBUTE_NAME);
        node.removeAttr(FOREACH_ATTRIBUTE_NAME);
        String closingTagStr = printTagLine(node, out);

        if (!forEachAttr.isEmpty()) {
            processForEachAttribute(node, ctx, out, forEachAttr);
        } else {
            processChildren(node, ctx, out);
        }

        addRemovedAttributes(node, ifAttr, textAttr, forEachAttr);
        out.println(closingTagStr);
    }

    private boolean processIfAttribute(Node node, TemplateContext ctx, String ifAttr) throws NoSuchFieldException, IllegalAccessException {
        node.removeAttr(IF_ATTRIBUTE_NAME);
        if (!isConditionTrue(ifAttr, ctx)) {
            node.remove();
            return true;
        }
        return false;
    }

    private void processTextAttribute(Node node, TemplateContext ctx, String textAttr) throws NoSuchFieldException, IllegalAccessException {
        node.removeAttr(TEXT_ATTRIBUTE_NAME);
        Object valueObject = getPropertyValue(ctx, textAttr);
        ((Element) node).text(valueObject.toString());
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
            processChildren(node, ctx, out);
        }

        if (oldValue == null) {
            ctx.remove(variableName);
        } else {
            ctx.put(variableName, oldValue);
        }
    }

    private static void addRemovedAttributes(Node node, String ifAttr, String textAttr, String forEachAttr) {
        if (ifAttr != null && !ifAttr.isEmpty()) {
            node.attr(IF_ATTRIBUTE_NAME, ifAttr);
        }

        if (forEachAttr != null && !forEachAttr.isEmpty()) {
            node.attr(FOREACH_ATTRIBUTE_NAME, forEachAttr);
        }

        if (textAttr != null && !textAttr.isEmpty()) {
            node.attr(TEXT_ATTRIBUTE_NAME, textAttr);
        }
    }

    private static String printTagLine(Node node, PrintWriter out) {
        List<Node> childNodes = node.childNodes();
        for (Node cn : childNodes) {
            cn.remove();
        }

        String elemStr = node.toString();
        int i = elemStr.indexOf("</");
        String openingTagStr = elemStr.substring(0, i);
        out.println(openingTagStr);
        String closingTagStr = elemStr.substring(i);

        ((Element) node).appendChildren(childNodes);
        return closingTagStr;
    }

    private void processChildren(Node node, TemplateContext ctx, PrintWriter out) throws NoSuchFieldException, IllegalAccessException {
        for (Node childNode : node.childNodes()) {
            if (childNode instanceof Element) {
                processElement(childNode, ctx, out);
                continue;
            }

            String str = childNode.toString();
            if (!str.isBlank()) {
                out.println(str.trim());
            }
        }
    }

    private boolean isConditionTrue(String attrValue, TemplateContext ctx) throws NoSuchFieldException, IllegalAccessException {
        Object valueObject = null;
        try {
            valueObject = getPropertyValue(ctx, attrValue);
        } catch (IllegalArgumentException ignored) {
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

    private Object getPropertyValue(TemplateContext ctx, String attrValue) throws NoSuchFieldException, IllegalAccessException {
        Matcher matcher = TEXT_SPLIT_PATTERN.matcher(attrValue);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid attribute value: " + attrValue);
        }

        String key = matcher.group(1);
        Object valueObject = ctx.get(key);
        if (valueObject == null) {
            throw new IllegalStateException("No property with name: " + key);
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
}
