package com.hjh.tlias.context;

/**
 * 当前登录员工的上下文。
 *
 * 解决的问题：Controller / Service 想拿「当前是谁在操作」，但又不想让每个方法
 * 都多一个 empId 参数、一路从 Controller 传到 Service 再传到 Mapper。
 *
 * 做法是把 empId 存在 ThreadLocal 里。ThreadLocal 可以理解成
 * 「每个线程各自的一个储物柜」——同一个请求从头到尾都在同一个线程上跑，
 * 所以请求开始时把工牌放进柜子，中途任何地方都能打开柜子看到是谁，
 * 请求结束时把柜子清空。
 *
 * ⚠️ 最后那步「清空」不能省。Tomcat 的线程是循环复用的，
 * 这次请求的工牌忘了拿走，下一个不相干的请求就可能捡到它，
 * 表现为「莫名其妙拿到了别人的数据」。
 */
public class BaseContext {

    private static final ThreadLocal<Long> CURRENT_EMP_ID = new ThreadLocal<>();

    public static void setCurrentId(Long empId) {
        CURRENT_EMP_ID.set(empId);
    }

    public static Long getCurrentId() {
        return CURRENT_EMP_ID.get();
    }

    /** 请求结束必须调用，否则线程被复用时会把上一个请求的身份带过去 */
    public static void removeCurrentId() {
        CURRENT_EMP_ID.remove();
    }

    private BaseContext() {
    }

}
