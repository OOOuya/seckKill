package com.anrolsp.seckill.pojo;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class User {
    int id;
    String name;
    String password;
}
