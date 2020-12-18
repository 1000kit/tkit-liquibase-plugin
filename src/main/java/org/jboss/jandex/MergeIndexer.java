package org.jboss.jandex;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MergeIndexer {

    public static String INDEX = "META-INF/jandex.idx";

    Map<DotName, List<AnnotationInstance>> annotations = new HashMap<>();
    Map<DotName, List<ClassInfo>> subclasses = new HashMap<>();
    Map<DotName, List<ClassInfo>> implementors = new HashMap<>();
    Map<DotName, ClassInfo> classes = new HashMap<>();

    public void addIndex(Index index) {
        annotations.putAll(index.annotations);
        subclasses.putAll(index.subclasses);
        implementors.putAll(index.implementors);
        classes.putAll(index.classes);
    }
    public Index complete() {
        return new Index(annotations, subclasses, implementors, classes);
    }

    public void loadFromUrl(URL url) throws Exception {
        try (InputStream input = url.openStream()) {
            IndexReader reader = new IndexReader(input);
            Index index = reader.read();
            addIndex(index);
        }
    }
}
