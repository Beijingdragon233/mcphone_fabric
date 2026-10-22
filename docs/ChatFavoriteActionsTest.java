package com.november.mcphone.feature.chat.client.messageaction;
import java.util.*;
public final class ChatFavoriteActionsTest {
 private static int checks;
 private static void check(boolean ok,String why) { checks++;if (!ok) throw new AssertionError(why); }
 public static void main(String[] args) {
  UUID p=new UUID(0,1),m=new UUID(0,2);var saved=new ArrayList<String>();var copied=new ArrayList<String>();
  var actions=new ChatMessageActions((g,x,y,w,h)->{},copied::add,(a,b,c)->{},r->{},n->{},(a,b,t)->saved.add(t));
  var target=new ChatMessageTarget(p,m,"完整正文\n第二行","正文");actions.execute(target,ChatMessageAction.FAVORITE,100);
  check(saved.equals(List.of(target.fullText())),"收藏完整正文，不截取当前选区");actions.execute(target,ChatMessageAction.COPY,100);check(copied.equals(List.of("正文")),"复制仍尊重选区");
  actions.execute(new ChatMessageTarget(p,m,null,""),ChatMessageAction.FAVORITE,100);check(saved.size()==1,"图片不收藏文字");
  var menu=new ChatContextMenu();menu.open(target,10,10,true);var entries=menu.entries(false);
  check(entries.size()==3&&entries.get(1).labelKey().equals("mcphone.chat.message_action.favorite"),"文字菜单复制收藏删除");
  menu.open(new ChatMessageTarget(p,m,null,""),10,10,true);check(menu.entries(false).size()==1,"图片只保留删除");
  System.out.println("全部通过："+checks+" 条断言");
 }
}
