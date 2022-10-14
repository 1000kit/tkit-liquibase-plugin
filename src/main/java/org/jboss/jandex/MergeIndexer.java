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
        mergeIndexMaps(index.annotations, annotations);
        mergeIndexMaps(index.subclasses, subclasses);
        mergeIndexMaps(index.implementors, implementors);
        classes.putAll(index.classes);
    }

    private <T> void mergeIndexMaps(Map<DotName, List<T>> source, Map<DotName, List<T>> target) {
        source.entrySet().stream().forEach(entry -> {
            if (target.containsKey(entry.getKey())) {
                target.get(entry.getKey()).addAll(entry.getValue());
            } else {

                target.put(entry.getKey(), entry.getValue());
            }
        });
    }

    public Index complete() {
        return Index.create(annotations, subclasses, implementors, classes);
    }

    public void loadFromUrl(URL url) throws Exception {
        try (InputStream input = url.openStream()) {
            IndexReader reader = new IndexReader(input);
            Index index = reader.read();
            addIndex(index);
        }
    }
}
