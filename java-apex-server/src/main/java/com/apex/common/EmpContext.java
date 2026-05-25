package com.apex.common;

/**
 * 员工号上下文 — 基于 ThreadLocal，从请求头 X-Emp-No 中获取当前操作员工号。
 * 前端 Header.vue 中设置员工号后，所有 /api 请求自动携带此请求头。
 */
public final class EmpContext {

    private static final ThreadLocal<String> EMP_NO_HOLDER = new ThreadLocal<>();
    public static final String DEFAULT_EMP_NO = "0000000";

    private EmpContext() {
    }

    public static void setEmpNo(String empNo) {
        EMP_NO_HOLDER.set(empNo);
    }

    public static String getEmpNo() {
        String empNo = EMP_NO_HOLDER.get();
        return empNo != null ? empNo : DEFAULT_EMP_NO;
    }

    public static void clear() {
        EMP_NO_HOLDER.remove();
    }
}
