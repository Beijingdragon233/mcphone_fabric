package com.november.mcphone.feature.settings.client.changelog;

import com.google.gson.Gson;
import java.io.Reader;
import java.util.List;
import java.util.Map;

/** 构建生成的只读日志。顺序由构建确定，运行时不比较版本大小。 */
public final class Changelog {
    private Changelog() {}
    public record Section(String title, List<String> entries) {}
    public record Release(String version, String date, Map<String, List<Section>> languages) {
        public List<Section> sections(String language) {
            return languages.getOrDefault("zh_cn".equals(language) ? "zh_cn" : "en_us", List.of());
        }
    }
    // Gson bundled with Minecraft 1.18.2 cannot deserialize Java records.
    private static final class SectionJson {
        String title;
        List<String> entries;
    }
    private static final class ReleaseJson {
        String version;
        String date;
        Map<String, List<SectionJson>> languages;
    }

    public static List<Release> read(Reader reader) {
        ReleaseJson[] values = new Gson().fromJson(reader, ReleaseJson[].class);
        if (values == null) throw new IllegalArgumentException("缺少更新日志列表");
        var releases = new java.util.ArrayList<Release>(values.length);
        for (ReleaseJson release : values) {
            if (release == null || release.version == null || release.date == null || release.languages == null)
                throw new IllegalArgumentException("无效的更新日志");
            var translations = new java.util.LinkedHashMap<String, List<Section>>();
            for (String language : List.of("zh_cn", "en_us")) {
                List<SectionJson> sections = release.languages.get(language);
                if (sections == null || sections.isEmpty()) throw new IllegalArgumentException("缺少日志翻译");
                for (SectionJson section : sections) {
                    if (section == null || section.title == null || section.entries == null
                            || section.entries.isEmpty() || section.entries.contains(null))
                        throw new IllegalArgumentException("无效的日志分类");
                }
            }
            for (var entry : release.languages.entrySet()) {
                var sections = entry.getValue();
                translations.put(entry.getKey(), sections == null ? null : sections.stream()
                        .map(section -> section == null ? null : new Section(section.title, section.entries)).toList());
            }
            releases.add(new Release(release.version, release.date, translations));
        }
        return List.copyOf(releases);
    }
    public static Release current(List<Release> releases, String version) {
        // 预发布与正式版共用同一基准号的正文；预发布不自动提醒。
        String base = version.split("-", 2)[0];
        return releases.stream().filter(r -> r.version().equals(base)).findFirst().orElse(null);
    }
    public static boolean shouldNotify(String version, String shown, boolean hasNotes) {
        return hasNotes && version.matches("\\d+\\.\\d+\\.\\d+") && !version.equals(shown);
    }
}
