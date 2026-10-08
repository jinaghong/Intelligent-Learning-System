package com.hjh.tlias.common.constant;

/**
 * JWT 载荷（payload）里的自定义字段名。
 *
 * 抽成常量而不是到处写字符串，是为了避免「一处写 empId、另一处写 empid」
 * 这种拼写不一致——这种 bug 不会编译报错，只会在运行时不声不响地取到 null。
 */
public class JwtClaimsConstant {

    public static final String EMP_ID = "empId";

    private JwtClaimsConstant() {
    }

}
