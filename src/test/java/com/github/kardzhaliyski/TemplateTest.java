package com.github.kardzhaliyski;

import com.github.kardzhaliyski.classes.Student;
import com.github.kardzhaliyski.classes.WelcomeMessage;
import org.jsoup.Jsoup;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

class TemplateTest {

    @BeforeEach
    void setUp() {
    }

    @Test
    void test() throws IOException, NoSuchFieldException, IllegalAccessException {
        TemplateContext ctx = new TemplateContext();
        WelcomeMessage welcome = new WelcomeMessage("hello world");
        ctx.put("welcome", welcome);

        Student students[] = {
                new Student(1, "Ivan"),
                new Student(2, "Maria"),
                new Student(3, "Nikola")
        };
        ctx.put("students", students);
        ctx.put("negativeNumber", -13);
        ctx.put("positiveNumber", 11.5);

        Template t = new Template("src/test/resources/template.tm");
        PrintWriter out = new PrintWriter(System.out);
        t.render(ctx, out);
    }

    @Test
    void testJsoup() throws IOException {
        String splitRegex = "#{(\\w+)(?:\\.(\\w+))?}";
        Document doc = Jsoup.parse(Path.of("src/test/resources/template.tm").toFile());
        Element element = doc.getElementsByAttribute("t:text").get(0);
        element.appendText("Malko text");
        System.out.println(element);
//        System.out.println(doc);
//        System.out.println(element.attr("t:text"));
    }

    @Test
    void test2() {
        Object[] str = new Integer[]{2, 1};
        List<Object> ints = Arrays.asList(str);
        for (Object n : ints) {
            System.out.println(n);
        }
        System.out.println(Collection.class.isAssignableFrom(str.getClass()));
        System.out.println(Collection.class.isAssignableFrom(ints.getClass()));
    }

    @Test
    void test3() throws IOException {
        Document doc = Jsoup.parse(Path.of("src/test/resources/template.tm").toFile());
        Element element = doc.getElementsByAttribute("t:each").get(0);
        Elements children = element.children();
        for (Element child : children) {
            System.out.println(child);
        }
    }

    @Test
    void test4() throws IOException, NoSuchFieldException, IllegalAccessException {
        TemplateContext ctx = new TemplateContext();
        WelcomeMessage welcome = new WelcomeMessage("hello world");
        ctx.put("welcome", welcome);

        Student students[] = {
                new Student(1, "Ivan"),
                new Student(2, "Maria"),
                new Student(3, "Nikola")
        };
        ctx.put("students", students);
        ctx.put("negativeNumber", -13);
        ctx.put("positiveNumber", 11.5);

        TemplateV2 t = new TemplateV2("src/test/resources/template.tm");
//        PrintWriter printWriter = new PrintWriter();
        PrintWriter out = new PrintWriter(System.out);
        t.render(ctx, out);
    }

    @Test
    void test5() throws IOException {
        PrintWriter out = new PrintWriter(System.out);
        out.println("Hello");
        out.println("World");
        out.flush();
//        Document parse = Jsoup.parse(Path.of("src/test/resources/template.tm").toFile());
//        Node body = parse.body();
//        for (Node childNode : body.childNodes()) {
//            if (childNode instanceof Element) {
//
//            } else {
//                String string = childNode.toString();
//                if (!string.isBlank()) {
//                    System.out.println("---- node ----");
//                    System.out.println(string.trim());
//                    System.out.println("-----------");
//                }
//
//            }

        }

    }
//}