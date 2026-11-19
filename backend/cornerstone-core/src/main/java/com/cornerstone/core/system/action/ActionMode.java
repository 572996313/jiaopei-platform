package com.cornerstone.core.system.action;

/**
 * 按钮授权项的授权模式。
 *
 * <p>决定前端角色授权弹窗如何渲染与保存：</p>
 * <ul>
 *   <li>{@link #STANDALONE}：有独立的按钮节点与独立权限码，可单独勾选/撤销。</li>
 *   <li>{@link #LINKED}：与同权限码的其他按钮共用一个授权节点，界面必须标明「同码联动」，
 *       勾一个等于这一组一起开；拆码前所有按钮都是这一档。</li>
 *   <li>{@link #UI_ONLY}：纯前端动作（统计、基于已加载行的导出等），没有后端接口可拦，
 *       不建节点、不造假码，跟随所属页面的读取权限。</li>
 * </ul>
 */
public enum ActionMode {
    STANDALONE,
    LINKED,
    UI_ONLY
}
