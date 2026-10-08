package com.hjh.tlias;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// 告诉 MyBatis-Plus 去这个包下找 Mapper 接口并自动生成实现类。
// 现在这个包还不存在，启动时只会打一行 warning（No MyBatis mapper was found），不影响启动；
// P3 建了 DeptMapper 之后它才会真正干活。
@MapperScan("com.hjh.tlias.mapper")
public class TliasApplication {

    public static void main(String[] args) {
        SpringApplication.run(TliasApplication.class, args);
    }

}
