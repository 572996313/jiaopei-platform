package com.cornerstone.core.system.action;

/**
 * 一个"按钮授权项"的代码侧登记。
 *
 * <p>目录只描述"界面上有哪些可授权动作、它们属于哪一页、对应哪个节点/权限码"，
 * 不存授权关系；授权仍然写 {@code sys_role_menu}。客户在界面上勾选的是节点，
 * 代码侧这层只负责把技术码翻译成客户能懂的中文按钮。</p>
 *
 * @param pageMenuCode   所属页面的 {@code sys_menu.menu_code}（页面必须存在且为 menu 类型）
 * @param actionKey      稳定业务键，与列表配置 {@code actions_json[].action} 对齐
 * @param grantMenuCode  承载授权的节点编码；只允许两种形态：
 *                       <ul>
 *                         <li>{@code STANDALONE}：独立的 button 节点（一项权限只服务这个动作）；</li>
 *                         <li>{@code LINKED}：共享的 button 节点，或所属页面节点本身
 *                             （读写同码的页面，勾页面即获得该页全部操作）。</li>
 *                       </ul>
 *                       UI_ONLY 为 null
 * @param permissionCode 该动作依赖的权限码，必须与授权节点的码一致；UI_ONLY 为 null
 * @param label          客户看到的中文按钮名
 * @param description    一句话说明，解释这个按钮会让用户做什么
 * @param mode           授权模式，见 {@link ActionMode}
 * @param sensitive      是否高危动作，界面用醒目样式标记
 * @param sortNo         同页内的展示顺序
 */
public record ActionDefinition(
    String pageMenuCode,
    String actionKey,
    String grantMenuCode,
    String permissionCode,
    String label,
    String description,
    ActionMode mode,
    boolean sensitive,
    int sortNo
) {
}
