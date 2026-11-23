package com.november.mcphone.feature.reader.client.source;

import com.november.mcphone.util.Java8;

import com.google.common.collect.ImmutableList;

import java.util.Map.Entry;
import com.november.mcphone.MCphone;
import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.feature.reader.BookRef;
import com.november.mcphone.platform.ModPresence;
import com.november.mcphone.platform.ResourceIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Discovers books from Modonomicon's synchronized catalog and uses its own reader. */
final class ModonomiconSource implements BookSource {
    private static final String MOD_ID = "modonomicon";
    private static final String PACKAGE = "com.klikli_dev.modonomicon.";
    private static final ResourceLocation SCRIPTURA_VITAE = ResourceIds.create("neovitae", "guide");

    private List<BookRef> books = ImmutableList.of();
    private final Map<ResourceLocation, ItemStack> icons = new HashMap<>();
    private boolean resolved;
    private Api api;
    private int loggedCount = -1;
    private Screen returnScreen;
    private LocalPlayer readingPlayer;

    @Override
    public String id() {
        return MOD_ID;
    }

    @Override
    public boolean isAvailable() {
        return ModPresence.isLoaded(MOD_ID);
    }

    @Override
    public List<BookRef> list() {
        return books;
    }

    @Override
    public List<String> searchAliases(BookRef book) {
        return SCRIPTURA_VITAE.equals(book.bookId()) ? ImmutableList.of("命血宝典", "命血手札", "Scriptura Vitae") : ImmutableList.of();
    }

    @Override
    public void refresh() {
        books = ImmutableList.of();
        icons.clear();
        Api access = resolve();
        if (access == null) return;

        ArrayList<com.november.mcphone.feature.reader.BookRef> entries = new ArrayList<BookRef>();
        try {
            for (Entry<?,?> entry : access.books().entrySet()) {
                Object idMatch;
                if (!((idMatch = entry.getKey()) instanceof ResourceLocation) || entry.getValue() == null) continue;
                try {
                    String name = (String) access.name().invoke(entry.getValue());
                    Component title = name == null || Java8.isBlank(name)
                            ? Component.literal(((ResourceLocation) idMatch).getPath()) : Component.translatable(name);
                    entries.add(new BookRef(MOD_ID, ((ResourceLocation) idMatch), title, null, BookSource.modName(((ResourceLocation) idMatch).getNamespace())));
                    identifyIcon(access, ((ResourceLocation) idMatch), entry.getValue());
                } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                    MCphone.LOGGER.warn("[MCphone] 读取 Modonomicon 手册 {} 失败", ((ResourceLocation) idMatch), failure);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            MCphone.LOGGER.warn("[MCphone] 读取 Modonomicon 书目失败", failure);
        }

        entries.sort(Comparator.comparing(BookRef::owner)
                .thenComparing(book -> book.title().getString())
                .thenComparing(book -> book.bookId().toString()));
        books = ImmutableList.copyOf(entries);
        if (books.size() != loggedCount) {
            loggedCount = books.size();
            MCphone.LOGGER.info("[MCphone] 书城扫到 {} 本 Modonomicon 手册", loggedCount);
        }
    }

    @Override
    public void open(BookRef book) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        Api access = resolve();
        if (access == null) return;

        Screen previous = client.screen;
        try {
            if (!access.books().containsKey(book.bookId())) {
                MCphone.LOGGER.warn("[MCphone] Modonomicon 书目中已没有手册 {}", book.bookId());
                return;
            }
            Object address = access.address() == null
                    ? book.bookId() : access.address().invoke(null, book.bookId());
            access.open().invoke(access.gui().invoke(null), address);
            if (previous != null && client.screen != previous && isBookScreen(client.screen)) {
                returnScreen = previous;
                readingPlayer = client.player;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            MCphone.LOGGER.warn("[MCphone] 打开 Modonomicon 手册 {} 失败", book.bookId(), failure);
        }
    }

    @Override
    public boolean renderIcon(GuiGraphics graphics, BookRef book, int x, int y, int size) {
        ItemStack icon = icons.get(book.bookId());
        return icon != null && GuiUtil.drawItemIcon(graphics, icon, x, y, size);
    }

    void tickReturn() {
        if (returnScreen == null) return;
        Minecraft client = Minecraft.getInstance();
        boolean samePlayer = client.player == readingPlayer && readingPlayer.isAlive();
        if (samePlayer && isBookScreen(client.screen)) return;

        Screen previous = returnScreen;
        returnScreen = null;
        readingPlayer = null;
        if (samePlayer && client.screen == null) client.setScreen(previous);
    }

    private static boolean isBookScreen(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith(PACKAGE + "client.gui.");
    }

    private void identifyIcon(Api access, ResourceLocation id, Object book) {
        try {
            if (access.icon() == null) return;
            Object result = access.icon().invoke(book);
            if (result instanceof ItemStack && !((ItemStack) result).isEmpty()) {
                icons.put(id, ((ItemStack) result));
            } else if (result instanceof ResourceLocation) {
                net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(((ResourceLocation) result));
                if (item != Items.AIR) icons.put(id, new ItemStack(item));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // A missing cover uses the bookshelf's default icon.
        }
    }

    private Api resolve() {
        if (resolved) return api;
        resolved = true;
        try {
            Class<?> data = Class.forName(PACKAGE + "data.BookDataManager");
            Class<?> book = Class.forName(PACKAGE + "book.Book");
            Class<?> gui = Class.forName(PACKAGE + "client.gui.BookGuiManager");
            Method getData = data.getMethod("get");
            Method getBooks = data.getMethod("getBooks");
            Method getName = book.getMethod("getName");
            Method getGui = gui.getMethod("get");

            Method address;
            Method open;
            try {
                Class<?> bookAddress = Class.forName(PACKAGE + "client.gui.book.BookAddress");
                address = bookAddress.getMethod("defaultFor", ResourceLocation.class);
                open = gui.getMethod("openBook", bookAddress);
            } catch (ClassNotFoundException | NoSuchMethodException legacyApi) {
                address = null;
                open = gui.getMethod("openBook", ResourceLocation.class);
            }
            Method icon = findIconMethod(book);
            api = new Api(getData, getBooks, getName, getGui, open, address, icon);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            MCphone.LOGGER.warn("[MCphone] Modonomicon 书目或开书接口无法解析", failure);
        }
        return api;
    }

    private static Method findIconMethod(Class<?> book) {
        for (String name : ImmutableList.of("getBookItem", "getCustomBookItem")) {
            try {
                return book.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // Try the other supported Modonomicon API.
            }
        }
        return null;
    }

    private static final class Api {
        private final Method data;
        private final Method catalog;
        private final Method name;
        private final Method gui;
        private final Method open;
        private final Method address;
        private final Method icon;

        private Api(Method data, Method catalog, Method name, Method gui, Method open, Method address, Method icon) {
            this.data = data;
            this.catalog = catalog;
            this.name = name;
            this.gui = gui;
            this.open = open;
            this.address = address;
            this.icon = icon;
        }

        public Method data() { return data; }

        public Method catalog() { return catalog; }

        public Method name() { return name; }

        public Method gui() { return gui; }

        public Method open() { return open; }

        public Method address() { return address; }

        public Method icon() { return icon; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Api)) return false;
            Api that = (Api) other;
            return java.util.Objects.equals(this.data, that.data)
                    && java.util.Objects.equals(this.catalog, that.catalog)
                    && java.util.Objects.equals(this.name, that.name)
                    && java.util.Objects.equals(this.gui, that.gui)
                    && java.util.Objects.equals(this.open, that.open)
                    && java.util.Objects.equals(this.address, that.address)
                    && java.util.Objects.equals(this.icon, that.icon);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(data);
            hash = 31 * hash + java.util.Objects.hashCode(catalog);
            hash = 31 * hash + java.util.Objects.hashCode(name);
            hash = 31 * hash + java.util.Objects.hashCode(gui);
            hash = 31 * hash + java.util.Objects.hashCode(open);
            hash = 31 * hash + java.util.Objects.hashCode(address);
            hash = 31 * hash + java.util.Objects.hashCode(icon);
            return hash;
        }

        @Override public String toString() { return "Api[data=" + data + ", catalog=" + catalog + ", name=" + name + ", gui=" + gui + ", open=" + open + ", address=" + address + ", icon=" + icon + "]"; }

        Map<?, ?> books() throws ReflectiveOperationException {
            return (Map<?, ?>) catalog.invoke(data.invoke(null));
        }
    }
}
