package com.november.mcphone.api.client.store;

import com.november.mcphone.api.client.app.IPhoneApp;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * App 元数据：只有可序列化字段，不引用实现类，所以远程来源能在没有实例时先列出 App。
 * 用 builder 而非 record，将来加字段不会打断已有附属。
 */
public final class AppInfo {

    private final ResourceLocation id;
    private final Component displayName;
    private final ResourceLocation iconTexture;
    private final String version;
    private final String author;
    private final String description;
    private final ResourceLocation sourceId;
    private final Component blockedReason;

    private AppInfo(Builder b) {
        this.id = b.id;
        this.displayName = b.displayName;
        this.iconTexture = b.iconTexture;
        this.version = b.version;
        this.author = b.author;
        this.description = b.description;
        this.sourceId = b.sourceId;
        this.blockedReason = b.blockedReason;
    }

    /** 与 {@link IPhoneApp#getId()} 对应 */
    public ResourceLocation id() { return id; }

    public Component displayName() { return displayName; }

    /** 可能为 null：远程来源列出的 App 本地还没有贴图 */
    public ResourceLocation iconTexture() { return iconTexture; }

    /** 没写时是空串，不是 null */
    public String version() { return version; }

    /** 没写时是空串，不是 null */
    public String author() { return author; }

    /** 商店详情页的简介，没写时是空串，不是 null */
    public String description() { return description; }

    /** 见 {@link IAppSource#getId()} */
    public ResourceLocation sourceId() { return sourceId; }

    /**
     * 这个 App 为什么装不了，能装就是 null。商店把按钮画灰并显示这句话。
     *
     * <p>判断在<b>来源</b>那边做，不在商店：来源才知道自己列的是什么。清单里声明了本机没有的
     * SDK 版本（§23.4）走的就是这条，将来"需要 xxx 货币"（§22.8）同理。
     */
    public Component blockedReason() { return blockedReason; }

    /** 三个必填项从这里给，其余可选 */
    public static Builder builder(ResourceLocation id, Component displayName,
                                  ResourceLocation sourceId) {
        return new Builder(id, displayName, sourceId);
    }

    /**
     * 从已有实例生成元数据，本地来源用。
     * 故意不兜第三方 App 抛的异常：调用方已在 SPI 兜底范围内，这里再兜会吞掉"哪个 App 有问题"。
     */
    public static AppInfo of(IPhoneApp app, ResourceLocation sourceId) {
        return of(app, sourceId, null);
    }

    /** 同上，另外带一条"为什么装不了"。{@code blockedReason} 为 null 就是能装。 */
    public static AppInfo of(IPhoneApp app, ResourceLocation sourceId, Component blockedReason) {
        return builder(app.getId(), app.getDisplayName(), sourceId)
                .icon(app.getIconTexture())
                .version(app.getVersion())
                .author(app.getAuthor())
                .description(app.getDescription())
                .blocked(blockedReason)
                .build();
    }

    public static final class Builder {

        private final ResourceLocation id;
        private final Component displayName;
        private final ResourceLocation sourceId;

        private ResourceLocation iconTexture = null;
        private String version = "";
        private String author = "";
        private String description = "";
        private Component blockedReason = null;

        private Builder(ResourceLocation id, Component displayName, ResourceLocation sourceId) {
            this.id = id;
            this.displayName = displayName;
            this.sourceId = sourceId;
        }

        /** 不给的话商店画占位方块 */
        public Builder icon(ResourceLocation iconTexture) {
            this.iconTexture = iconTexture;
            return this;
        }

        public Builder version(String version) {
            this.version = version == null ? "" : version;
            return this;
        }

        public Builder author(String author) {
            this.author = author == null ? "" : author;
            return this;
        }

        public Builder description(String description) {
            this.description = description == null ? "" : description;
            return this;
        }

        /** 给了就是"装不了"，商店把按钮画灰并显示它。 */
        public Builder blocked(Component reason) {
            this.blockedReason = reason;
            return this;
        }

        public AppInfo build() {
            return new AppInfo(this);
        }
    }
}
