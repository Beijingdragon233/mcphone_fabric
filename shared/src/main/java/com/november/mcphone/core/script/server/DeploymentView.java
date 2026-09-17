package com.november.mcphone.core.script.server;

/**
 * 本服批准了哪些 App 的哪些动作（施工方案 §13.4 第 3 层）。真正的部署表是 S17 的交付物。
 *
 * <p><b>两个轴都要查</b>：§15.8 只写了"未知 appId 不建桶"，那挡不住 actionId 这一轴 ——
 * {@code actionId} 是客户端说了算的字符串（线上允许 64 个字符），
 * 拿一个<b>合法的</b> appId 配上无穷多个伪造 actionId，照样能把限流的桶表撑爆。
 */
public interface DeploymentView {

    /** 这个 App 在本服有已批准的部署吗。 */
    boolean deployed(String appId);

    /** 这个动作在那个部署声明的动作里吗。<b>不在就连桶都不要建。</b> */
    boolean hasAction(String appId, String actionId);

    /** 服务端认的部署版本。与客户端报的对不上就是 VERSION_MISMATCH。 */
    String deployRev(String appId);
}
