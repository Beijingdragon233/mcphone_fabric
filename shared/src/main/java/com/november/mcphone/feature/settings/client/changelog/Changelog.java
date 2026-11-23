package com.november.mcphone.feature.settings.client.changelog;

import com.google.common.collect.ImmutableList;
import com.november.mcphone.util.Java8Collections;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import com.google.gson.Gson;
import java.io.Reader;
import java.util.List;
import java.util.Map;

/** 构建生成的只读日志。顺序由构建确定，运行时不比较版本大小。 */
public final class Changelog {
    private Changelog() {}
    public static final class Section {
        private final String title;
        private final List<String> entries;

        public Section(String title, List<String> entries) {
            this.title = title;
            this.entries = entries;
        }

        public String title() { return title; }

        public List<String> entries() { return entries; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Section)) return false;
            Section that = (Section) other;
            return java.util.Objects.equals(this.title, that.title)
                    && java.util.Objects.equals(this.entries, that.entries);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(title);
            hash = 31 * hash + java.util.Objects.hashCode(entries);
            return hash;
        }

        @Override public String toString() { return "Section[title=" + title + ", entries=" + entries + "]"; }
}
    public static final class Release {
        private final String version;
        private final String date;
        private final Map<String, List<Section>> languages;

        public Release(String version, String date, Map<String, List<Section>> languages) {
            this.version = version;
            this.date = date;
            this.languages = languages;
        }

        public String version() { return version; }

        public String date() { return date; }

        public Map<String, List<Section>> languages() { return languages; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Release)) return false;
            Release that = (Release) other;
            return java.util.Objects.equals(this.version, that.version)
                    && java.util.Objects.equals(this.date, that.date)
                    && java.util.Objects.equals(this.languages, that.languages);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(version);
            hash = 31 * hash + java.util.Objects.hashCode(date);
            hash = 31 * hash + java.util.Objects.hashCode(languages);
            return hash;
        }

        @Override public String toString() { return "Release[version=" + version + ", date=" + date + ", languages=" + languages + "]"; }

        public List<Section> sections(String language) {
            return languages.getOrDefault("zh_cn".equals(language) ? "zh_cn" : "en_us", ImmutableList.of());
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
        ArrayList<Changelog.Release> releases = new java.util.ArrayList<Release>(values.length);
        for (ReleaseJson release : values) {
            if (release == null || release.version == null || release.date == null || release.languages == null)
                throw new IllegalArgumentException("无效的更新日志");
            LinkedHashMap<String,List<Changelog.Section>> translations = new java.util.LinkedHashMap<String, List<Section>>();
            for (String language : ImmutableList.of("zh_cn", "en_us")) {
                List<SectionJson> sections = release.languages.get(language);
                if (sections == null || sections.isEmpty()) throw new IllegalArgumentException("缺少日志翻译");
                for (SectionJson section : sections) {
                    if (section == null || section.title == null || section.entries == null
                            || section.entries.isEmpty() || section.entries.contains(null))
                        throw new IllegalArgumentException("无效的日志分类");
                }
            }
            for (Entry<String,List<Changelog.SectionJson>> entry : release.languages.entrySet()) {
                List<Changelog.SectionJson> sections = entry.getValue();
                translations.put(entry.getKey(), sections == null ? null : sections.stream()
                        .map(section -> section == null ? null : new Section(section.title, section.entries)).collect(Java8Collections.toList()));
            }
            releases.add(new Release(release.version, release.date, translations));
        }
        return ImmutableList.copyOf(releases);
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
