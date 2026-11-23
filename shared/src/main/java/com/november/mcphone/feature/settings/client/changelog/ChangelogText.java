package com.november.mcphone.feature.settings.client.changelog;

import com.google.common.collect.ImmutableList;

import java.util.regex.Matcher;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 支持发布说明中的 Markdown 链接；链接另起可点击行，长标题照常换行。 */
public final class ChangelogText {
    private ChangelogText() {}
    private static final Pattern LINK = Pattern.compile("\\[([^]\\r\\n]+)]\\((https?://[^\\s)]+)\\)");
    public static final class Link {
        private final String label;
        private final String url;

        public Link(String label, String url) {
            this.label = label;
            this.url = url;
        }

        public String label() { return label; }

        public String url() { return url; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Link)) return false;
            Link that = (Link) other;
            return java.util.Objects.equals(this.label, that.label)
                    && java.util.Objects.equals(this.url, that.url);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(label);
            hash = 31 * hash + java.util.Objects.hashCode(url);
            return hash;
        }

        @Override public String toString() { return "Link[label=" + label + ", url=" + url + "]"; }
}
    public static final class Entry {
        private final String text;
        private final List<Link> links;

        public Entry(String text, List<Link> links) {
            this.text = text;
            this.links = links;
        }

        public String text() { return text; }

        public List<Link> links() { return links; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Entry)) return false;
            Entry that = (Entry) other;
            return java.util.Objects.equals(this.text, that.text)
                    && java.util.Objects.equals(this.links, that.links);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(text);
            hash = 31 * hash + java.util.Objects.hashCode(links);
            return hash;
        }

        @Override public String toString() { return "Entry[text=" + text + ", links=" + links + "]"; }
}
    public static Entry parse(String source) {
        Matcher matcher = LINK.matcher(source);
        ArrayList<ChangelogText.Link> links = new ArrayList<Link>();
        StringBuffer text = new StringBuffer();
        while (matcher.find()) {
            String url = matcher.group(2);
            try {
                URI uri = URI.create(url);
                if (uri.getHost() != null && uri.getUserInfo() == null) links.add(new Link(matcher.group(1), url));
            } catch (IllegalArgumentException ignored) { }
            matcher.appendReplacement(text, java.util.regex.Matcher.quoteReplacement(matcher.group(1)));
        }
        matcher.appendTail(text);
        return new Entry(text.toString(), ImmutableList.copyOf(links));
    }
}
