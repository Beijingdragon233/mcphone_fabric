package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.feature.chat.client.favorite.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 收藏列表与正文查看器；只消费独立快照，不打开会话或访问服务器。 */
final class ChatFavoritePane {
    private record Hit(ChatLayout.Rect bounds,FavoriteText entry) {}
    private final Supplier<FavoriteStore> source;
    private final Consumer<String> copy;
    private List<Hit> hits=List.of();
    private FavoriteText selected;
    private ChatLayout.Rect area,copyButton,removeButton;
    private int offset,maximum,textOffset,textMaximum;
    private String status="";
    ChatFavoritePane(Supplier<FavoriteStore> source,Consumer<String> copy) { this.source=source;this.copy=copy; }
    void close() { hits=List.of();selected=null;area=copyButton=removeButton=null;offset=maximum=textOffset=textMaximum=0;status=""; }
    boolean back() { if (selected==null) return false;selected=null;copyButton=removeButton=null;hits=List.of();status="";return true; }
    void render(GuiGraphics g,Font font,ChatLayout.Rect bounds,int mx,int my) {
        area=bounds;hits=List.of();copyButton=removeButton=null;
        FavoriteStore store;
        try { store=source.get(); } catch (RuntimeException e) { empty(g,font,"storage_error");return; }
        if (!store.available()) { empty(g,font,"storage_error");return; }
        if (selected!=null) { renderText(g,font,mx,my);return; }
        var values=store.snapshot();
        int rowHeight=27;maximum=Math.max(0,values.size()*rowHeight-bounds.height());offset=Math.max(0,Math.min(offset,maximum));
        if (values.isEmpty()) { empty(g,font,"empty");return; }
        var next=new ArrayList<Hit>();
        GuiUtil.enableScissor(g,bounds.x(),bounds.y(),bounds.x()+bounds.width(),bounds.y()+bounds.height());
        try {
            for (int i=0;i<values.size();i++) {
                int y=bounds.y()+i*rowHeight-offset;
                if (y+rowHeight<=bounds.y()) continue;if (y>=bounds.y()+bounds.height()) break;
                var item=values.get(i);var row=new ChatLayout.Rect(bounds.x(),y,bounds.width(),25);next.add(new Hit(row,item));
                ChatUi.glass(g,ChatGlass.Surface.CARD,row.x(),row.y(),row.width(),row.height(),row.contains(mx,my));
                ChatUi.label(g,font,ChatUi.truncate(font,item.senderName()+" · "+item.sourceName(),row.width()-10,ChatLayout.META_SCALE),row.x()+4,y+7,ChatLayout.META_SCALE,FontPalette.subtle());
                ChatUi.label(g,font,ChatUi.truncate(font,item.text().replace('\n',' '),row.width()-10,ChatLayout.TEXT_SCALE),row.x()+4,y+17,ChatLayout.TEXT_SCALE,FontPalette.body());
            }
        } finally { GuiUtil.disableScissor(g); }
        hits=List.copyOf(next);
    }
    private void empty(GuiGraphics g,Font font,String key) {
        ChatUi.label(g,font,ChatUi.truncate(font,Component.translatable("mcphone.chat.favorite."+key).getString(),area.width()-6,ChatLayout.TEXT_SCALE),area.x()+3,area.y()+8,ChatLayout.TEXT_SCALE,FontPalette.subtle());
    }
    private void renderText(GuiGraphics g,Font font,int mx,int my) {
        int x=area.x(),y=area.y(),w=area.width();
        ChatUi.label(g,font,ChatUi.truncate(font,selected.senderName()+" · "+selected.sourceName(),w-6,ChatLayout.META_SCALE),x+3,y+5,ChatLayout.META_SCALE,FontPalette.subtle());
        var lines=font.split(Component.literal(selected.text()),ChatLayout.unscaledWidth(w-8,ChatLayout.TEXT_SCALE));
        int step=ChatUi.lineHeight(font,ChatLayout.TEXT_SCALE)+2,top=y+15,bottom=y+area.height()-28;
        textMaximum=Math.max(0,lines.size()*step-Math.max(0,bottom-top));textOffset=Math.max(0,Math.min(textOffset,textMaximum));
        GuiUtil.enableScissor(g,x,top,x+w,Math.max(top,bottom));
        try { int lineY=top-textOffset;for (var line:lines) { ChatUi.text(g,font,line,x+4,lineY,ChatLayout.TEXT_SCALE,FontPalette.body());lineY+=step; } }
        finally { GuiUtil.disableScissor(g); }
        int buttonY=y+area.height()-14,half=(w-3)/2;
        copyButton=new ChatLayout.Rect(x,buttonY,half,14);removeButton=new ChatLayout.Rect(x+half+3,buttonY,w-half-3,14);
        ChatProfileUi.button(g,font,copyButton,Component.translatable("mcphone.chat.message_action.copy_message").getString(),mx,my,FontPalette.body());
        ChatProfileUi.button(g,font,removeButton,ChatProfileUi.tr("remove_favorite"),mx,my,FontPalette.danger());
        if (!status.isEmpty()) ChatUi.label(g,font,ChatUi.truncate(font,Component.translatable(status).getString(),w-6,ChatLayout.META_SCALE),x+3,buttonY-7,ChatLayout.META_SCALE,FontPalette.subtle());
    }
    boolean click(double mx,double my,int button) {
        if (button!=0 || area==null || !area.contains(mx,my)) return false;
        if (selected==null) {
            for (var hit:hits) if (hit.bounds().contains(mx,my)) { selected=hit.entry();textOffset=0;hits=List.of();return true; }
        } else if (copyButton!=null && copyButton.contains(mx,my)) { copy.accept(selected.text()); }
        else if (removeButton!=null && removeButton.contains(mx,my)) {
            FavoriteStore.Result result;
            try { result=source.get().remove(selected.key()); } catch (RuntimeException e) { result=FavoriteStore.Result.STORAGE_ERROR; }
            if (result==FavoriteStore.Result.OK) back();else status="mcphone.chat.favorite.storage_error";
        }
        return true;
    }
    boolean scroll(double mx,double my,double amount) {
        if (area==null || !area.contains(mx,my) || amount==0) return false;
        int delta=amount>0?-12:12;
        if (selected==null) offset=Math.max(0,Math.min(maximum,offset+delta));else textOffset=Math.max(0,Math.min(textMaximum,textOffset+delta));
        hits=List.of();return true;
    }
}
