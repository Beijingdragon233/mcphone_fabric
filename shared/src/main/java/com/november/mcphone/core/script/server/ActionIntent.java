package com.november.mcphone.core.script.server;

/**
 * worker 产出的"要对世界做什么"（施工方案 §15.5）。
 *
 * <p><b>意图不是效果</b>：worker 只能产出它，落地一律回主线程，而且落地前要重查授权。
 *
 * <p>本步只定形状，<b>一个具体的意图类型都不定</b>：给物品、改属性、跑命令模板分别是
 * §18.2 / §18.4 / §26 的事，那时才知道各自要带什么。现在猜一批出来，
 * 到时候没有一个对得上。
 *
 * @param kind    意图种类，如 {@code item.give}。落地时按它分派
 * @param payload 这一条意图的内容，由产出它的那一层与落地那一层商量，本步不解释
 */
public record ActionIntent(String kind, byte[] payload) {

    public ActionIntent {
        if (kind == null || kind.isEmpty()) throw new IllegalArgumentException("ActionIntent.kind 不能为空");
        if (payload == null) payload = new byte[0];
    }
}
